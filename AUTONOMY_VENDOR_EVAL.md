# CloneApp Autonomous Engineering Platform Evaluation

_Last updated: 2026-09-19_

## Objective

Reach continuous, bounded autonomous engineering as quickly as possible without sacrificing code quality, auditability or Android test evidence.

## Current recommendation

### Primary architecture

**Symphony-style persistent orchestrator + GitHub control plane + replaceable coding-agent backends.**

Why:
- matches our desired operating model exactly;
- keeps GitHub as source of truth;
- provides issue claiming, isolated workspaces, concurrency, retries and lifecycle;
- avoids making one vendor's UI the project-management system;
- allows Codex/Cursor/other workers to be swapped or specialized later.

### Primary coding backend to test first

**Codex**, because the current project is already being developed successfully through ChatGPT/OpenAI tooling and the Symphony model is explicitly designed around long-running coding-agent orchestration.

### Fast off-the-shelf secondary option

**Cursor Cloud Agents / Background Agents.**

Useful capabilities:
- remote isolated coding environments;
- GitHub integration;
- agents can work asynchronously;
- PR-oriented handoff;
- event/schedule automation;
- programmatic Background Agents API;
- parallel/subagent capabilities.

Potential downside:
- another paid vendor/runtime;
- code/secrets/environment move through vendor-managed cloud;
- less control over orchestration semantics than our own control plane.

### GitHub-native secondary option

**GitHub Copilot cloud/third-party coding agents.**

Useful capabilities:
- issue -> autonomous agent -> pull request;
- deeply integrated with GitHub;
- API support for assigning issues to coding agents;
- GitHub supports third-party coding agents including Codex and Claude in preview;
- built-in security scanning around agent changes.

Potential downside:
- GitHub's agent workflow is excellent for individual issue execution but is not, by itself, the full dependency-aware continuous Control Tower we want;
- preview surfaces may change.

### Enterprise platform to keep under observation

**Factory Droids.**

Interesting capabilities:
- multi-agent sessions;
- persistent Droid Computers;
- managed/BYO machines;
- enterprise engineering focus.

Evaluate only if our self-directed stack becomes operationally expensive or if enterprise-grade controls materially accelerate delivery.

---

# Android QA platform

## Local/GitHub-hosted Android Emulator

Use for:
- every-PR smoke tests;
- instrumentation;
- quick regression;
- no-cost/low-cost routine validation.

## Firebase Test Lab

Use later for:
- Pixel/Samsung/other physical-device validation;
- Android-version matrix;
- instrumentation suites;
- milestone/release gates;
- device-specific regressions.

Do not run expensive physical-device matrices on every trivial commit. Use them at defined gates.

---

# Decision framework

Adopt a third-party tool when it materially improves at least one of:

- build-to-next-action latency;
- autonomous issue throughput;
- isolation/security;
- Android test coverage;
- observability;
- agent concurrency;
- cost per accepted issue.

Reject/replace it if it introduces:

- opaque state we cannot reconstruct;
- uncontrolled direct-to-main writes;
- inability to enforce stop conditions;
- excessive vendor lock-in;
- weak evidence/testing;
- security/privacy exposure disproportionate to benefit.

---

# Recommended target stack

```text
GitHub Issues / Project
        |
        v
Persistent CA Orchestrator
(Symphony-style)
        |
        +--> Codex worker       [primary]
        +--> Cursor worker      [optional]
        +--> GitHub agent       [optional]
        |
        v
Branch / Pull Request
        |
        v
GitHub Actions
  - compile
  - unit
  - lint
  - Android emulator
        |
        v
Acceptance Agent
        |
   +----+----+
   |         |
  RED      GREEN
   |         |
repair      merge
   |         |
   +----> next issue

Milestone gates
        |
        v
Firebase Test Lab
physical + virtual device matrix
```

## Principle

Use the best worker for a task, but keep **ownership, queue state, acceptance criteria and audit trail in our own GitHub-centered engineering system**.
