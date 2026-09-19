from __future__ import annotations

import subprocess
from pathlib import Path


class GitRepairPublisher:
    """Publish a completed repair commit back to the branch that failed CI.

    Publication runs in the orchestrator process, not inside the coding worker.
    A normal non-force push is intentional: if the remote branch advanced while
    the repair was running, Git rejects the push instead of overwriting newer work.
    """

    def publish(
        self,
        workspace: str | Path,
        head_branch: str,
        expected_commit_sha: str | None = None,
    ) -> str:
        root = Path(workspace).resolve()
        if not root.is_dir():
            raise RuntimeError(f"repair workspace does not exist: {root}")
        if not head_branch:
            raise RuntimeError("repair publication requires a target branch")

        check = subprocess.run(
            ["git", "check-ref-format", "--branch", head_branch],
            capture_output=True,
            text=True,
            check=False,
        )
        if check.returncode != 0:
            raise RuntimeError(f"invalid repair target branch: {head_branch}")

        head = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()

        if expected_commit_sha and head != expected_commit_sha:
            raise RuntimeError(
                "repair worker commit mismatch: "
                f"worker={expected_commit_sha} workspace={head}"
            )

        subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "push",
                "origin",
                f"HEAD:refs/heads/{head_branch}",
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        return head
