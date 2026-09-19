from __future__ import annotations

import json
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from .models import Claim


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class StateStore:
    """Restart-safe orchestration state backed by SQLite."""

    def __init__(self, path: str | Path, concurrency: int = 1) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.concurrency = concurrency
        self._init_schema()

    @contextmanager
    def _connection(self):
        conn = sqlite3.connect(self.path, timeout=30, isolation_level=None)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
        finally:
            conn.close()

    def _init_schema(self) -> None:
        with self._connection() as conn:
            conn.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS claims (
                    issue_number INTEGER PRIMARY KEY,
                    workspace TEXT NOT NULL,
                    status TEXT NOT NULL,
                    claimed_at TEXT NOT NULL,
                    released_at TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE IF NOT EXISTS failures (
                    issue_number INTEGER NOT NULL,
                    fingerprint TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (issue_number, fingerprint)
                );
                CREATE TABLE IF NOT EXISTS worker_runs (
                    run_id TEXT PRIMARY KEY,
                    issue_number INTEGER NOT NULL,
                    workspace TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    completed_at TEXT,
                    exit_code INTEGER,
                    thread_id TEXT,
                    commit_sha TEXT,
                    events_path TEXT,
                    final_message_path TEXT,
                    stderr_path TEXT,
                    error_kind TEXT,
                    error_message TEXT
                );
                CREATE INDEX IF NOT EXISTS worker_runs_issue_started
                    ON worker_runs(issue_number, started_at DESC);
                CREATE TABLE IF NOT EXISTS github_deliveries (
                    delivery_id TEXT PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    payload_sha256 TEXT NOT NULL,
                    received_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS ci_feedback (
                    run_id INTEGER PRIMARY KEY,
                    delivery_id TEXT NOT NULL,
                    workflow_name TEXT NOT NULL,
                    issue_number INTEGER,
                    pr_number INTEGER,
                    commit_sha TEXT NOT NULL,
                    head_branch TEXT NOT NULL,
                    status TEXT NOT NULL,
                    conclusion TEXT,
                    result_state TEXT NOT NULL,
                    classification TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS ci_feedback_issue_updated
                    ON ci_feedback(issue_number, updated_at DESC);
                """
            )

    def try_claim(self, issue_number: int, workspace: str) -> bool:
        with self._connection() as conn:
            conn.execute("BEGIN IMMEDIATE")
            active = conn.execute(
                "SELECT COUNT(*) AS n FROM claims WHERE status = 'claimed'"
            ).fetchone()["n"]
            existing = conn.execute(
                "SELECT status FROM claims WHERE issue_number = ?", (issue_number,)
            ).fetchone()

            if active >= self.concurrency or (
                existing is not None and existing["status"] == "claimed"
            ):
                conn.execute("ROLLBACK")
                return False

            conn.execute(
                """
                INSERT INTO claims(issue_number, workspace, status, claimed_at, released_at, attempts)
                VALUES (?, ?, 'claimed', ?, NULL, 0)
                ON CONFLICT(issue_number) DO UPDATE SET
                    workspace = excluded.workspace,
                    status = 'claimed',
                    claimed_at = excluded.claimed_at,
                    released_at = NULL
                """,
                (issue_number, workspace, _now()),
            )
            conn.execute("COMMIT")
            return True

    def release(self, issue_number: int) -> bool:
        with self._connection() as conn:
            cursor = conn.execute(
                """
                UPDATE claims
                SET status = 'released', released_at = ?
                WHERE issue_number = ? AND status = 'claimed'
                """,
                (_now(), issue_number),
            )
            return cursor.rowcount == 1

    def active_claims(self) -> list[Claim]:
        with self._connection() as conn:
            rows = conn.execute(
                """
                SELECT issue_number, workspace, status, claimed_at, attempts
                FROM claims
                WHERE status = 'claimed'
                ORDER BY claimed_at
                """
            ).fetchall()
        return [
            Claim(
                issue_number=row["issue_number"],
                workspace=row["workspace"],
                status=row["status"],
                claimed_at=row["claimed_at"],
                attempts=row["attempts"],
            )
            for row in rows
        ]

    def start_worker_run(
        self,
        issue_number: int,
        workspace: str,
        events_path: str,
        final_message_path: str,
        stderr_path: str,
    ) -> str:
        run_id = uuid.uuid4().hex
        with self._connection() as conn:
            conn.execute(
                """
                INSERT INTO worker_runs(
                    run_id, issue_number, workspace, status, started_at,
                    events_path, final_message_path, stderr_path
                )
                VALUES (?, ?, ?, 'running', ?, ?, ?, ?)
                """,
                (
                    run_id,
                    issue_number,
                    workspace,
                    _now(),
                    events_path,
                    final_message_path,
                    stderr_path,
                ),
            )
        return run_id

    def finish_worker_run(
        self,
        run_id: str,
        *,
        status: str,
        exit_code: int | None = None,
        thread_id: str | None = None,
        commit_sha: str | None = None,
        error_kind: str | None = None,
        error_message: str | None = None,
    ) -> None:
        with self._connection() as conn:
            conn.execute(
                """
                UPDATE worker_runs
                SET status = ?, completed_at = ?, exit_code = ?, thread_id = ?,
                    commit_sha = ?, error_kind = ?, error_message = ?
                WHERE run_id = ?
                """,
                (
                    status,
                    _now(),
                    exit_code,
                    thread_id,
                    commit_sha,
                    error_kind,
                    error_message,
                    run_id,
                ),
            )

    def latest_worker_run(self, issue_number: int) -> dict | None:
        with self._connection() as conn:
            row = conn.execute(
                """
                SELECT * FROM worker_runs
                WHERE issue_number = ?
                ORDER BY started_at DESC
                LIMIT 1
                """,
                (issue_number,),
            ).fetchone()
        return dict(row) if row is not None else None

    def record_failure(self, issue_number: int, fingerprint: str) -> int:
        with self._connection() as conn:
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute(
                """
                SELECT count FROM failures
                WHERE issue_number = ? AND fingerprint = ?
                """,
                (issue_number, fingerprint),
            ).fetchone()
            count = 1 if row is None else int(row["count"]) + 1
            conn.execute(
                """
                INSERT INTO failures(issue_number, fingerprint, count, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(issue_number, fingerprint) DO UPDATE SET
                    count = excluded.count,
                    updated_at = excluded.updated_at
                """,
                (issue_number, fingerprint, count, _now()),
            )
            conn.execute("COMMIT")
            return count

    def has_github_delivery(self, delivery_id: str) -> bool:
        with self._connection() as conn:
            row = conn.execute(
                "SELECT 1 FROM github_deliveries WHERE delivery_id = ?",
                (delivery_id,),
            ).fetchone()
        return row is not None

    def record_github_delivery(
        self,
        delivery_id: str,
        event_type: str,
        payload_sha256: str,
    ) -> bool:
        with self._connection() as conn:
            cursor = conn.execute(
                """
                INSERT OR IGNORE INTO github_deliveries(
                    delivery_id, event_type, payload_sha256, received_at
                )
                VALUES (?, ?, ?, ?)
                """,
                (delivery_id, event_type, payload_sha256, _now()),
            )
        return cursor.rowcount == 1

    def upsert_ci_feedback(
        self,
        *,
        run_id: int,
        delivery_id: str,
        workflow_name: str,
        issue_number: int | None,
        pr_number: int | None,
        commit_sha: str,
        head_branch: str,
        status: str,
        conclusion: str | None,
        result_state: str,
        classification: str,
    ) -> None:
        with self._connection() as conn:
            conn.execute(
                """
                INSERT INTO ci_feedback(
                    run_id, delivery_id, workflow_name, issue_number, pr_number,
                    commit_sha, head_branch, status, conclusion, result_state,
                    classification, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(run_id) DO UPDATE SET
                    delivery_id = excluded.delivery_id,
                    workflow_name = excluded.workflow_name,
                    issue_number = excluded.issue_number,
                    pr_number = excluded.pr_number,
                    commit_sha = excluded.commit_sha,
                    head_branch = excluded.head_branch,
                    status = excluded.status,
                    conclusion = excluded.conclusion,
                    result_state = excluded.result_state,
                    classification = excluded.classification,
                    updated_at = excluded.updated_at
                """,
                (
                    run_id,
                    delivery_id,
                    workflow_name,
                    issue_number,
                    pr_number,
                    commit_sha,
                    head_branch,
                    status,
                    conclusion,
                    result_state,
                    classification,
                    _now(),
                ),
            )

    def latest_ci_feedback(self, issue_number: int) -> dict | None:
        with self._connection() as conn:
            row = conn.execute(
                """
                SELECT * FROM ci_feedback
                WHERE issue_number = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """,
                (issue_number,),
            ).fetchone()
        return dict(row) if row is not None else None

    def has_active_ci(self, issue_number: int) -> bool:
        latest = self.latest_ci_feedback(issue_number)
        return latest is not None and latest["result_state"] == "ACTIVE"

    def export(self) -> dict:
        with self._connection() as conn:
            claims = [
                dict(row)
                for row in conn.execute(
                    "SELECT * FROM claims ORDER BY issue_number"
                ).fetchall()
            ]
            failures = [
                dict(row)
                for row in conn.execute(
                    "SELECT * FROM failures ORDER BY issue_number, fingerprint"
                ).fetchall()
            ]
            worker_runs = [
                dict(row)
                for row in conn.execute(
                    "SELECT * FROM worker_runs ORDER BY started_at DESC LIMIT 50"
                ).fetchall()
            ]
            github_deliveries = [
                dict(row)
                for row in conn.execute(
                    """
                    SELECT * FROM github_deliveries
                    ORDER BY received_at DESC
                    LIMIT 100
                    """
                ).fetchall()
            ]
            ci_feedback = [
                dict(row)
                for row in conn.execute(
                    """
                    SELECT * FROM ci_feedback
                    ORDER BY updated_at DESC
                    LIMIT 100
                    """
                ).fetchall()
            ]
        return {
            "concurrency": self.concurrency,
            "claims": claims,
            "failures": failures,
            "worker_runs": worker_runs,
            "github_deliveries": github_deliveries,
            "ci_feedback": ci_feedback,
        }

    def export_json(self) -> str:
        return json.dumps(self.export(), indent=2, sort_keys=True)
