from __future__ import annotations

import json
from datetime import datetime, timezone

from .action_executor import CiActionExecutor
from .models import PlannedIssue, ReconcileResult
from .repair_policy import decide_ci_action
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
        action_executor: CiActionExecutor | None = None,
    ):
        self.github = github
        self.state = state
        self.workspaces = workspaces
        self.plan = plan
        self.worker = worker
        self.action_executor = action_executor

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

            ci = self.state.latest_ci_feedback(item.number)
            if ci is not None:
                result_state = str(ci["result_state"])
                if result_state in {"ACTIVE", "RED", "GREEN"}:
                    state = f"CI_{result_state}"
                    classification = str(ci["classification"])
                    similar_failures = 0
                    if result_state == "RED":
                        similar_failures = self.state.failure_count(
                            item.number,
                            f"ci:{classification}",
                        )
                    decision = decide_ci_action(
                        result_state=result_state,
                        classification=classification,
                        similar_failures=similar_failures,
                    )
                    _event(
                        "ci_gate",
                        issue=item.number,
                        state=state,
                        run_id=ci["run_id"],
                        commit_sha=ci["commit_sha"],
                        classification=classification,
                        next_action=decision.action,
                        similar_failures=similar_failures,
                    )
                    return ReconcileResult(
                        state=state,
                        issue_number=item.number,
                        reason=classification,
                        action=decision.action,
                    )

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
            result.state == "CI_RED"
            and result.issue_number is not None
            and result.action == "RETRY_INFRASTRUCTURE"
            and self.action_executor is not None
        ):
            ci = self.state.latest_ci_feedback(result.issue_number)
            if ci is None:
                return result
            similar_failures = self.state.failure_count(
                result.issue_number,
                f"ci:{ci['classification']}",
            )
            decision = decide_ci_action(
                result_state=str(ci["result_state"]),
                classification=str(ci["classification"]),
                similar_failures=similar_failures,
            )
            execution = self.action_executor.execute(
                issue_number=result.issue_number,
                ci=ci,
                decision=decision,
            )
            _event(
                "ci_action_execution",
                issue=result.issue_number,
                run_id=ci["run_id"],
                action=execution.action,
                action_state=execution.state,
                failure_count=execution.failure_count,
            )
            return ReconcileResult(
                state=f"ACTION_{execution.state}",
                issue_number=result.issue_number,
                reason=result.reason,
                action=execution.action,
            )
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
