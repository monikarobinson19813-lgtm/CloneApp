import hashlib
import hmac
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from ca_orchestrator.ci_feedback import CiFeedbackProcessor, classify_workflow_run
from ca_orchestrator.state import StateStore
from ca_orchestrator.webhook import create_webhook_server


class FakeGitHub:
    def __init__(self, jobs=None):
        self.jobs = jobs or []
        self.requested_run_ids = []

    def workflow_jobs(self, run_id):
        self.requested_run_ids.append(run_id)
        return self.jobs


def workflow_payload(
    *,
    run_id=101,
    status="completed",
    conclusion="success",
    branch="ca/1-import-apk",
    sha="abc123",
    pr_number=32,
):
    return {
        "action": "completed" if status == "completed" else status,
        "workflow_run": {
            "id": run_id,
            "name": "Android Build",
            "status": status,
            "conclusion": conclusion,
            "head_branch": branch,
            "head_sha": sha,
            "pull_requests": [{"number": pr_number}] if pr_number else [],
        },
    }


class CiFeedbackTests(unittest.TestCase):
    def test_completed_success_correlates_issue_pr_commit_and_persists_green(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            github = FakeGitHub(
                jobs=[
                    {"name": "build", "conclusion": "success", "steps": []},
                    {"name": "Android 16 emulator smoke", "conclusion": "success", "steps": []},
                ]
            )
            processor = CiFeedbackProcessor(github, state)
            payload = workflow_payload()
            raw = json.dumps(payload).encode()

            result = processor.handle(
                delivery_id="delivery-1",
                event_type="workflow_run",
                payload=payload,
                raw_body=raw,
            )

            self.assertEqual("ROUTED", result["state"])
            self.assertEqual(1, result["issue_number"])
            self.assertEqual(32, result["pr_number"])
            self.assertEqual("abc123", result["commit_sha"])
            self.assertEqual("GREEN", result["result_state"])
            persisted = state.latest_ci_feedback(1)
            self.assertEqual(101, persisted["run_id"])
            self.assertEqual("GREEN", persisted["result_state"])
            self.assertEqual("WORKFLOW", persisted["classification"])

    def test_duplicate_delivery_is_ignored_without_refetching_jobs(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            github = FakeGitHub(jobs=[])
            processor = CiFeedbackProcessor(github, state)
            payload = workflow_payload()
            raw = json.dumps(payload).encode()

            first = processor.handle(
                delivery_id="same-delivery",
                event_type="workflow_run",
                payload=payload,
                raw_body=raw,
            )
            second = processor.handle(
                delivery_id="same-delivery",
                event_type="workflow_run",
                payload=payload,
                raw_body=raw,
            )

            self.assertEqual("ROUTED", first["state"])
            self.assertEqual("DUPLICATE", second["state"])
            self.assertEqual([101], github.requested_run_ids)

    def test_in_progress_event_is_active_without_job_lookup(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            github = FakeGitHub()
            processor = CiFeedbackProcessor(github, state)
            payload = workflow_payload(status="in_progress", conclusion=None)
            raw = json.dumps(payload).encode()

            result = processor.handle(
                delivery_id="delivery-active",
                event_type="workflow_run",
                payload=payload,
                raw_body=raw,
            )

            self.assertEqual("ACTIVE", result["result_state"])
            self.assertTrue(state.has_active_ci(1))
            self.assertEqual([], github.requested_run_ids)

    def test_failed_build_and_emulator_are_distinguished(self):
        build_run = workflow_payload(conclusion="failure")["workflow_run"]
        self.assertEqual(
            ("RED", "BUILD"),
            classify_workflow_run(
                build_run,
                [{"name": "build", "conclusion": "failure", "steps": []}],
            ),
        )

        self.assertEqual(
            ("RED", "EMULATOR"),
            classify_workflow_run(
                build_run,
                [
                    {
                        "name": "Android 16 emulator smoke",
                        "conclusion": "failure",
                        "steps": [
                            {
                                "name": "Run emulator smoke test",
                                "conclusion": "failure",
                            }
                        ],
                    }
                ],
            ),
        )

        self.assertEqual(
            ("RED", "INFRASTRUCTURE"),
            classify_workflow_run(
                build_run,
                [
                    {
                        "name": "Android 16 emulator smoke",
                        "conclusion": "failure",
                        "steps": [
                            {
                                "name": "Enable KVM",
                                "conclusion": "failure",
                            }
                        ],
                    }
                ],
            ),
        )

    def test_signed_webhook_accepts_valid_signature_and_rejects_invalid_signature(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = StateStore(Path(tmp) / "state.sqlite3")
            processor = CiFeedbackProcessor(FakeGitHub(jobs=[]), state)
            secret = "unit-test-secret"
            server = create_webhook_server(processor, secret, "127.0.0.1", 0)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                payload = workflow_payload(status="in_progress", conclusion=None)
                body = json.dumps(payload).encode()
                signature = "sha256=" + hmac.new(
                    secret.encode(),
                    body,
                    hashlib.sha256,
                ).hexdigest()
                url = f"http://127.0.0.1:{server.server_port}/github/webhook"
                request = urllib.request.Request(
                    url,
                    data=body,
                    method="POST",
                    headers={
                        "Content-Type": "application/json",
                        "X-GitHub-Delivery": "delivery-http",
                        "X-GitHub-Event": "workflow_run",
                        "X-Hub-Signature-256": signature,
                    },
                )
                with urllib.request.urlopen(request, timeout=2) as response:
                    self.assertEqual(202, response.status)

                bad_request = urllib.request.Request(
                    url,
                    data=body,
                    method="POST",
                    headers={
                        "Content-Type": "application/json",
                        "X-GitHub-Delivery": "delivery-http-bad",
                        "X-GitHub-Event": "workflow_run",
                        "X-Hub-Signature-256": "sha256=bad",
                    },
                )
                with self.assertRaises(urllib.error.HTTPError) as raised:
                    urllib.request.urlopen(bad_request, timeout=2)
                self.assertEqual(401, raised.exception.code)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
