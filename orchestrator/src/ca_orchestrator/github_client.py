from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request

from .models import IssueSnapshot


class GitHubError(RuntimeError):
    pass


class GitHubClient:
    def __init__(self, repo: str, token: str, api_base: str = "https://api.github.com") -> None:
        if "/" not in repo:
            raise ValueError("repo must be in owner/name form")
        self.repo = repo
        self.token = token
        self.api_base = api_base.rstrip("/")

    def _get_json(self, path: str) -> dict:
        request = urllib.request.Request(
            f"{self.api_base}{path}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "cloneapp-orchestrator",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise GitHubError(f"GitHub HTTP {exc.code}: {body}") from exc
        except urllib.error.URLError as exc:
            raise GitHubError(f"GitHub connection failed: {exc}") from exc

    def issue(self, number: int) -> IssueSnapshot:
        data = self._get_json(f"/repos/{self.repo}/issues/{number}")
        state = data.get("state")
        if state not in {"open", "closed"}:
            raise GitHubError(f"Unexpected issue state for #{number}: {state!r}")
        return IssueSnapshot(
            number=int(data["number"]),
            title=str(data.get("title", "")),
            state=state,
            body=str(data.get("body") or ""),
        )

    def workflow_jobs(self, run_id: int) -> list[dict]:
        jobs: list[dict] = []
        page = 1
        while True:
            query = urllib.parse.urlencode({"per_page": 100, "page": page})
            data = self._get_json(
                f"/repos/{self.repo}/actions/runs/{run_id}/jobs?{query}"
            )
            batch = data.get("jobs") or []
            if not isinstance(batch, list):
                raise GitHubError(f"Unexpected jobs payload for workflow run {run_id}")
            jobs.extend(job for job in batch if isinstance(job, dict))
            if len(batch) < 100:
                return jobs
            page += 1
