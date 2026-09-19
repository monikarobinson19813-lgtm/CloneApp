from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from .models import Claim


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class StateStore:
    """Restart-safe orchestration state backed by SQLite.

    BEGIN IMMEDIATE plus a global active-claim count gives us a single-writer,
    bounded-concurrency claim operation without relying on chat/session memory.
    """

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

    def export(self) -> dict:
        with self._connection() as conn:
            claims = [dict(row) for row in conn.execute(
                "SELECT * FROM claims ORDER BY issue_number"
            ).fetchall()]
            failures = [dict(row) for row in conn.execute(
                "SELECT * FROM failures ORDER BY issue_number, fingerprint"
            ).fetchall()]
        return {
            "concurrency": self.concurrency,
            "claims": claims,
            "failures": failures,
        }

    def export_json(self) -> str:
        return json.dumps(self.export(), indent=2, sort_keys=True)
