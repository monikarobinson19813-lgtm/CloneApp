from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class ProviderReply:
    provider: str
    model: str
    text: str
    raw: dict


class JsonTransport(Protocol):
    def post(self, url: str, *, headers: dict[str, str], body: dict) -> dict: ...


class UrllibJsonTransport:
    def post(self, url: str, *, headers: dict[str, str], body: dict) -> dict:
        request = urllib.request.Request(
            url,
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json", **headers},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            payload = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Provider HTTP {exc.code}: {payload}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Provider connection failed: {exc}") from exc


class AgentProvider(Protocol):
    provider_id: str
    model: str

    def send(self, prompt: str) -> ProviderReply: ...


class OpenAIResponsesProvider:
    provider_id = "openai"

    def __init__(
        self,
        *,
        api_key: str | None = None,
        model: str = "gpt-5.6",
        transport: JsonTransport | None = None,
    ) -> None:
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY", "")
        self.model = model
        self.transport = transport or UrllibJsonTransport()

    def send(self, prompt: str) -> ProviderReply:
        if not self.api_key:
            raise RuntimeError("OPENAI_API_KEY is required")
        raw = self.transport.post(
            "https://api.openai.com/v1/responses",
            headers={"Authorization": f"Bearer {self.api_key}"},
            body={"model": self.model, "input": prompt},
        )
        return ProviderReply(
            provider=self.provider_id,
            model=self.model,
            text=self._extract_text(raw),
            raw=raw,
        )

    def _extract_text(self, raw: dict) -> str:
        direct = raw.get("output_text")
        if isinstance(direct, str):
            return direct

        chunks: list[str] = []
        for item in raw.get("output", []) or []:
            for content in item.get("content", []) or []:
                text = content.get("text")
                if isinstance(text, str):
                    chunks.append(text)
        return "\n".join(chunks).strip()


class AnthropicMessagesProvider:
    provider_id = "anthropic"

    def __init__(
        self,
        *,
        api_key: str | None = None,
        model: str = "claude-sonnet-5",
        max_tokens: int = 4096,
        transport: JsonTransport | None = None,
    ) -> None:
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        self.model = model
        self.max_tokens = max_tokens
        self.transport = transport or UrllibJsonTransport()

    def send(self, prompt: str) -> ProviderReply:
        if not self.api_key:
            raise RuntimeError("ANTHROPIC_API_KEY is required")
        raw = self.transport.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
            body={
                "model": self.model,
                "max_tokens": self.max_tokens,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        text = "\n".join(
            block.get("text", "")
            for block in raw.get("content", []) or []
            if block.get("type") == "text"
        ).strip()
        return ProviderReply(
            provider=self.provider_id,
            model=self.model,
            text=text,
            raw=raw,
        )
