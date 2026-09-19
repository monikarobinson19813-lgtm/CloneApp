import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.slack_router import ChannelDirectory, SlackEventStore, SlackRouter


CHANNELS = {
    "cloneapp-agent-bus": "C-BUS",
    "cloneapp-handoffs": "C-HANDOFF",
    "cloneapp-decisions": "C-DECISION",
    "cloneapp-incidents": "C-INCIDENT",
    "cloneapp-qa": "C-QA",
}


class SlackRouterTests(unittest.TestCase):
    def build_router(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        sent = []

        def post_message(**kwargs):
            sent.append(kwargs)

        router = SlackRouter(
            event_store=SlackEventStore(Path(temp.name) / "events.sqlite3"),
            directory=ChannelDirectory(CHANNELS),
            post_message=post_message,
            bot_user_id="U-BOT",
        )
        return router, sent

    def test_handoff_routes_to_handoff_channel(self):
        router, sent = self.build_router()
        result = router.handle_envelope(
            {
                "event_id": "Ev1",
                "event": {
                    "type": "message",
                    "channel": "C-ENGINE",
                    "user": "U-ENGINE",
                    "ts": "1.0",
                    "text": "[HANDOFF] ISSUE #28 — storage ready for QA\nTo: qa",
                },
            }
        )
        self.assertEqual("ROUTED_HANDOFFS", result)
        self.assertEqual("C-HANDOFF", sent[0]["channel_id"])
        self.assertIn("Issue: #28", sent[0]["message"])

    def test_explicit_department_route_uses_target(self):
        router, sent = self.build_router()
        result = router.handle_envelope(
            {
                "event_id": "Ev2",
                "event": {
                    "type": "message",
                    "channel": "C-ENGINE",
                    "user": "U-ENGINE",
                    "ts": "2.0",
                    "text": "[QUESTION] ISSUE #31 — please validate\nTo: qa",
                },
            }
        )
        self.assertEqual("ROUTED_QA", result)
        self.assertEqual("C-QA", sent[0]["channel_id"])

    def test_blocker_routes_to_incidents(self):
        router, sent = self.build_router()
        result = router.handle_envelope(
            {
                "event_id": "Ev3",
                "event": {
                    "type": "message",
                    "channel": "C-QA",
                    "user": "U-QA",
                    "ts": "3.0",
                    "text": "[BLOCKER] ISSUE #31 — repeated emulator failure",
                },
            }
        )
        self.assertEqual("ROUTED_INCIDENTS", result)
        self.assertEqual("C-INCIDENT", sent[0]["channel_id"])

    def test_bot_messages_are_ignored_to_prevent_loops(self):
        router, sent = self.build_router()
        result = router.handle_envelope(
            {
                "event_id": "Ev4",
                "event": {
                    "type": "message",
                    "channel": "C-QA",
                    "bot_id": "B123",
                    "ts": "4.0",
                    "text": "[STATUS] bot echo",
                },
            }
        )
        self.assertEqual("IGNORED_BOT", result)
        self.assertEqual([], sent)

    def test_duplicate_event_is_ignored(self):
        router, sent = self.build_router()
        envelope = {
            "event_id": "Ev5",
            "event": {
                "type": "message",
                "channel": "C-ENGINE",
                "user": "U1",
                "ts": "5.0",
                "text": "[STATUS] ISSUE #10 — active",
            },
        }
        first = router.handle_envelope(envelope)
        second = router.handle_envelope(envelope)
        self.assertEqual("ROUTED_AGENT_BUS", first)
        self.assertEqual("IGNORED_DUPLICATE", second)
        self.assertEqual(1, len(sent))


if __name__ == "__main__":
    unittest.main()
