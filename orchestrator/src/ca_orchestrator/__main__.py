from __future__ import annotations

import argparse
import json
import sys
import time

from .config import load_settings
from .engine import Orchestrator
from .github_client import GitHubClient
from .state import StateStore
from .workspace import WorkspaceManager


def build_orchestrator(plan_path: str):
    settings = load_settings(plan_path)
    github = GitHubClient(settings.repo, settings.token)
    state = StateStore(settings.state_db, concurrency=settings.concurrency)
    workspaces = WorkspaceManager(
        settings.work_root,
        mode=settings.workspace_mode,
        repo_path=settings.repo_path,
    )
    return settings, state, Orchestrator(github, state, workspaces, settings.plan)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="CloneApp persistent engineering orchestrator")
    parser.add_argument("--plan", default="orchestrator/plan.example.json")
    parser.add_argument("--once", action="store_true", help="Run one reconciliation tick and exit")
    parser.add_argument("--status", action="store_true", help="Print persisted state as JSON")
    parser.add_argument("--release", type=int, metavar="ISSUE", help="Release one claimed issue")
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
        result = orchestrator.reconcile_once()
        print(json.dumps(result.__dict__, sort_keys=True))
        return 0

    backoff = settings.poll_seconds
    while True:
        try:
            orchestrator.reconcile_once()
            backoff = settings.poll_seconds
            time.sleep(settings.poll_seconds)
        except KeyboardInterrupt:
            return 0
        except Exception as exc:
            print(
                json.dumps({"event": "orchestrator_error", "error": str(exc), "backoff": backoff}),
                file=sys.stderr,
                flush=True,
            )
            time.sleep(backoff)
            backoff = min(backoff * 2, 300)


if __name__ == "__main__":
    raise SystemExit(main())
