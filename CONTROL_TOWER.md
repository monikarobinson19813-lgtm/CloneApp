# CloneApp Control Tower

_Last reconciled: 2026-09-20_

This file is the first file every CloneApp engineering/control agent should read.

## Mission

Build CloneApp into a reliable Android multi-instance platform while simultaneously building a progressively autonomous engineering organization around it.

## Product milestone

**v0.1 — Virtual Engine POC**

Target:
- same unmodified CA Test App APK;
- Alice / counter 10;
- Bob / counter 50;
- independent persistent state;
- no package rewrite/resigning/root.

## Current overall state

**PRODUCT: READY — ISSUE #3**  
**ENGINEERING OS: ADVANCED FOUNDATION BUILT; FULL CLOSED-LOOP AUTONOMY NOT YET COMPLETE**

## Current product task

**Issue #3 — Build Virtual Package Registry for imported guests**

State: **CURRENT / READY**

Dependencies:
- Issue #1 — accepted/closed;
- Issue #2 — accepted/closed and merged to `main`.

Latest product evidence:
- PR #43 merged to `main`;
- Android Build #94 on the validated PR head: GREEN, including Android 16 emulator runtime evidence;
- Android Build #95 on `main`: GREEN;
- no active/queued Android run;
- no open pull request.

Issue #3 bounded scope:
- register imported package metadata;
- query package by name;
- query launcher activity/component list;
- bind package record to CA virtual users/instances;
- deterministic persistence;
- clean delete/unregister semantics.

Out of scope for Issue #3:
- ActivityManager interception;
- process hosting;
- guest execution;
- Android PackageManager spoofing.

Next bounded action:
- dispatch/implement Issue #3 on an issue-specific branch;
- add/update tests where practical;
- push one coherent patch and open/update its PR;
- stop and await required CI/runtime evidence.

## Product queue

1. Issue #1 — Guest APK import — **ACCEPTED / CLOSED**
2. Issue #2 — Parse guest package/manifest/components — **ACCEPTED / CLOSED**
3. Issue #3 — Virtual package registry — **CURRENT / READY**
4. Issue #4 — Stub guest process host — WAITING
5. Issue #5 — Guest activity launch path — WAITING
6. Issue #6 — Per-instance filesystem/IO isolation — WAITING
7. Issue #7 — Provider isolation/routing — WAITING
8. Issue #8 — Per-instance notification translation — WAITING
9. Issue #9 — Alice/Bob v0.1 integration acceptance — WAITING

## Engineering OS status

Completed foundation includes PR-first worker delivery, persistent orchestration, Codex worker adapter, observability/control room, Slack routing/status, multi-model provider gateway, GitHub event routing, and department-agent registry/channel factory.

The remaining Engineering OS work is tracked in its open issues and must not displace the ordered product queue unless its issue is explicitly made current.

## Owner-facing Control Room

GitHub Issue #20 is the live engineering status surface.

It should remain owner-facing and reconstruct:
- system state;
- active/recent runs;
- open PRs;
- open engineering issues;
- failures;
- owner-action rule.

## Current blockers

Product: **NONE for Issue #3 dispatch.**

## Owner decision required?

**NO**

No current product/commercial/security/architecture decision is required from Naveen.

## Owner involvement rule

Naveen should normally intervene only for:
- product/commercial priority changes;
- meaningful architecture forks;
- paid infrastructure decisions;
- privacy/security tradeoffs;
- irreversible user-data decisions;
- milestone/release review.

Routine build fixes, test-fixture fixes, already-approved implementation work, and progression through the existing queue do not require owner approval.

## State machine

```text
READY
  ↓
IN_PROGRESS
  ↓
PR / CI
  ├── RED
  │    ↓
  │  classify PRODUCT vs INFRA/TEST
  │    ↓
  │  bounded repair
  │
  └── GREEN
       ↓
  required runtime evidence?
       ├── NO → ACCEPTED
       └── YES → TESTING
                  ├── FAIL → FIX_REQUIRED
                  └── PASS → ACCEPTED
                               ↓
                         NEXT ELIGIBLE ISSUE
```

## Failure budget

Three materially similar failures on the same issue trigger architecture/review intervention. Do not enter an infinite patch loop.

## Primary autonomy KPI

**Eligible Work Idle Time**

If approved dependency-satisfied work exists and no worker/CI/test/owner gate is active, classify the engineering system as:

**IDLE_WITH_ELIGIBLE_WORK**

and treat that as an Engineering OS defect.

## Source-of-truth reading order for a fresh Control Tower

1. `CONTROL_TOWER.md`
2. `AGENTS.md`
3. `KNOWLEDGE_CAPTURE.md`
4. `WORKER_PROTOCOL.md`
5. GitHub Issue #20 (Control Room)
6. open Pull Requests
7. current GitHub Actions runs
8. current issue
9. `PRODUCT_VISION.md`
10. `ROADMAP.md`
11. `FEATURES.md`
12. `DECISIONS.md`
13. `ARCHITECTURE.md`
14. `TEST_PLAN.md`
15. `COMPATIBILITY_MATRIX.md`
16. `AUTONOMY_ROADMAP.md` and `AUTONOMY_VENDOR_EVAL.md`

Chat history is supplementary, not authoritative.
