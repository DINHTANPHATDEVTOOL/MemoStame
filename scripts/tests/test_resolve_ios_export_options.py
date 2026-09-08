#!/usr/bin/env python3
"""
scripts/tests/test_resolve_ios_export_options.py

Regression test suite for Issue #89:
1. Malformed array plist is rejected.
2. Valid dictionary plist is accepted.
3. Bundle ID mapping (com.mipastudio.memostamp) is preserved.
4. Installed .mobileprovision parsing and reconstruction.
5. Safe fallback to iosApp/ExportOptions-AppStore.plist.
6. Invalid plist never produces invalid output for xcodebuild -exportArchive.
7. #88 archive signing settings (CODE_SIGN_STYLE = Manual, DEVELOPMENT_TEAM) do not regress.
"""

from __future__ import annotations

import os
import pathlib
import plistlib
import subprocess
import sys
import tempfile
import unittest

# Add repo root to import resolve_export_options
REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT))

from scripts.resolve_ios_export_options import (
    DEFAULT_BUNDLE_ID,
    discover_installed_profiles,
    parse_mobileprovision,
    resolve_export_options,
    validate_export_options,
)


class TestResolveIosExportOptions(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.dir_path = pathlib.Path(self.temp_dir.name)
        self.static_plist = REPO_ROOT / "iosApp" / "ExportOptions-AppStore.plist"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_reject_array_provisioning_profiles(self):
        """Proves that a dynamic plist with an array for provisioningProfiles is rejected."""
        malformed_plist = {
            "method": "app-store",
            "destination": "export",
            "provisioningProfiles": ["MemoStamp App Store"],  # ARRAY -> MALFORMED
        }
        is_valid, reason = validate_export_options(malformed_plist, DEFAULT_BUNDLE_ID)
        self.assertFalse(is_valid)
        self.assertIn("Array/List", reason)
        self.assertIn("status code 70", reason)

        # Ensure resolve_export_options rejects it and falls back to static dictionary
        dyn_path = self.dir_path / "dynamic_malformed.plist"
        out_path = self.dir_path / "resolved.plist"
        with dyn_path.open("wb") as f:
            plistlib.dump(malformed_plist, f)

        success = resolve_export_options(
            dynamic_plist_path=dyn_path,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
        )
        self.assertTrue(success)
        self.assertTrue(out_path.is_file())

        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertIsInstance(resolved["provisioningProfiles"], dict)
        self.assertIn(DEFAULT_BUNDLE_ID, resolved["provisioningProfiles"])

    def test_accept_valid_dictionary_plist(self):
        """Proves that a valid dynamic plist with Dictionary provisioningProfiles is accepted."""
        valid_plist = {
            "method": "app-store",
            "destination": "export",
            "signingStyle": "manual",
            "provisioningProfiles": {
                DEFAULT_BUNDLE_ID: "MemoStamp App Store Dynamic",
            },
        }
        is_valid, reason = validate_export_options(valid_plist, DEFAULT_BUNDLE_ID)
        self.assertTrue(is_valid, reason)

        dyn_path = self.dir_path / "dynamic_valid.plist"
        out_path = self.dir_path / "resolved_valid.plist"
        with dyn_path.open("wb") as f:
            plistlib.dump(valid_plist, f)

        success = resolve_export_options(
            dynamic_plist_path=dyn_path,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
        )
        self.assertTrue(success)

        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertEqual(
            resolved["provisioningProfiles"][DEFAULT_BUNDLE_ID],
            "MemoStamp App Store Dynamic",
        )

    def test_bundle_id_mapping_preserved(self):
        """Proves bundle ID com.mipastudio.memostamp mapping is strictly preserved."""
        out_path = self.dir_path / "resolved_mapping.plist"
        success = resolve_export_options(
            dynamic_plist_path=None,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            team_id="495W92GA23",
        )
        self.assertTrue(success)
        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertIn(DEFAULT_BUNDLE_ID, resolved["provisioningProfiles"])
        self.assertEqual(resolved["teamID"], "495W92GA23")

    def test_reconstruction_from_installed_mobileprovision(self):
        """Proves profile name and UUID reconstruction from installed .mobileprovision files."""
        # Create a mock .mobileprovision container
        mock_profile_xml = {
            "Name": "MemoStamp Discovered Production Profile",
            "UUID": "12345678-ABCD-EF01-2345-6789ABCDEF01",
            "TeamIdentifier": ["999TEAMID99"],
            "Entitlements": {
                "application-identifier": f"999TEAMID99.{DEFAULT_BUNDLE_ID}",
                "aps-environment": "production",
                "get-task-allow": False,
            },
        }
        xml_bytes = plistlib.dumps(mock_profile_xml)
        # Wrap with dummy CMS header/trailer
        container_bytes = b"\x30\x82\x05\x00DummyHeader" + xml_bytes + b"DummyTrailer\x00"

        profiles_dir = self.dir_path / "Provisioning Profiles"
        profiles_dir.mkdir(parents=True, exist_ok=True)
        prof_file = profiles_dir / "test_profile.mobileprovision"
        prof_file.write_bytes(container_bytes)

        discovered = discover_installed_profiles(DEFAULT_BUNDLE_ID, [profiles_dir])
        self.assertEqual(len(discovered), 1)
        self.assertEqual(discovered[0]["name"], "MemoStamp Discovered Production Profile")
        self.assertEqual(discovered[0]["uuid"], "12345678-ABCD-EF01-2345-6789ABCDEF01")
        self.assertEqual(discovered[0]["team_id"], "999TEAMID99")

        # Now resolve export options when dynamic plist is malformed: should use discovered profile!
        dyn_path = self.dir_path / "dynamic_bad.plist"
        with dyn_path.open("wb") as f:
            plistlib.dump({"method": "app-store", "provisioningProfiles": []}, f)

        out_path = self.dir_path / "reconstructed_out.plist"
        success = resolve_export_options(
            dynamic_plist_path=dyn_path,
            static_plist_path=self.static_plist,
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            search_dirs=[profiles_dir],
        )
        self.assertTrue(success)

        with out_path.open("rb") as f:
            resolved = plistlib.load(f)
        self.assertEqual(
            resolved["provisioningProfiles"][DEFAULT_BUNDLE_ID],
            "MemoStamp Discovered Production Profile",
        )
        self.assertEqual(resolved["teamID"], "999TEAMID99")

    def test_invalid_plist_never_passed_to_xcodebuild(self):
        """Proves that if neither dynamic, installed, nor static plists are valid, resolver fails closed."""
        out_path = self.dir_path / "should_not_exist.plist"
        dyn_path = self.dir_path / "invalid_dyn.plist"
        with dyn_path.open("wb") as f:
            plistlib.dump({"method": "invalid-method", "provisioningProfiles": []}, f)

        success = resolve_export_options(
            dynamic_plist_path=dyn_path,
            static_plist_path=None,  # No static fallback
            output_plist_path=out_path,
            bundle_id=DEFAULT_BUNDLE_ID,
            search_dirs=[],  # No installed profiles
        )
        self.assertFalse(success)
        self.assertFalse(out_path.exists())

    def test_no_regression_88_archive_signing(self):
        """Proves that #88 fixes in project.pbxproj and codemagic.yaml are intact."""
        pbxproj_path = REPO_ROOT / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj"
        pbx_text = pbxproj_path.read_text(encoding="utf-8")

        # Verify CODE_SIGN_STYLE is Manual and DEVELOPMENT_TEAM is wired
        self.assertIn("CODE_SIGN_STYLE = Manual;", pbx_text)
        self.assertNotIn("CODE_SIGN_STYLE = Automatic;", pbx_text)
        self.assertIn('DEVELOPMENT_TEAM = "$(DEVELOPMENT_TEAM)";', pbx_text)
        self.assertIn("ProvisioningStyle = Manual;", pbx_text)

        # Verify codemagic.yaml preserves unsigned workflow and credential wiring
        cm_path = REPO_ROOT / "codemagic.yaml"
        cm_text = cm_path.read_text(encoding="utf-8")
        self.assertIn("ios-kmp-workflow", cm_text)
        self.assertIn("CODE_SIGNING_ALLOWED=NO", cm_text)
        self.assertIn("CM_PROVISIONING_PROFILE", cm_text)
        self.assertIn("CM_CERTIFICATE", cm_text)
        self.assertIn("APP_STORE_CONNECT_PRIVATE_KEY", cm_text)


if __name__ == "__main__":
    unittest.main()
