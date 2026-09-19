import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from ca_orchestrator.repair_publisher import GitRepairPublisher


class RepairPublisherTests(unittest.TestCase):
    def test_publish_uses_normal_non_force_push_to_failed_branch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            calls = []

            def fake_run(args, **kwargs):
                calls.append(args)
                if args[:2] == ["git", "check-ref-format"]:
                    return subprocess.CompletedProcess(args, 0, "", "")
                if "rev-parse" in args:
                    return subprocess.CompletedProcess(args, 0, "abc123\n", "")
                if "push" in args:
                    return subprocess.CompletedProcess(args, 0, "", "")
                raise AssertionError(f"unexpected git command: {args}")

            publisher = GitRepairPublisher()
            with patch("ca_orchestrator.repair_publisher.subprocess.run", side_effect=fake_run):
                sha = publisher.publish(
                    root,
                    "ca/1-import-apk",
                    expected_commit_sha="abc123",
                )

            self.assertEqual("abc123", sha)
            push = next(args for args in calls if "push" in args)
            self.assertEqual(
                [
                    "git",
                    "-C",
                    str(root.resolve()),
                    "push",
                    "origin",
                    "HEAD:refs/heads/ca/1-import-apk",
                ],
                push,
            )
            self.assertNotIn("--force", push)
            self.assertNotIn("--force-with-lease", push)

    def test_publish_rejects_commit_mismatch_before_push(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            calls = []

            def fake_run(args, **kwargs):
                calls.append(args)
                if args[:2] == ["git", "check-ref-format"]:
                    return subprocess.CompletedProcess(args, 0, "", "")
                if "rev-parse" in args:
                    return subprocess.CompletedProcess(args, 0, "newer123\n", "")
                raise AssertionError(f"unexpected git command: {args}")

            publisher = GitRepairPublisher()
            with patch("ca_orchestrator.repair_publisher.subprocess.run", side_effect=fake_run):
                with self.assertRaisesRegex(RuntimeError, "commit mismatch"):
                    publisher.publish(
                        root,
                        "ca/1-import-apk",
                        expected_commit_sha="worker123",
                    )

            self.assertFalse(any("push" in args for args in calls))

    def test_publish_rejects_invalid_branch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch(
                "ca_orchestrator.repair_publisher.subprocess.run",
                return_value=subprocess.CompletedProcess([], 1, "", "invalid"),
            ):
                with self.assertRaisesRegex(RuntimeError, "invalid repair target branch"):
                    GitRepairPublisher().publish(root, "-bad-branch")


if __name__ == "__main__":
    unittest.main()
