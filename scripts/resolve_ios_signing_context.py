#!/usr/bin/env python3
"""
scripts/resolve_ios_signing_context.py

Canonical iOS signing-context resolver for MemoStamp release pipeline.
1. Validates and sanitizes Apple Development Team ID:
   - Rejects literal shell syntax (e.g. '${DEVELOPMENT_TEAM:-...}')
   - Accepts valid 10-character alphanumeric Apple Team IDs
   - Resolves from DEVELOPMENT_TEAM, APPLE_TEAM_ID, APNS_TEAM_ID, or derives from installed profile.
2. Audits installed codesigning identities (security find-identity -v -p codesigning).
3. Discovers and validates installed provisioning profiles:
   - Verifies bundle ID matches 'com.mipastudio.memostamp' (exact or wildcard)
   - Verifies profile has not expired (ExpirationDate > UTC now)
   - Verifies profile is App Store/Distribution (get-task-allow == False)
   - Verifies profile matches resolved Team ID
4. Exports non-secret environment variables and statuses:
   - RESOLVED_DEVELOPMENT_TEAM
   - RESOLVED_PROFILE_NAME
   - RESOLVED_PROFILE_UUID
   - RESOLVED_BUNDLE_ID
   - SIGNING_CONFIG_READY (true/false)
   - SIGNING_STATUS (READY / EXTERNAL_SETUP_REQUIRED)
NEVER logs or exposes private keys, certificates, or passwords.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import os
import pathlib
import plistlib
import re
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_BUNDLE_ID = "com.mipastudio.memostamp"
TEAM_ID_REGEX = re.compile(r"^[A-Z0-9]{10}$")


def sanitize_team_id(candidate: Optional[str]) -> Optional[str]:
    """
    Validates candidate Apple Team ID.
    Rejects literal shell syntax (${...}), quotes, spaces, or non-alphanumeric formats.
    Returns cleaned Team ID if valid, or None.
    """
    if not candidate:
        return None
    cleaned = candidate.strip().strip("'\"")
    if not cleaned:
        return None
    # Reject shell expansion syntax or special characters
    if "${" in cleaned or "}" in cleaned or ":-" in cleaned or "$" in cleaned:
        return None
    if "\n" in cleaned or "\r" in cleaned or " " in cleaned:
        return None
    # Apple Team IDs are 10 alphanumeric characters (e.g., 495W92GA23)
    if TEAM_ID_REGEX.match(cleaned):
        return cleaned
    return None


def parse_mobileprovision(file_path: pathlib.Path) -> Optional[Dict[str, Any]]:
    """
    Extracts and parses embedded XML plist from a .mobileprovision file.
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


def validate_profile_metadata(
    profile_data: Dict[str, Any],
    target_bundle_id: str,
    target_team_id: Optional[str] = None,
    current_time: Optional[datetime] = None,
) -> Tuple[bool, str]:
    """
    Validates provisioning profile metadata against bundle ID, team ID, expiration, and distribution type.
    Returns (is_valid, reason).
    """
    now = current_time or datetime.now(timezone.utc)

    # 1. Expiration Date Check
    exp_date = profile_data.get("ExpirationDate")
    if not exp_date or not isinstance(exp_date, datetime):
        return False, "Missing or invalid ExpirationDate in profile"
    # Ensure timezone awareness for comparison
    if exp_date.tzinfo is None:
        exp_date = exp_date.replace(tzinfo=timezone.utc)
    if exp_date <= now:
        return False, f"Profile expired on {exp_date.isoformat()}"

    # 2. Bundle ID Matching
    entitlements = profile_data.get("Entitlements", {})
    app_id = entitlements.get("application-identifier", "")
    if not app_id:
        return False, "Missing application-identifier entitlement"

    # Match bundle ID: direct, prefixed with TEAM_ID., or wildcard
    matches_bundle = (
        app_id == target_bundle_id
        or app_id.endswith("." + target_bundle_id)
        or (app_id.endswith(".*") and target_bundle_id.startswith(app_id[:-2]))
    )
    if not matches_bundle:
        return False, f"Profile bundle ID '{app_id}' does not match target '{target_bundle_id}'"

    # 3. Team ID Matching (if provided)
    team_ids = profile_data.get("TeamIdentifier", [])
    if not team_ids or not isinstance(team_ids, list):
        return False, "Missing TeamIdentifier in profile"
    prof_team_id = team_ids[0]

    if target_team_id and prof_team_id != target_team_id:
        return False, f"Profile Team ID '{prof_team_id}' does not match target Team ID '{target_team_id}'"

    # 4. Distribution Profile Check (get-task-allow must be False for App Store / TestFlight)
    get_task_allow = entitlements.get("get-task-allow", False)
    if get_task_allow is True:
        return False, "Profile has get-task-allow=True (Development profile, not valid for App Store distribution)"

    return True, "OK"


