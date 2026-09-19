import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from ca_orchestrator.models import IssueSnapshot
from ca_orchestrator.state import StateStore
from ca_orchestrator.worker import CodexWorkerAdapter


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def init_repo(root: Path) -> None:
    git(root, "init", "-b", "main")
    for name in ("AGENTS.md", "CONTROL_TOWER.md", "WORKER_PROTOCOL.md"):
        (root / name).write_text(f"# {name}\nRule: bounded issue only.\n", encoding="utf-8")
    (root / "README.md").write_text("seed\n", encoding="utf-8")
    git(root, "add", "-A")
    subprocess.run(
        [
            "git",
            "-C",
            str(root),
            "-c",
            "user.name=Test",
            "-c",
            "user.email=test@example.com",
            "commit",
            "-m",
            "seed",
        ],
        capture_output=True,
        text=True,
        check=True,
    )


def make_fake_codex(path: Path, *, exit_code: int = 0) -> None:
    script = rf"""#!/usr/bin/env python3
import json
import os
import pathlib
import sys

args = sys.argv[1:]
out = args[args.index("--output-last-message") + 1]
prompt = args[-1]
pathlib.Path("worker-output.txt").write_text("implemented\n", encoding="utf-8")
pathlib.Path("captured-prompt.txt").write_text(prompt, encoding="utf-8")
pathlib.Path("github-token-visible.txt").write_text(
    os.environ.get("GITHUB_TOKEN", "MISSING"), encoding="utf-8"
)
pathlib.Path(out).write_text("done", encoding="utf-8")
print(json.dumps({{"type": "thread.started", "thread_id": "thread-test-123"}}))
print(json.dumps({{"type": "turn.completed"}}))
sys.stderr.write("fake codex stderr\n")
raise SystemExit({exit_code})
"""
    path.write_text(script, encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR)


class CodexWorkerAdapterTests(unittest.TestCase):
    def test_successful_worker_is_bounded_persisted_and_committed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "repo"
            root.mkdir()
            init_repo(root)

            state = StateStore(Path(tmp) / "state" / "state.sqlite3")
            fake = Path(tmp) / "fake-codex"
            make_fake_codex(fake)

            issue = IssueSnapshot(
                number=14,
                title="Add Codex adapter",
                state="open",
                body="Acceptance: implement only this issue.",
            )

            with patch.dict(
                os.environ,
                {"CODEX_API_KEY": "test-key", "GITHUB_TOKEN": "must-not-leak"},
                clear=False,
            ):
                result = CodexWorkerAdapter(
                    state,
                    codex_binary=str(fake),
                    timeout_seconds=30,
                ).run(issue, root)

            self.assertEqual("completed", result.status)
            self.assertEqual("thread-test-123", result.thread_id)
            self.assertTrue(result.commit_sha)
            self.assertEqual("MISSING", (root / "github-token-visible.txt").read_text())
            prompt = (root / "captured-prompt.txt").read_text()
            self.assertIn("GitHub Issue #14 ONLY", prompt)
            self.assertIn("AGENTS.md", prompt)
            self.assertIn("WORKER_PROTOCOL.md", prompt)
            self.assertEqual("", git(root, "status", "--porcelain"))

            recorded = state.latest_worker_run(14)
            self.assertEqual("completed", recorded["status"])
            self.assertEqual(result.commit_sha, recorded["commit_sha"])
            self.assertTrue(Path(recorded["events_path"]).is_file())
            self.assertNotIn(".ca-agent", git(root, "show", "--name-only", "--format="))

    def test_nonzero_codex_exit_is_classified_and_not_committed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "repo"
            root.mkdir()
            init_repo(root)

            state = StateStore(Path(tmp) / "state.sqlite3")
            fake = Path(tmp) / "fake-codex"
            make_fake_codex(fake, exit_code=7)

            with patch.dict(os.environ, {"CODEX_API_KEY": "test-key"}, clear=False):
                result = CodexWorkerAdapter(
                    state,
                    codex_binary=str(fake),
                    timeout_seconds=30,
                ).run(
                    IssueSnapshot(14, "Adapter", "open", "test failure"),
                    root,
                )

            self.assertEqual("failed", result.status)
            self.assertEqual("WORKER_FAILURE", result.error_kind)
            self.assertIsNone(result.commit_sha)
            self.assertNotEqual("", git(root, "status", "--porcelain"))

    def test_missing_codex_credential_stops_before_launch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "repo"
            root.mkdir()
            init_repo(root)

            state = StateStore(Path(tmp) / "state.sqlite3")
            with patch.dict(os.environ, {}, clear=True):
                result = CodexWorkerAdapter(
                    state,
                    codex_binary="does-not-matter",
                ).run(IssueSnapshot(14, "Adapter", "open"), root)

            self.assertEqual("failed", result.status)
            self.assertEqual("MISSING_CODEX_CREDENTIAL", result.error_kind)
            self.assertIsNone(state.latest_worker_run(14))


if __name__ == "__main__":
    unittest.main()
