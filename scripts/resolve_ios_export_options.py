#!/usr/bin/env python3
"""
scripts/resolve-ios-export-options.py

Validates and resolves a robust ExportOptions.plist for Xcode `xcodebuild -exportArchive`.
Guarantees the strict contract:
- Root is a Dictionary
- `provisioningProfiles` is a Dictionary<String, String>, NOT an array
- Maps the target bundle identifier (com.mipastudio.memostamp) to a valid profile name or UUID
- Safely handles malformed dynamic plists (e.g. array-formatted provisioningProfiles from CLI tools)
  by rejecting them and reconstructing valid options from installed .mobileprovision files or static fallback.
"""

from __future__ import annotations

import argparse
import glob
import os
import pathlib
import plistlib
import sys
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_BUNDLE_ID = "com.mipastudio.memostamp"


def validate_export_options(
    plist_data: Any, expected_bundle_id: str
) -> Tuple[bool, str]:
    """
    Validates that plist_data satisfies Apple's -exportOptionsPlist dictionary contract.
    Returns (True, "OK") or (False, "<detailed reason>").
    """
    if not isinstance(plist_data, dict):
        return False, f"Root element is {type(plist_data).__name__}, expected Dictionary"

    method = plist_data.get("method")
    if not method or method not in ("app-store", "ad-hoc", "enterprise", "development"):
        return False, f"Invalid or missing distribution 'method': {method!r}"

    profiles = plist_data.get("provisioningProfiles")
    if profiles is None:
        return False, "Missing 'provisioningProfiles' key in ExportOptions dictionary"

    if isinstance(profiles, list):
        return False, (
            f"'provisioningProfiles' is an Array/List ({len(profiles)} items), "
            "expected Dictionary<String, String>. This causes Xcode status code 70."
        )

    if not isinstance(profiles, dict):
        return False, f"'provisioningProfiles' is {type(profiles).__name__}, expected Dictionary<String, String>"

    if expected_bundle_id not in profiles:
        available_keys = list(profiles.keys())
        return False, (
            f"Bundle identifier '{expected_bundle_id}' not found in provisioningProfiles dictionary. "
            f"Available mappings: {available_keys}"
        )

    profile_specifier = profiles[expected_bundle_id]
    if not isinstance(profile_specifier, str) or not profile_specifier.strip():
        return False, (
            f"Profile specifier for '{expected_bundle_id}' must be a non-empty string, "
            f"got: {profile_specifier!r}"
        )

    return True, "OK"


def parse_mobileprovision(file_path: pathlib.Path) -> Optional[Dict[str, Any]]:
    """
    Extracts and parses the embedded XML plist from a signed .mobileprovision file.
    """
    try:
        data = file_path.read_bytes()
        start = data.find(b"<?xml")
        end = data.find(b"</plist>")
        if start == -1 or end == -1:
            return None
        plist_bytes = data[start : end + len(b"</plist>")]
        parsed = plistlib.loads(plist_bytes)
        if isinstance(parsed, dict):
            return parsed
    except Exception as err:
        print(f"[DEBUG] Failed to parse {file_path}: {err}", file=sys.stderr)
    return None


