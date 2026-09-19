from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Department:
    id: str
    name: str
    channel: str
    agent_role: str
    preferred_provider: str
    dynamic_channels: bool
    concurrency_limit: int
    escalation_target: str


@dataclass(frozen=True)
class ChannelRequest:
    kind: str
    slug: str
    parent_department: str
    owner_agent: str
    github_ref: str
    purpose: str
    expected_lifetime_hours: int = 24
    departments_involved: int = 1
    severity: str | None = None


class DepartmentRegistry:
    def __init__(self, namespace: str, exchange_budget: int, departments: list[Department]):
        self.namespace = namespace
        self.exchange_budget = exchange_budget
        self._departments = {item.id: item for item in departments}

    @classmethod
    def load(cls, path: str | Path) -> "DepartmentRegistry":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        departments = [Department(**item) for item in data["departments"]]
        return cls(
            namespace=str(data.get("namespace", "cloneapp")),
            exchange_budget=max(1, int(data.get("default_exchange_budget", 3))),
            departments=departments,
        )

    def department(self, department_id: str) -> Department:
        return self._departments[department_id]

    def all(self) -> list[Department]:
        return list(self._departments.values())


class ChannelFactory:
    ALLOWED_KINDS = {"issue", "incident", "release", "research"}

    def __init__(self, registry: DepartmentRegistry, slack_client) -> None:
        self.registry = registry
        self.slack = slack_client

    def should_create(self, request: ChannelRequest) -> bool:
        if request.kind == "incident" and (request.severity or "").upper() in {"SEV1", "SEV2"}:
            return True
        if request.kind == "release":
            return True
        if request.departments_involved > 2:
            return True
        if request.expected_lifetime_hours > 24:
            return True
        if request.kind == "research" and request.expected_lifetime_hours >= 8:
            return True
        return False

    def create(self, request: ChannelRequest) -> dict:
        if request.kind not in self.ALLOWED_KINDS:
            raise ValueError(f"Unsupported dynamic channel kind: {request.kind}")

        department = self.registry.department(request.parent_department)
        if not department.dynamic_channels:
            raise PermissionError(f"{department.id} cannot create dynamic channels")
        if not self.should_create(request):
            raise ValueError("Thread-first policy says this work does not need a new channel")

        name = self._name(request)
        response = self.slack.conversations_create(name=name, is_private=True)
        channel = response["channel"]
        channel_id = channel["id"]

        charter = self._charter(request, department.name)
        self.slack.chat_postMessage(channel=channel_id, text=charter)

        return {
            "id": channel_id,
            "name": channel["name"],
            "parent_department": request.parent_department,
            "github_ref": request.github_ref,
            "archive_after_hours": request.expected_lifetime_hours,
        }

    def _name(self, request: ChannelRequest) -> str:
        clean = re.sub(r"[^a-z0-9-]+", "-", request.slug.lower()).strip("-")
        clean = re.sub(r"-+", "-", clean)
        if not clean:
            raise ValueError("Channel slug is empty after normalization")

        prefix = f"{self.registry.namespace}-{request.kind}-"
        if request.kind in {"issue", "incident"}:
            match = re.search(r"#?(\d+)", request.github_ref)
            if match:
                clean = f"{match.group(1)}-{clean}"

        return (prefix + clean)[:80].rstrip("-")

    def _charter(self, request: ChannelRequest, parent_name: str) -> str:
        return "\n".join(
            [
                f"**Dynamic CloneApp {request.kind.title()} Room**",
                f"Owner Agent: {request.owner_agent}",
                f"Parent Department: {parent_name}",
                f"GitHub: {request.github_ref}",
                f"Purpose: {request.purpose}",
                f"Expected Lifetime: {request.expected_lifetime_hours}h",
                "Exit/Archive Rule: linked work complete + no unresolved blocker/decision + 24h no action",
                "",
                "GitHub remains authoritative. Use this room for coordination/evidence only.",
            ]
        )
