from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time

from .action_executor import CiActionExecutor
from .ci_feedback import CiFeedbackProcessor
from .config import load_settings
from .engine import Orchestrator
from .github_client import GitHubClient
from .repair_dispatcher import RepairWorkerDispatcher
from .repair_publisher import GitRepairPublisher
from .state import StateStore
from .webhook import create_webhook_server
from .worker import CodexWorkerAdapter
from .workspace import WorkspaceManager


def build_orchestrator(plan_path: str):
    settings = load_settings(plan_path)

    if settings.enable_codex_worker:
        if settings.workspace_mode != "worktree":
            raise RuntimeError("CA_ENABLE_CODEX_WORKER=1 requires CA_WORKSPACE_MODE=worktree")
        if settings.repo_path is None:
            raise RuntimeError("CA_ENABLE_CODEX_WORKER=1 requires CA_REPO_PATH")

    github = GitHubClient(settings.repo, settings.token)
    state = StateStore(settings.state_db, concurrency=settings.concurrency)
    workspaces = WorkspaceManager(
        settings.work_root,
        mode=settings.workspace_mode,
        repo_path=settings.repo_path,
    )
    worker = None
    if settings.enable_codex_worker:
        worker = CodexWorkerAdapter(
            state,
            codex_binary=settings.codex_binary,
            timeout_seconds=settings.codex_timeout_seconds,
        )

    repair_dispatcher = RepairWorkerDispatcher(
        github,
        workspaces,
        worker,
        publisher=GitRepairPublisher(),
    )

    action_executor = CiActionExecutor(
        state,
        retry_infrastructure=github.rerun_failed_jobs,
        dispatch_repair=repair_dispatcher,
    )

    return settings, state, Orchestrator(
        github,
        state,
        workspaces,
        settings.plan,
        worker=worker,
        action_executor=action_executor,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="CloneApp persistent engineering orchestrator")
    parser.add_argument("--plan", default="orchestrator/plan.example.json")
    parser.add_argument("--once", action="store_true", help="Run one reconciliation/dispatch tick and exit")
    parser.add_argument("--status", action="store_true", help="Print persisted state as JSON")
    parser.add_argument("--release", type=int, metavar="ISSUE", help="Release one claimed issue")
    parser.add_argument(
        "--webhook",
        action="store_true",
        help="Also listen for signed GitHub workflow_run webhooks",
    )
    parser.add_argument(
        "--webhook-host",
        default=os.environ.get("CA_WEBHOOK_HOST", "127.0.0.1"),
    )
    parser.add_argument(
        "--webhook-port",
        type=int,
        default=int(os.environ.get("CA_WEBHOOK_PORT", "8787")),
    )
    args = parser.parse_args(argv)

    settings, state, orchestrator = build_orchestrator(args.plan)

    if args.status:
        print(state.export_json())
        return 0

    if args.release is not None:
        released = state.release(args.release)
        print(json.dumps({"issue": args.release, "released": released}))
        return 0 if released else 1

    if args.once:
        result = orchestrator.reconcile_and_dispatch_once()
        print(json.dumps(result.__dict__, sort_keys=True))
        return 0

    wake_event = threading.Event()
    webhook_server = None
    webhook_thread = None

    if args.webhook:
        secret = os.environ.get("CA_GITHUB_WEBHOOK_SECRET", "")
        if not secret:
            raise RuntimeError("CA_GITHUB_WEBHOOK_SECRET is required with --webhook")
        processor = CiFeedbackProcessor(orchestrator.github, state)
        webhook_server = create_webhook_server(
            processor,
            secret,
            args.webhook_host,
            args.webhook_port,
            on_event=lambda _result: wake_event.set(),
        )
        webhook_thread = threading.Thread(
            target=webhook_server.serve_forever,
            name="cloneapp-github-webhook",
            daemon=True,
        )
        webhook_thread.start()
        print(
            json.dumps(
                {
                    "event": "webhook_listening",
                    "host": args.webhook_host,
                    "port": args.webhook_port,
                }
            ),
            flush=True,
        )

    backoff = settings.poll_seconds
    try:
        while True:
            try:
                orchestrator.reconcile_and_dispatch_once()
                backoff = settings.poll_seconds
                if args.webhook:
                    wake_event.wait(timeout=settings.poll_seconds)
                    wake_event.clear()
                else:
                    time.sleep(settings.poll_seconds)
            except KeyboardInterrupt:
                return 0
            except Exception as exc:
                print(
                    json.dumps({"event": "orchestrator_error", "error": str(exc), "backoff": backoff}),
                    file=sys.stderr,
                    flush=True,
                )
                if args.webhook:
                    wake_event.wait(timeout=backoff)
                    wake_event.clear()
                else:
                    time.sleep(backoff)
                backoff = min(backoff * 2, 300)
    finally:
        if webhook_server is not None:
            webhook_server.shutdown()
            webhook_server.server_close()
        if webhook_thread is not None:
            webhook_thread.join(timeout=5)


if __name__ == "__main__":
    raise SystemExit(main())
