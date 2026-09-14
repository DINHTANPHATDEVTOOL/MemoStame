#!/usr/bin/env python3
"""
scripts/resolve_ios_export_options.py

Validates and resolves a robust ExportOptions.plist for Xcode `xcodebuild -exportArchive`.
Guarantees the strict contract:
- Root is a Dictionary
- `provisioningProfiles` is a Dictionary<String, String>, NOT an array
- Maps the target bundle identifier (com.mipastudio.memostamp) to a valid installed profile Name or UUID
- Distribution method uses modern 'app-store-connect' (with 'app-store' compatibility)
- Rejects literal shell parameter-expansion syntax (e.g. '${DEVELOPMENT_TEAM:-...}')
- Derives Team ID from installed profile metadata if not explicitly provided
- Fails early with EXTERNAL_SETUP_REQUIRED when no compatible Apple distribution profile is installed
  instead of falling back to a non-existent static profile name.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import os
import pathlib
import plistlib
import sys
from typing import Any, Dict, List, Optional, Tuple

try:
    from scripts.resolve_ios_signing_context import (
        DEFAULT_BUNDLE_ID,
        default_profile_search_dirs,
        discover_installed_profiles,
        parse_mobileprovision,
        sanitize_team_id,
        validate_profile_metadata,
    )
except ImportError:
    from resolve_ios_signing_context import (  # type: ignore
        DEFAULT_BUNDLE_ID,
        default_profile_search_dirs,
        discover_installed_profiles,
        parse_mobileprovision,
        sanitize_team_id,
        validate_profile_metadata,
    )


VALID_EXPORT_METHODS = (
    "app-store-connect",
    "app-store",
    "ad-hoc",
    "enterprise",
    "development",
)


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
    if not method or method not in VALID_EXPORT_METHODS:
        return False, f"Invalid or missing distribution 'method': {method!r}. Supported: {VALID_EXPORT_METHODS}"

    # Validate Team ID if present
    team_id = plist_data.get("teamID")
    if team_id is not None:
        if not isinstance(team_id, str):
            return False, f"teamID must be a string, got {type(team_id).__name__}"
        cleaned = sanitize_team_id(team_id)
        if not cleaned:
            return False, f"Invalid or unresolved Team ID expression: {team_id!r}"

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


def is_profile_specifier_installed(
    specifier: str,
    bundle_id: str,
    search_dirs: List[pathlib.Path],
    team_id: Optional[str] = None,
    current_time: Optional[datetime] = None,
) -> bool:
    """
    Checks if a profile name or UUID is actually installed on disk and matches bundle_id and team_id.
    """
    installed = discover_installed_profiles(
        bundle_id=bundle_id,
        search_dirs=search_dirs,
        team_id=team_id,
        current_time=current_time,
    )
    for p in installed:
        if p["name"] == specifier or p["uuid"] == specifier:
            return True
    return False


def resolve_export_options(
    dynamic_plist_path: Optional[pathlib.Path],
    static_plist_path: Optional[pathlib.Path],
    output_plist_path: pathlib.Path,
    bundle_id: str = DEFAULT_BUNDLE_ID,
    team_id: Optional[str] = None,
    search_dirs: Optional[List[pathlib.Path]] = None,
    require_installed_profile: bool = True,
    current_time: Optional[datetime] = None,
    export_method: str = "app-store-connect",
) -> bool:
    """
    Resolves a valid ExportOptions dictionary and writes it to output_plist_path.
    Returns True if successfully resolved and validated, False otherwise.
    """
    resolved_data: Optional[Dict[str, Any]] = None
    selection_reason = ""
    dirs = search_dirs if search_dirs is not None else default_profile_search_dirs()

    # Sanitize Team ID: reject literal syntax (${...}), quotes, spaces
    clean_team_id = (
        sanitize_team_id(team_id)
        or sanitize_team_id(os.environ.get("DEVELOPMENT_TEAM"))
        or sanitize_team_id(os.environ.get("APPLE_TEAM_ID"))
        or sanitize_team_id(os.environ.get("APNS_TEAM_ID"))
    )

    # 1. Attempt validation of dynamic plist
    if dynamic_plist_path and dynamic_plist_path.is_file():
        try:
            with dynamic_plist_path.open("rb") as f:
                dyn_data = plistlib.load(f)
            is_valid, msg = validate_export_options(dyn_data, bundle_id)
            if is_valid:
                # If dynamic plist has a clean team ID or we have clean_team_id, ensure it's not a literal string
                dyn_team = sanitize_team_id(dyn_data.get("teamID"))
                if clean_team_id and not dyn_team:
                    dyn_data["teamID"] = clean_team_id
                elif dyn_team:
                    dyn_data["teamID"] = dyn_team
                elif "teamID" in dyn_data and not dyn_team:
                    # Invalid literal teamID in dynamic plist -> reject
                    del dyn_data["teamID"]

                print(f"[INFO] Dynamic export options at '{dynamic_plist_path}' are structurally valid.")
                resolved_data = dyn_data
                selection_reason = f"Validated dynamic plist ({dynamic_plist_path})"
            else:
                print(f"[WARN] Dynamic plist '{dynamic_plist_path}' is INVALID: {msg}")
                print("[WARN] Rejecting dynamic plist. Will NOT pass malformed plist to xcodebuild.")
        except Exception as err:
            print(f"[WARN] Error reading dynamic plist '{dynamic_plist_path}': {err}")

    # 2. Discover installed profiles matching bundle_id (and clean_team_id if known)
    matching_installed = discover_installed_profiles(
        bundle_id=bundle_id,
        search_dirs=dirs,
        team_id=clean_team_id,
        current_time=current_time,
    )

    # If clean_team_id was not provided, derive it from the installed profile
    if not clean_team_id and matching_installed:
        derived_team = sanitize_team_id(matching_installed[0]["team_id"])
        if derived_team:
            clean_team_id = derived_team
            print(f"[INFO] Derived Team ID '{clean_team_id}' from installed provisioning profile metadata.")

    # 3. If dynamic plist was rejected or missing, reconstruct from discovered installed profile
    if resolved_data is None and matching_installed:
        best_profile = matching_installed[0]
        prof_name = best_profile["name"] or best_profile["uuid"]
        prof_team = best_profile["team_id"] or clean_team_id or ""
        print(
            f"[INFO] Discovered matching installed profile: '{best_profile['name']}' "
            f"(UUID: {best_profile['uuid']}, Team: {prof_team}, File: {best_profile['path']})"
        )

        reconstructed = {
            "method": export_method,
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

    # 4. If still unresolved, check static plist template
    if resolved_data is None and static_plist_path and static_plist_path.is_file():
        try:
            with static_plist_path.open("rb") as f:
                static_data = plistlib.load(f)
            is_valid, msg = validate_export_options(static_data, bundle_id)
            if is_valid:
                specifier = static_data.get("provisioningProfiles", {}).get(bundle_id, "")
                # Check if this profile actually exists on the runner
                installed_ok = is_profile_specifier_installed(
                    specifier=specifier,
                    bundle_id=bundle_id,
                    search_dirs=dirs,
                    team_id=clean_team_id,
                    current_time=current_time,
                )
                if installed_ok or not require_installed_profile:
                    resolved_data = static_data
                    if clean_team_id and not resolved_data.get("teamID"):
                        resolved_data["teamID"] = clean_team_id
                    selection_reason = f"Validated static fallback plist ({static_plist_path})"
                else:
                    print(
                        f"[WARN] Static plist '{static_plist_path}' references '{specifier}', "
                        f"but no such profile is installed for team '{clean_team_id or '(any)'}'."
                    )
            else:
                print(f"[WARN] Static plist '{static_plist_path}' is INVALID: {msg}")
        except Exception as err:
            print(f"[WARN] Error reading static plist '{static_plist_path}': {err}")

    # 5. Fail closed if no valid configuration could be resolved
    if resolved_data is None:
        print(
            f"[ERROR] EXTERNAL_SETUP_REQUIRED: No compatible Apple App Store provisioning profile "
            f"installed for bundle ID '{bundle_id}' and team '{clean_team_id or '(unspecified)'}'.",
            file=sys.stderr,
        )
        return False

    # Inject clean_team_id if not present or replace invalid literal teamID
    if clean_team_id:
        resolved_data["teamID"] = clean_team_id

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
    parser.add_argument(
        "--method",
        type=str,
        default="app-store-connect",
        choices=list(VALID_EXPORT_METHODS),
        help="Export distribution method",
    )
    parser.add_argument(
        "--allow-uninstalled-static",
        action="store_true",
        help="Allow static plist even if referenced profile is not installed",
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
        require_installed_profile=not args.allow_uninstalled_static,
        export_method=args.method,
    )

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
