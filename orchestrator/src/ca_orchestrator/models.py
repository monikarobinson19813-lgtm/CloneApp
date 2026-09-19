from __future__ import annotations

from dataclasses import dataclass
from typing import Literal


@dataclass(frozen=True)
class PlannedIssue:
    number: int
    dependencies: tuple[int, ...] = ()
    owner_gate: bool = False


@dataclass(frozen=True)
class IssueSnapshot:
    number: int
    title: str
    state: Literal["open", "closed"]
    body: str = ""


@dataclass(frozen=True)
class Claim:
    issue_number: int
    workspace: str
    status: str
    claimed_at: str
    attempts: int


@dataclass(frozen=True)
class WorkerRunResult:
    run_id: str
    issue_number: int
    status: str
    workspace: str
    exit_code: int | None = None
    thread_id: str | None = None
    commit_sha: str | None = None
    error_kind: str | None = None
    error_message: str | None = None


@dataclass(frozen=True)
class ReconcileResult:
    state: str
    issue_number: int | None = None
    workspace: str | None = None
    reason: str | None = None
