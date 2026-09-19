import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.models import IssueSnapshot, WorkerRunResult
from ca_orchestrator.repair_dispatcher import RepairWorkerDispatcher
from ca_orchestrator.workspace import WorkspaceManager


class FakeGitHub:
    def issue(self, number):
        return IssueSnapshot(
            number=number,
            title=f"Issue {number}",
            state="open",
            body="Original issue body.",
        )


class FakeWorker:
    def __init__(self):
        self.calls = []

    def run(self, issue, workspace):
        self.calls.append((issue, str(workspace)))
        return WorkerRunResult(
            run_id="repair-run",
            issue_number=issue.number,
            status="completed",
            workspace=str(workspace),
            commit_sha="abc123",
        )


class FakePublisher:
    def __init__(self):
        self.calls = []

    def publish(self, workspace, head_branch, expected_commit_sha=None):
        self.calls.append((str(workspace), head_branch, expected_commit_sha))
        return expected_commit_sha or "published"


class RepairWorkerDispatcherTests(unittest.TestCase):
    def test_dispatch_uses_failed_branch_workspace_and_repair_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            workspaces = WorkspaceManager(Path(tmp) / "workspaces")
            worker = FakeWorker()
            publisher = FakePublisher()
            dispatcher = RepairWorkerDispatcher(
                FakeGitHub(),
                workspaces,
                worker,
                publisher=publisher,
            )
            ci = {
                "run_id": 321,
                "head_branch": "ca/1-import-apk",
                "commit_sha": "deadbeef",
                "classification": "BUILD",
            }

            result = dispatcher(1, ci)

            self.assertEqual("completed", result.status)
            self.assertEqual(1, len(worker.calls))
            issue, workspace = worker.calls[0]
            self.assertIn("AUTOMATED REPAIR CONTEXT", issue.body)
            self.assertIn("Failed workflow run: 321", issue.body)
            self.assertIn("Failed branch: ca/1-import-apk", issue.body)
            self.assertIn("Classification: BUILD", issue.body)
            metadata = Path(workspace, ".ca-workspace.json").read_text()
            self.assertIn('"kind": "repair"', metadata)
            self.assertIn('"head_branch": "ca/1-import-apk"', metadata)
            self.assertEqual(
                [(workspace, "ca/1-import-apk", "abc123")],
                publisher.calls,
            )

    def test_missing_failed_branch_is_rejected_before_worker(self):
        with tempfile.TemporaryDirectory() as tmp:
            worker = FakeWorker()
            dispatcher = RepairWorkerDispatcher(
                FakeGitHub(),
                WorkspaceManager(Path(tmp) / "workspaces"),
                worker,
                publisher=FakePublisher(),
            )

            with self.assertRaisesRegex(RuntimeError, "failed CI head branch"):
                dispatcher(
                    1,
                    {
                        "run_id": 322,
                        "head_branch": "",
                        "classification": "BUILD",
                    },
                )

            self.assertEqual([], worker.calls)


if __name__ == "__main__":
    unittest.main()
