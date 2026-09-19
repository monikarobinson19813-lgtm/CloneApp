# CloneApp Control Tower

_Last reconciled: 2026-09-19_

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

**PRODUCT: TESTING / FIX_REQUIRED**  
**ENGINEERING OS: ADVANCED FOUNDATION BUILT; FULL CLOSED-LOOP AUTONOMY NOT YET COMPLETE**

## Current product task

**Issue #1 — Import one guest APK into CloneApp**

Implementation exists and compiles.

Current runtime-acceptance PR:
- PR #32 — `ca/1-apk-import-runtime-acceptance`
- Android Build #54
- Build/unit/artifact stage: **GREEN**
- Android 16 emulator boot: **GREEN**
- CloneApp/Test App/test APK installation: **GREEN**
- Runtime instrumentation: **RED**

Failure:
> CA Test App APK was not visible in Android document picker.

Current classification:
**TEST-HARNESS / FIXTURE FAILURE until proven otherwise.**
The failure occurs in the automated picker fixture/discovery path; it is not yet evidence that the core GuestApkRepository import implementation is broken.

Next bounded action:
- repair the emulator document-picker fixture/selection path on PR #32;
- rerun Build #54-equivalent;
- accept Issue #1 only after import + persistence runtime evidence is GREEN;
- do not start Issue #2 before Issue #1 acceptance.

## Product queue

1. Issue #1 — Guest APK import — **TESTING / FIX_REQUIRED**
2. Issue #2 — Parse guest package/manifest/components — WAITING
3. Issue #3 — Virtual package registry — WAITING
4. Issue #4 — Stub guest process host — WAITING
5. Issue #5 — Guest activity launch path — WAITING
6. Issue #6 — Per-instance filesystem/IO isolation — WAITING
7. Issue #7 — Provider isolation/routing — WAITING
8. Issue #8 — Per-instance notification translation — WAITING
9. Issue #9 — Alice/Bob v0.1 integration acceptance — WAITING

## Engineering OS status

Completed:
- Issue #12 — PR-first autonomous worker delivery.
- Issue #13 — persistent Symphony-style orchestrator POC.
- Issue #14 — Codex worker adapter.
- Issue #17 — autonomous engineering observability/control room.
- Issue #24 — Slack Router for autonomous agent communication.
- Issue #25 — multi-model agent provider gateway.
- Issue #26 — GitHub event routing to Slack/agents.
- Issue #27 — department-agent registry and channel factory.

Operational evidence:
- Engineering Control Room workflow is running successfully.
- Slack 5-Minute Status workflow is running successfully.
- Latest main commit: `84684a5d67d70dd73579a9c403e2a7f5f575eb1e` — multi-model agent provider gateway.

Still open / not fully closed:
- Issue #10 — Android emulator smoke-test lane.
- Issue #11 — Control Tower build-state monitoring.
- Issue #15 — event-driven CI feedback into orchestrator.
- Issue #16 — automatic RED repair / acceptance / next-issue progression.
- Issue #18 — Firebase Test Lab milestone device matrix.

## Autonomy assessment

We are no longer chat-only or repository-only.

Current capabilities include:
- GitHub source of truth;
- bounded issue/branch/PR workflow;
- automated Android CI;
- Android emulator lane;
- persistent orchestrator code;
- coding-agent adapter;
- multi-model provider gateway;
- department/agent registry;
- Slack routing/status;
- live Engineering Control Room.

The remaining critical autonomy gap is the **closed loop**:

```text
CI/test result
  ↓
event reaches orchestrator immediately
  ↓
RED → classified bounded repair worker
GREEN → acceptance evaluation
  ↓
issue close / tracking update
  ↓
next dependency-satisfied issue automatically dispatched
```

Issues #15 and #16 own this gap.

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

Product:
- emulator test fixture cannot currently discover the pushed APK in Android DocumentsUI.

Engineering OS:
- closed-loop event-driven repair/progression (#15/#16) is not yet fully complete.

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
3. `WORKER_PROTOCOL.md`
4. GitHub Issue #20 (Control Room)
5. open Pull Requests
6. current GitHub Actions runs
7. current issue
8. `PRODUCT_VISION.md`
9. `ROADMAP.md`
10. `FEATURES.md`
11. `DECISIONS.md`
12. `ARCHITECTURE.md`
13. `TEST_PLAN.md`
14. `COMPATIBILITY_MATRIX.md`
15. `AUTONOMY_ROADMAP.md` and `AUTONOMY_VENDOR_EVAL.md`

Chat history is supplementary, not authoritative.