def discover_installed_profiles(
    bundle_id: str,
    search_dirs: List[pathlib.Path],
    team_id: Optional[str] = None,
    current_time: Optional[datetime] = None,
) -> List[Dict[str, Any]]:
    """
    Scans search directories for valid, unexpired .mobileprovision files matching bundle_id and team_id.
    """
    valid_profiles: List[Dict[str, Any]] = []

    for s_dir in search_dirs:
        if not s_dir.is_dir():
            continue
        for p_file in sorted(s_dir.glob("*.mobileprovision")):
            info = parse_mobileprovision(p_file)
            if not info:
                continue

            is_valid, reason = validate_profile_metadata(
                profile_data=info,
                target_bundle_id=bundle_id,
                target_team_id=team_id,
                current_time=current_time,
            )
            if not is_valid:
                continue

            name = info.get("Name", "")
            uuid = info.get("UUID", "")
            prof_team = info.get("TeamIdentifier", [""])[0]
            entitlements = info.get("Entitlements", {})
            aps_env = entitlements.get("aps-environment", "")

            valid_profiles.append(
                {
                    "path": str(p_file),
                    "name": name,
                    "uuid": uuid,
                    "team_id": prof_team,
                    "aps_env": aps_env,
                    "expiration": info.get("ExpirationDate"),
                }
            )

    # Prefer profiles with aps-environment = production
    valid_profiles.sort(
        key=lambda p: (
            p["aps_env"] == "production",
            bool(p["name"]),
        ),
        reverse=True,
    )
    return valid_profiles


def count_signing_identities() -> int:
    """
    Runs security find-identity on macOS runner to find valid code signing identities.
    Returns 0 if on non-macOS or no identities exist.
    """
    try:
        res = subprocess.run(
            ["security", "find-identity", "-v", "-p", "codesigning"],
            capture_output=True,
            text=True,
            check=False,
        )
        if res.returncode == 0:
            lines = [l for l in res.stdout.splitlines() if re.match(r"^\s*[0-9]+\)", l)]
            return len(lines)
    except FileNotFoundError:
        # Non-macOS environment
        pass
    except Exception as err:
        print(f"[DEBUG] Error querying security find-identity: {err}", file=sys.stderr)
    return 0


def resolve_signing_context(
    bundle_id: str = DEFAULT_BUNDLE_ID,
    env_team_id: Optional[str] = None,
    apple_team_id: Optional[str] = None,
    apns_team_id: Optional[str] = None,
    search_dirs: Optional[List[pathlib.Path]] = None,
    identities_count: Optional[int] = None,
    current_time: Optional[datetime] = None,
) -> Dict[str, Any]:
    """
    Deterministic resolution of signing context.
    Returns a dictionary of resolved variables and statuses.
    """
    # 1. Resolve Team ID from explicit environment variables (sanitizing and rejecting shell expansion)
    resolved_team = (
        sanitize_team_id(env_team_id)
        or sanitize_team_id(apple_team_id)
        or sanitize_team_id(apns_team_id)
    )

    # 2. Discover matching installed provisioning profiles
    dirs = search_dirs if search_dirs is not None else default_profile_search_dirs()
    matching_profiles = discover_installed_profiles(
        bundle_id=bundle_id,
        search_dirs=dirs,
        team_id=resolved_team,
        current_time=current_time,
    )

    # 3. If Team ID was not provided in env, derive from first matching installed profile
    if not resolved_team and matching_profiles:
        derived = sanitize_team_id(matching_profiles[0]["team_id"])
        if derived:
            resolved_team = derived

    # 4. Check identities
    num_identities = identities_count if identities_count is not None else count_signing_identities()

    best_profile = matching_profiles[0] if matching_profiles else None
    profile_name = best_profile["name"] if best_profile else ""
    profile_uuid = best_profile["uuid"] if best_profile else ""

    # Signing is ready if we have a valid resolved team, at least one matching profile,
    # and either we are in an environment with valid identities or running in a context where identity check is bypassed
    signing_ready = bool(resolved_team and best_profile and (num_identities > 0 or identities_count == -1))

    status = "READY" if signing_ready else "EXTERNAL_SETUP_REQUIRED"

    return {
        "RESOLVED_DEVELOPMENT_TEAM": resolved_team or "",
        "RESOLVED_PROFILE_NAME": profile_name,
        "RESOLVED_PROFILE_UUID": profile_uuid,
        "RESOLVED_BUNDLE_ID": bundle_id,
        "DISCOVERED_PROFILES_COUNT": len(matching_profiles),
        "DISCOVERED_IDENTITIES_COUNT": num_identities,
        "SIGNING_CONFIG_READY": "true" if signing_ready else "false",
        "SIGNING_STATUS": status,
    }


