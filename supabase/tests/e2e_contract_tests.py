#!/usr/bin/env python3
"""
MemoStamp Black-Box Multi-User Production Contract E2E Test Suite
Communicates strictly through public client endpoints:
  - /auth/v1
  - /rest/v1
  - /storage/v1
  - /rest/v1/rpc

Safety Rules:
  - Localhost / 127.0.0.1 ONLY. Aborts immediately on remote hosts.
  - Zero token/password/credential logging.
  - Real Auth signup & password login (User A, B, C).
  - Production contracts: Profiles, Friends, Feeds, Storage, DMs, Isolation.
"""

import json
import os
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

# 1x1 Transparent PNG fixture
PNG_1X1_FIXTURE = (
    b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01'
    b'\x08\x06\x00\x00\x00\x1f\x15c4\x00\x00\x00\rIDATx\x9cc\xf8\xff\xff?'
    b'\x00\x05\xfe\x02\xfe\xa76\x814\x00\x00\x00\x00IEND\xaeB`\x82'
)


def sanitize_text(text: str) -> str:
    """Redact tokens, passwords, and sensitive keys from error or log messages."""
    if not isinstance(text, str):
        text = str(text)
    # Redact JWTs
    text = re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[REDACTED_JWT]', text)
    # Redact passwords
    text = re.sub(r'(["\']?password["\']?\s*:\s*["\'])[^"\']+(["\'])', r'\1[REDACTED_PASSWORD]\2', text)
    # Redact bearer auth header
    text = re.sub(r'Bearer\s+[A-Za-z0-9_\-\.]+', 'Bearer [REDACTED_TOKEN]', text, flags=re.IGNORECASE)
    return text


def get_local_config():
    """Retrieve local Supabase URL and anon key safely."""
    supabase_url = os.environ.get("SUPABASE_URL")
    anon_key = os.environ.get("SUPABASE_ANON_KEY")

    if not supabase_url or not anon_key:
        # Try supabase status -o json
        try:
            proc = subprocess.run(
                ["supabase", "status", "-o", "json"],
                capture_output=True,
                text=True,
                check=True,
                timeout=10
            )
            data = json.loads(proc.stdout)
            supabase_url = supabase_url or data.get("API_URL") or data.get("api_url")
            anon_key = anon_key or data.get("ANON_KEY") or data.get("anon_key")
        except Exception:
            pass

    if not supabase_url:
        supabase_url = "http://127.0.0.1:54321"

    # Strict local-only host verification
    parsed = urllib.parse.urlparse(supabase_url)
    hostname = (parsed.hostname or "").lower()
    if hostname not in ("127.0.0.1", "localhost", "::1"):
        print(f"[FATAL] REFUSING TO RUN E2E AGAINST NON-LOCAL SUPABASE: {hostname}", file=sys.stderr)
        sys.exit(1)

    if not anon_key:
        print("[FATAL] Missing SUPABASE_ANON_KEY from environment or 'supabase status'", file=sys.stderr)
        sys.exit(1)

    return supabase_url.rstrip("/"), anon_key


def discover_mail_catcher_url() -> str:
    """Safely determine local Supabase mail catcher URL (Inbucket or Mailpit)."""
    env_url = os.environ.get("MAIL_CATCHER_URL") or os.environ.get("INBUCKET_URL") or os.environ.get("MAILPIT_URL")
    if env_url:
        return env_url.rstrip("/")

    try:
        proc = subprocess.run(
            ["supabase", "status", "-o", "json"],
            capture_output=True,
            text=True,
            timeout=5
        )
        if proc.returncode == 0 and proc.stdout:
            data = json.loads(proc.stdout)
            for k in ("INBUCKET_URL", "inbucket_url", "MAILPIT_URL", "mailpit_url", "MAIL_URL", "mail_url"):
                if data.get(k):
                    return data[k].rstrip("/")
    except Exception:
        pass

    return "http://127.0.0.1:54324"


def _extract_verify_url(body: str) -> str:
    if not body:
        return None
    clean_body = body.replace("&amp;", "&")
    match = re.search(r'https?://[^\s"\'<>]+/auth/v1/verify[^\s"\'<>]+', clean_body)
    if match:
        return match.group(0)
    return None


def fetch_recovery_email_link(mail_base_url: str, target_email: str, timeout_sec: int = 20) -> str:
    """Retrieve recovery email from local Inbucket or Mailpit and extract the verification link."""
    parsed_email = target_email.strip().lower()
    mailbox_name = parsed_email.split("@")[0]
    deadline = time.time() + timeout_sec
    candidates = [mail_base_url, "http://127.0.0.1:54324", "http://127.0.0.1:8025", "http://127.0.0.1:9000"]
    seen = set()
    candidate_urls = [u for u in candidates if u not in seen and not seen.add(u)]

    while time.time() < deadline:
        for base in candidate_urls:
            # 1. Try Inbucket endpoint: GET /api/v1/mailbox/{mailbox}
            try:
                req = urllib.request.Request(f"{base}/api/v1/mailbox/{mailbox_name}")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    if resp.status == 200:
                        messages = json.loads(resp.read().decode("utf-8"))
                        if messages and isinstance(messages, list):
                            msg_id = messages[-1].get("id") or messages[0].get("id")
                            detail_req = urllib.request.Request(f"{base}/api/v1/mailbox/{mailbox_name}/{msg_id}")
                            with urllib.request.urlopen(detail_req, timeout=3) as dresp:
                                msg_data = json.loads(dresp.read().decode("utf-8"))
                                body_text = ""
                                if isinstance(msg_data.get("body"), dict):
                                    body_text = (msg_data["body"].get("text") or "") + " " + (msg_data["body"].get("html") or "")
                                elif isinstance(msg_data.get("body"), str):
                                    body_text = msg_data.get("body")
                                link = _extract_verify_url(body_text)
                                if link:
                                    return link
            except Exception:
                pass

            # 2. Try Mailpit endpoint: GET /api/v1/messages
            try:
                req = urllib.request.Request(f"{base}/api/v1/messages")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    if resp.status == 200:
                        data = json.loads(resp.read().decode("utf-8"))
                        messages = data.get("messages") or []
                        for m in messages:
                            to_list = m.get("To") or []
                            matches_user = any(
                                target_email in (t.get("Address") or "").lower()
                                for t in to_list if isinstance(t, dict)
                            )
                            if matches_user:
                                msg_id = m.get("ID")
                                detail_req = urllib.request.Request(f"{base}/api/v1/message/{msg_id}")
                                with urllib.request.urlopen(detail_req, timeout=3) as dresp:
                                    msg_data = json.loads(dresp.read().decode("utf-8"))
                                    body_text = (msg_data.get("Text") or "") + " " + (msg_data.get("HTML") or "")
                                    link = _extract_verify_url(body_text)
                                    if link:
                                        return link
            except Exception:
                pass

        time.sleep(1)

    raise TimeoutError(f"Timed out waiting for recovery email for user in local mail catcher ({mail_base_url})")


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def follow_recovery_verification(verify_url: str) -> str:
    """Follow verification link without following custom app deep link scheme."""
    opener = urllib.request.build_opener(NoRedirectHandler())
    req = urllib.request.Request(verify_url, headers={"User-Agent": "MemoStamp-E2E/1.0"})
    status = None
    loc = None
    try:
        resp = opener.open(req)
        status = resp.status
        loc = resp.headers.get("Location") or resp.headers.get("location")
    except urllib.error.HTTPError as e:
        status = e.code
        loc = e.headers.get("Location") or e.headers.get("location")

    if status not in (301, 302, 303, 307, 308) or not loc:
        raise ValueError(f"Expected HTTP redirect from recovery verification link, got status {status}")

    return loc


