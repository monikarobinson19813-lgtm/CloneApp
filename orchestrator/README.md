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
- optional bounded Codex coding-worker dispatch.

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
