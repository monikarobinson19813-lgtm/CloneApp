import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.action_executor import CiActionExecutor
from ca_orchestrator.repair_policy import CiActionDecision
from ca_orchestrator.state import StateStore


class CiActionExecutorTests(unittest.TestCase):
    def test_infrastructure_retry_is_executed_once_per_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            retries = []
            repairs = []
            executor = CiActionExecutor(
                state,
                retry_infrastructure=lambda run_id: retries.append(run_id),
                dispatch_repair=lambda issue, ci: repairs.append((issue, ci)),
            )
            ci = {"run_id": 101, "classification": "INFRASTRUCTURE"}
            decision = CiActionDecision(
                "RETRY_INFRASTRUCTURE",
                "infra_failure_no_product_change",
            )

            first = executor.execute(issue_number=1, ci=ci, decision=decision)
            second = executor.execute(issue_number=1, ci=ci, decision=decision)

            self.assertEqual("COMPLETED", first.state)
            self.assertEqual("ALREADY_HANDLED", second.state)
            self.assertEqual([101], retries)
            self.assertEqual([], repairs)
            self.assertEqual(1, state.failure_count(1, "ci:INFRASTRUCTURE"))

    def test_build_repair_is_dispatched_once_per_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            retries = []
            repairs = []
            executor = CiActionExecutor(
                state,
                retry_infrastructure=lambda run_id: retries.append(run_id),
                dispatch_repair=lambda issue, ci: repairs.append((issue, ci["run_id"])),
            )
            ci = {"run_id": 202, "classification": "BUILD"}
            decision = CiActionDecision(
                "DISPATCH_REPAIR",
                "build_failure_repairable",
            )

            first = executor.execute(issue_number=16, ci=ci, decision=decision)
            second = executor.execute(issue_number=16, ci=ci, decision=decision)

            self.assertEqual("COMPLETED", first.state)
            self.assertEqual("ALREADY_HANDLED", second.state)
            self.assertEqual([], retries)
            self.assertEqual([(16, 202)], repairs)
            self.assertEqual(1, state.failure_count(16, "ci:BUILD"))

    def test_non_side_effect_decision_does_not_create_action_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            executor = CiActionExecutor(
                state,
                retry_infrastructure=lambda run_id: None,
                dispatch_repair=lambda issue, ci: None,
            )
            result = executor.execute(
                issue_number=1,
                ci={"run_id": 303, "classification": "WORKFLOW"},
                decision=CiActionDecision(
                    "EVALUATE_ACCEPTANCE",
                    "ci_green_requires_feature_acceptance",
                ),
            )

            self.assertEqual("NO_SIDE_EFFECT", result.state)
            self.assertIsNone(result.action_key)
            self.assertEqual([], state.export()["action_executions"])

    def test_failed_side_effect_is_persisted_and_not_repeated(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")

            def fail_retry(run_id):
                raise RuntimeError("runner unavailable")

            executor = CiActionExecutor(
                state,
                retry_infrastructure=fail_retry,
                dispatch_repair=lambda issue, ci: None,
            )
            ci = {"run_id": 404, "classification": "INFRASTRUCTURE"}
            decision = CiActionDecision(
                "RETRY_INFRASTRUCTURE",
                "infra_failure_no_product_change",
            )

            with self.assertRaises(RuntimeError):
                executor.execute(issue_number=1, ci=ci, decision=decision)

            record = state.action_execution("ci:404:RETRY_INFRASTRUCTURE")
            self.assertEqual("failed", record["status"])
            self.assertIn("runner unavailable", record["error_message"])

            second = executor.execute(issue_number=1, ci=ci, decision=decision)
            self.assertEqual("ALREADY_HANDLED", second.state)


if __name__ == "__main__":
    unittest.main()
