# CloneApp Persistent Orchestrator

This directory contains the persistent control plane for the CloneApp autonomous engineering system.

## Current capabilities

- sub-hour polling (default: 60 seconds);
- GitHub Issues as the work source;
- dependency-aware eligibility;
- bounded concurrency;
- SQLite-backed restart-safe state;
- atomic claim/release mechanics;
- one isolated Git worktree per issue;
- owner-decision stop state;
- structured JSON event logs;
- exponential retry/backoff for transient orchestrator failures;
- machine-readable status;
- optional bounded Codex coding-worker dispatch;
- signed GitHub `workflow_run` webhook ingress with delivery dedupe;
- persistent CI correlation by issue / PR / commit and build/emulator/infra classification.

## Codex worker contract

When `CA_ENABLE_CODEX_WORKER=1`, a newly claimed issue can be handed to a Codex non-interactive worker.

The worker invocation uses:

```text
codex exec
--json
--ephemeral
--sandbox workspace-write
--ignore-user-config
--output-last-message <outside-worktree-path>
<prompt>
```

The adapter deliberately does **not** use the deprecated `--full-auto` compatibility flag.

The worker receives:
- the one assigned GitHub issue number/title/body;
- AGENTS.md;
- CONTROL_TOWER.md;
- WORKER_PROTOCOL.md;
- an explicit instruction not to select another issue, push, merge, or alter architecture outside scope.

The worker environment receives the minimum operational environment plus Codex authentication. GitHub credentials are intentionally not passed into the coding subprocess.

After a successful worker run, the orchestrator:
1. stores JSONL/stderr/final-message evidence outside the worktree;
2. extracts the Codex thread ID;
3. creates one bounded local Git commit;
4. records the commit/session result in SQLite;
5. leaves the claim active for review/CI/PR handling.

Automatic RED repair, PR creation/merge, and next-issue progression remain outside Issue #14.

## Run without coding workers

```bash
export GITHUB_TOKEN=...
export CA_GITHUB_REPO=monikarobinson19813-lgtm/CloneApp
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json
```

## Enable Codex worker dispatch

The always-on host must contain:
- the CloneApp Git repository;
- Codex CLI;
- Git;
- Python 3.11+;
- a scoped GitHub token for the orchestrator control plane;
- Codex authentication for the coding subprocess.

Example:

```bash
export GITHUB_TOKEN=...
export CA_GITHUB_REPO=monikarobinson19813-lgtm/CloneApp

export CA_ENABLE_CODEX_WORKER=1
export CA_WORKSPACE_MODE=worktree
export CA_REPO_PATH=/opt/cloneapp/repo
export CA_WORK_ROOT=/opt/cloneapp/workspaces

export CODEX_API_KEY=...
# Or use CODEX_ACCESS_TOKEN for an approved trusted automation environment.

PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json
```

`GITHUB_TOKEN` stays in the orchestrator process. The Codex subprocess environment is allow-listed and does not receive it.

## State and worker evidence

Default state database:

```text
.ca-orchestrator/state.sqlite3
```

Worker events/logs live beside the state database, outside the Git worktree:

```text
.ca-orchestrator/worker-artifacts/issue-<N>/
```

Inspect state:

```bash
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json --status
```

Release a claimed issue manually:

```bash
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json --release 14
```

## Deployment

A Dockerfile and example systemd unit are included.

Actual 24x7 deployment still requires selecting/provisioning an always-on host and configuring scoped credentials. A paid hosting decision remains an owner gate; the software should stay portable across Linux VM/container providers.


## Event-driven CI feedback

Issue #15 adds a webhook ingress path so CI state can wake the daemon immediately instead of waiting for the next poll interval.

Run the daemon with webhook intake enabled:

```bash
export GITHUB_TOKEN=...
export CA_GITHUB_REPO=monikarobinson19813-lgtm/CloneApp
export CA_GITHUB_WEBHOOK_SECRET='use-a-random-shared-secret'

PYTHONPATH=orchestrator/src python -m ca_orchestrator \
  --plan orchestrator/plan.example.json \
  --webhook \
  --webhook-host 127.0.0.1 \
  --webhook-port 8787
```

GitHub webhook endpoint:

```text
POST /github/webhook
```

Subscribe to the GitHub `workflow_run` event. The receiver:

- requires and validates `X-Hub-Signature-256`;
- deduplicates on `X-GitHub-Delivery`;
- correlates branch convention `ca/<issue>-...` / `eng-os/<issue>-...` to the exact issue;
- records PR number and head SHA from the workflow event;
- fetches completed workflow jobs to classify RED as build, emulator, or infrastructure;
- persists ACTIVE / RED / GREEN state in SQLite;
- wakes the daemon immediately;
- gates ordinary issue dispatch while CI is ACTIVE or awaiting RED/GREEN handling.

This issue deliberately does **not** auto-repair RED, auto-accept GREEN, close issues, or start the next issue. Those transitions belong to Engineering OS Issue #16.

The listener defaults to loopback. Exposing it to GitHub requires a secure reachable endpoint and repository webhook configuration. Hosting/provisioning remains a separate deployment decision; no paid infrastructure is introduced by this implementation.