def discover_installed_profiles(
    bundle_id: str, search_dirs: List[pathlib.Path]
) -> List[Dict[str, Any]]:
    """
    Scans search directories for .mobileprovision files matching the given bundle_id.
    """
    discovered: List[Dict[str, Any]] = []

    for s_dir in search_dirs:
        if not s_dir.is_dir():
            continue
        for p_file in s_dir.glob("*.mobileprovision"):
            info = parse_mobileprovision(p_file)
            if not info:
                continue

            name = info.get("Name", "")
            uuid = info.get("UUID", "")
            team_ids = info.get("TeamIdentifier", [])
            team_id = team_ids[0] if team_ids else ""
            entitlements = info.get("Entitlements", {})
            app_id = entitlements.get("application-identifier", "")
            aps_env = entitlements.get("aps-environment", "")
            get_task_allow = entitlements.get("get-task-allow", False)

            # Match bundle_id either directly or prefixed with Team ID (e.g. TEAMID.bundle_id)
            matches_bundle = (
                app_id == bundle_id
                or app_id.endswith("." + bundle_id)
                or (app_id.endswith(".*") and bundle_id.startswith(app_id[:-2]))
            )

            if matches_bundle:
                discovered.append(
                    {
                        "path": str(p_file),
                        "name": name,
                        "uuid": uuid,
                        "team_id": team_id,
                        "app_id": app_id,
                        "aps_env": aps_env,
                        "get_task_allow": get_task_allow,
                    }
                )

    # Sort profiles: prefer production App Store profiles (get_task_allow == False, aps_env == production)
    discovered.sort(
        key=lambda p: (
            not p["get_task_allow"],
            p["aps_env"] == "production",
            bool(p["name"]),
        ),
        reverse=True,
    )
    return discovered


def resolve_export_options(
    dynamic_plist_path: Optional[pathlib.Path],
    static_plist_path: Optional[pathlib.Path],
    output_plist_path: pathlib.Path,
    bundle_id: str,
    team_id: Optional[str] = None,
    search_dirs: Optional[List[pathlib.Path]] = None,
) -> bool:
    """
    Resolves a valid ExportOptions dictionary and writes it to output_plist_path.
    Returns True if successfully resolved and validated, False otherwise.
    """
    resolved_data: Optional[Dict[str, Any]] = None
    selection_reason = ""

    # 1. Attempt validation of dynamic plist
    if dynamic_plist_path and dynamic_plist_path.is_file():
        try:
            with dynamic_plist_path.open("rb") as f:
                dyn_data = plistlib.load(f)
            is_valid, msg = validate_export_options(dyn_data, bundle_id)
            if is_valid:
                print(f"[INFO] Dynamic export options at '{dynamic_plist_path}' are valid.")
                resolved_data = dyn_data
                selection_reason = f"Validated dynamic plist ({dynamic_plist_path})"
            else:
                print(f"[WARN] Dynamic plist '{dynamic_plist_path}' is INVALID: {msg}")
                print("[WARN] Rejecting dynamic plist. Will NOT pass malformed plist to xcodebuild.")
        except Exception as err:
            print(f"[WARN] Error reading dynamic plist '{dynamic_plist_path}': {err}")

    # 2. If dynamic plist is rejected or missing, discover from installed .mobileprovision
    if resolved_data is None and search_dirs:
        print(f"[INFO] Searching for installed provisioning profiles for bundle ID '{bundle_id}'...")
        matching_profiles = discover_installed_profiles(bundle_id, search_dirs)
        if matching_profiles:
            best_profile = matching_profiles[0]
            prof_name = best_profile["name"] or best_profile["uuid"]
            prof_team = best_profile["team_id"] or team_id or ""
            print(
                f"[INFO] Discovered matching installed profile: '{best_profile['name']}' "
                f"(UUID: {best_profile['uuid']}, Team: {prof_team}, File: {best_profile['path']})"
            )

            reconstructed = {
                "method": "app-store",
                "destination": "export",
                "signingStyle": "manual",
                "stripSwiftSymbols": True,
                "uploadSymbols": True,
                "uploadBitcode": False,
                "compileBitcode": False,
                "provisioningProfiles": {
                    bundle_id: prof_name,
                },
            }
            if prof_team:
                reconstructed["teamID"] = prof_team

            is_valid, msg = validate_export_options(reconstructed, bundle_id)
            if is_valid:
                resolved_data = reconstructed
                selection_reason = f"Reconstructed from installed profile '{prof_name}'"
            else:
                print(f"[WARN] Reconstructed profile plist failed validation: {msg}")

    # 3. Fallback to static plist if dynamic was rejected and reconstruction was not possible
    if resolved_data is None and static_plist_path and static_plist_path.is_file():
        print(f"[INFO] Falling back to static export options '{static_plist_path}'...")
        try:
            with static_plist_path.open("rb") as f:
                static_data = plistlib.load(f)
            is_valid, msg = validate_export_options(static_data, bundle_id)
            if is_valid:
                resolved_data = static_data
                selection_reason = f"Validated static fallback plist ({static_plist_path})"
            else:
                print(f"[WARN] Static plist '{static_plist_path}' is INVALID: {msg}")
        except Exception as err:
            print(f"[WARN] Error reading static plist '{static_plist_path}': {err}")

    # 4. If we still don't have resolved data, fail closed
    if resolved_data is None:
        print(
            f"[ERROR] Could not resolve any valid ExportOptions.plist for bundle ID '{bundle_id}'!",
            file=sys.stderr,
        )
        return False

    # Inject / update teamID if available and not already set
    effective_team = team_id or os.environ.get("DEVELOPMENT_TEAM") or os.environ.get("APNS_TEAM_ID")
    if effective_team and not resolved_data.get("teamID"):
        resolved_data["teamID"] = effective_team

    # Final sanity validation before writing
    final_valid, final_msg = validate_export_options(resolved_data, bundle_id)
    if not final_valid:
        print(f"[ERROR] Resolved data failed final validation: {final_msg}", file=sys.stderr)
        return False

    # Ensure parent directory exists and write plist
    output_plist_path.parent.mkdir(parents=True, exist_ok=True)
    with output_plist_path.open("wb") as f:
        plistlib.dump(resolved_data, f)

    print("=================================================================")
    print("   RESOLVED EXPORT OPTIONS CONTRACT VERIFICATION                 ")
    print("=================================================================")
    print(f"Output File:        {output_plist_path}")
    print(f"Selection Reason:   {selection_reason}")
    print(f"Method:             {resolved_data.get('method')}")
    print(f"Signing Style:      {resolved_data.get('signingStyle')}")
    print(f"Team ID:            {resolved_data.get('teamID', '(none)')}")
    print("provisioningProfiles mapping:")
    for b_id, p_name in resolved_data.get("provisioningProfiles", {}).items():
        print(f"  - {b_id} => {p_name}")
    print("=================================================================")
    print("[SUCCESS] ExportOptions.plist is valid and ready for xcodebuild.")
    return True


