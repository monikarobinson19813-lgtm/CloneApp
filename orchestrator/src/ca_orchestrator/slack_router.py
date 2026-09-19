from __future__ import annotations

import json
import os
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Mapping


_ALLOWED_TYPES = {
    "TASK",
    "STATUS",
    "QUESTION",
    "HANDOFF",
    "DECISION",
    "BLOCKER",
    "EVIDENCE",
    "INCIDENT",
    "DONE",
}

_DEPARTMENT_CHANNELS = {
    "control": "cloneapp-control-room",
    "product": "cloneapp-product",
    "engine": "cloneapp-engine",
    "ui": "cloneapp-ui",
    "qa": "cloneapp-qa",
    "architecture": "cloneapp-architecture",
    "security": "cloneapp-security",
    "compatibility": "cloneapp-compatibility",
    "autonomy": "cloneapp-autonomy",
    "release": "cloneapp-release",
    "research": "cloneapp-research",
    "incidents": "cloneapp-incidents",
    "agent_bus": "cloneapp-agent-bus",
    "handoffs": "cloneapp-handoffs",
    "decisions": "cloneapp-decisions",
    "status_5min": "cloneapp-5min-status",
    "digest_30min": "cloneapp-30min-digest",
}


@dataclass(frozen=True)
class AgentMessage:
    message_type: str
    text: str
    source_channel: str
    target_department: str | None = None
    issue_number: int | None = None
    thread_ts: str | None = None


