from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from .repair_policy import CiActionDecision
from .state import StateStore


@dataclass(frozen=True)
class ActionExecutionResult:
    state: str
    action: str
    action_key: str | None = None
    failure_count: int = 0


class CiActionExecutor:
    """Execute CI decisions exactly once per workflow-run/action pair.

    Side-effecting operations are injected so the policy remains testable and
    the executor can coordinate GitHub retries or repair-worker dispatch
    without coupling those implementations to state bookkeeping.
    """

    _SIDE_EFFECTING = {"RETRY_INFRASTRUCTURE", "DISPATCH_REPAIR"}

    def __init__(
        self,
        state: StateStore,
        *,
        retry_infrastructure: Callable[[int], Any],
        dispatch_repair: Callable[[int, dict[str, Any]], Any],
    ) -> None:
        self.state = state
        self.retry_infrastructure = retry_infrastructure
        self.dispatch_repair = dispatch_repair

    def execute(
        self,
        *,
        issue_number: int,
        ci: dict[str, Any],
        decision: CiActionDecision,
    ) -> ActionExecutionResult:
        action = decision.action
        if action not in self._SIDE_EFFECTING:
            return ActionExecutionResult(state="NO_SIDE_EFFECT", action=action)

        run_id = int(ci["run_id"])
        action_key = f"ci:{run_id}:{action}"
        if not self.state.try_start_action(
            action_key=action_key,
            issue_number=issue_number,
            run_id=run_id,
            action=action,
        ):
            return ActionExecutionResult(
                state="ALREADY_HANDLED",
                action=action,
                action_key=action_key,
                failure_count=self.state.failure_count(
                    issue_number,
                    f"ci:{ci['classification']}",
                ),
            )

        fingerprint = f"ci:{ci['classification']}"
        failure_count = self.state.record_failure(issue_number, fingerprint)
        try:
            if action == "RETRY_INFRASTRUCTURE":
                self.retry_infrastructure(run_id)
            elif action == "DISPATCH_REPAIR":
                self.dispatch_repair(issue_number, ci)
        except Exception as exc:
            self.state.finish_action(
                action_key,
                status="failed",
                error_message=str(exc),
            )
            raise

        self.state.finish_action(action_key, status="completed")
        return ActionExecutionResult(
            state="COMPLETED",
            action=action,
            action_key=action_key,
            failure_count=failure_count,
        )
