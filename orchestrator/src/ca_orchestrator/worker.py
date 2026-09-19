from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

from .models import IssueSnapshot, WorkerRunResult
from .state import StateStore


_RULE_FILES = ("AGENTS.md", "CONTROL_TOWER.md", "WORKER_PROTOCOL.md")


class CodexWorkerAdapter:
    def __init__(
        self,
        state: StateStore,
        *,
        codex_binary: str = "codex",
        timeout_seconds: int = 1800,
    ) -> None:
        self.state = state
        self.codex_binary = codex_binary
        self.timeout_seconds = timeout_seconds

    def run(self, issue: IssueSnapshot, workspace: str | Path) -> WorkerRunResult:
        root = Path(workspace).resolve()
        if not root.is_dir():
            return WorkerRunResult(
                run_id="",
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                error_kind="WORKSPACE_MISSING",
                error_message=f"Workspace does not exist: {root}",
            )

        if not (root / ".git").exists() and not self._is_git_worktree(root):
            return WorkerRunResult(
                run_id="",
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                error_kind="WORKSPACE_NOT_GIT",
                error_message="Codex workers require an isolated Git worktree",
            )

        if not (os.environ.get("CODEX_API_KEY") or os.environ.get("CODEX_ACCESS_TOKEN")):
            return WorkerRunResult(
                run_id="",
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                error_kind="MISSING_CODEX_CREDENTIAL",
                error_message="CODEX_API_KEY or CODEX_ACCESS_TOKEN is required",
            )

        artifacts = root / ".ca-agent"
        artifacts.mkdir(parents=True, exist_ok=True)
        events_path = artifacts / f"issue-{issue.number}-events.jsonl"
        final_path = artifacts / f"issue-{issue.number}-final.txt"
        stderr_path = artifacts / f"issue-{issue.number}-stderr.txt"

        run_id = self.state.start_worker_run(
            issue.number,
            str(root),
            str(events_path),
            str(final_path),
            str(stderr_path),
        )

        prompt = self._build_prompt(issue, root)
        command = [
            self.codex_binary,
            "exec",
            "--json",
            "--ephemeral",
            "--sandbox",
            "workspace-write",
            "--ignore-user-config",
            "--output-last-message",
            str(final_path),
            prompt,
        ]

        env = self._worker_environment()
        try:
            completed = subprocess.run(
                command,
                cwd=root,
                env=env,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
        except FileNotFoundError as exc:
            self.state.finish_worker_run(
                run_id,
                status="failed",
                error_kind="WORKER_UNAVAILABLE",
                error_message=str(exc),
            )
            return WorkerRunResult(
                run_id=run_id,
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                error_kind="WORKER_UNAVAILABLE",
                error_message=str(exc),
            )
        except subprocess.TimeoutExpired as exc:
            self.state.finish_worker_run(
                run_id,
                status="failed",
                error_kind="WORKER_TIMEOUT",
                error_message=str(exc),
            )
            return WorkerRunResult(
                run_id=run_id,
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                error_kind="WORKER_TIMEOUT",
                error_message=str(exc),
            )

        events_path.write_text(completed.stdout or "", encoding="utf-8")
        stderr_path.write_text(completed.stderr or "", encoding="utf-8")
        thread_id = self._thread_id(completed.stdout or "")

        if completed.returncode != 0:
            kind = self._classify_failure(completed.stderr or "", completed.stdout or "")
            self.state.finish_worker_run(
                run_id,
                status="failed",
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind=kind,
                error_message=(completed.stderr or completed.stdout or "")[-4000:],
            )
            return WorkerRunResult(
                run_id=run_id,
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind=kind,
                error_message=(completed.stderr or completed.stdout or "")[-4000:],
            )

        try:
            commit_sha = self._commit_workspace(root, issue.number)
        except subprocess.CalledProcessError as exc:
            message = (exc.stderr or exc.stdout or str(exc))[-4000:]
            self.state.finish_worker_run(
                run_id,
                status="failed",
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind="GIT_COMMIT_FAILED",
                error_message=message,
            )
            return WorkerRunResult(
                run_id=run_id,
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind="GIT_COMMIT_FAILED",
                error_message=message,
            )

        if commit_sha is None:
            self.state.finish_worker_run(
                run_id,
                status="failed",
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind="NO_CHANGES",
                error_message="Codex completed but produced no repository changes",
            )
            return WorkerRunResult(
                run_id=run_id,
                issue_number=issue.number,
                status="failed",
                workspace=str(root),
                exit_code=completed.returncode,
                thread_id=thread_id,
                error_kind="NO_CHANGES",
                error_message="Codex completed but produced no repository changes",
            )

        self.state.finish_worker_run(
            run_id,
            status="completed",
            exit_code=completed.returncode,
            thread_id=thread_id,
            commit_sha=commit_sha,
        )
        return WorkerRunResult(
            run_id=run_id,
            issue_number=issue.number,
            status="completed",
            workspace=str(root),
            exit_code=completed.returncode,
            thread_id=thread_id,
            commit_sha=commit_sha,
        )

    def _build_prompt(self, issue: IssueSnapshot, root: Path) -> str:
        rule_sections = []
        for name in _RULE_FILES:
            path = root / name
            if path.is_file():
                rule_sections.append(f"## {name}\n{path.read_text(encoding='utf-8')[:20000]}")

        rules = "\n\n".join(rule_sections)
        return f"""You are a bounded CloneApp development worker.

ASSIGNMENT: GitHub Issue #{issue.number} ONLY
TITLE: {issue.title}

ISSUE BODY:
{issue.body}

HARD SCOPE:
- Work only on Issue #{issue.number}.
- Do not select, begin, or prepare another issue.
- Do not push to GitHub and do not open or merge a pull request.
- Do not change accepted product/architecture/security decisions unless the assigned issue explicitly requires it.
- Make the smallest coherent implementation satisfying this issue's acceptance criteria.
- Add/update tests where practical.
- Run relevant local tests if available.
- Leave all intended repository changes in this isolated worktree.
- Do not commit; the orchestrator creates the bounded commit after your run.
- Stop after the assigned issue implementation is complete.

REPOSITORY RULES:
{rules}
"""

    def _worker_environment(self) -> dict[str, str]:
        allow = {
            "PATH",
            "HOME",
            "TMPDIR",
            "TMP",
            "TEMP",
            "LANG",
            "LC_ALL",
            "SHELL",
            "USER",
            "SSL_CERT_FILE",
            "CODEX_CA_CERTIFICATE",
            "HTTPS_PROXY",
            "HTTP_PROXY",
            "NO_PROXY",
            "CODEX_API_KEY",
            "CODEX_ACCESS_TOKEN",
            "OPENAI_FEDERATION_RULE_ID",
        }
        return {key: value for key, value in os.environ.items() if key in allow}

    def _thread_id(self, jsonl: str) -> str | None:
        for line in jsonl.splitlines():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") == "thread.started":
                value = event.get("thread_id")
                return str(value) if value else None
        return None

    def _classify_failure(self, stderr: str, stdout: str) -> str:
        combined = f"{stderr}\n{stdout}".lower()
        if "api key" in combined or "authentication" in combined or "unauthorized" in combined:
            return "AUTH_FAILURE"
        if "rate limit" in combined or "429" in combined:
            return "RATE_LIMIT"
        return "WORKER_FAILURE"

    def _is_git_worktree(self, root: Path) -> bool:
        probe = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "--is-inside-work-tree"],
            capture_output=True,
            text=True,
            check=False,
        )
        return probe.returncode == 0 and probe.stdout.strip() == "true"

    def _commit_workspace(self, root: Path, issue_number: int) -> str | None:
        status = subprocess.run(
            ["git", "-C", str(root), "status", "--porcelain"],
            capture_output=True,
            text=True,
            check=True,
        )
        if not status.stdout.strip():
            return None

        subprocess.run(
            ["git", "-C", str(root), "add", "-A"],
            capture_output=True,
            text=True,
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "-c",
                "user.name=CloneApp Autonomous Worker",
                "-c",
                "user.email=cloneapp-agent@users.noreply.github.com",
                "commit",
                "-m",
                f"CA-{issue_number}: autonomous worker implementation",
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        sha = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=True,
        )
        return sha.stdout.strip()
