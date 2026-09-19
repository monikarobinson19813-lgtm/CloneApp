# CloneApp Agent Communication Protocol

_Last updated: 2026-09-19_

## Purpose

Slack is CloneApp's visible internal office.

GitHub remains the authoritative engineering system of record.

The persistent Control Tower/orchestrator is the router between:
- Slack;
- GitHub;
- coding agents;
- QA/test systems;
- model providers.

Agents should communicate continuously **only when there is meaningful state to exchange**.

Do not create chatter for the sake of activity.

---

# 1. Message types

Every agent-to-agent message should be one of these:

- `TASK` — bounded work assignment.
- `STATUS` — meaningful state transition.
- `QUESTION` — information needed from another department.
- `HANDOFF` — transfer of ownership/evidence.
- `DECISION` — accepted technical/product decision.
- `BLOCKER` — work cannot progress.
- `EVIDENCE` — test/build/log/result.
- `INCIDENT` — repeated failure or operational outage.
- `DONE` — bounded task accepted.

Suggested first line:

```text
[TYPE] ISSUE #123 — short summary
```

Example:

```text
[HANDOFF] ISSUE #28 — Storage isolation ready for QA

Commit: abc123
Acceptance checks: 6/6 implemented
Requested QA: prefs, SQLite, files, delete-A-keeps-B
```

---

# 2. Department home channels

Permanent rooms:

- #cloneapp-control-room
- #cloneapp-product
- #cloneapp-engine
- #cloneapp-ui
- #cloneapp-qa
- #cloneapp-architecture
- #cloneapp-security
- #cloneapp-compatibility
- #cloneapp-autonomy
- #cloneapp-release
- #cloneapp-research
- #cloneapp-incidents

Operational feeds:

- #cloneapp-agent-bus
- #cloneapp-handoffs
- #cloneapp-decisions
- #cloneapp-5min-status
- #cloneapp-30min-digest

---

# 3. Thread-first rule

Default: use a Slack thread in the department home channel.

Create a new channel only when at least one is true:

1. Work is expected to last > 1 day.
2. More than two departments/agents must coordinate.
3. A high-severity incident requires isolation.
4. A milestone/release needs a dedicated war room.
5. A research spike has substantial evidence/discussion.
6. Owner explicitly requests a dedicated room.

This prevents uncontrolled channel sprawl.

---

# 4. Autonomous channel creation

The Slack Router may create a channel automatically when the thread-first threshold is met.

Required metadata before creation:

- parent department;
- owner agent;
- GitHub issue/PR/ADR reference;
- purpose;
- expected lifetime;
- archive condition.

Naming:

```text
cloneapp-issue-<number>-<slug>
cloneapp-incident-<number>-<slug>
cloneapp-release-<version>
cloneapp-research-<slug>
```

Examples:

```text
cloneapp-issue-28-storage-isolation
cloneapp-incident-07-emulator-flake
cloneapp-release-v0-1
cloneapp-research-android-virtualization
```

The first message must contain:

```text
Owner Agent:
Parent Department:
GitHub:
Purpose:
Exit/Archive Rule:
```

---

# 5. Channel lifecycle

A dynamic channel moves:

```text
OPEN
 ↓
ACTIVE
 ↓
WAITING / BLOCKED
 ↓
DONE
 ↓
ARCHIVE_CANDIDATE
 ↓
ARCHIVED
```

Archive candidate after:
- linked issue is closed; and
- no unresolved decision/blocker remains; and
- no message requiring action for 24 hours.

Permanent department channels are never auto-archived.

---

# 6. Cross-department routing

Agents do not freely broadcast everywhere.

Use:

```text
Department Agent
      ↓
#cloneapp-agent-bus
      ↓
Control Tower Router
      ↓
target department
```

For formal transfer:

```text
source department
      ↓
#cloneapp-handoffs
      ↓
target department
```

Accepted decisions:

```text
#cloneapp-decisions
      ↓
GitHub ADR / issue update
```

Repeated/stalled failures:

```text
#cloneapp-incidents
```

---

# 7. Source-of-truth rule

Slack is discussion/coordination.

Anything that affects implementation must be persisted back to GitHub:

- task → Issue;
- code → branch/PR;
- decision → ADR/DECISIONS.md;
- acceptance evidence → issue/PR/test report;
- compatibility result → COMPATIBILITY_MATRIX.md;
- current state → CONTROL_TOWER.md / Control Room.

If Slack and GitHub disagree, GitHub wins.

---

# 8. Bot/agent identity

Every autonomous agent message must identify:

- agent role;
- provider/backend;
- issue/task;
- run/session ID where available.

Example:

```text
Agent: Engine Worker
Backend: Codex
Issue: #28
Run: worker-9f23
```

This lets us see whether Codex, OpenAI Agents, Claude, or another backend produced the work.

---

# 9. Multi-model policy

The Control Tower may choose different model backends by task.

Example routing:

- code implementation → Codex;
- architecture review → OpenAI reasoning agent / Claude reviewer;
- independent code review → alternate provider where useful;
- product/research synthesis → research agent;
- test/log classification → QA agent.

No model provider owns project state.

All providers receive the same bounded task contract from GitHub.

---

# 10. Loop prevention

No agent may reply indefinitely to another agent.

Maximum default autonomous round-trip:

```text
QUESTION
→ ANSWER
→ ACK/HANDOFF
→ STOP
```

If still unresolved after 3 agent exchanges:
- Control Tower summarizes;
- architecture/owner gate if required;
- no further free-form debate.

---

# 11. 5-minute and 30-minute channels

## #cloneapp-5min-status

Operational ticker.

Post only if:
- active worker set changed;
- CI/test state changed;
- RED/GREEN;
- repair dispatched;
- issue accepted;
- new issue dispatched;
- blocker/owner gate appeared;
- eligible work is idle.

If nothing changed, one heartbeat every ~5 minutes may say:

```text
HEALTHY — #28 worker active; Build #102 running; eligible idle work=0.
```

## #cloneapp-30min-digest

Executive view:
- milestone progress;
- completed work;
- current work;
- repairs;
- blockers;
- owner decisions;
- eligible-work idle time;
- next 30-minute target.

---

# 12. Owner interaction

Naveen can speak in any department channel.

The Slack Router should classify owner messages:

- informational → department agent responds;
- product decision → Product + Control Tower;
- architecture decision → Architecture + Control Tower;
- urgent/blocker → Incidents + Control Tower.

When a Slack question requires a richer ChatGPT/OpenAI/Claude conversation, the router starts/resumes an agent session and posts the answer back to the same Slack thread.

The owner should not need to open a browser chat simply to wake the system.
