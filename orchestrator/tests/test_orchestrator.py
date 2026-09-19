import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.action_executor import CiActionExecutor
from ca_orchestrator.engine import Orchestrator
from ca_orchestrator.models import IssueSnapshot, PlannedIssue
from ca_orchestrator.state import StateStore
from ca_orchestrator.workspace import WorkspaceManager


class FakeGitHub:
    def __init__(self, states):
        self.states = states

    def issue(self, number):
        state = self.states[number]
        return IssueSnapshot(number=number, title=f"Issue {number}", state=state)


class OrchestratorTests(unittest.TestCase):
    def test_dependency_closed_issue_is_claimed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = StateStore(root / "state.sqlite3", concurrency=1)
            workspaces = WorkspaceManager(root / "workspaces")
            github = FakeGitHub({1: "closed", 2: "open"})
            orchestrator = Orchestrator(
                github,
                state,
                workspaces,
                (PlannedIssue(2, dependencies=(1,)),),
            )

            result = orchestrator.reconcile_once()

            self.assertEqual("CLAIMED", result.state)
            self.assertEqual(2, result.issue_number)
            self.assertTrue(Path(result.workspace).is_dir())
            self.assertEqual(1, len(state.active_claims()))

    def test_open_dependency_blocks_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            orchestrator = Orchestrator(
                FakeGitHub({1: "open", 2: "open"}),
                StateStore(root / "state.sqlite3"),
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(2, dependencies=(1,)),),
            )

            result = orchestrator.reconcile_once()

            self.assertEqual("IDLE", result.state)

    def test_owner_gate_stops_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            orchestrator = Orchestrator(
                FakeGitHub({7: "open"}),
                StateStore(root / "state.sqlite3"),
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(7, owner_gate=True),),
            )

            result = orchestrator.reconcile_once()

            self.assertEqual("OWNER_DECISION", result.state)
            self.assertEqual(7, result.issue_number)
            self.assertEqual([], orchestrator.state.active_claims())

    def test_existing_active_claim_prevents_second_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = StateStore(root / "state.sqlite3", concurrency=1)
            state.try_claim(10, str(root / "issue-10"))
            orchestrator = Orchestrator(
                FakeGitHub({11: "open"}),
                state,
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(11),),
            )

            result = orchestrator.reconcile_once()

            self.assertEqual("ACTIVE_CLAIM", result.state)
            self.assertEqual(10, result.issue_number)

    def test_claim_persists_across_store_restart_and_can_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "state.sqlite3"
            first = StateStore(db)
            self.assertTrue(first.try_claim(4, "/tmp/issue-4"))

            second = StateStore(db)
            claims = second.active_claims()
            self.assertEqual([4], [claim.issue_number for claim in claims])
            self.assertTrue(second.release(4))
            self.assertEqual([], second.active_claims())

    def test_same_failure_fingerprint_counts_for_loop_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            self.assertEqual(1, state.record_failure(9, "compile:error-x"))
            self.assertEqual(2, state.record_failure(9, "compile:error-x"))
            self.assertEqual(3, state.record_failure(9, "compile:error-x"))

    def test_machine_readable_export_contains_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            state.try_claim(3, "/tmp/issue-3")

            exported = state.export()

            self.assertEqual(1, exported["concurrency"])
            self.assertEqual(3, exported["claims"][0]["issue_number"])

    def test_active_ci_prevents_claim_and_worker_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = StateStore(root / "state.sqlite3")
            state.upsert_ci_feedback(
                run_id=55,
                delivery_id="delivery-active",
                workflow_name="Android Build",
                issue_number=1,
                pr_number=32,
                commit_sha="abc123",
                head_branch="ca/1-apk-import-runtime-acceptance",
                status="in_progress",
                conclusion=None,
                result_state="ACTIVE",
                classification="WORKFLOW",
            )
            orchestrator = Orchestrator(
                FakeGitHub({1: "open"}),
                state,
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(1),),
            )

            result = orchestrator.reconcile_and_dispatch_once()

            self.assertEqual("CI_ACTIVE", result.state)
            self.assertEqual(1, result.issue_number)
            self.assertEqual("WAIT_FOR_CI", result.action)
            self.assertEqual([], state.active_claims())

    def test_red_ci_is_persisted_as_gate_until_repair_policy_handles_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = StateStore(root / "state.sqlite3")
            state.upsert_ci_feedback(
                run_id=54,
                delivery_id="delivery-red",
                workflow_name="Android Build",
                issue_number=1,
                pr_number=32,
                commit_sha="abc123",
                head_branch="ca/1-apk-import-runtime-acceptance",
                status="completed",
                conclusion="failure",
                result_state="RED",
                classification="EMULATOR",
            )
            orchestrator = Orchestrator(
                FakeGitHub({1: "open"}),
                state,
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(1),),
            )

            result = orchestrator.reconcile_once()

            self.assertEqual("CI_RED", result.state)
            self.assertEqual("EMULATOR", result.reason)
            self.assertEqual("RUNTIME_CLASSIFICATION_REQUIRED", result.action)
            self.assertEqual([], state.active_claims())

    def test_infrastructure_red_executes_one_live_retry_action(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = StateStore(root / "state.sqlite3")
            state.upsert_ci_feedback(
                run_id=77,
                delivery_id="delivery-infra-red",
                workflow_name="Android Build",
                issue_number=1,
                pr_number=32,
                commit_sha="infra123",
                head_branch="ca/1-apk-import-runtime-acceptance",
                status="completed",
                conclusion="failure",
                result_state="RED",
                classification="INFRASTRUCTURE",
            )
            retries = []
            executor = CiActionExecutor(
                state,
                retry_infrastructure=lambda run_id: retries.append(run_id),
                dispatch_repair=lambda issue, ci: None,
            )
            orchestrator = Orchestrator(
                FakeGitHub({1: "open"}),
                state,
                WorkspaceManager(root / "workspaces"),
                (PlannedIssue(1),),
                action_executor=executor,
            )

            first = orchestrator.reconcile_and_dispatch_once()
            second = orchestrator.reconcile_and_dispatch_once()

            self.assertEqual("ACTION_COMPLETED", first.state)
            self.assertEqual("RETRY_INFRASTRUCTURE", first.action)
            self.assertEqual("ACTION_ALREADY_HANDLED", second.state)
            self.assertEqual([77], retries)
            self.assertEqual(1, state.failure_count(1, "ci:INFRASTRUCTURE"))



if __name__ == "__main__":
    unittest.main()
