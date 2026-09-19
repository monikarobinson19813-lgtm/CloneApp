# CloneApp Control Tower

_Last updated: 2026-09-19_

This file is the first file every CloneApp engineering agent should read.

## Mission

Build CloneApp into a reliable Android multi-instance platform using the product vision, architecture, roadmap and compatibility rules already accepted in this repository.

## Current milestone

**v0.1 — Virtual Engine POC**

Goal: run the same unmodified CA Test App APK as two independent virtual instances:

- Alice / counter 10
- Bob / counter 50

## Current phase

**Engineering Operating System — Phase 2: toward persistent autonomy**

Implemented:
- bounded GitHub Issues;
- worker protocol;
- explicit acceptance criteria;
- master/control-tower state;
- automatic CI;
- automatic build-state monitoring.

Next Engineering OS upgrade:
- **Issue #10 — Android emulator smoke-test lane: PUSHED, awaiting CI evidence.**

## Current state

**PUSHED — awaiting CI/runtime evidence**

## Current engineering task

**Issue #1 — Import one guest APK into CloneApp**

Implementation has been pushed through commit `063ecae3f968c2f56ae17eaacfd85f809bf7b5b2` with status tracked in later documentation commits. Await CI/unit-test results and runtime import verification. Do not start Issue #2 until Control Tower accepts Issue #1.

## Ordered product queue

1. Issue #1 — Guest APK import
2. Issue #2 — Parse guest package/manifest/components
3. Issue #3 — Virtual package registry
4. Issue #4 — Stub guest process host
5. Issue #5 — Guest activity launch path
6. Issue #6 — Per-instance filesystem/IO isolation
7. Issue #7 — Provider isolation/routing
8. Issue #8 — Per-instance notification translation
9. Issue #9 — Alice/Bob v0.1 integration acceptance

Do not skip ahead merely because a later task looks interesting.

## Engineering OS support queue

- Issue #10 — Android emulator smoke-test lane — **PUSHED**, awaiting reliable evidence.
- Issue #11 — Control Tower monitoring — **operational**, but hourly scheduling is transitional.
- Issue #12 — PR-first autonomous worker delivery.
- Issue #13 — Persistent Symphony-style orchestrator POC.
- Issue #14 — Codex worker adapter.
- Issue #15 — Event-driven CI feedback.
- Issue #16 — Automatic RED repair / acceptance / next-issue progression.
- Issue #17 — Engineering observability dashboard.
- Issue #18 — Firebase Test Lab milestone device matrix.

**North Star:** replace hour-scale scheduled supervision with a persistent, event-driven orchestrator so approved work does not sit idle between CI/test completion and the next bounded engineering action.

See `AUTONOMY_ROADMAP.md` and `AUTONOMY_VENDOR_EVAL.md`.

## Last accepted build

- CI checkpoint: Android Build #3
- Commit: `194abb68327132e96480c34ac92ae430cbd2a435`
- Result: GREEN
- Meaning: source compiled and both debug APK artifacts were produced.
- Runtime virtualization: NOT YET GREEN.

Documentation-only/control-system commits after this checkpoint do not change the accepted runtime baseline.

## Current blockers

None.

## Owner decision required?

**NO**

The current queue follows already approved product and architecture decisions.

## When the Control Tower must stop and ask Naveen

Stop for owner input if work would:
- materially change product vision;
- abandon virtualization-first architecture;
- add root as a core dependency;
- add a paid external dependency/service;
- lower Android security/target requirements to regain obsolete behavior;
- bypass Play Integrity, anti-tamper controls or `REQUIRE_SECURE_ENV`;
- remove a committed major feature;
- make a consequential privacy/security tradeoff;
- move WhatsApp testing ahead of the controlled POC sequence;
- create an irreversible user-data design decision.

Routine implementation, refactoring, tests, build fixes and already-approved roadmap tasks do not require owner approval.

## State machine

```text
READY
  ↓
IN_PROGRESS
  ↓
PUSHED
  ↓
CI_BUILDING
  ├── RED → FIX_REQUIRED → IN_PROGRESS
  └── GREEN
          ↓
      TEST_REQUIRED?
        ├── NO → ACCEPTED
        └── YES → TESTING
                    ├── FAIL → FIX_REQUIRED
                    └── PASS → ACCEPTED
                                  ↓
                              NEXT ISSUE
```

## Meaning of ACCEPTED

A task is accepted only when:
1. its issue acceptance criteria are satisfied;
2. required automated checks pass;
3. required emulator/device checks pass;
4. project tracking docs are updated when the change alters feature status.

## Anti-loop rule

No agent may continue inventing work after completing its assigned issue.

**Push the bounded change, report state, and stop.**

Only the Control Tower selects the next issue.
