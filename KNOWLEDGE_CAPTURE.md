# GitHub Knowledge Capture Policy

_Last updated: 2026-09-19_

GitHub is the durable engineering memory for CloneApp and the Software Engineering Power House (PH) work being proven here.

Chat may produce ideas, decisions, requirements and execution instructions, but an important outcome is not treated as durable project truth until it is captured in the appropriate GitHub artifact.

## Capture threshold

Persist a discussion outcome when at least one of these is true:

- it changes product scope, behavior or user promise;
- it changes architecture, security, privacy or infrastructure direction;
- it adds, removes or reorders a roadmap milestone/version;
- it creates or changes a feature requirement;
- it changes acceptance, testing or compatibility criteria;
- it changes the Engineering OS, autonomy model, agent/department model or owner gates;
- it affects how future projects will reuse PH;
- it creates executable engineering work;
- it is an owner decision that a future engineer must not have to rediscover;
- losing it in 30 days would create rework, ambiguity or a wrong implementation.

Do not create permanent documentation for:

- casual brainstorming with no selected direction;
- transient troubleshooting details that are fully resolved and already represented by commits/tests;
- repeated status snapshots that belong in GitHub Actions, PRs or Control Room Issue #20;
- speculative ideas that have not yet earned roadmap/backlog status.

## Classification matrix

| Discussion outcome | Authoritative GitHub home |
|---|---|
| Accepted product/architecture/operating decision and reasoning | `DECISIONS.md` |
| Product destination / user promise / long-term aspiration | `PRODUCT_VISION.md` |
| Version, milestone, sequencing or future release direction | `ROADMAP.md` |
| Feature requirement or capability inventory | `FEATURES.md` |
| Technical architecture / component boundaries | `ARCHITECTURE.md` |
| Acceptance criteria / test evidence / quality gate | `TEST_PLAN.md` |
| Device/app/OEM compatibility evidence | `COMPATIBILITY_MATRIX.md` |
| Engineering organization / operating process | `ENGINEERING_OS.md` |
| Autonomy stages / orchestration evolution / PH roadmap | `AUTONOMY_ROADMAP.md` |
| Concrete bounded work to execute | GitHub Issue |
| Code implementation | Branch + PR + commits |
| Live engineering state | Control Room Issue #20 + PRs + Actions |
| Temporary owner-facing heartbeat | Slack status channel |

One discussion may legitimately update more than one artifact. Example: an accepted new capability may require a decision entry, roadmap placement, feature entry and one or more implementation issues.

## Three-state rule: Idea -> Decision -> Work

### 1. Idea
An idea can remain in chat while it is exploratory.

Capture it only when it is worth preserving for future consideration. If so, place it in the relevant backlog/roadmap or create a research issue.

### 2. Decision
Once the owner/Control Tower selects a direction, record it in the authoritative document with:
- decision;
- date;
- reason;
- alternatives/constraints where useful;
- reconsideration trigger if applicable.

### 3. Work
If the decision requires implementation, create a bounded GitHub Issue with:
- objective;
- dependencies;
- acceptance criteria;
- stop condition;
- owner gate if any.

A decision is not the same as completed implementation.

## Capture workflow for Control Tower

After an important owner conversation:

1. Extract the durable outcomes, not the whole conversation.
2. Classify each outcome using the matrix above.
3. Check whether GitHub already contains it.
4. Update the minimum authoritative documents needed.
5. Create or update bounded Issues for executable work.
6. Cross-link decisions, roadmap items and issues when helpful.
7. Do not duplicate live status into static documents.
8. Report back what was captured and what remains only exploratory.

## PH / CA separation rule

PH is being proven inside the CloneApp repository, but reusable engineering capabilities must not become permanently dependent on CloneApp-specific product assumptions.

Until the first closed autonomous loop is proven:
- keep implementation in this repository to minimize migration risk;
- isolate generic orchestration logic from CloneApp-specific configuration where practical.

After the closed loop is proven:
- extract PH into a separate reusable product/control-plane repository;
- keep project-specific product vision, architecture, tests, acceptance policy and configuration in each product repository;
- connect CloneApp to PH as its first customer/proving project;
- bootstrap future products from PH rather than rebuilding the Engineering OS.

The extraction itself requires a dedicated milestone/issue and must not be performed as incidental refactoring.
