# CloneApp Engineering Operating System

_Last updated: 2026-09-19_

## Objective

Use CloneApp as the proving ground for a controlled, increasingly autonomous software engineering organization.

The goal is not autonomous code generation alone.

The goal is:

> **bounded autonomous execution + strong automated verification + explicit decision gates.**

# Roles

```text
Product Owner (Naveen)
        │
        ▼
Control Tower / Engineering Manager
        │
        ├── Architecture Worker
        ├── Development Worker
        ├── CI Worker
        ├── Test Worker
        ├── Compatibility Worker
        ├── Security Worker
        ├── Regression Worker
        └── Documentation Worker
```

GitHub is the shared memory and coordination layer.

# Phase 1 — Organized Engineering

Status: **IN PROGRESS**

Components:
- GitHub as source of truth;
- CONTROL_TOWER.md;
- bounded issues;
- worker protocol;
- explicit acceptance criteria;
- automatic builds;
- feature/roadmap/decision tracking.

Exit condition:
- every active engineering change maps to one bounded GitHub Issue;
- Control Tower can answer current/next/blocker without chat-memory dependence.

# Phase 2 — Automated QA

Planned:
- JVM/unit tests;
- lint/static analysis;
- Android emulator boot;
- install built APKs;
- launch smoke tests;
- instrumentation/UI tests;
- capture logs/screenshots/test reports;
- classify product failure vs infrastructure failure.

Exit condition:
- routine patches can be verified without Naveen manually installing every APK.

# Phase 3 — Automated Repair Loop

Planned flow:

```text
Issue
 ↓
Development Worker
 ↓
Push
 ↓
CI/Test
 ├── GREEN → Control Tower accepts
 └── RED   → bounded fix attempt
                 ↓
             max 3 similar failures
                 ↓
          architecture/review gate
```

Exit condition:
- routine failures are repaired and rechecked without owner intervention.

# Phase 4 — Bounded Autonomous Sprint

Control Tower may process an already-approved ordered issue set automatically.

It stops at:
- owner decision gate;
- architecture reassess gate;
- repeated failure threshold;
- security/integrity restriction;
- milestone acceptance test.

# Phase 5 — Engineering Powerhouse

Add independent specialist lanes where useful:
- Engine;
- UI;
- QA;
- compatibility;
- performance;
- security;
- release;
- documentation.

Integration remains gated.

# Core principles

1. GitHub, not chat history, is project memory.
2. Agents have roles and stop conditions.
3. No worker chooses its own infinite backlog.
4. Automation expands only with verification.
5. Build GREEN is not Feature GREEN.
6. Failures produce evidence, not random patches.
7. Three similar failed attempts trigger review.
8. Product Owner sees decisions and milestone summaries, not routine plumbing.
