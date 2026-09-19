# CloneApp Persistent Orchestrator POC

This directory contains the first persistent control-plane service for the CloneApp autonomous engineering system.

## What this POC proves

- sub-hour polling (default: 60 seconds);
- GitHub Issues as the work source;
- dependency-aware eligibility;
- bounded concurrency (default: 1);
- SQLite-backed restart-safe state;
- atomic claim/release mechanics;
- one isolated workspace per issue;
- owner-gate stop state;
- structured JSON event logs;
- exponential retry/backoff for transient failures;
- machine-readable status.

**It deliberately does not launch a coding agent yet.** Issue #14 adds the worker adapter.

## Run once

```bash
export GITHUB_TOKEN=...
export CA_GITHUB_REPO=monikarobinson19813-lgtm/CloneApp
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json --once
```

## Run as a daemon

```bash
export GITHUB_TOKEN=...
export CA_GITHUB_REPO=monikarobinson19813-lgtm/CloneApp
export CA_POLL_SECONDS=60
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json
```

## State

Default state database:

```text
.ca-orchestrator/state.sqlite3
```

Inspect it without opening SQLite manually:

```bash
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json --status
```

Release a claimed issue:

```bash
PYTHONPATH=orchestrator/src python -m ca_orchestrator --plan orchestrator/plan.example.json --release 13
```

## Workspace modes

`directory` is the safe POC/test mode.

For real workers use Git worktrees:

```bash
export CA_WORKSPACE_MODE=worktree
export CA_REPO_PATH=/opt/cloneapp/repo
```

The worker adapter in Issue #14 will consume these isolated workspaces.

## Deployment

A Dockerfile and example systemd unit are included. Actual always-on deployment requires a host/runtime and a scoped GitHub credential; selecting paid hosting is an owner-decision gate.
