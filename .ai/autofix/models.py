from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field

class ControllerStatus(str, Enum):
    IDLE = "IDLE"
    ANALYZING = "ANALYZING"
    CODING = "CODING"
    TESTING = "TESTING"
    PUSHING = "PUSHING"
    CI_PENDING = "CI_PENDING"
    REPAIRING = "REPAIRING"
    READY_FOR_REVIEW = "READY_FOR_REVIEW"
    BLOCKED = "BLOCKED"

class FixPlan(BaseModel):
    failure_summary: str = Field(description="Summary of the build/test failure")
    first_error: str = Field(description="First actionable compiler error or assertion failure line")
    root_cause: str = Field(description="Analyzed root cause of the error")
    affected_files: List[str] = Field(default_factory=list, description="Relative file paths likely needing edits")
    search_patterns: List[str] = Field(default_factory=list, description="Patterns/symbols to check for identical bugs")
    recommended_changes: List[str] = Field(default_factory=list, description="High-level fix instructions")
    commands_to_run: List[str] = Field(default_factory=list, description="Verification commands")
    do_not_change: List[str] = Field(default_factory=list, description="Protected scopes or files to keep untouched")
    security_notes: List[str] = Field(default_factory=list, description="Security constraints to enforce")
    confidence: float = Field(default=0.9, description="Confidence score (0.0 to 1.0)")

class JobFailureDetails(BaseModel):
    workflow_name: str
    job_name: str
    job_id: str
    step_name: Optional[str] = None
    first_error: str
    log_snippet: str
    commit_sha: str
    branch: str
    pr_number: Optional[int] = None
    run_id: str

class ControllerState(BaseModel):
    active_issue: Optional[int] = None
    active_branch: Optional[str] = None
    active_pr: Optional[int] = None
    last_processed_run: Optional[str] = None
    iteration: int = 0
    status: ControllerStatus = ControllerStatus.IDLE
    last_error: Optional[str] = None
