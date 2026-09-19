from __future__ import annotations

import json
import urllib.error
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
        )
