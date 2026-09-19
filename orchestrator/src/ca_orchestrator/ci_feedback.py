from __future__ import annotations

import hashlib
import json
import re
from typing import Any

from .state import StateStore


_BRANCH_ISSUE = re.compile(r"^(?:ca|eng-os)/(\d+)(?:-|$)")
_INFRA_CONCLUSIONS = {
    "cancelled",
    "timed_out",
    "action_required",
    "startup_failure",
    "stale",
}


def issue_number_from_branch(branch: str) -> int | None:
    match = _BRANCH_ISSUE.match(branch)
    return int(match.group(1)) if match else None


def _failed_steps(job: dict[str, Any]) -> list[str]:
    return [
        str(step.get("name", ""))
        for step in job.get("steps", [])
        if step.get("conclusion") == "failure"
    ]


def classify_workflow_run(
    workflow_run: dict[str, Any],
    jobs: list[dict[str, Any]],
) -> tuple[str, str]:
    status = str(workflow_run.get("status") or "")
    conclusion = workflow_run.get("conclusion")

    if status != "completed":
        return "ACTIVE", "WORKFLOW"

    if conclusion == "success":
        return "GREEN", "WORKFLOW"

    if conclusion in _INFRA_CONCLUSIONS:
        return "RED", "INFRASTRUCTURE"

    failed_jobs = [job for job in jobs if job.get("conclusion") == "failure"]
    if not failed_jobs:
        return "RED", "INFRASTRUCTURE"

    for job in failed_jobs:
        name = str(job.get("name", "")).lower()
        if "emulator" in name:
            failed_steps = {step.lower() for step in _failed_steps(job)}
            if "run emulator smoke test" in failed_steps:
                return "RED", "EMULATOR"
            return "RED", "INFRASTRUCTURE"

    if any("build" in str(job.get("name", "")).lower() for job in failed_jobs):
        return "RED", "BUILD"

    return "RED", "INFRASTRUCTURE"


class CiFeedbackProcessor:
    """Normalize GitHub workflow_run events into persistent orchestrator state."""

    def __init__(self, github, state: StateStore) -> None:
        self.github = github
        self.state = state

    def handle(
        self,
        *,
        delivery_id: str,
        event_type: str,
        payload: dict[str, Any],
        raw_body: bytes,
    ) -> dict[str, Any]:
        if self.state.has_github_delivery(delivery_id):
            return {"state": "DUPLICATE", "delivery_id": delivery_id}

        if event_type != "workflow_run":
            digest = hashlib.sha256(raw_body).hexdigest()
            self.state.record_github_delivery(delivery_id, event_type, digest)
            return {"state": "IGNORED", "event_type": event_type}

        workflow_run = payload.get("workflow_run")
        if not isinstance(workflow_run, dict):
            raise ValueError("workflow_run payload is missing workflow_run")

        run_id = int(workflow_run["id"])
        status = str(workflow_run.get("status") or "")
        conclusion = workflow_run.get("conclusion")
        workflow_name = str(workflow_run.get("name") or "")
        head_branch = str(workflow_run.get("head_branch") or "")
        commit_sha = str(workflow_run.get("head_sha") or "")
        if not commit_sha:
            raise ValueError("workflow_run payload is missing head_sha")

        pull_requests = workflow_run.get("pull_requests") or []
        pr_number = None
        if pull_requests:
            first_pr = pull_requests[0]
            if isinstance(first_pr, dict) and first_pr.get("number") is not None:
                pr_number = int(first_pr["number"])

        issue_number = issue_number_from_branch(head_branch)
        jobs: list[dict[str, Any]] = []
        if status == "completed":
            jobs = self.github.workflow_jobs(run_id)

        result_state, classification = classify_workflow_run(workflow_run, jobs)
        self.state.upsert_ci_feedback(
            run_id=run_id,
            delivery_id=delivery_id,
            workflow_name=workflow_name,
            issue_number=issue_number,
            pr_number=pr_number,
            commit_sha=commit_sha,
            head_branch=head_branch,
            status=status,
            conclusion=str(conclusion) if conclusion is not None else None,
            result_state=result_state,
            classification=classification,
        )

        digest = hashlib.sha256(raw_body).hexdigest()
        inserted = self.state.record_github_delivery(delivery_id, event_type, digest)
        if not inserted:
            return {"state": "DUPLICATE", "delivery_id": delivery_id}

        return {
            "state": "ROUTED",
            "delivery_id": delivery_id,
            "run_id": run_id,
            "workflow_name": workflow_name,
            "issue_number": issue_number,
            "pr_number": pr_number,
            "commit_sha": commit_sha,
            "result_state": result_state,
            "classification": classification,
        }


def canonical_payload_bytes(payload: dict[str, Any]) -> bytes:
    return json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
