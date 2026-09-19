from __future__ import annotations

import json
from datetime import datetime, timezone

from .models import PlannedIssue, ReconcileResult
from .state import StateStore
from .workspace import WorkspaceManager


def _event(name: str, **fields) -> None:
    payload = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "event": name,
        **fields,
    }
    print(json.dumps(payload, sort_keys=True), flush=True)


class Orchestrator:
    def __init__(
        self,
        github,
        state: StateStore,
        workspaces: WorkspaceManager,
        plan: tuple[PlannedIssue, ...],
        worker=None,
    ):
        self.github = github
        self.state = state
        self.workspaces = workspaces
        self.plan = plan
        self.worker = worker

    def _dependencies_closed(self, item: PlannedIssue) -> bool:
        return all(self.github.issue(dep).state == "closed" for dep in item.dependencies)

    def reconcile_once(self) -> ReconcileResult:
        active = self.state.active_claims()
        if active:
            claim = active[0]
            _event("active_claim", issue=claim.issue_number, workspace=claim.workspace)
            return ReconcileResult(
                state="ACTIVE_CLAIM",
                issue_number=claim.issue_number,
                workspace=claim.workspace,
            )

        for item in self.plan:
            issue = self.github.issue(item.number)
            if issue.state != "open":
                continue
            if not self._dependencies_closed(item):
                continue
            if item.owner_gate:
                _event("owner_decision", issue=item.number, title=issue.title)
                return ReconcileResult(
                    state="OWNER_DECISION",
                    issue_number=item.number,
                    reason="owner_gate",
                )

            workspace = self.workspaces.create(item.number)
            if self.state.try_claim(item.number, str(workspace)):
                _event("claimed", issue=item.number, workspace=str(workspace))
                return ReconcileResult(
                    state="CLAIMED",
                    issue_number=item.number,
                    workspace=str(workspace),
                )

        _event("idle_no_eligible_work")
        return ReconcileResult(state="IDLE")

    def reconcile_and_dispatch_once(self) -> ReconcileResult:
        result = self.reconcile_once()
        if (
            self.worker is None
            or result.issue_number is None
            or result.workspace is None
            or result.state not in {"CLAIMED", "ACTIVE_CLAIM"}
        ):
            return result

        latest = self.state.latest_worker_run(result.issue_number)
        if latest is not None:
            _event(
                "worker_already_recorded",
                issue=result.issue_number,
                worker_status=latest["status"],
                run_id=latest["run_id"],
            )
            return ReconcileResult(
                state=f"WORKER_{str(latest['status']).upper()}",
                issue_number=result.issue_number,
                workspace=result.workspace,
                reason=latest.get("error_kind"),
            )

        issue = self.github.issue(result.issue_number)
        _event("worker_dispatch", issue=issue.number, workspace=result.workspace)
        worker_result = self.worker.run(issue, result.workspace)
        _event(
            "worker_finished",
            issue=issue.number,
            status=worker_result.status,
            run_id=worker_result.run_id,
            commit_sha=worker_result.commit_sha,
            error_kind=worker_result.error_kind,
        )
        return ReconcileResult(
            state=f"WORKER_{worker_result.status.upper()}",
            issue_number=issue.number,
            workspace=result.workspace,
            reason=worker_result.error_kind,
        )
