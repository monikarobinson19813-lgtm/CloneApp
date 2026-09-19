from __future__ import annotations

import json
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from .providers import AgentProvider, ProviderReply


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class SessionResult:
    session_id: str
    provider: str
    model: str
    response: str


class ProviderSessionStore:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.path) as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS provider_sessions (
                    session_id TEXT PRIMARY KEY,
                    provider TEXT NOT NULL,
                    task_type TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS provider_messages (
                    session_id TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY(session_id, seq)
                );
                """
            )

    def create(self, provider: str, task_type: str) -> str:
        session_id = uuid.uuid4().hex
        now = _now()
        with sqlite3.connect(self.path) as conn:
            conn.execute(
                """
                INSERT INTO provider_sessions(session_id, provider, task_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                (session_id, provider, task_type, now, now),
            )
        return session_id

    def provider_for(self, session_id: str) -> str:
        with sqlite3.connect(self.path) as conn:
            row = conn.execute(
                "SELECT provider FROM provider_sessions WHERE session_id = ?",
                (session_id,),
            ).fetchone()
        if row is None:
            raise KeyError(f"Unknown provider session: {session_id}")
        return str(row[0])

    def append(self, session_id: str, role: str, content: str) -> None:
        with sqlite3.connect(self.path) as conn:
            seq = conn.execute(
                """
                SELECT COALESCE(MAX(seq), 0) + 1
                FROM provider_messages
                WHERE session_id = ?
                """,
                (session_id,),
            ).fetchone()[0]
            conn.execute(
                """
                INSERT INTO provider_messages(session_id, seq, role, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                (session_id, seq, role, content, _now()),
            )
            conn.execute(
                "UPDATE provider_sessions SET updated_at = ? WHERE session_id = ?",
                (_now(), session_id),
            )

    def transcript(self, session_id: str) -> list[dict[str, str]]:
        with sqlite3.connect(self.path) as conn:
            rows = conn.execute(
                """
                SELECT role, content FROM provider_messages
                WHERE session_id = ?
                ORDER BY seq
                """,
                (session_id,),
            ).fetchall()
        return [{"role": str(row[0]), "content": str(row[1])} for row in rows]


class ProviderGateway:
    def __init__(
        self,
        store: ProviderSessionStore,
        providers: dict[str, AgentProvider],
        routing: dict[str, str],
    ) -> None:
        self.store = store
        self.providers = providers
        self.routing = routing

    def start(self, *, task_type: str, prompt: str) -> SessionResult:
        provider_id = self.routing.get(task_type) or self.routing.get("default")
        if provider_id is None:
            raise KeyError(f"No provider route configured for task type {task_type}")
        if provider_id not in self.providers:
            raise KeyError(f"Provider {provider_id} is not configured")

        session_id = self.store.create(provider_id, task_type)
        return self._send(session_id, prompt)

    def resume(self, *, session_id: str, prompt: str) -> SessionResult:
        return self._send(session_id, prompt)

    def _send(self, session_id: str, prompt: str) -> SessionResult:
        provider_id = self.store.provider_for(session_id)
        provider = self.providers[provider_id]

        self.store.append(session_id, "user", prompt)
        transcript = self.store.transcript(session_id)
        compiled = self._compile(transcript)

        reply: ProviderReply = provider.send(compiled)
        self.store.append(session_id, "assistant", reply.text)

        return SessionResult(
            session_id=session_id,
            provider=reply.provider,
            model=reply.model,
            response=reply.text,
        )

    def _compile(self, transcript: list[dict[str, str]]) -> str:
        return "\n\n".join(
            f"{item['role'].upper()}:\n{item['content']}"
            for item in transcript
        )