def parse_recovery_redirect(location_url: str) -> dict:
    """Validate canonical recovery redirect URL and extract credentials."""
    parsed = urllib.parse.urlparse(location_url)
    if parsed.scheme.lower() != "memostamp":
        raise ValueError(f"Invalid redirect scheme: {parsed.scheme} (expected memostamp)")
    if (parsed.netloc or "").lower() != "auth":
        raise ValueError(f"Invalid redirect host: {parsed.netloc} (expected auth)")
    if parsed.path.rstrip("/") != "/recovery":
        raise ValueError(f"Invalid redirect path: {parsed.path} (expected /recovery)")

    params = {}
    if parsed.fragment:
        for k, v in urllib.parse.parse_qsl(parsed.fragment):
            params[k] = v
    if parsed.query:
        for k, v in urllib.parse.parse_qsl(parsed.query):
            if k not in params:
                params[k] = v

    type_val = params.get("type", "")
    if type_val and type_val.lower() != "recovery":
        raise ValueError(f"Invalid recovery type: {type_val}")

    token = params.get("access_token") or params.get("token")
    if not token or not token.strip():
        raise ValueError("Missing access token in recovery redirect")

    return {
        "access_token": token.strip(),
        "refresh_token": params.get("refresh_token", "").strip(),
        "type": type_val or "recovery"
    }


class SupabaseHttpClient:
    def __init__(self, base_url: str, anon_key: str):
        self.base_url = base_url
        self.anon_key = anon_key

    def request(self, method: str, path: str, token: str = None, json_data=None, raw_body: bytes = None,
                content_type: str = None, headers: dict = None):
        """Execute HTTP request without logging credentials."""
        url = f"{self.base_url}/{path.lstrip('/')}"
        req_headers = {
            "apikey": self.anon_key,
        }
        if token:
            req_headers["Authorization"] = f"Bearer {token}"
        if headers:
            req_headers.update(headers)

        body_bytes = None
        if json_data is not None:
            body_bytes = json.dumps(json_data).encode("utf-8")
            req_headers["Content-Type"] = content_type or "application/json"
        elif raw_body is not None:
            body_bytes = raw_body
            if content_type:
                req_headers["Content-Type"] = content_type

        req = urllib.request.Request(url, data=body_bytes, headers=req_headers, method=method)

        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                status_code = resp.status
                resp_bytes = resp.read()
                resp_text = resp_bytes.decode("utf-8", errors="replace")
                try:
                    resp_json = json.loads(resp_text)
                except Exception:
                    resp_json = None
                return status_code, resp_json, resp_text, resp_bytes
        except urllib.error.HTTPError as e:
            status_code = e.code
            resp_bytes = e.read()
            resp_text = resp_bytes.decode("utf-8", errors="replace")
            try:
                resp_json = json.loads(resp_text)
            except Exception:
                resp_json = None
            return status_code, resp_json, resp_text, resp_bytes
        except Exception as e:
            sanitized_err = sanitize_text(str(e))
            raise RuntimeError(f"Network error on {method} {path}: {sanitized_err}") from None


