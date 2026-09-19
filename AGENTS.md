# CloneApp Agent Instructions

Any coding/review agent operating in this repository must follow this order:

1. Read `CONTROL_TOWER.md`.
2. Read `WORKER_PROTOCOL.md`.
3. Read the single assigned GitHub Issue.
4. Read relevant product/architecture/test documents.
5. Work only within that issue.
6. Push one bounded logical change to the issue-specific branch.
7. Open/update the issue PR; do not push routine autonomous work directly to `main`.
8. Stop.

## Hard rules

- Do not choose your own next feature.
- Do not bypass the issue branch/PR gate for routine autonomous work.
- Do not expand scope silently.
- Do not call compile success "feature GREEN".
- Do not bypass Play Integrity, anti-tamper controls or `REQUIRE_SECURE_ENV`.
- Do not introduce root as a normal product dependency.
- Do not lower Android security/target behavior merely to regain obsolete platform access without an explicit architecture decision.
- After three materially similar failures on one issue, stop and escalate to Control Tower review.

## Source of truth

- Product destination: `PRODUCT_VISION.md`
- Competitive target: `ASPIRATION_MATRIX.md`
- Current/next/blockers: `CONTROL_TOWER.md`
- Roadmap: `ROADMAP.md`
- Feature inventory: `FEATURES.md`
- Accepted decisions: `DECISIONS.md`
- Architecture: `ARCHITECTURE.md`
- Test evidence: `TEST_PLAN.md` and `COMPATIBILITY_MATRIX.md`
