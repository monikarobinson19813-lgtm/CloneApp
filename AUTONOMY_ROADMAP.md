# CloneApp Autonomous Engineering Roadmap

_Last updated: 2026-09-19_

## North Star

CloneApp engineering should operate continuously with minimal owner intervention.

Naveen should primarily:
- review progress;
- make product/commercial decisions;
- approve architecture forks, security/privacy tradeoffs, paid infrastructure, and milestone releases.

Routine engineering should not require:
- checking whether CI finished;
- telling an agent to fix a normal build failure;
- manually choosing the next already-approved issue;
- repeatedly saying continue/yes/next;
- manually installing every build for routine smoke validation.

## Current maturity

### Level 0 — Chat-driven
Human asks for every next step.

Status: LEFT BEHIND.

### Level 1 — Repository-driven
GitHub contains roadmap, issues, decisions, current state and worker rules.

Status: IMPLEMENTED.

### Level 2 — Reactive automation
Push triggers build/unit tests/emulator. Scheduled Control Tower checks state.

Status: IN PROGRESS.

### Level 3 — Persistent orchestrator
A long-running service continuously watches eligible GitHub work and dispatches bounded coding-agent runs without waiting for chat.

Status: NEXT MAJOR ENGINEERING-OS GOAL.

### Level 4 — Event-driven autonomous engineering
GitHub events immediately trigger the appropriate next action:
- issue ready -> coding worker;
- PR pushed -> CI/QA;
- RED -> repair worker;
- GREEN -> acceptance/review;
- accepted -> next dependency-satisfied issue.

Status: PLANNED.

### Level 5 — Multi-agent engineering company
Multiple specialist lanes work concurrently within ownership boundaries:
- engine;
- UI;
- QA;
- compatibility;
- security;
- performance;
- release/documentation.

Status: FUTURE, after integration discipline is proven.

---

# Recommended Architecture

Use a Symphony-style orchestration model adapted to CloneApp and GitHub.

```text
GitHub Issues / Project
        |
        v
CA Engineering Orchestrator (24/7 daemon)
        |
        +--> Queue / dependency resolver
        +--> Retry / failure budget
        +--> Concurrency manager
        +--> Owner-decision gate
        |
        +-------------------+
        |                   |
        v                   v
Coding Worker A        Coding Worker B
isolated workspace     isolated workspace
branch/PR              branch/PR
        |                   |
        +---------+---------+
                  |
                  v
             GitHub PR
                  |
                  v
        GitHub Actions / QA
                  |
          +-------+-------+
          |               |
         RED            GREEN
          |               |
          v               v
     Repair queue     Acceptance Agent
                          |
                          v
                  merge / next issue
```

## Core rule

No autonomous worker writes directly to production/main once the orchestrator is mature.

Use:
- one issue;
- one isolated workspace;
- one branch/PR;
- required CI;
- acceptance evidence;
- auto-merge only when policy allows.

---

# Orchestrator Responsibilities

The persistent Control Tower must:

1. Poll or subscribe to GitHub issue/PR/Actions state.
2. Identify dependency-satisfied eligible work.
3. Claim work atomically so two agents cannot take the same issue.
4. Create an isolated workspace/worktree/container.
5. Dispatch one bounded agent task.
6. Persist run/session state.
7. Observe CI/test result.
8. Retry transient failures.
9. Route product failures to a bounded repair attempt.
10. Stop after three materially similar failures.
11. Escalate explicit owner-decision gates.
12. Accept/close work only after required evidence passes.
13. Select the next approved work automatically.
14. Maintain auditable logs of what each agent changed and why.

---

# Immediate Technology Direction

## Preferred starting design

- Tracker/control plane: GitHub Issues + labels/dependencies.
- Source control: GitHub.
- Coding agent: Codex as primary worker; architecture should remain model/agent replaceable.
- Orchestrator: long-running Symphony-style service.
- Runtime: small always-on Linux VM/container host.
- Isolation: one workspace/container per issue.
- CI: GitHub Actions.
- Android QA: Android Emulator in CI.
- Later real-device QA: cloud device lab and/or dedicated physical test devices.
- Observability: structured run logs + GitHub issue/PR comments + simple dashboard.
- Secrets: orchestrator secret store; workers receive minimum required credentials.

