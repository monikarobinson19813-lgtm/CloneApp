# CloneApp (CA) Architecture & Product Decisions

_Last updated: 2026-09-19_

This file records important decisions so future development does not lose the reasoning behind the architecture.

---

## ADR-001 — Use GitHub as the engineering source of truth

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

Keep CA engineering state in the CloneApp GitHub repository:
- roadmap;
- feature inventory;
- architecture;
- decisions;
- compatibility matrix;
- test plan;
- source code;
- CI/build status.

Notion may later be used for broader product/business/competitor research, but it should not duplicate live engineering status.

### Reason

Duplicating status in GitHub and Notion would create drift. GitHub already contains the code, commits, Actions builds and technical history.

---

## ADR-002 — Virtualization-first for v0.1

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

The first working CA engine will be a Virtual Engine, not a Work Profile engine.

### Reason

The core product vision includes x3/x4 and potentially more instances. Native Android profile counts and OEM behavior are not a clean arbitrary-N primitive.

The Virtual Engine is the architecture that must be proven if CA is to behave like Parallel Space / 2Accounts-style products.

### Alternative considered

Android Managed/Work Profile first.

### Why not primary for v0.1

It offers stronger OS isolation and excellent compatibility but does not directly solve the many-instance requirement.

### Reconsider if

The controlled CA Test App cannot run reliably under a modern virtual runtime without obsolete target-SDK behavior, root, or fragile hidden-API dependencies.

---

## ADR-003 — Preserve a future Native/Profile engine

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

CA will be designed as a future hybrid platform:

```text
CloneApp
├── CA Virtual Engine
└── CA Native/Profile Engine
```

The Virtual Engine is the primary multi-instance path. A Native/Profile engine can later provide stronger OS-backed isolation or compatibility for apps unsuitable for virtualization.

---

## ADR-004 — Do not use APK package rewriting as the primary architecture

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

Do not build CA around rewriting package IDs and resigning arbitrary third-party APKs.

### Reason

It creates compatibility problems with:
- app signatures;
- updates;
- split APKs;
- app links/deep links;
- provider authorities;
- signature permissions;
- OAuth;
- self-checks;
- licensing;
- Play Integrity.

Package rewriting may only be reconsidered for narrowly controlled cases, not as the CA core engine.

---

## ADR-005 — No root requirement for the core product

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

CA core must work on stock Android.

One-time provisioning may be considered for specific future modes, but daily operation must not rely on a PC/ADB/root.

---

## ADR-006 — Do not bypass application security/integrity controls

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

CA will not:
- bypass Play Integrity;
- hide virtualization to evade app restrictions;
- bypass anti-tamper controls;
- ignore `REQUIRE_SECURE_ENV`.

### Behavior

If an app cannot legally/technically run in the Virtual Engine, mark it incompatible or route it to a future Native/Profile engine.

---

## ADR-007 — Controlled Test App before WhatsApp

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

Do not begin guest-runtime development with WhatsApp.

### Sequence

1. CA Test App x2.
2. Simple third-party app x2.
3. Controlled Firebase/FCM app x2.
4. WhatsApp x2.
5. WhatsApp x3.
6. WhatsApp x4.

### Reason

The Test App lets CA distinguish engine defects from third-party app compatibility problems.

---

## ADR-008 — Same unmodified APK for v0.1

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

The Alice/Bob POC must use the exact same unmodified Test App APK for both virtual instances.

No package-name rewrite and no resigning.

---

## ADR-009 — Do not equate virtual isolation with Android kernel isolation

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

CA documentation and UI must distinguish:
- CA Virtual isolation;
- native Android profile/UID isolation.

### Reason

Multiple virtual instances may share CA's real Android UID and depend on CA's own mediation layer. That is not identical to separate Android UIDs enforced by the kernel.

---

## ADR-010 — Current Android baseline

**Status:** Accepted for current prototype  
**Date:** 2026-09-19

### Current project configuration

- compileSdk: 36
- targetSdk: 36
- minSdk: 33
- JDK: 17
- Android Gradle Plugin: 9.4.0

### Note

This may be revisited as the engine encounters real compatibility requirements. Do not lower the target SDK simply to regain obsolete platform behavior.

---

## ADR-011 — Incremental GREEN-build discipline

**Status:** Accepted  
**Date:** 2026-09-19

### Decision

Work in bounded increments:
- implement;
- commit;
- GitHub Actions builds automatically;
- continue development without repeatedly polling CI;
- check CI at meaningful checkpoints;
- RED → inspect/fix;
- GREEN → device test if required.

### Important distinction

CI GREEN means the project compiles and artifacts were produced.

Feature GREEN means its physical/runtime acceptance tests also pass.

---

## ADR-012 — Initial OEM validation order

**Status:** Accepted  
**Date:** 2026-09-19

### Order

1. Pixel — clean Android reference.
2. Samsung — large OEM surface and real-world importance.
3. OnePlus.
4. Xiaomi.
5. Oppo.
6. Vivo.
7. Motorola.

Do not treat an OEM-specific clone feature as CA's core architecture.

---

## ADR-013 — Compatibility tiers

**Status:** Accepted as framework; exact criteria to evolve  
**Date:** 2026-09-19

### Tier A

Works reliably in CA Virtual Engine with core functionality.

### Tier B

Works with documented limitations.

### Tier C

Requires CA Native/Profile engine or a special supported mode.

### Tier D

Unsupported / unsuitable for CA.

Compatibility must be based on tested evidence, not assumptions.
