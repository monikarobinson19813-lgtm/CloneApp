import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.provider_gateway import ProviderGateway, ProviderSessionStore
from ca_orchestrator.providers import (
    AnthropicMessagesProvider,
    OpenAIResponsesProvider,
    ProviderReply,
)


class FakeProvider:
    def __init__(self, provider_id="fake", model="fake-model"):
        self.provider_id = provider_id
        self.model = model
        self.prompts = []

    def send(self, prompt):
        self.prompts.append(prompt)
        return ProviderReply(
            provider=self.provider_id,
            model=self.model,
            text=f"reply-{len(self.prompts)}",
            raw={"ok": True},
        )


class FakeTransport:
    def __init__(self, response):
        self.response = response
        self.calls = []

    def post(self, url, *, headers, body):
        self.calls.append({"url": url, "headers": headers, "body": body})
        return self.response


class ProviderGatewayTests(unittest.TestCase):
    def test_start_and_resume_preserve_transcript(self):
        with tempfile.TemporaryDirectory() as tmp:
            fake = FakeProvider()
            store = ProviderSessionStore(Path(tmp) / "sessions.sqlite3")
            gateway = ProviderGateway(
                store,
                providers={"fake": fake},
                routing={"architecture": "fake", "default": "fake"},
            )

            first = gateway.start(task_type="architecture", prompt="question one")
            second = gateway.resume(session_id=first.session_id, prompt="question two")

            self.assertEqual(first.session_id, second.session_id)
            self.assertIn("question one", fake.prompts[1])
            self.assertIn("reply-1", fake.prompts[1])
            self.assertIn("question two", fake.prompts[1])

    def test_openai_provider_uses_responses_endpoint(self):
        transport = FakeTransport(
            {"output": [{"content": [{"type": "output_text", "text": "hello"}]}]}
        )
        provider = OpenAIResponsesProvider(
            api_key="test",
            model="gpt-5.6",
            transport=transport,
        )

        reply = provider.send("ping")

        self.assertEqual("hello", reply.text)
        self.assertTrue(transport.calls[0]["url"].endswith("/v1/responses"))
        self.assertEqual("ping", transport.calls[0]["body"]["input"])

    def test_anthropic_provider_uses_messages_endpoint(self):
        transport = FakeTransport(
            {"content": [{"type": "text", "text": "hello claude"}]}
        )
        provider = AnthropicMessagesProvider(
            api_key="test",
            model="claude-sonnet-5",
            transport=transport,
        )

        reply = provider.send("ping")

        self.assertEqual("hello claude", reply.text)
        self.assertTrue(transport.calls[0]["url"].endswith("/v1/messages"))
        self.assertEqual("ping", transport.calls[0]["body"]["messages"][0]["content"])


if __name__ == "__main__":
    unittest.main()
