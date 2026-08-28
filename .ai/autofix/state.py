import os
import json
import time
from pathlib import Path
from typing import Optional
from models import ControllerState, ControllerStatus

RUNTIME_DIR = Path(__file__).parent / "runtime"
STATE_FILE = RUNTIME_DIR / "state.json"
LOCK_FILE = RUNTIME_DIR / "autofix.lock"
LOG_FILE = RUNTIME_DIR / "autofix.log"

class SingleInstanceLock:
    def __init__(self, lock_path: Path = LOCK_FILE):
        self.lock_path = lock_path
        self._acquired = False

    def acquire(self) -> bool:
        RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
        if self.lock_path.exists():
            try:
                with open(self.lock_path, "r") as f:
                    pid = int(f.read().strip())
                # Check if PID is alive on Windows/Unix
                if self._pid_exists(pid):
                    return False
            except Exception:
                pass
        with open(self.lock_path, "w") as f:
            f.write(str(os.getpid()))
        self._acquired = True
        return True

    def release(self):
        if self._acquired and self.lock_path.exists():
            try:
                self.lock_path.unlink()
            except Exception:
                pass
            self._acquired = False

    def _pid_exists(self, pid: int) -> bool:
        if pid <= 0:
            return False
        try:
            if os.name == 'nt':
                import ctypes
                kernel32 = ctypes.windll.kernel32
                SYNCHRONIZE = 0x00100000
                process = kernel32.OpenProcess(SYNCHRONIZE, False, pid)
                if process:
                    kernel32.CloseHandle(process)
                    return True
                return False
            else:
                os.kill(pid, 0)
                return True
        except Exception:
            return False

def load_state() -> ControllerState:
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        try:
            with open(STATE_FILE, "r") as f:
                data = json.load(f)
            return ControllerState(**data)
        except Exception:
            pass
    return ControllerState()

def save_state(state: ControllerState):
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    with open(STATE_FILE, "w") as f:
        f.write(state.model_dump_json(indent=2))

def append_log(message: str):
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("[%Y-%m-%d %H:%M:%S]")
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(f"{timestamp} {message}\n")
