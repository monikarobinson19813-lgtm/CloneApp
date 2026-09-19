from __future__ import annotations

import json
import subprocess
from pathlib import Path


class WorkspaceManager:
    """Creates one isolated workspace per claimed issue.

    Directory mode is useful for orchestration tests. Worktree mode is the
    production path and creates an isolated Git worktree/branch per issue.
    """

    def __init__(
        self,
        root: str | Path,
        mode: str = "directory",
        repo_path: str | Path | None = None,
        branch_prefix: str = "agent",
    ) -> None:
        if mode not in {"directory", "worktree"}:
            raise ValueError("workspace mode must be 'directory' or 'worktree'")
        if mode == "worktree" and repo_path is None:
            raise ValueError("repo_path is required for worktree mode")
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.mode = mode
        self.repo_path = Path(repo_path) if repo_path is not None else None
        self.branch_prefix = branch_prefix

    def create_repair(
        self,
        issue_number: int,
        run_id: int,
        head_branch: str,
    ) -> Path:
        """Create an isolated repair workspace from the branch that actually failed CI."""
        path = self.root / f"repair-issue-{issue_number}-run-{run_id}"
        if path.exists():
            return path

        base_ref = head_branch
        if self.mode == "worktree":
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(self.repo_path),
                    "fetch",
                    "origin",
                    head_branch,
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            base_ref = f"origin/{head_branch}"
            branch = f"{self.branch_prefix}/{issue_number}-repair-{run_id}"
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(self.repo_path),
                    "worktree",
                    "add",
                    "-B",
                    branch,
                    str(path),
                    base_ref,
                ],
                check=True,
                capture_output=True,
                text=True,
            )
        else:
            path.mkdir(parents=True)

        (path / ".ca-workspace.json").write_text(
            json.dumps(
                {
                    "issue_number": issue_number,
                    "run_id": run_id,
                    "mode": self.mode,
                    "kind": "repair",
                    "head_branch": head_branch,
                    "base_ref": base_ref,
                },
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        return path

    def create(self, issue_number: int, base_ref: str = "origin/main") -> Path:
        path = self.root / f"issue-{issue_number}"
        if path.exists():
            return path

        if self.mode == "worktree":
            branch = f"{self.branch_prefix}/{issue_number}-worker"
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(self.repo_path),
                    "worktree",
                    "add",
                    "-B",
                    branch,
                    str(path),
                    base_ref,
                ],
                check=True,
                capture_output=True,
                text=True,
            )
        else:
            path.mkdir(parents=True)

        (path / ".ca-workspace.json").write_text(
            json.dumps(
                {
                    "issue_number": issue_number,
                    "mode": self.mode,
                    "base_ref": base_ref,
                },
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        return path
