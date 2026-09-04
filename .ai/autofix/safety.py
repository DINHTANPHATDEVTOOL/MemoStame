import re
from typing import Tuple, List

DENIED_COMMAND_PATTERNS = [
    r"git\s+push\s+.*--force",
    r"git\s+push\s+.*-f\b",
    r"git\s+reset\s+--hard\s+origin/main",
    r"git\s+reset\s+--hard\s+main",
    r"git\s+clean\s+-[a-zA-Z]*f",
    r"rm\s+-rf\s+/",
    r"\bformat\s+[a-zA-Z]:",
    r"\bdiskpart\b",
    r"\bDROP\s+DATABASE\b",
    r"\bDROP\s+TABLE\b",
    r"\bTRUNCATE\b",
    r"supabase\s+db\s+reset\s+--linked",
    r"^(env|set|Get-ChildItem\s+Env:|printenv)\s*$"
]

SECRET_PATTERNS = [
    r"ghp_[a-zA-Z0-9]{36}",
    r"gho_[a-zA-Z0-9]{36}",
    r"sk-[a-zA-Z0-9_-]{20,}",
    r"AIzaSy[a-zA-Z0-9_-]{33}",
    r"eyJ[a-zA-Z0-9_-]+\.eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+"
]

def check_command_safety(command: str) -> Tuple[bool, str]:
    cmd_strip = command.strip()
    for pattern in DENIED_COMMAND_PATTERNS:
        if re.search(pattern, cmd_strip, re.IGNORECASE):
            return False, f"Denied unsafe command pattern: {pattern}"
    return True, "Allowed"

def sanitize_text(text: str) -> str:
    sanitized = text
    for pattern in SECRET_PATTERNS:
        sanitized = re.sub(pattern, "[REDACTED_SECRET]", sanitized)
    return sanitized
