from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path

from .models import PlannedIssue


@dataclass(frozen=True)
class Settings:
    repo: str
    token: str
    poll_seconds: int
    state_db: Path
    work_root: Path
    workspace_mode: str
    repo_path: Path | None
    concurrency: int
    plan: tuple[PlannedIssue, ...]
    enable_codex_worker: bool
    codex_binary: str
    codex_timeout_seconds: int


def load_settings(plan_path: str | Path) -> Settings:
    data = json.loads(Path(plan_path).read_text(encoding="utf-8"))
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise RuntimeError("GITHUB_TOKEN is required")

    repo = os.environ.get("CA_GITHUB_REPO", data.get("repo", ""))
    if not repo:
        raise RuntimeError("CA_GITHUB_REPO or plan.repo is required")

    repo_path_raw = os.environ.get("CA_REPO_PATH")
    plan = tuple(
        PlannedIssue(
            number=int(item["number"]),
            dependencies=tuple(int(n) for n in item.get("dependencies", [])),
            owner_gate=bool(item.get("owner_gate", False)),
        )
        for item in data.get("issues", [])
    )

    return Settings(
        repo=repo,
        token=token,
        poll_seconds=max(10, int(os.environ.get("CA_POLL_SECONDS", "60"))),
        state_db=Path(os.environ.get("CA_STATE_DB", ".ca-orchestrator/state.sqlite3")),
        work_root=Path(os.environ.get("CA_WORK_ROOT", ".ca-orchestrator/workspaces")),
        workspace_mode=os.environ.get("CA_WORKSPACE_MODE", "directory"),
        repo_path=Path(repo_path_raw) if repo_path_raw else None,
        concurrency=max(1, int(os.environ.get("CA_CONCURRENCY", "1"))),
        plan=plan,
        enable_codex_worker=os.environ.get("CA_ENABLE_CODEX_WORKER", "0") == "1",
        codex_binary=os.environ.get("CA_CODEX_BINARY", "codex"),
        codex_timeout_seconds=max(
            60, int(os.environ.get("CA_CODEX_TIMEOUT_SECONDS", "1800"))
        ),
    )