## Why not rely only on ChatGPT scheduled tasks?

Scheduled tasks are useful supervision/heartbeat, but they are not a replacement for a persistent event-driven engineering daemon. They can leave unnecessary latency between a GitHub result and the next coding action.

---

# Delivery Phases

## A1 — Make Git safe for autonomous workers

- stop routine direct-to-main development;
- create branches/PRs per issue;
- protect main;
- require build/unit/emulator checks before merge;
- define controlled auto-merge conditions;
- keep emergency/manual override.

## A2 — Persistent dispatcher POC

Build/deploy a long-running orchestrator that:
- reads CloneApp GitHub Issues;
- understands ready/blocked/in-progress/review/done;
- claims exactly one issue;
- creates an isolated workspace;
- launches a coding-agent session;
- captures output/session state;
- creates/pushes a branch/PR;
- stops cleanly.

Initial concurrency: 1.

## A3 — Automated QA feedback

- PR triggers build/unit/emulator;
- orchestrator reads results;
- RED -> one bounded repair run;
- GREEN -> acceptance evaluation;
- infra failures are retried without changing app code.

## A4 — Automatic progression

- acceptance closes current issue;
- dependencies unlock next issue;
- dispatcher immediately starts next eligible issue;
- no human "continue" required.

## A4.5 — Extract Software Engineering Power House (PH)

After the first closed-loop autonomous flow is proven on CloneApp:

- create PH as a separate reusable product/control-plane repository;
- move generic orchestration, state, CI-event handling, repair policy, worker/provider adapters, acceptance/progression framework, Slack integration and bootstrap templates into PH;
- replace CloneApp-specific constants/namespaces with project configuration;
- keep CloneApp product vision, roadmap, architecture, feature requirements, test/compatibility evidence and app code in the CloneApp repository;
- reconnect CloneApp as PH's first customer/project;
- prove PH can bootstrap and operate a second independent product without rebuilding the Engineering OS.

Do not perform this extraction before Issue #16 proves the full sequential closed loop. Portability is a milestone, not incidental refactoring.

## A5 — Controlled concurrency

Increase from 1 to 2-3 simultaneous workers only for independent tasks.

Examples:
- Engine issue + CI infrastructure issue: safe in parallel.
- Storage isolation + provider routing before common runtime foundation stabilizes: unsafe.

## A6 — Specialist QA/review agents

Add:
- test review;
- architecture review;
- security review;
- compatibility review;
- documentation/release review.

## A7 — Overnight autonomous sprint

System may work through an already-approved milestone continuously.

It stops only at:
- owner decision;
- architecture reassess;
- security restriction;
- repeated-failure threshold;
- milestone physical-device gate.

---

# Operating Metrics

Do not measure success by "number of agents busy."

Measure:

- Eligible Work Idle Time.
- Issue Lead Time.
- Build-to-next-action latency.
- First-pass CI success rate.
- Automatic repair success rate.
- Regression escape rate.
- Mean attempts per accepted issue.
- Cost per accepted issue.
- Number of owner interventions per milestone.
- Percentage of milestone completed autonomously.

Primary KPI:

> Eligible approved work should not sit idle when it can safely progress.

---

# Safety / Quality Gates

Autonomy must never override:

- Play Integrity restrictions;
- anti-tamper controls;
- REQUIRE_SECURE_ENV;
- Android security model;
- explicit owner decision gates;
- accepted architecture without an ADR/review;
- destructive user-data changes.

Autonomous speed is valuable only when verification keeps pace.

---

# Third-party solution strategy

We will continuously evaluate external tooling instead of building every orchestration primitive ourselves.

Categories:
- OpenAI Codex/Symphony ecosystem;
- GitHub Copilot cloud/third-party coding agents;
- persistent autonomous engineering platforms;
- background-agent IDE/cloud systems;
- Android cloud-device testing;
- observability and workflow engines.

Adopt external tools when they materially reduce time-to-autonomy without locking CloneApp engineering into a weak or opaque workflow.

The orchestrator contract should remain replaceable: tracker, agent and execution backend are adapters, not hard-coded product assumptions.
