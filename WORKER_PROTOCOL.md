# CloneApp Worker Protocol

_Last updated: 2026-09-19_

This protocol governs all development, testing and review agents working on CloneApp.

# 1. Required reading order

Before working:

1. `CONTROL_TOWER.md`
2. the assigned GitHub Issue
3. `PRODUCT_VISION.md`
4. `ROADMAP.md`
5. `FEATURES.md`
6. `DECISIONS.md`
7. relevant architecture/test files

The issue is the immediate scope. The vision/decisions are constraints.

# 2. Development Worker contract

A Development Worker must:

1. Work on exactly one assigned issue.
2. Inspect current code before changing it.
3. Implement the smallest coherent patch that meets the issue acceptance criteria.
4. Add/update automated tests where practical.
5. Avoid unrelated refactors.
6. Update documentation only when required by the task.
7. Commit/push the patch.
8. Record what changed and any known limitations.
9. Stop.

A Development Worker must NOT:
- choose the next issue;
- broaden scope because another improvement is obvious;
- redesign accepted architecture without a decision gate;
- repeatedly poll CI;
- mark a runtime feature GREEN merely because compilation succeeds.

# 3. CI Worker contract

CI is responsible for:
- compile;
- lint/static checks where configured;
- unit tests;
- artifact generation;
- later: emulator smoke/instrumentation tests.

CI returns machine evidence only.

A RED result means either:
- PRODUCT FAILURE; or
- INFRASTRUCTURE FAILURE.

These must be distinguished before changing product code.

# 4. Test Worker contract

A Test Worker receives:
- exact commit/build;
- exact acceptance criteria;
- exact test environment.

It must:
1. execute only the required test suite;
2. preserve evidence: logs, screenshots, test reports where useful;
3. report PASS / FAIL / INFRA FAILURE;
4. update compatibility evidence when appropriate;
5. stop.

It does not redesign the feature while testing.

# 5. Control Tower contract

The Control Tower:
- reads repository state;
- selects the next eligible issue;
- checks dependency order;
- decides whether a result is accepted;
- routes failures back to the correct worker;
- updates `CONTROL_TOWER.md`;
- escalates only defined owner-decision gates.

It must not become an unbounded coding worker.

# 6. Architecture Worker contract

Use only when:
- an issue explicitly requires an architecture decision; or
- an accepted approach hits a documented reassess gate.

Output:
- options;
- constraints;
- evidence;
- recommendation;
- explicit decision required.

Do not silently replace accepted architecture.

# 7. Definition of done

There are two different GREEN states.

## CI GREEN

Code compiled/tests passed/artifacts produced.

## FEATURE GREEN

Acceptance criteria passed in the required runtime environment.

Never conflate them.

# 8. Commit discipline

Prefer one bounded logical change per commit.

Recommended commit format:

```text
CA-<issue>: <imperative summary>
```

Examples:

```text
CA-1: import guest APK into app-private storage
CA-2: parse guest package metadata
CA-6: isolate virtual user file roots
```

# 9. Failure budget / anti-loop controls

If the same issue fails three materially similar attempts:
- stop automatic patching;
- summarize attempts and evidence;
- request Control Tower architecture/review intervention.

Do not enter a fourth blind patch loop.

If a fix changes the fundamental approach, invoke an architecture decision gate.

# 10. Parallel work rule

Parallel workers are allowed only when:
- files/ownership boundaries are clear;
- tasks are independent;
- integration order is defined.

Do not parallelize tightly coupled Virtual Engine foundations merely for speed.

# 11. Owner involvement

Naveen is Product Owner, not CI operator.

Routine flow should continue without asking:
- "shall I fix this compile error?";
- "shall I add this test?";
- "shall I push this approved issue?".

Escalate only the explicit decision gates in `CONTROL_TOWER.md`.
