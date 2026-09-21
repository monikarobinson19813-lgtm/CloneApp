# CloneApp Control Tower

_Last reconciled: 2026-09-21_

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

**PRODUCT: READY — ISSUE #9 ACCEPTANCE GATE**  
**ENGINEERING OS: ADVANCED FOUNDATION BUILT; FULL CLOSED-LOOP AUTONOMY NOT YET COMPLETE**

## Current product task

**Issue #9 — Alice/Bob v0.1 integration acceptance gate**

State: **CURRENT / READY**

Dependencies:
- Issues #1 through #8 — accepted/closed.

Latest product evidence:
- PR #50 (Issue #8 per-instance notification translation) merged to `main` as `6b6db6a049784230c6b25cfb002fc86339c188ad`;
- validated PR head passed Android Build #134 including Android 16 emulator runtime evidence;
- Issue #8 is closed completed;
- Issue #9 is open and dependency-satisfied.

Issue #9 bounded scope:
- run the complete v0.1 Alice/Bob acceptance suite;
- record exact CA commit/build and Android/device;
- verify storage/provider/notification isolation and lifecycle persistence;
- explicitly record native ARM64 probe status;
- update `COMPATIBILITY_MATRIX.md` with evidence.

Out of scope for Issue #9:
- feature implementation or opportunistic product patches;
- broad architecture changes;
- Play Integrity or security bypasses.

If acceptance FAILS, create a bounded defect issue and route it through Control Tower; do not patch inside Issue #9.

Next bounded action:
- execute/check the Issue #9 v0.1 acceptance gate against the exact current CA commit/build;
- preserve automated/runtime evidence;
- report only PASS / FAIL / INFRASTRUCTURE FAILURE;
- stop.

## Product queue

1. Issue #1 — Guest APK import — **ACCEPTED / CLOSED**
2. Issue #2 — Parse guest package/manifest/components — **ACCEPTED / CLOSED**
3. Issue #3 — Virtual package registry — **ACCEPTED / CLOSED**
4. Issue #4 — Stub guest process host — **ACCEPTED / CLOSED**
5. Issue #5 — Guest activity launch path — **ACCEPTED / CLOSED**
6. Issue #6 — Per-instance filesystem/IO isolation — **ACCEPTED / MERGED**
7. Issue #7 — Provider isolation/routing — **ACCEPTED / MERGED**
8. Issue #8 — Per-instance notification translation — **ACCEPTED / CLOSED**
9. Issue #9 — Alice/Bob v0.1 integration acceptance — **CURRENT / READY**

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

Product: **NONE for Issue #9 acceptance execution.**

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
