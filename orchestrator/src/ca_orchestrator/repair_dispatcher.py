from __future__ import annotations

from typing import Any

from .models import IssueSnapshot


class RepairWorkerDispatcher:
    """Turn one repair action into one bounded coding-worker run.

    The repair workspace is created from the exact branch that failed CI so
    the worker repairs the failing change instead of starting again from main.
    """

    def __init__(self, github, workspaces, worker) -> None:
        self.github = github
        self.workspaces = workspaces
        self.worker = worker

    def __call__(self, issue_number: int, ci: dict[str, Any]):
        if self.worker is None:
            raise RuntimeError("repair worker is not configured")

        run_id = int(ci["run_id"])
        head_branch = str(ci.get("head_branch") or "")
        if not head_branch:
            raise RuntimeError("repair dispatch requires the failed CI head branch")

        workspace = self.workspaces.create_repair(
            issue_number,
            run_id,
            head_branch,
        )
        issue = self.github.issue(issue_number)

        repair_context = (
            "\n\nAUTOMATED REPAIR CONTEXT\n"
            f"- Failed workflow run: {run_id}\n"
            f"- Failed branch: {head_branch}\n"
            f"- Failed commit: {ci.get('commit_sha', '')}\n"
            f"- Classification: {ci.get('classification', '')}\n"
            "- This is a repair attempt for the existing issue, not a new feature.\n"
            "- Preserve scope and fix only the failure evidenced by CI.\n"
        )
        repair_issue = IssueSnapshot(
            number=issue.number,
            title=issue.title,
            state=issue.state,
            body=f"{issue.body}{repair_context}",
        )

        result = self.worker.run(repair_issue, workspace)
        if result.status != "completed":
            raise RuntimeError(
                "repair worker failed"
                + (f": {result.error_kind}" if result.error_kind else "")
            )
        return result
