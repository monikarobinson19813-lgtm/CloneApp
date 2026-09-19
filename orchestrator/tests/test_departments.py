import json
import tempfile
import unittest
from pathlib import Path

from ca_orchestrator.departments import ChannelFactory, ChannelRequest, DepartmentRegistry


class FakeSlack:
    def __init__(self):
        self.created = []
        self.messages = []

    def conversations_create(self, **kwargs):
        self.created.append(kwargs)
        return {"channel": {"id": "C-DYNAMIC", "name": kwargs["name"]}}

    def chat_postMessage(self, **kwargs):
        self.messages.append(kwargs)


class DepartmentRegistryTests(unittest.TestCase):
    def registry(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "departments.json"
            path.write_text(
                json.dumps(
                    {
                        "namespace": "cloneapp",
                        "default_exchange_budget": 3,
                        "departments": [
                            {
                                "id": "engine",
                                "name": "Core Engine",
                                "channel": "cloneapp-engine",
                                "agent_role": "engine_worker",
                                "preferred_provider": "codex",
                                "dynamic_channels": True,
                                "concurrency_limit": 2,
                                "escalation_target": "architecture",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            yield DepartmentRegistry.load(path)

    def test_thread_first_rejects_short_single_department_work(self):
        registry = next(self.registry())
        factory = ChannelFactory(registry, FakeSlack())
        request = ChannelRequest(
            kind="issue",
            slug="small-fix",
            parent_department="engine",
            owner_agent="Engine Worker",
            github_ref="#50",
            purpose="One small fix",
            expected_lifetime_hours=2,
            departments_involved=1,
        )
        self.assertFalse(factory.should_create(request))

    def test_multi_department_issue_can_create_private_room(self):
        registry = next(self.registry())
        slack = FakeSlack()
        factory = ChannelFactory(registry, slack)
        request = ChannelRequest(
            kind="issue",
            slug="storage-isolation",
            parent_department="engine",
            owner_agent="Engine Worker",
            github_ref="#28",
            purpose="Coordinate engine/QA/architecture",
            expected_lifetime_hours=12,
            departments_involved=3,
        )

        result = factory.create(request)

        self.assertEqual("cloneapp-issue-28-storage-isolation", result["name"])
        self.assertTrue(slack.created[0]["is_private"])
        self.assertIn("Owner Agent: Engine Worker", slack.messages[0]["text"])
        self.assertIn("GitHub: #28", slack.messages[0]["text"])

    def test_sev1_incident_always_gets_room(self):
        registry = next(self.registry())
        factory = ChannelFactory(registry, FakeSlack())
        request = ChannelRequest(
            kind="incident",
            slug="worker-outage",
            parent_department="engine",
            owner_agent="Control Tower",
            github_ref="#91",
            purpose="Critical worker outage",
            expected_lifetime_hours=4,
            departments_involved=1,
            severity="SEV1",
        )
        self.assertTrue(factory.should_create(request))


if __name__ == "__main__":
    unittest.main()