class E2EContractRunner:
    def __init__(self, client: SupabaseHttpClient):
        self.client = client
        self.run_id = secrets.token_hex(4)
        self.users = {}  # "A", "B", "C" -> {"email", "password", "uid", "token"}

    def log(self, section: str, message: str):
        print(f"[{section}] {sanitize_text(message)}")

    def assert_status(self, actual: int, expected, scenario: str, method: str, path: str, body: str = ""):
        if isinstance(expected, int):
            expected = [expected]
        if actual not in expected:
            sanitized_body = sanitize_text(body)[:400]
            raise AssertionError(
                f"[FAIL] {scenario} | {method} {path} | Expected status {expected}, got {actual} | Body: {sanitized_body}"
            )

    # ----------------------------------------------------
    # PHASE 1: REAL AUTH & AUTH NEGATIVE
    # ----------------------------------------------------
    def phase1_real_auth(self):
        self.log("PHASE 1", "Starting real Auth signup and password login...")

        for role_name in ("A", "B", "C"):
            email = f"e2e-{role_name.lower()}-{self.run_id}-{secrets.token_hex(4)}@memostamp.test"
            password = f"TestPass123!{secrets.token_hex(6)}"

            status, data, text, _ = self.client.request(
                "POST",
                "/auth/v1/signup",
                json_data={"email": email, "password": password}
            )
            self.assert_status(status, [200, 201], f"Signup {role_name}", "POST", "/auth/v1/signup", text)

            uid = (data.get("user") or {}).get("id") or data.get("id")
            token = data.get("access_token")

            # If confirmations are disabled, token is returned directly on signup;
            # if token is not returned directly, log in to get access token.
            if not token:
                login_status, login_data, login_text, _ = self.client.request(
                    "POST",
                    "/auth/v1/token?grant_type=password",
                    json_data={"email": email, "password": password}
                )
                self.assert_status(login_status, 200, f"Login {role_name}", "POST", "/auth/v1/token", login_text)
                token = login_data.get("access_token")
                uid = uid or (login_data.get("user") or {}).get("id")

            # Validate UID authority
            assert uid and isinstance(uid, str), f"Missing or invalid UID for User {role_name}"
            uuid_obj = uuid.UUID(uid)  # Validates UUID format
            assert token and isinstance(token, str), f"Missing access_token for User {role_name}"

            self.users[role_name] = {
                "email": email,
                "password": password,
                "uid": str(uuid_obj),
                "token": token
            }
            self.log("PHASE 1", f"User {role_name} signed up successfully with valid UUID")

        # Assert distinct UIDs
        uid_a = self.users["A"]["uid"]
        uid_b = self.users["B"]["uid"]
        uid_c = self.users["C"]["uid"]
        assert uid_a != uid_b and uid_b != uid_c and uid_a != uid_c, "UID collision detected!"

        # Exercise password login for User A and verify returned UID matches signup UID
        login_status, login_data, login_text, _ = self.client.request(
            "POST",
            "/auth/v1/token?grant_type=password",
            json_data={"email": self.users["A"]["email"], "password": self.users["A"]["password"]}
        )
        self.assert_status(login_status, 200, "Password login User A", "POST", "/auth/v1/token", login_text)
        login_uid = (login_data.get("user") or {}).get("id")
        assert login_uid == uid_a, f"Login UID mismatch: {login_uid} != {uid_a}"
        self.log("PHASE 1", "User A password login verified: returned UID equals signup UID")

        # Auth Negative 1: Wrong password
        neg_status, _, neg_text, _ = self.client.request(
            "POST",
            "/auth/v1/token?grant_type=password",
            json_data={"email": self.users["A"]["email"], "password": "WrongPassword999!"}
        )
        self.assert_status(neg_status, [400, 401], "Wrong password rejected", "POST", "/auth/v1/token", neg_text)
        self.log("PHASE 1", "Auth negative: wrong password rejected")

        # Auth Negative 2: Protected mutation without Authorization
        neg_status, _, neg_text, _ = self.client.request(
            "POST",
            "/rest/v1/profiles",
            json_data={"id": str(uuid.uuid4()), "username": f"ghost_{self.run_id}"}
        )
        self.assert_status(neg_status, [401, 403], "Unauthenticated mutation denied", "POST", "/rest/v1/profiles", neg_text)
        self.log("PHASE 1", "Auth negative: request without Authorization denied")

        # Auth Negative 3: Invalid Bearer token
        neg_status, _, neg_text, _ = self.client.request(
            "POST",
            "/rest/v1/profiles",
            token="invalid.bearer.token",
            json_data={"id": str(uuid.uuid4()), "username": f"invalid_{self.run_id}"}
        )
        self.assert_status(neg_status, [401, 403], "Invalid Bearer token denied", "POST", "/rest/v1/profiles", neg_text)
        self.log("PHASE 1", "Auth negative: invalid Bearer token denied")

    # ----------------------------------------------------
    # PHASE 2: PROFILES & OWNERSHIP
    # ----------------------------------------------------
    def phase2_profiles(self):
        self.log("PHASE 2", "Starting Profile creation, ownership, and discovery...")
        for role_name in ("A", "B", "C"):
            u = self.users[role_name]
            username = f"user_{role_name.lower()}_{self.run_id}"
            display_name = f"Memo User {role_name}"
            u["username"] = username
            u["display_name"] = display_name

            status, data, text, _ = self.client.request(
                "POST",
                "/rest/v1/profiles",
                token=u["token"],
                headers={"Prefer": "return=representation"},
                json_data={
                    "id": u["uid"],
                    "username": username,
                    "display_name": display_name,
                    "bio": f"Bio of {role_name}"
                }
            )
            self.assert_status(status, [201, 200], f"Create profile {role_name}", "POST", "/rest/v1/profiles", text)
            if isinstance(data, list) and len(data) > 0:
                assert data[0].get("id") == u["uid"], f"Profile ID mismatch for {role_name}"
            self.log("PHASE 2", f"User {role_name} created own profile with identity == auth UID")

        # A updates A's profile -> SUCCESS
        u_a = self.users["A"]
        status, data, text, _ = self.client.request(
            "PATCH",
            f"/rest/v1/profiles?id=eq.{u_a['uid']}",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={"bio": "Bio updated by owner A"}
        )
        self.assert_status(status, 200, "Owner A updates own profile", "PATCH", "/rest/v1/profiles", text)
        assert isinstance(data, list) and len(data) == 1, "Expected exactly 1 updated profile row"
        assert data[0].get("bio") == "Bio updated by owner A"
        self.log("PHASE 2", "User A successfully updated own profile")

        # A attempts to update B's profile -> DENIED (0 rows updated or 403)
        u_b = self.users["B"]
        status, data, text, _ = self.client.request(
            "PATCH",
            f"/rest/v1/profiles?id=eq.{u_b['uid']}",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={"bio": "Hacked by A"}
        )
        if status == 200:
            assert isinstance(data, list) and len(data) == 0, f"Profile RLS leak: A updated B profile: {data}"
        else:
            self.assert_status(status, [400, 403, 404], "A update B profile denied", "PATCH", "/rest/v1/profiles", text)
        self.log("PHASE 2", "User A forbidden from updating User B profile")

        # Public Profile Discovery: A queries public_profiles view for B
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/public_profiles?id=eq.{u_b['uid']}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "Public profile discovery", "GET", "/rest/v1/public_profiles", text)
        assert isinstance(data, list) and len(data) == 1, "Expected User B public profile to be discoverable"
        assert data[0].get("id") == u_b["uid"]
        assert data[0].get("username") == u_b["username"]
        self.log("PHASE 2", "User A successfully discovered User B public profile via public_profiles view")

        # Base profiles table SELECT for B by A is denied
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/profiles?id=eq.{u_b['uid']}",
            token=u_a["token"]
        )
        if status == 200:
            assert isinstance(data, list) and len(data) == 0, "Base profiles table leaked non-owner row to User A"
        else:
            self.assert_status(status, [401, 403], "Base profiles table SELECT denied", "GET", "/rest/v1/profiles", text)
        self.log("PHASE 2", "Base profiles table protects non-owner privacy")

    # ----------------------------------------------------
    # PHASE 3: FRIEND LIFECYCLE
    # ----------------------------------------------------
    def phase3_friend_lifecycle(self):
        self.log("PHASE 3", "Starting friend request lifecycle & RPC tests...")
        u_a = self.users["A"]
        u_b = self.users["B"]
        u_c = self.users["C"]

        # Friend Spoof Test: A attempts to insert friend request pretending sender = B
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/friend_requests",
            token=u_a["token"],
            json_data={
                "id": str(uuid.uuid4()),
                "sender_id": u_b["uid"],
                "recipient_id": u_a["uid"],
                "status": "PENDING"
            }
        )
        self.assert_status(status, [400, 403, 409], "Friend sender spoof denied", "POST", "/rest/v1/friend_requests", text)
        self.log("PHASE 3", "Friend spoof test passed: User A cannot insert with sender = B")

        # Real request: A sends request to B
        req_id = str(uuid.uuid4())
        self.friend_req_id = req_id
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/friend_requests",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": req_id,
                "sender_id": u_a["uid"],
                "recipient_id": u_b["uid"],
                "status": "PENDING",
                "sender_username": u_a["username"],
                "recipient_username": u_b["username"]
            }
        )
        self.assert_status(status, [200, 201], "A sends friend request to B", "POST", "/rest/v1/friend_requests", text)
        self.log("PHASE 3", "Friend request sent from A to B")

        # B sees incoming request
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?id=eq.{req_id}",
            token=u_b["token"]
        )
        self.assert_status(status, 200, "B reads incoming request", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 1, "B could not see incoming friend request"
        assert data[0].get("status") == "PENDING"

        # A sees outgoing request
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?id=eq.{req_id}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "A reads outgoing request", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 1, "A could not see outgoing friend request"

        # C must NOT see A <-> B request
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?id=eq.{req_id}",
            token=u_c["token"]
        )
        self.assert_status(status, 200, "C friend request isolation", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 0, "Privacy Leak: C can see A-B friend request"
        self.log("PHASE 3", "Friend request visibility verified: A and B see it, C does not")

        # Accept Authority Negative 1: Sender A attempts to accept own outgoing request -> DENIED
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/accept_friend_request",
            token=u_a["token"],
            json_data={"p_request_id": req_id}
        )
        self.assert_status(status, [400, 403, 500], "Sender A cannot accept own request", "POST", "/rest/v1/rpc/accept_friend_request", text)

        # Accept Authority Negative 2: Unrelated User C attempts to accept A->B request -> DENIED
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/accept_friend_request",
            token=u_c["token"],
            json_data={"p_request_id": req_id}
        )
        self.assert_status(status, [400, 403, 500], "Third user C cannot accept A-B request", "POST", "/rest/v1/rpc/accept_friend_request", text)
        self.log("PHASE 3", "Accept authority verified: non-recipients rejected")

        # Accept Authority: Recipient B calls accept_friend_request -> SUCCESS
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/accept_friend_request",
            token=u_b["token"],
            json_data={"p_request_id": req_id}
        )
        self.assert_status(status, 200, "Recipient B accepts friend request", "POST", "/rest/v1/rpc/accept_friend_request", text)
        self.log("PHASE 3", "Recipient B successfully accepted friend request via RPC")

        # Verify Canonical Friendship Result
        # A sees friendship
        status, data_a, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friends?or=(and(user_id_1.eq.{u_a['uid']},user_id_2.eq.{u_b['uid']}),and(user_id_1.eq.{u_b['uid']},user_id_2.eq.{u_a['uid']}))",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "A reads friendship", "GET", "/rest/v1/friends", text)
        assert isinstance(data_a, list) and len(data_a) == 1, f"Expected 1 friendship pair for A, got {data_a}"

        # B sees friendship
        status, data_b, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friends?or=(and(user_id_1.eq.{u_a['uid']},user_id_2.eq.{u_b['uid']}),and(user_id_1.eq.{u_b['uid']},user_id_2.eq.{u_a['uid']}))",
            token=u_b["token"]
        )
        self.assert_status(status, 200, "B reads friendship", "GET", "/rest/v1/friends", text)
        assert isinstance(data_b, list) and len(data_b) == 1, f"Expected 1 friendship pair for B, got {data_b}"

        # C sees neither friendship
        status, data_c, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friends?or=(and(user_id_1.eq.{u_a['uid']},user_id_2.eq.{u_b['uid']}),and(user_id_1.eq.{u_b['uid']},user_id_2.eq.{u_a['uid']}))",
            token=u_c["token"]
        )
        self.assert_status(status, 200, "C reads A-B friendship", "GET", "/rest/v1/friends", text)
        assert isinstance(data_c, list) and len(data_c) == 0, f"Privacy Leak: C sees A-B friendship: {data_c}"
        self.log("PHASE 3", "Friendship canonical pair confirmed for A and B; invisible to C")

        # Duplicate accept on non-pending request -> DENIED
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/accept_friend_request",
            token=u_b["token"],
            json_data={"p_request_id": req_id}
        )
        self.assert_status(status, [400, 403, 500], "Repeat accept denied", "POST", "/rest/v1/rpc/accept_friend_request", text)
        self.log("PHASE 3", "Duplicate accept on non-pending request rejected")

    # ----------------------------------------------------
    # PHASE 4: FEED
    # ----------------------------------------------------
    def phase4_feed(self):
        self.log("PHASE 4", "Starting Feed posts, visibility, reactions, comments, and replies...")
        u_a = self.users["A"]
        u_b = self.users["B"]
        u_c = self.users["C"]

        post_only_me_id = f"post_a_only_me_{self.run_id}"
        post_friends_id = f"post_a_friends_{self.run_id}"
        self.post_friends_id = post_friends_id

        # A creates ONLY_ME post
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_posts",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": post_only_me_id,
                "author_id": u_a["uid"],
                "author_name": u_a["display_name"],
                "caption": "Personal private note",
                "audience_type": "ONLY_ME",
                "type": "STAMP"
            }
        )
        self.assert_status(status, [200, 201], "A creates ONLY_ME post", "POST", "/rest/v1/feed_posts", text)

        # A creates FRIENDS post
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_posts",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": post_friends_id,
                "author_id": u_a["uid"],
                "author_name": u_a["display_name"],
                "caption": "Friends-only update",
                "audience_type": "FRIENDS",
                "type": "STAMP"
            }
        )
        self.assert_status(status, [200, 201], "A creates FRIENDS post", "POST", "/rest/v1/feed_posts", text)
        self.log("PHASE 4", "User A created ONLY_ME and FRIENDS posts")

        # Visibility: A sees ONLY_ME
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?id=eq.{post_only_me_id}", token=u_a["token"])
        self.assert_status(status, 200, "A sees own ONLY_ME", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 1

        # Visibility: B must NOT see ONLY_ME
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?id=eq.{post_only_me_id}", token=u_b["token"])
        self.assert_status(status, 200, "B cannot see ONLY_ME", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 0, "Feed Privacy Leak: B saw A's ONLY_ME post"

        # Visibility: C must NOT see ONLY_ME
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?id=eq.{post_only_me_id}", token=u_c["token"])
        self.assert_status(status, 200, "C cannot see ONLY_ME", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 0, "Feed Privacy Leak: C saw A's ONLY_ME post"

        # Visibility: B sees FRIENDS post (A & B are friends)
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?id=eq.{post_friends_id}", token=u_b["token"])
        self.assert_status(status, 200, "Friend B sees FRIENDS post", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 1, "Friend B could not see A's FRIENDS post"

        # Visibility: C does NOT see FRIENDS post (C is not friend)
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?id=eq.{post_friends_id}", token=u_c["token"])
        self.assert_status(status, 200, "Unrelated C cannot see FRIENDS post", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 0, "Feed Privacy Leak: C saw A's FRIENDS post"
        self.log("PHASE 4", "Feed visibility verified: ONLY_ME private, FRIENDS visible to B, hidden from C")

        # Feed Reaction: B reacts to visible FRIENDS post
        reaction_id = f"react_b_{self.run_id}"
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_reactions",
            token=u_b["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": reaction_id,
                "post_id": post_friends_id,
                "user_id": u_b["uid"],
                "emoji": "❤️"
            }
        )
        self.assert_status(status, [200, 201], "B reacts to A post", "POST", "/rest/v1/feed_reactions", text)

        # Feed Reaction Spoof: B attempts reaction with user_id = A -> DENIED
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_reactions",
            token=u_b["token"],
            json_data={
                "id": f"react_spoof_{self.run_id}",
                "post_id": post_friends_id,
                "user_id": u_a["uid"],
                "emoji": "🔥"
            }
        )
        self.assert_status(status, [400, 403, 409], "Reaction user spoof denied", "POST", "/rest/v1/feed_reactions", text)
        self.log("PHASE 4", "Feed reaction verified: legitimate reaction allowed, spoofing user_id denied")

        # Feed Comment: B adds comment to A's FRIENDS post
        comment_id = f"comment_b_{self.run_id}"
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_comments",
            token=u_b["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": comment_id,
                "post_id": post_friends_id,
                "author_id": u_b["uid"],
                "content": "Great stamp photo!"
            }
        )
        self.assert_status(status, [200, 201], "B comments on A post", "POST", "/rest/v1/feed_comments", text)
        assert isinstance(data, list) and len(data) == 1
        assert data[0].get("author_id") == u_b["uid"]

        # Comment Visibility: A and B can read comment; C cannot
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_comments?id=eq.{comment_id}", token=u_a["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_comments?id=eq.{comment_id}", token=u_b["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_comments?id=eq.{comment_id}", token=u_c["token"])
        assert isinstance(data, list) and len(data) == 0, "Feed comment privacy leak: C read comment on inaccessible post"
        self.log("PHASE 4", "Feed comment visibility follows parent post: visible to A & B, hidden from C")

        # Comment Deletion: A attempts to delete B's comment -> DENIED
        status, data, text, _ = self.client.request(
            "DELETE",
            f"/rest/v1/feed_comments?id=eq.{comment_id}",
            token=u_a["token"],
            headers={"Prefer": "return=representation"}
        )
        if status == 200:
            assert isinstance(data, list) and len(data) == 0, "Comment RLS Leak: Non-author A deleted B's comment"
        else:
            self.assert_status(status, [400, 403, 404], "A delete B comment denied", "DELETE", "/rest/v1/feed_comments", text)

        # Comment Deletion: Author B deletes B's comment -> SUCCESS
        status, data, text, _ = self.client.request(
            "DELETE",
            f"/rest/v1/feed_comments?id=eq.{comment_id}",
            token=u_b["token"],
            headers={"Prefer": "return=representation"}
        )
        self.assert_status(status, 200, "Author B deletes own comment", "DELETE", "/rest/v1/feed_comments", text)
        assert isinstance(data, list) and len(data) == 1
        self.log("PHASE 4", "Comment deletion authority verified: only author can delete")

        # Feed Replies: B adds reply to A's FRIENDS post
        reply_id = f"reply_b_{self.run_id}"
        reply_url = "https://example.com/reply_stamp.png"
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_replies",
            token=u_b["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": reply_id,
                "post_id": post_friends_id,
                "author_id": u_b["uid"],
                "reply_stamp_url": reply_url
            }
        )
        self.assert_status(status, [200, 201], "B adds reply to A post", "POST", "/rest/v1/feed_replies", text)

        # Feed Reply Parent Visibility: A & B can read reply; C cannot
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_replies?id=eq.{reply_id}", token=u_a["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_replies?id=eq.{reply_id}", token=u_b["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_replies?id=eq.{reply_id}", token=u_c["token"])
        assert isinstance(data, list) and len(data) == 0, "Feed reply privacy leak: C read reply to inaccessible post"

        # C cannot create reply on inaccessible post
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_replies",
            token=u_c["token"],
            json_data={
                "id": f"reply_c_unauth_{self.run_id}",
                "post_id": post_friends_id,
                "author_id": u_c["uid"],
                "reply_stamp_url": reply_url
            }
        )
        self.assert_status(status, [400, 403, 409], "C insert reply to inaccessible post denied", "POST", "/rest/v1/feed_replies", text)
        self.log("PHASE 4", "Feed replies contract verified: accessible to participants, inaccessible to C")

    # ----------------------------------------------------
    # PHASE 5: STORAGE & MEDIA CONSTRAINTS
    # ----------------------------------------------------
    def phase5_storage(self):
        self.log("PHASE 5", "Starting Storage upload, cross-account security & media constraints...")
        u_a = self.users["A"]
        u_b = self.users["B"]

        # Owner Upload: User A uploads rendered PNG to stamp-media
        object_name = f"{u_a['uid']}/rendered/{uuid.uuid4()}.png"
        self.storage_object_a = object_name

        status, data, text, _ = self.client.request(
            "POST",
            f"/storage/v1/object/stamp-media/{object_name}",
            token=u_a["token"],
            raw_body=PNG_1X1_FIXTURE,
            content_type="image/png"
        )
        self.assert_status(status, [200, 201], "Owner A uploads to stamp-media", "POST", f"/storage/v1/object/stamp-media/{object_name}", text)
        self.log("PHASE 5", "User A successfully uploaded rendered media to stamp-media bucket")

        # Cross-Account Storage Negative 1: User B attempts upload under User A's prefix -> DENIED
        b_spoof_obj = f"{u_a['uid']}/rendered/spoofed_{uuid.uuid4()}.png"
        status, _, text, _ = self.client.request(
            "POST",
            f"/storage/v1/object/stamp-media/{b_spoof_obj}",
            token=u_b["token"],
            raw_body=PNG_1X1_FIXTURE,
            content_type="image/png"
        )
        self.assert_status(status, [400, 403, 404], "B upload under A prefix denied", "POST", f"/storage/v1/object/stamp-media/{b_spoof_obj}", text)

        # Cross-Account Storage Negative 2: User B attempts delete A's object -> DENIED
        status, _, text, _ = self.client.request(
            "DELETE",
            f"/storage/v1/object/stamp-media/{object_name}",
            token=u_b["token"]
        )
        self.assert_status(status, [400, 403, 404], "B delete A object denied", "DELETE", f"/storage/v1/object/stamp-media/{object_name}", text)

        # Cross-Account Storage Negative 3: Anon attempts upload -> DENIED
        anon_obj = f"{u_a['uid']}/rendered/anon_{uuid.uuid4()}.png"
        status, _, text, _ = self.client.request(
            "POST",
            f"/storage/v1/object/stamp-media/{anon_obj}",
            raw_body=PNG_1X1_FIXTURE,
            content_type="image/png"
        )
        self.assert_status(status, [400, 401, 403], "Anon upload denied", "POST", f"/storage/v1/object/stamp-media/{anon_obj}", text)
        self.log("PHASE 5", "Storage cross-account and anon protections verified")

        # Public Read Contract: Unauthenticated GET on public rendered media -> 200 OK & byte match
        status, _, text, resp_bytes = self.client.request(
            "GET",
            f"/storage/v1/object/public/stamp-media/{object_name}"
        )
        self.assert_status(status, 200, "Public read rendered media", "GET", f"/storage/v1/object/public/stamp-media/{object_name}", text)
        assert resp_bytes == PNG_1X1_FIXTURE, "Downloaded public storage bytes do not match uploaded fixture"
        self.log("PHASE 5", "Public read contract for rendered stamp media verified")

        # Media URL Constraints:
        # direct_messages.stamp_image_url
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/direct_messages",
            token=u_a["token"],
            json_data={
                "id": str(uuid.uuid4()),
                "sender_id": u_a["uid"],
                "recipient_id": u_b["uid"],
                "text": "Bad stamp url test",
                "stamp_image_url": "file:///tmp/malicious.png"
            }
        )
        self.assert_status(status, [400, 409], "DM file:// URL constraint rejected", "POST", "/rest/v1/direct_messages", text)

        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/direct_messages",
            token=u_a["token"],
            json_data={
                "id": str(uuid.uuid4()),
                "sender_id": u_a["uid"],
                "recipient_id": u_b["uid"],
                "text": "Data base64 test",
                "stamp_image_url": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
            }
        )
        self.assert_status(status, [400, 409], "DM data: URL constraint rejected", "POST", "/rest/v1/direct_messages", text)

        # feed_posts.stamp_url
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_posts",
            token=u_a["token"],
            json_data={
                "id": f"post_bad_url_{self.run_id}",
                "author_id": u_a["uid"],
                "caption": "Bad stamp post",
                "stamp_url": "content://media/external/images/media/1"
            }
        )
        self.assert_status(status, [400, 409], "Feed post content:// URL constraint rejected", "POST", "/rest/v1/feed_posts", text)

        # feed_replies.reply_stamp_url
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_replies",
            token=u_b["token"],
            json_data={
                "id": f"reply_bad_url_{self.run_id}",
                "post_id": self.post_friends_id,
                "author_id": u_b["uid"],
                "reply_stamp_url": "blob:http://localhost/abc-123"
            }
        )
        self.assert_status(status, [400, 409], "Feed reply blob: URL constraint rejected", "POST", "/rest/v1/feed_replies", text)
        self.log("PHASE 5", "Remote media CHECK constraints verified on DMs, Feed Posts, and Feed Replies")

    # ----------------------------------------------------
    # PHASE 6: DIRECT MESSAGES
    # ----------------------------------------------------
    def phase6_direct_messages(self):
        self.log("PHASE 6", "Starting Direct Messages lifecycle, authority, read RPC, and isolation...")
        u_a = self.users["A"]
        u_b = self.users["B"]
        u_c = self.users["C"]

        dm_id = str(uuid.uuid4())
        dm_text = f"DM Hello Beta {self.run_id}"

        # Real DM INSERT from A to B
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/direct_messages",
            token=u_a["token"],
            headers={"Prefer": "return=representation"},
            json_data={
                "id": dm_id,
                "sender_id": u_a["uid"],
                "recipient_id": u_b["uid"],
                "text": dm_text,
                "is_read": False
            }
        )
        self.assert_status(status, [200, 201], "A sends DM to B", "POST", "/rest/v1/direct_messages", text)

        # Validate Server Authority
        assert isinstance(data, list) and len(data) == 1, "Expected returned DM representation"
        dm_row = data[0]
        assert dm_row.get("id") == dm_id, "DM ID mismatch"
        assert dm_row.get("sender_id") == u_a["uid"], "Sender mismatch"
        assert dm_row.get("recipient_id") == u_b["uid"], "Recipient mismatch"
        assert dm_row.get("created_at") and isinstance(dm_row.get("created_at"), str), "Server timestamp missing"
        assert dm_row.get("is_read") is False
        self.log("PHASE 6", "Server-authoritative DM created with valid server timestamp")

        # DM Message Visibility: A and B can read; C cannot
        status, data, text, _ = self.client.request("GET", f"/rest/v1/direct_messages?id=eq.{dm_id}", token=u_a["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/direct_messages?id=eq.{dm_id}", token=u_b["token"])
        assert isinstance(data, list) and len(data) == 1
        status, data, text, _ = self.client.request("GET", f"/rest/v1/direct_messages?id=eq.{dm_id}", token=u_c["token"])
        assert isinstance(data, list) and len(data) == 0, "DM Privacy Leak: C read A-B direct message"
        self.log("PHASE 6", "DM visibility verified: A & B can read, C receives 0 rows")

        # Sender Spoof: A attempts sender_id = B -> DENIED
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/direct_messages",
            token=u_a["token"],
            json_data={
                "id": str(uuid.uuid4()),
                "sender_id": u_b["uid"],
                "recipient_id": u_a["uid"],
                "text": "Spoofed sender message"
            }
        )
        self.assert_status(status, [400, 403, 409], "DM sender spoof denied", "POST", "/rest/v1/direct_messages", text)
        self.log("PHASE 6", "DM sender spoofing strictly denied")

        # Recipient Mutation: B attempts direct UPDATE on DM text -> DENIED
        status, data, text, _ = self.client.request(
            "PATCH",
            f"/rest/v1/direct_messages?id=eq.{dm_id}",
            token=u_b["token"],
            headers={"Prefer": "return=representation"},
            json_data={"text": "Hacked message content"}
        )
        if status == 200:
            assert isinstance(data, list) and len(data) == 0, "DM RLS Leak: Recipient directly updated message text"
        else:
            self.assert_status(status, [400, 403, 404], "Direct DM update denied", "PATCH", "/rest/v1/direct_messages", text)
        self.log("PHASE 6", "Direct broad DM update forbidden for recipient")

        # Mark Read Negative: A calls mark_direct_messages_read with p_sender_id = B -> updated_count == 0
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/mark_direct_messages_read",
            token=u_a["token"],
            json_data={"p_sender_id": u_b["uid"]}
        )
        self.assert_status(status, 200, "A mark read negative", "POST", "/rest/v1/rpc/mark_direct_messages_read", text)
        assert data.get("updated_count") == 0, "A incorrectly marked read messages where A is not recipient"

        # Mark Read Negative: C calls mark_direct_messages_read with p_sender_id = A -> updated_count == 0
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/mark_direct_messages_read",
            token=u_c["token"],
            json_data={"p_sender_id": u_a["uid"]}
        )
        self.assert_status(status, 200, "C mark read negative", "POST", "/rest/v1/rpc/mark_direct_messages_read", text)
        assert data.get("updated_count") == 0, "C affected A-B direct messages read state"

        # Legitimate Mark Read: Recipient B calls mark_direct_messages_read with p_sender_id = A -> SUCCESS
        status, data, text, _ = self.client.request(
            "POST",
            "/rest/v1/rpc/mark_direct_messages_read",
            token=u_b["token"],
            json_data={"p_sender_id": u_a["uid"]}
        )
        self.assert_status(status, 200, "B marks read A messages", "POST", "/rest/v1/rpc/mark_direct_messages_read", text)
        assert data.get("updated_count") >= 1, "Expected at least 1 message marked as read"

        # Verify is_read is now true
        status, data, text, _ = self.client.request("GET", f"/rest/v1/direct_messages?id=eq.{dm_id}", token=u_b["token"])
        assert isinstance(data, list) and len(data) == 1
        assert data[0].get("is_read") is True, "DM is_read was not updated to true"
        self.log("PHASE 6", "mark_direct_messages_read RPC verified: is_read updated to true")

    # ----------------------------------------------------
    # PHASE 7: ACCOUNT ISOLATION (CROSS-TABLE INVARIANT)
    # ----------------------------------------------------
    def phase7_account_isolation(self):
        self.log("PHASE 7", "Starting cross-table account isolation audit...")
        u_a = self.users["A"]
        u_b = self.users["B"]
        u_c = self.users["C"]

        # C queries all DMs
        status, data, text, _ = self.client.request("GET", "/rest/v1/direct_messages", token=u_c["token"])
        self.assert_status(status, 200, "C queries all DMs", "GET", "/rest/v1/direct_messages", text)
        for row in (data or []):
            assert row.get("sender_id") == u_c["uid"] or row.get("recipient_id") == u_c["uid"], \
                f"Isolation Leak: C saw DM between {row.get('sender_id')} and {row.get('recipient_id')}"

        # C queries all friend requests
        status, data, text, _ = self.client.request("GET", "/rest/v1/friend_requests", token=u_c["token"])
        self.assert_status(status, 200, "C queries all friend requests", "GET", "/rest/v1/friend_requests", text)
        for row in (data or []):
            assert row.get("sender_id") == u_c["uid"] or row.get("recipient_id") == u_c["uid"], \
                f"Isolation Leak: C saw friend request between {row.get('sender_id')} and {row.get('recipient_id')}"

        # C queries all friends
        status, data, text, _ = self.client.request("GET", "/rest/v1/friends", token=u_c["token"])
        self.assert_status(status, 200, "C queries all friends", "GET", "/rest/v1/friends", text)
        for row in (data or []):
            assert row.get("user_id_1") == u_c["uid"] or row.get("user_id_2") == u_c["uid"], \
                f"Isolation Leak: C saw friendship pair ({row.get('user_id_1')}, {row.get('user_id_2')})"

        # C queries all feed posts
        status, data, text, _ = self.client.request("GET", "/rest/v1/feed_posts", token=u_c["token"])
        self.assert_status(status, 200, "C queries feed posts", "GET", "/rest/v1/feed_posts", text)
        for row in (data or []):
            # C should only see posts by C or public posts
            assert row.get("author_id") == u_c["uid"] or row.get("audience_type") == "EVERYONE", \
                f"Isolation Leak: C saw private/friend post of {row.get('author_id')}"

        self.log("PHASE 7", "Cross-table account isolation invariant verified for all users")

    # ----------------------------------------------------
    # PHASE 8: SELF-SERVICE ACCOUNT DELETION & PURGE
    # ----------------------------------------------------
    def phase8_self_service_account_deletion(self):
        self.log("PHASE 8", "Starting self-service account deletion & purge tests...")
        u_a = self.users["A"]
        u_b = self.users["B"]
        u_c = self.users["C"]

        # Step 1: Setup disposable User D
        email_d = f"e2e-d-{self.run_id}-{secrets.token_hex(4)}@memostamp.test"
        password_d = f"TestPass123!{secrets.token_hex(6)}"

        status, data, text, _ = self.client.request(
            "POST",
            "/auth/v1/signup",
            json_data={"email": email_d, "password": password_d}
        )
        self.assert_status(status, [200, 201], "Signup User D", "POST", "/auth/v1/signup", text)

        uid_d = (data.get("user") or {}).get("id") or data.get("id")
        token_d = data.get("access_token")

        if not token_d:
            login_status, login_data, login_text, _ = self.client.request(
                "POST",
                "/auth/v1/token?grant_type=password",
                json_data={"email": email_d, "password": password_d}
            )
            self.assert_status(login_status, 200, "Login User D", "POST", "/auth/v1/token", login_text)
            token_d = login_data.get("access_token")
            uid_d = uid_d or (login_data.get("user") or {}).get("id")

        assert uid_d and isinstance(uid_d, str), "Missing UID for User D"
        assert token_d and isinstance(token_d, str), "Missing access_token for User D"
        uuid.UUID(uid_d)  # Validates UUID format

        # Create Profile for User D
        username_d = f"user_d_{self.run_id}"
        display_name_d = "Disposable User D"
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/profiles",
            token=token_d,
            headers={"Prefer": "return=representation"},
            json_data={
                "id": uid_d,
                "username": username_d,
                "display_name": display_name_d,
                "bio": "To be deleted"
            }
        )
        self.assert_status(status, [200, 201], "Create User D profile", "POST", "/rest/v1/profiles", text)

        # Upload Storage object for User D under stamp-media/<uid>/rendered/...
        object_d_name = f"{uid_d}/rendered/stamp_{uuid.uuid4()}.png"
        status, _, text, _ = self.client.request(
            "POST",
            f"/storage/v1/object/stamp-media/{object_d_name}",
            token=token_d,
            raw_body=PNG_1X1_FIXTURE,
            content_type="image/png"
        )
        self.assert_status(status, [200, 201], "User D uploads media to stamp-media", "POST", f"/storage/v1/object/stamp-media/{object_d_name}", text)

        # Verify Storage object is publicly readable before deletion
        status, _, text, resp_bytes = self.client.request(
            "GET",
            f"/storage/v1/object/public/stamp-media/{object_d_name}"
        )
        self.assert_status(status, 200, "Public read User D media before deletion", "GET", f"/storage/v1/object/public/stamp-media/{object_d_name}", text)
        assert resp_bytes == PNG_1X1_FIXTURE

        # Create Feed Post by User D
        post_d_id = str(uuid.uuid4())
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/feed_posts",
            token=token_d,
            headers={"Prefer": "return=representation"},
            json_data={
                "id": post_d_id,
                "author_id": uid_d,
                "author_name": display_name_d,
                "caption": "Goodbye World",
                "audience_type": "EVERYONE",
                "type": "STAMP"
            }
        )
        self.assert_status(status, [200, 201], "User D creates feed post", "POST", "/rest/v1/feed_posts", text)

        # Create Friend Request involving User D (D -> A)
        freq_d_id = str(uuid.uuid4())
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/friend_requests",
            token=token_d,
            headers={"Prefer": "return=representation"},
            json_data={
                "id": freq_d_id,
                "sender_id": uid_d,
                "recipient_id": u_a["uid"],
                "status": "PENDING"
            }
        )
        self.assert_status(status, [200, 201], "User D sends friend request to A", "POST", "/rest/v1/friend_requests", text)

        # Create Direct Message involving User D (D -> A)
        dm_d_id = str(uuid.uuid4())
        status, _, text, _ = self.client.request(
            "POST",
            "/rest/v1/direct_messages",
            token=token_d,
            headers={"Prefer": "return=representation"},
            json_data={
                "id": dm_d_id,
                "sender_id": uid_d,
                "recipient_id": u_a["uid"],
                "text": "Message from D before deletion"
            }
        )
        self.assert_status(status, [200, 201], "User D sends direct message to A", "POST", "/rest/v1/direct_messages", text)

        # Verify A can see incoming friend request and direct message before deletion
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?id=eq.{freq_d_id}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "A sees incoming friend request from D before deletion", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 1, "A should see D's friend request before deletion"

        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/direct_messages?id=eq.{dm_d_id}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "A sees incoming direct message from D before deletion", "GET", "/rest/v1/direct_messages", text)
        assert isinstance(data, list) and len(data) == 1, "A should see D's direct message before deletion"
        self.log("PHASE 8", "User D setup complete: profile, storage, post, friend request, direct message verified")

        # Step 2: Negative Deletion Tests
        # Negative 1: Call delete endpoint without Authorization header -> 401
        status, _, text, _ = self.client.request(
            "POST",
            "/functions/v1/delete-account",
            json_data={}
        )
        self.assert_status(status, 401, "Delete without token rejected", "POST", "/functions/v1/delete-account", text)

        # Negative 2: Call delete endpoint with invalid Bearer token -> 401
        status, _, text, _ = self.client.request(
            "POST",
            "/functions/v1/delete-account",
            token="invalid.bearer.token",
            json_data={}
        )
        self.assert_status(status, [401, 403], "Delete with invalid token rejected", "POST", "/functions/v1/delete-account", text)

        # Negative 3: Caller tries to pass user_id = A in body -> 400 (Client cannot select deletion target)
        status, _, text, _ = self.client.request(
            "POST",
            "/functions/v1/delete-account",
            token=token_d,
            json_data={"user_id": u_a["uid"]}
        )
        self.assert_status(status, 400, "Explicit user selector in delete body rejected", "POST", "/functions/v1/delete-account", text)

        # Verify User A's profile remains intact
        status, data, text, _ = self.client.request("GET", f"/rest/v1/profiles?id=eq.{u_a['uid']}", token=u_a["token"])
        self.assert_status(status, 200, "User A profile intact after negative test", "GET", "/rest/v1/profiles", text)
        assert isinstance(data, list) and len(data) == 1, "User A was impacted by rejected delete attempt"

        # Negative 4: Wrong HTTP method (GET) -> 405
        status, _, text, _ = self.client.request(
            "GET",
            "/functions/v1/delete-account",
            token=token_d
        )
        self.assert_status(status, 405, "GET method rejected on delete endpoint", "GET", "/functions/v1/delete-account", text)
        self.log("PHASE 8", "Negative deletion tests passed: unauthenticated, invalid token, selector spoofing, wrong method rejected")

        # Step 3: Legitimate Account Deletion for User D
        status, data, text, _ = self.client.request(
            "POST",
            "/functions/v1/delete-account",
            token=token_d,
            json_data={}
        )
        self.assert_status(status, [200, 204], "Legitimate delete User D", "POST", "/functions/v1/delete-account", text)
        if status == 200:
            assert data.get("success") is True, f"Expected success: true in delete response, got {data}"
        self.log("PHASE 8", "Server returned success for account deletion")

        # Step 4: Verify Auth Deletion
        # GET /auth/v1/user with old token must fail
        status, _, text, _ = self.client.request("GET", "/auth/v1/user", token=token_d)
        self.assert_status(status, [401, 403], "Old token invalid after deletion", "GET", "/auth/v1/user", text)

        # Password login for User D must fail
        status, _, text, _ = self.client.request(
            "POST",
            "/auth/v1/token?grant_type=password",
            json_data={"email": email_d, "password": password_d}
        )
        self.assert_status(status, [400, 401], "Login fails for deleted user", "POST", "/auth/v1/token", text)

        # Profile for User D must be gone
        status, data, text, _ = self.client.request("GET", f"/rest/v1/profiles?id=eq.{uid_d}", token=u_a["token"])
        self.assert_status(status, 200, "Query profile for deleted user D", "GET", "/rest/v1/profiles", text)
        assert isinstance(data, list) and len(data) == 0, f"Profile row for deleted user D still exists: {data}"
        self.log("PHASE 8", "Auth deletion verified: token rejected, login denied, profile gone")

        # Step 5: Verify Storage Purge
        status, _, text, _ = self.client.request(
            "GET",
            f"/storage/v1/object/public/stamp-media/{object_d_name}"
        )
        self.assert_status(status, [400, 404], "Public read purged media fails", "GET", f"/storage/v1/object/public/stamp-media/{object_d_name}", text)
        self.log("PHASE 8", "Storage media purge verified: object no longer accessible")

        # Step 6: Verify Database Cascade
        # Feed post by D must be gone
        status, data, text, _ = self.client.request("GET", f"/rest/v1/feed_posts?author_id=eq.{uid_d}", token=u_a["token"])
        self.assert_status(status, 200, "Query feed posts by deleted user D", "GET", "/rest/v1/feed_posts", text)
        assert isinstance(data, list) and len(data) == 0, f"Feed posts for deleted user D still exist: {data}"

        # Specific Friend request involving D must be gone
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?id=eq.{freq_d_id}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "Query specific friend request after D deletion", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 0, f"Friend request {freq_d_id} still exists after D deletion"

        # All friend requests involving D must be gone
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/friend_requests?or=(sender_id.eq.{uid_d},recipient_id.eq.{uid_d})",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "Query friend requests involving user D", "GET", "/rest/v1/friend_requests", text)
        assert isinstance(data, list) and len(data) == 0, f"Friend requests for deleted user D still exist: {data}"

        # Specific Direct message involving D must be gone
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/direct_messages?id=eq.{dm_d_id}",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "Query specific direct message after D deletion", "GET", "/rest/v1/direct_messages", text)
        assert isinstance(data, list) and len(data) == 0, f"Direct message {dm_d_id} still exists after D deletion"

        # All direct messages involving D must be gone
        status, data, text, _ = self.client.request(
            "GET",
            f"/rest/v1/direct_messages?or=(sender_id.eq.{uid_d},recipient_id.eq.{uid_d})",
            token=u_a["token"]
        )
        self.assert_status(status, 200, "Query direct messages involving user D", "GET", "/rest/v1/direct_messages", text)
        assert isinstance(data, list) and len(data) == 0, f"Direct messages for deleted user D still exist: {data}"
        self.log("PHASE 8", "Database cascade verified: feed posts, friend requests, direct messages removed")

        # Step 7: Verify Unrelated Users A, B, C State Remains Intact
        for role, u in (("A", u_a), ("B", u_b), ("C", u_c)):
            status, data, text, _ = self.client.request("GET", f"/rest/v1/profiles?id=eq.{u['uid']}", token=u["token"])
            self.assert_status(status, 200, f"Verify User {role} profile intact", "GET", "/rest/v1/profiles", text)
            assert isinstance(data, list) and len(data) == 1, f"User {role} profile unexpectedly modified: {data}"

        self.log("PHASE 8", "All account deletion & purge contracts successfully verified!")

    # ----------------------------------------------------
    # PHASE 9: PASSWORD RECOVERY & AUTH DEEP LINK E2E
    # ----------------------------------------------------
    def phase9_password_recovery(self):
        self.log("PHASE 9", "Starting password recovery & deep-link contract tests...")

        # E2E TEST 8: Pure redirect URL & deep link parser validation
        self.log("PHASE 9", "Running E2E Test 8: Deep link parser validations...")
        # Valid fragment
        p1 = parse_recovery_redirect("memostamp://auth/recovery#access_token=test_token_123&type=recovery")
        assert p1["access_token"] == "test_token_123"
        assert p1["type"] == "recovery"

        # Valid query
        p2 = parse_recovery_redirect("memostamp://auth/recovery?access_token=test_token_456&type=recovery")
        assert p2["access_token"] == "test_token_456"

        # Wrong scheme rejected
        try:
            parse_recovery_redirect("https://auth/recovery#access_token=abc")
            assert False, "Should reject non-memostamp scheme"
        except ValueError:
            pass

        # Wrong host rejected
        try:
            parse_recovery_redirect("memostamp://profile/recovery#access_token=abc")
            assert False, "Should reject non-auth host"
        except ValueError:
            pass

        # Wrong path rejected
        try:
            parse_recovery_redirect("memostamp://auth/login#access_token=abc")
            assert False, "Should reject non-recovery path"
        except ValueError:
            pass

        # Wrong type rejected
        try:
            parse_recovery_redirect("memostamp://auth/recovery#access_token=abc&type=signup")
            assert False, "Should reject non-recovery type"
        except ValueError:
            pass

        # Missing token rejected
        try:
            parse_recovery_redirect("memostamp://auth/recovery#type=recovery")
            assert False, "Should reject missing token"
        except ValueError:
            pass

        self.log("PHASE 9", "E2E Test 8 PASSED: Parser strict rejection verified")

        # Create isolated Recovery User E
        email_e = f"e2e-recovery-e-{self.run_id}-{secrets.token_hex(4)}@memostamp.test"
        initial_password_e = f"InitialPass123!{secrets.token_hex(6)}"
        new_password_e = f"RecoveredPass456!{secrets.token_hex(6)}"

        status, data, text, _ = self.client.request(
            "POST",
            "/auth/v1/signup",
            json_data={"email": email_e, "password": initial_password_e}
        )
        self.assert_status(status, [200, 201], "Signup Recovery User E", "POST", "/auth/v1/signup", text)
        uid_e = (data.get("user") or {}).get("id") or data.get("id")
        assert uid_e and isinstance(uid_e, str), "Missing UID for Recovery User E"
        uuid.UUID(uid_e)
        self.log("PHASE 9", "Recovery User E created successfully")

        # E2E TEST 6: Unknown email recovery request (Anti-enumeration)
        self.log("PHASE 9", "Running E2E Test 6: Unknown email anti-enumeration check...")
        unknown_email = f"unknown-e-{self.run_id}-{secrets.token_hex(6)}@memostamp.test"
        status, _, text, _ = self.client.request(
            "POST",
            "/auth/v1/recover",
            json_data={
                "email": unknown_email,
                "redirect_to": "memostamp://auth/recovery"
            }
        )
        self.assert_status(status, 200, "Unknown email recovery request", "POST", "/auth/v1/recover", text)
        self.log("PHASE 9", "E2E Test 6 PASSED: Anti-enumeration preserved on unknown email")

        # E2E TEST 1: Known account recovery request & mail delivery
        self.log("PHASE 9", "Running E2E Test 1: Requesting password recovery for User E...")
        status, _, text, _ = self.client.request(
            "POST",
            "/auth/v1/recover",
            json_data={
                "email": email_e,
                "redirect_to": "memostamp://auth/recovery"
            }
        )
        self.assert_status(status, 200, "Password recovery request for User E", "POST", "/auth/v1/recover", text)

        # Retrieve actual recovery email from local mail catcher
        mail_catcher_url = discover_mail_catcher_url()
        self.log("PHASE 9", "Retrieving recovery email from local mail catcher...")
        verify_url = fetch_recovery_email_link(mail_catcher_url, email_e, timeout_sec=20)
        assert verify_url and "/auth/v1/verify" in verify_url, "Verification URL not found in email"

        # Follow verification URL without following custom app scheme
        location_header = follow_recovery_verification(verify_url)
        assert location_header.startswith("memostamp://auth/recovery"), "Redirect Location mismatch"

        recovery_redirect = parse_recovery_redirect(location_header)
        recovery_token = recovery_redirect["access_token"]
        assert recovery_token and len(recovery_token) > 20, "Invalid recovery token format"
        self.log("PHASE 9", "E2E Test 1 PASSED: Real recovery email delivered and valid redirect captured")

        # E2E TEST 2: Validate recovery credential via /auth/v1/user
        self.log("PHASE 9", "Running E2E Test 2: Validating recovery token through /auth/v1/user...")
        status, user_data, text, _ = self.client.request(
            "GET",
            "/auth/v1/user",
            token=recovery_token
        )
        self.assert_status(status, 200, "Validate recovery token", "GET", "/auth/v1/user", text)
        validated_uid = (user_data or {}).get("id")
        validated_email = (user_data or {}).get("email") or ""
        assert validated_uid == uid_e, f"Recovery UID mismatch: {validated_uid} != {uid_e}"
        assert validated_email.lower() == email_e.lower(), f"Recovery email mismatch: {validated_email} != {email_e}"
        self.log("PHASE 9", "E2E Test 2 PASSED: Recovery token validated with matching User E UID")

        # E2E TEST 7: Invalid/tampered recovery credential rejected
        self.log("PHASE 9", "Running E2E Test 7: Tampered recovery token rejection check...")
        status, _, text, _ = self.client.request(
            "GET",
            "/auth/v1/user",
            token="tampered.recovery.token.memostamp"
        )
        self.assert_status(status, [400, 401, 403], "Tampered recovery token rejected", "GET", "/auth/v1/user", text)
        self.log("PHASE 9", "E2E Test 7 PASSED: Tampered token safely rejected")

        # E2E TEST 3: Update password through recovery credential
        self.log("PHASE 9", "Running E2E Test 3: Updating password via recovery bearer...")
        status, update_data, text, _ = self.client.request(
            "PUT",
            "/auth/v1/user",
            token=recovery_token,
            json_data={"password": new_password_e}
        )
        self.assert_status(status, 200, "Update password with recovery token", "PUT", "/auth/v1/user", text)
        updated_uid = (update_data or {}).get("id") or (update_data.get("user") or {}).get("id")
        if updated_uid:
            assert updated_uid == uid_e, "Updated UID does not match User E"
        self.log("PHASE 9", "E2E Test 3 PASSED: Server password update succeeded")

        # E2E TEST 4: Old password login must FAIL
        self.log("PHASE 9", "Running E2E Test 4: Verifying old password login fails...")
        status, _, text, _ = self.client.request(
            "POST",
            "/auth/v1/token?grant_type=password",
            json_data={"email": email_e, "password": initial_password_e}
        )
        self.assert_status(status, [400, 401], "Old password login rejected", "POST", "/auth/v1/token", text)
        self.log("PHASE 9", "E2E Test 4 PASSED: Old password successfully invalidated")

        # E2E TEST 5: New password login must SUCCEED
        self.log("PHASE 9", "Running E2E Test 5: Verifying new password login succeeds...")
        status, login_data, text, _ = self.client.request(
            "POST",
            "/auth/v1/token?grant_type=password",
            json_data={"email": email_e, "password": new_password_e}
        )
        self.assert_status(status, 200, "New password login succeeds", "POST", "/auth/v1/token", text)
        new_uid = (login_data.get("user") or {}).get("id")
        assert new_uid == uid_e, f"New login UID mismatch: {new_uid} != {uid_e}"
        self.log("PHASE 9", "E2E Test 5 PASSED: New password login verified with matching User E UID")

        # Regression: Verify users A, B, C remain intact
        for role, u in (("A", self.users["A"]), ("B", self.users["B"]), ("C", self.users["C"])):
            status, data, text, _ = self.client.request("GET", f"/rest/v1/profiles?id=eq.{u['uid']}", token=u["token"])
            self.assert_status(status, 200, f"Verify User {role} profile intact after recovery", "GET", "/rest/v1/profiles", text)
            assert isinstance(data, list) and len(data) == 1, f"User {role} profile unexpectedly modified"

        self.log("PHASE 9", "All password recovery & deep-link E2E contracts verified!")

    def run_all(self):
        print("=" * 60)
        print("MEMOSTAMP BLACK-BOX E2E CONTRACT GATE SUITE")
        print("=" * 60)
        self.phase1_real_auth()
        self.phase2_profiles()
        self.phase3_friend_lifecycle()
        self.phase4_feed()
        self.phase5_storage()
        self.phase6_direct_messages()
        self.phase7_account_isolation()
        self.phase8_self_service_account_deletion()
        self.phase9_password_recovery()
        print("=" * 60)
        print("ALL BLACK-BOX E2E CONTRACT TESTS PASSED")
        print("=" * 60)


def main():
    base_url, anon_key = get_local_config()
    client = SupabaseHttpClient(base_url, anon_key)
    runner = E2EContractRunner(client)
    try:
        runner.run_all()
    except Exception as e:
        sanitized = sanitize_text(str(e))
        print(f"\n[FATAL ERROR] {sanitized}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
