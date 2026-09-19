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


@dataclass(frozen=True)
class Claim:
    issue_number: int
    workspace: str
    status: str
    claimed_at: str
    attempts: int


@dataclass(frozen=True)
class ReconcileResult:
    state: str
    issue_number: int | None = None
    workspace: str | None = None
    reason: str | None = None