class SlackEventStore:
    """Small durable dedupe store for Slack event ids."""

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS slack_events (
                    event_id TEXT PRIMARY KEY,
                    seen_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """
            )

    def claim_event(self, event_id: str) -> bool:
        with sqlite3.connect(self.path) as conn:
            try:
                conn.execute(
                    "INSERT INTO slack_events(event_id) VALUES (?)",
                    (event_id,),
                )
                conn.commit()
                return True
            except sqlite3.IntegrityError:
                return False


class ChannelDirectory:
    """Resolves stable department names to Slack channel ids at runtime."""

    def __init__(self, channel_ids: Mapping[str, str]) -> None:
        self.channel_ids = dict(channel_ids)

    def id_for_department(self, department: str) -> str:
        channel_name = _DEPARTMENT_CHANNELS[department]
        try:
            return self.channel_ids[channel_name]
        except KeyError as exc:
            raise KeyError(f"Slack channel id not configured for {channel_name}") from exc


class RouterPolicy:
    TYPE_PATTERN = re.compile(r"^\[(?P<type>[A-Z_]+)\]\s*(?P<body>.*)$", re.DOTALL)
    ISSUE_PATTERN = re.compile(r"(?:ISSUE\s*)?#(?P<number>\d+)", re.IGNORECASE)
    TARGET_PATTERN = re.compile(r"^To:\s*(?P<target>[a-z0-9_-]+)\s*$", re.IGNORECASE | re.MULTILINE)

    def parse(self, *, channel: str, text: str, thread_ts: str | None = None) -> AgentMessage | None:
        match = self.TYPE_PATTERN.match(text.strip())
        if not match:
            return None

        message_type = match.group("type")
        if message_type not in _ALLOWED_TYPES:
            return None

        issue_match = self.ISSUE_PATTERN.search(text)
        target_match = self.TARGET_PATTERN.search(text)
        return AgentMessage(
            message_type=message_type,
            text=text.strip(),
            source_channel=channel,
            target_department=(target_match.group("target").lower() if target_match else None),
            issue_number=(int(issue_match.group("number")) if issue_match else None),
            thread_ts=thread_ts,
        )

    def default_route(self, message: AgentMessage) -> str | None:
        if message.message_type == "HANDOFF":
            return "handoffs"
        if message.message_type == "DECISION":
            return "decisions"
        if message.message_type in {"BLOCKER", "INCIDENT"}:
            return "incidents"
        if message.target_department in _DEPARTMENT_CHANNELS:
            return message.target_department
        return "agent_bus"


class SlackRouter:
    def __init__(
        self,
        *,
        event_store: SlackEventStore,
        directory: ChannelDirectory,
        post_message: Callable[..., None],
        bot_user_id: str | None = None,
        on_owner_message: Callable[[dict], None] | None = None,
    ) -> None:
        self.event_store = event_store
        self.directory = directory
        self.post_message = post_message
        self.bot_user_id = bot_user_id
        self.on_owner_message = on_owner_message
        self.policy = RouterPolicy()

    def handle_envelope(self, envelope: dict) -> str:
        event_id = str(envelope.get("event_id") or "")
        if not event_id:
            return "IGNORED_NO_EVENT_ID"
        if not self.event_store.claim_event(event_id):
            return "IGNORED_DUPLICATE"

        event = envelope.get("event") or {}
        if event.get("type") != "message":
            return "IGNORED_EVENT_TYPE"
        if event.get("subtype"):
            return "IGNORED_SUBTYPE"
        if event.get("bot_id"):
            return "IGNORED_BOT"
        if self.bot_user_id and event.get("user") == self.bot_user_id:
            return "IGNORED_SELF"

        channel = str(event.get("channel") or "")
        text = str(event.get("text") or "")
        thread_ts = event.get("thread_ts") or event.get("ts")

        parsed = self.policy.parse(channel=channel, text=text, thread_ts=thread_ts)
        if parsed is None:
            if self.on_owner_message:
                self.on_owner_message(envelope)
                return "OWNER_MESSAGE_DISPATCHED"
            return "IGNORED_UNSTRUCTURED"

        target = self.policy.default_route(parsed)
        if target is None:
            return "NO_ROUTE"

        target_channel = self.directory.id_for_department(target)
        self.post_message(
            channel_id=target_channel,
            message=self._render(parsed),
            thread_ts=None,
        )
        return f"ROUTED_{target.upper()}"

    def _render(self, message: AgentMessage) -> str:
        identity = [
            f"Type: {message.message_type}",
            f"Source: {message.source_channel}",
        ]
        if message.issue_number is not None:
            identity.append(f"Issue: #{message.issue_number}")
        if message.target_department:
            identity.append(f"Target: {message.target_department}")
        return "\n".join(identity) + "\n\n" + message.text


def discover_cloneapp_channels(client) -> dict[str, str]:
    result: dict[str, str] = {}
    cursor = None
    while True:
        response = client.conversations_list(
            types="public_channel,private_channel",
            exclude_archived=True,
            limit=200,
            cursor=cursor,
        )
        for channel in response.get("channels", []):
            name = channel.get("name")
            channel_id = channel.get("id")
            if name and channel_id and name.startswith("cloneapp-"):
                result[name] = channel_id
        cursor = (response.get("response_metadata") or {}).get("next_cursor")
        if not cursor:
            return result


def run_socket_mode() -> None:
    bot_token = os.environ.get("SLACK_BOT_TOKEN", "")
    app_token = os.environ.get("SLACK_APP_TOKEN", "")
    if not bot_token or not app_token:
        raise RuntimeError("SLACK_BOT_TOKEN and SLACK_APP_TOKEN are required")

    try:
        from slack_bolt import App
        from slack_bolt.adapter.socket_mode import SocketModeHandler
    except ImportError as exc:
        raise RuntimeError(
            "Slack runtime dependency missing. Install with: pip install 'cloneapp-orchestrator[slack]'"
        ) from exc

    app = App(token=bot_token)
    auth = app.client.auth_test()
    channel_ids = discover_cloneapp_channels(app.client)
    directory = ChannelDirectory(channel_ids)
    state_path = Path(os.environ.get("CA_SLACK_STATE_DB", ".ca-orchestrator/slack.sqlite3"))

    def post_message(*, channel_id: str, message: str, thread_ts: str | None = None) -> None:
        app.client.chat_postMessage(
            channel=channel_id,
            text=message,
            thread_ts=thread_ts,
        )

    router = SlackRouter(
        event_store=SlackEventStore(state_path),
        directory=directory,
        post_message=post_message,
        bot_user_id=auth.get("user_id"),
    )

    @app.event("message")
    def handle_message_events(body, logger):
        try:
            result = router.handle_envelope(body)
            logger.info(json.dumps({"event": "slack_router", "result": result}))
        except Exception:
            logger.exception("Slack router failed")

    SocketModeHandler(app, app_token).start()


def main() -> None:
    run_socket_mode()


if __name__ == "__main__":
    main()
