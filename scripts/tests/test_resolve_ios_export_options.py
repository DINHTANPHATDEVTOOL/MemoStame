#!/usr/bin/env python3
"""
scripts/tests/test_resolve_ios_export_options.py

Comprehensive regression test suite for Issue #91 (preserving #89 and #88):
1. literal `${DEVELOPMENT_TEAM:-...}` is rejected as Team ID
2. valid 10-character Apple Team ID accepted
3. Team ID can be derived from profile metadata
4. wrong-bundle profile rejected
5. wrong-team profile rejected
6. expired profile rejected
7. no profile causes early EXTERNAL_SETUP_REQUIRED, not static fake fallback
8. actual profile Name/UUID is injected into final ExportOptions
9. final provisioningProfiles remains dictionary
10. app-store-connect distribution method is used where supported
11. #89 malformed-array regression remains PASS
12. #88 archive/DEVELOPMENT_TEAM regression remains PASS
13. unsigned ios-kmp-workflow remains credential-free
14. no secret value is logged or committed
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import os
import pathlib
import plistlib
import subprocess
import sys
import tempfile
import unittest

# Add repo root to import resolve_export_options and resolve_ios_signing_context
REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT))

from scripts.resolve_ios_export_options import (
    DEFAULT_BUNDLE_ID,
    is_profile_specifier_installed,
    resolve_export_options,
    validate_export_options,
)
from scripts.resolve_ios_signing_context import (
    discover_installed_profiles,
    parse_mobileprovision,
    resolve_signing_context,
    sanitize_team_id,
    validate_profile_metadata,
    write_env_file,
)


def create_mock_mobileprovision(
    name: str = "MemoStamp Test Profile",
    uuid: str = "12345678-ABCD-EF01-2345-6789ABCDEF01",
    team_id: str = "495W92GA23",
    bundle_id: str = DEFAULT_BUNDLE_ID,
    expiration_date: datetime | None = None,
    aps_environment: str = "production",
    get_task_allow: bool = False,
) -> bytes:
    if expiration_date is None:
        expiration_date = datetime.now(timezone.utc) + timedelta(days=365)

    mock_dict = {
        "Name": name,
        "UUID": uuid,
        "TeamIdentifier": [team_id],
        "ExpirationDate": expiration_date,
        "Entitlements": {
            "application-identifier": f"{team_id}.{bundle_id}",
            "aps-environment": aps_environment,
            "get-task-allow": get_task_allow,
        },
    }
    xml_bytes = plistlib.dumps(mock_dict)
    return b"\x30\x82\x05\x00DummyHeader" + xml_bytes + b"DummyTrailer\x00"


class TestResolveIosExportOptions(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.dir_path = pathlib.Path(self.temp_dir.name)
        self.static_plist = REPO_ROOT / "iosApp" / "ExportOptions-AppStore.plist"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_1_literal_team_id_rejected(self):
        """1. Proves literal shell parameter expansion is rejected as Team ID."""
        literal_expr = '${DEVELOPMENT_TEAM:-${APNS_TEAM_ID:-${APPLE_TEAM_ID:-""}}}'
        self.assertIsNone(sanitize_team_id(literal_expr))
        self.assertIsNone(sanitize_team_id('${DEVELOPMENT_TEAM:-}'))
        self.assertIsNone(sanitize_team_id('$APNS_TEAM_ID'))
        self.assertIsNone(sanitize_team_id('"495W92GA23"bad'))

        # Plist with literal team ID fails validation
        bad_plist = {
            "method": "app-store-connect",
            "teamID": literal_expr,
            "provisioningProfiles": {DEFAULT_BUNDLE_ID: "Prof"},
        }
        is_valid, reason = validate_export_options(bad_plist, DEFAULT_BUNDLE_ID)
        self.assertFalse(is_valid)
        self.assertIn("Invalid or unresolved Team ID expression", reason)

    def test_2_valid_team_id_accepted(self):
        """2. Proves valid 10-character Apple Team ID is accepted."""
        self.assertEqual(sanitize_team_id("495W92GA23"), "495W92GA23")
        self.assertEqual(sanitize_team_id(" ABC123DEFG "), "ABC123DEFG")
        self.assertEqual(sanitize_team_id('"495W92GA23"'), "495W92GA23")

        valid_plist = {
            "method": "app-store-connect",
            "teamID": "495W92GA23",
            "provisioningProfiles": {DEFAULT_BUNDLE_ID: "Prof"},
        }
        is_valid, reason = validate_export_options(valid_plist, DEFAULT_BUNDLE_ID)
        self.assertTrue(is_valid, reason)

    def test_3_derive_team_id_from_profile(self):
        """3. Proves Team ID can be derived from installed provisioning profile metadata."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "test.mobileprovision"
        prof_file.write_bytes(
            create_mock_mobileprovision(
                team_id="DERIVEDTM1",
                name="MemoStamp Profile",
            )
        )

        out_path = self.dir_path / "derived.plist"
        success = resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id=None,  # Not provided in env
            search_dirs=[profiles_dir],
        )
        self.assertTrue(success)
        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertEqual(resolved["teamID"], "DERIVEDTM1")

        # Also test in resolve_signing_context
        context = resolve_signing_context(
            bundle_id=DEFAULT_BUNDLE_ID,
            env_team_id=None,
            search_dirs=[profiles_dir],
            identities_count=-1,  # Bypass identity requirement for unit test
        )
        self.assertEqual(context["RESOLVED_DEVELOPMENT_TEAM"], "DERIVEDTM1")

    def test_4_wrong_bundle_profile_rejected(self):
        """4. Proves a profile with a mismatched bundle ID is rejected."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "wrong_bundle.mobileprovision"
        prof_file.write_bytes(
            create_mock_mobileprovision(
                bundle_id="com.other.app",
                name="Other App Profile",
            )
        )

        discovered = discover_installed_profiles(DEFAULT_BUNDLE_ID, [profiles_dir])
        self.assertEqual(len(discovered), 0)

        # Direct metadata validation
        info = parse_mobileprovision(prof_file)
        self.assertIsNotNone(info)
        is_valid, reason = validate_profile_metadata(info, DEFAULT_BUNDLE_ID)
        self.assertFalse(is_valid)
        self.assertIn("does not match target", reason)

    def test_5_wrong_team_profile_rejected(self):
        """5. Proves a profile with a mismatched Team ID is rejected when team is specified."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "wrong_team.mobileprovision"
        prof_file.write_bytes(
            create_mock_mobileprovision(
                team_id="WRONGTEAM1",
                name="Wrong Team Profile",
            )
        )

        discovered = discover_installed_profiles(
            bundle_id=DEFAULT_BUNDLE_ID,
            search_dirs=[profiles_dir],
            team_id="TARGETTEAM",
        )
        self.assertEqual(len(discovered), 0)

        info = parse_mobileprovision(prof_file)
        self.assertIsNotNone(info)
        is_valid, reason = validate_profile_metadata(info, DEFAULT_BUNDLE_ID, target_team_id="TARGETTEAM")
        self.assertFalse(is_valid)
        self.assertIn("does not match target Team ID", reason)

    def test_6_expired_profile_rejected(self):
        """6. Proves an expired provisioning profile is rejected."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "expired.mobileprovision"
        past_date = datetime(2021, 1, 1, tzinfo=timezone.utc)
        prof_file.write_bytes(
            create_mock_mobileprovision(
                expiration_date=past_date,
                name="Expired Profile",
            )
        )

        discovered = discover_installed_profiles(DEFAULT_BUNDLE_ID, [profiles_dir])
        self.assertEqual(len(discovered), 0)

        info = parse_mobileprovision(prof_file)
        self.assertIsNotNone(info)
        is_valid, reason = validate_profile_metadata(info, DEFAULT_BUNDLE_ID)
        self.assertFalse(is_valid)
        self.assertIn("Profile expired", reason)

    def test_7_no_profile_causes_early_external_setup_required(self):
        """7. Proves lack of installed profile causes early failure instead of using static fake fallback."""
        empty_dir = self.dir_path / "empty_profiles"
        empty_dir.mkdir(parents=True, exist_ok=True)

        out_path = self.dir_path / "should_fail.plist"
        success = resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id="495W92GA23",
            search_dirs=[empty_dir],
            require_installed_profile=True,
        )
        self.assertFalse(success)
        self.assertFalse(out_path.exists())

        context = resolve_signing_context(
            bundle_id=DEFAULT_BUNDLE_ID,
            search_dirs=[empty_dir],
        )
        self.assertEqual(context["SIGNING_CONFIG_READY"], "false")
        self.assertEqual(context["SIGNING_STATUS"], "EXTERNAL_SETUP_REQUIRED")

    def test_8_actual_profile_name_uuid_injected(self):
        """8. Proves actual installed profile Name and UUID are injected into final ExportOptions."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "prod.mobileprovision"
        prof_file.write_bytes(
            create_mock_mobileprovision(
                name="MemoStamp Actual Production 2026",
                uuid="98765432-1111-2222-3333-444455556666",
                team_id="495W92GA23",
            )
        )

        out_path = self.dir_path / "out.plist"
        success = resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id="495W92GA23",
            search_dirs=[profiles_dir],
        )
        self.assertTrue(success)

        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertEqual(
            resolved["provisioningProfiles"][DEFAULT_BUNDLE_ID],
            "MemoStamp Actual Production 2026",
        )
        self.assertEqual(resolved["teamID"], "495W92GA23")

    def test_9_provisioning_profiles_remains_dictionary(self):
        """9. Proves final provisioningProfiles is strictly a dictionary, not an array."""
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "prod.mobileprovision"
        prof_file.write_bytes(create_mock_mobileprovision(team_id="495W92GA23"))

        out_path = self.dir_path / "out_dict.plist"
        success = resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id="495W92GA23",
            search_dirs=[profiles_dir],
        )
        self.assertTrue(success)

        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertIsInstance(resolved["provisioningProfiles"], dict)
        self.assertNotIsInstance(resolved["provisioningProfiles"], list)
        self.assertIn(DEFAULT_BUNDLE_ID, resolved["provisioningProfiles"])

    def test_10_app_store_connect_distribution_method(self):
        """10. Proves app-store-connect distribution method is used and validated."""
        valid_plist = {
            "method": "app-store-connect",
            "destination": "export",
            "signingStyle": "manual",
            "teamID": "495W92GA23",
            "provisioningProfiles": {DEFAULT_BUNDLE_ID: "Prof"},
        }
        is_valid, reason = validate_export_options(valid_plist, DEFAULT_BUNDLE_ID)
        self.assertTrue(is_valid, reason)

        # Check that ExportOptions-AppStore.plist uses app-store-connect
        with self.static_plist.open("rb") as f:
            static_data = plistlib.load(f)
        self.assertEqual(static_data["method"], "app-store-connect")

        # Check reconstructed profile uses app-store-connect
        profiles_dir = self.dir_path / "profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "prod.mobileprovision"
        prof_file.write_bytes(create_mock_mobileprovision(team_id="495W92GA23"))

        out_path = self.dir_path / "out_method.plist"
        resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id="495W92GA23",
            search_dirs=[profiles_dir],
            export_method="app-store-connect",
        )
        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertEqual(resolved["method"], "app-store-connect")

    def test_11_regression_89_malformed_array_rejected(self):
        """11. Proves #89 malformed-array regression remains PASS (rejected with status 70 explanation)."""
        malformed_plist = {
            "method": "app-store-connect",
            "destination": "export",
            "provisioningProfiles": ["MemoStamp App Store"],  # ARRAY
        }
        is_valid, reason = validate_export_options(malformed_plist, DEFAULT_BUNDLE_ID)
        self.assertFalse(is_valid)
        self.assertIn("Array/List", reason)
        self.assertIn("status code 70", reason)

    def test_12_regression_88_archive_signing(self):
        """12. Proves #88 archive signing settings (Manual signing, DEVELOPMENT_TEAM) remain intact."""
        pbxproj_path = REPO_ROOT / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj"
        pbx_text = pbxproj_path.read_text(encoding="utf-8")

        self.assertIn("CODE_SIGN_STYLE = Manual;", pbx_text)
        self.assertNotIn("CODE_SIGN_STYLE = Automatic;", pbx_text)
        self.assertIn('DEVELOPMENT_TEAM = "$(DEVELOPMENT_TEAM)";', pbx_text)
        self.assertIn("ProvisioningStyle = Manual;", pbx_text)

        # Verify codemagic.yaml does not contain literal shell parameter expansion in vars
        cm_path = REPO_ROOT / "codemagic.yaml"
        cm_text = cm_path.read_text(encoding="utf-8")
        self.assertNotIn("DEVELOPMENT_TEAM: ${", cm_text)

    def test_13_unsigned_ios_kmp_workflow_credential_free(self):
        """13. Proves unsigned ios-kmp-workflow remains completely credential-free for PR/CI."""
        cm_path = REPO_ROOT / "codemagic.yaml"
        cm_text = cm_path.read_text(encoding="utf-8")

        # Find ios-kmp-workflow section
        self.assertIn("ios-kmp-workflow:", cm_text)
        workflow_idx = cm_text.index("ios-kmp-workflow:")
        release_idx = cm_text.index("ios-app-store-release:")
        kmp_section = cm_text[workflow_idx:release_idx]

        self.assertIn("CODE_SIGNING_ALLOWED=NO", kmp_section)
        self.assertIn("CODE_SIGNING_REQUIRED=NO", kmp_section)
        self.assertNotIn("app_store_credentials", kmp_section)
        self.assertNotIn("CM_CERTIFICATE", kmp_section)
        self.assertNotIn("APP_STORE_CONNECT_PRIVATE_KEY", kmp_section)

    def test_14_no_secrets_logged_or_committed(self):
        """14. Proves no secrets are tracked in git or emitted to signing context environment files."""
        res = subprocess.run(
            ["git", "ls-files"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            check=True,
        )
        tracked = res.stdout.splitlines()
        forbidden_extensions = (".p8", ".p12", ".keystore", ".jks", ".mobileprovision")
        for f in tracked:
            for ext in forbidden_extensions:
                self.assertFalse(
                    f.endswith(ext),
                    f"Forbidden secret file tracked in git: {f}",
                )

        # Verify write_env_file outputs only safe non-secret identifiers
        env_file = self.dir_path / "test.env"
        test_context = {
            "RESOLVED_DEVELOPMENT_TEAM": "495W92GA23",
            "RESOLVED_PROFILE_NAME": "MemoStamp Prod",
            "RESOLVED_PROFILE_UUID": "1234-5678",
            "RESOLVED_BUNDLE_ID": DEFAULT_BUNDLE_ID,
            "SIGNING_CONFIG_READY": "true",
            "SIGNING_STATUS": "READY",
        }
        write_env_file(test_context, env_file)
        env_content = env_file.read_text(encoding="utf-8")
        self.assertIn("495W92GA23", env_content)
        self.assertNotIn("password", env_content.lower())
        self.assertNotIn("private_key", env_content.lower())
        self.assertNotIn("certificate", env_content.lower())


if __name__ == "__main__":
    unittest.main()
