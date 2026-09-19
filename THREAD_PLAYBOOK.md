# CloneApp Chat / Agent Thread Playbook

_Last updated: 2026-09-19_

Chat threads are interfaces to the repository. They are not project memory.

## 1. Control Tower thread

Keep one long-lived thread called approximately:

**CA — Control Tower**

Use it for:
- where are we?;
- what is next?;
- accepting/rejecting worker results;
- architecture/product decisions;
- milestone summaries;
- owner decision gates.

The Control Tower should avoid doing large implementation work itself.

If the thread becomes unwieldy, start a new Control Tower thread with:

> Read the CloneApp GitHub repository beginning with CONTROL_TOWER.md and WORKER_PROTOCOL.md. You are the CloneApp Control Tower. Reconstruct current state only from GitHub and continue from the current state machine. Do not rely on prior chat memory.

No manual project recap should be necessary.

## 2. Development Worker thread

Use one disposable worker thread per bounded GitHub Issue.

Start it with:

> Work on CloneApp GitHub Issue #N only. Read AGENTS.md, CONTROL_TOWER.md and WORKER_PROTOCOL.md first. Implement the smallest patch satisfying the issue acceptance criteria, push it to GitHub, report the commit/build state, and stop. Do not start the next issue.

Close/abandon the thread after the issue is accepted.

## 3. Test Worker thread

Use when emulator/device testing requires interactive analysis.

Start it with:

> Validate CloneApp Issue #N against its acceptance criteria for commit/build X. Read CONTROL_TOWER.md and TEST_PLAN.md. Report PASS / FAIL / INFRA FAILURE with evidence. Do not modify architecture or begin unrelated fixes.

## 4. Architecture thread

Create only at an architecture/reassess gate.

It should produce a decision proposal, not implementation.

## 5. When to start a new thread

Start a new worker thread when:
- a new GitHub Issue becomes current;
- a task needs a different specialist role;
- a focused test/debug session begins.

Do NOT start a new thread merely because:
- a day changed;
- CI is building;
- a few messages accumulated.

## 6. The answer to "what do I do now?"

Look only at `CONTROL_TOWER.md`.

The fields that matter are:
- Current milestone;
- Current task;
- State;
- Last accepted build;
- Next task;
- Blockers;
- Owner decision required?

That file should make the next action obvious without reconstructing chat history.