def default_profile_search_dirs() -> List[pathlib.Path]:
    home = pathlib.Path.home()
    return [
        home / "Library" / "MobileDevice" / "Provisioning Profiles",
        home / "Library" / "Developer" / "Xcode" / "UserData" / "Provisioning Profiles",
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description="Resolve and validate iOS ExportOptions.plist")
    parser.add_argument("--dynamic-plist", type=pathlib.Path, default=None, help="Dynamic plist from use-profiles")
    parser.add_argument("--static-plist", type=pathlib.Path, default=None, help="Static fallback plist")
    parser.add_argument("--output-plist", type=pathlib.Path, required=True, help="Destination output path")
    parser.add_argument("--bundle-id", type=str, default=DEFAULT_BUNDLE_ID, help="Target bundle identifier")
    parser.add_argument("--team-id", type=str, default=None, help="Optional Apple Development Team ID")
    parser.add_argument(
        "--profiles-dir",
        type=pathlib.Path,
        action="append",
        default=[],
        help="Directories to search for .mobileprovision files",
    )

    args = parser.parse_args()

    search_dirs = args.profiles_dir if args.profiles_dir else default_profile_search_dirs()

    success = resolve_export_options(
        dynamic_plist_path=args.dynamic_plist,
        static_plist_path=args.static_plist,
        output_plist_path=args.output_plist,
        bundle_id=args.bundle_id,
        team_id=args.team_id,
        search_dirs=search_dirs,
    )

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