def default_profile_search_dirs() -> List[pathlib.Path]:
    home = pathlib.Path.home()
    return [
        home / "Library" / "MobileDevice" / "Provisioning Profiles",
        home / "Library" / "Developer" / "Xcode" / "UserData" / "Provisioning Profiles",
    ]


def write_env_file(context: Dict[str, Any], output_path: pathlib.Path) -> None:
    """
    Writes shell key-value assignments for sourcing.
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"export RESOLVED_DEVELOPMENT_TEAM='{context['RESOLVED_DEVELOPMENT_TEAM']}'",
        f"export RESOLVED_PROFILE_NAME='{context['RESOLVED_PROFILE_NAME']}'",
        f"export RESOLVED_PROFILE_UUID='{context['RESOLVED_PROFILE_UUID']}'",
        f"export RESOLVED_BUNDLE_ID='{context['RESOLVED_BUNDLE_ID']}'",
        f"export SIGNING_CONFIG_READY='{context['SIGNING_CONFIG_READY']}'",
        f"export SIGNING_STATUS='{context['SIGNING_STATUS']}'",
    ]
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Resolve iOS signing context for MemoStamp release")
    parser.add_argument("--bundle-id", type=str, default=DEFAULT_BUNDLE_ID, help="Target bundle ID")
    parser.add_argument("--team-id", type=str, default=None, help="Explicit DEVELOPMENT_TEAM")
    parser.add_argument("--apple-team-id", type=str, default=None, help="APPLE_TEAM_ID")
    parser.add_argument("--apns-team-id", type=str, default=None, help="APNS_TEAM_ID")
    parser.add_argument(
        "--profiles-dir",
        type=pathlib.Path,
        action="append",
        default=[],
        help="Search directory for .mobileprovision",
    )
    parser.add_argument("--output-env", type=pathlib.Path, default=None, help="Path to write shell export env file")
    parser.add_argument("--fail-if-missing", action="store_true", help="Exit code 1 if signing is not ready")

    args = parser.parse_args()

    search_dirs = args.profiles_dir if args.profiles_dir else default_profile_search_dirs()

    context = resolve_signing_context(
        bundle_id=args.bundle_id,
        env_team_id=args.team_id or os.environ.get("DEVELOPMENT_TEAM"),
        apple_team_id=args.apple_team_id or os.environ.get("APPLE_TEAM_ID"),
        apns_team_id=args.apns_team_id or os.environ.get("APNS_TEAM_ID"),
        search_dirs=search_dirs,
    )

    print("=================================================================")
    print("   RESOLVED IOS SIGNING CONTEXT                                  ")
    print("=================================================================")
    print(f"Resolved Team ID:       {context['RESOLVED_DEVELOPMENT_TEAM'] or '(none)'}")
    print(f"Resolved Profile Name: {context['RESOLVED_PROFILE_NAME'] or '(none)'}")
    print(f"Resolved Profile UUID: {context['RESOLVED_PROFILE_UUID'] or '(none)'}")
    print(f"Target Bundle ID:      {context['RESOLVED_BUNDLE_ID']}")
    print(f"Matching Profiles:     {context['DISCOVERED_PROFILES_COUNT']}")
    print(f"Discovered Identities: {context['DISCOVERED_IDENTITIES_COUNT']}")
    print(f"SIGNING_CONFIG_READY:  {context['SIGNING_CONFIG_READY'].upper()}")
    print(f"SIGNING_STATUS:        {context['SIGNING_STATUS']}")
    print("=================================================================")

    if args.output_env:
        write_env_file(context, args.output_env)
        print(f"[INFO] Exported signing context to {args.output_env}")

    if args.fail_if_missing and context["SIGNING_CONFIG_READY"] != "true":
        print("[ERROR] EXTERNAL_SETUP_REQUIRED: Apple distribution credentials or matching App Store profile missing.", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
