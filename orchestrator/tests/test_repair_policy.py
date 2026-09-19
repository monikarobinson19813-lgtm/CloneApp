import unittest

from ca_orchestrator.repair_policy import decide_ci_action


class RepairPolicyTests(unittest.TestCase):
    def test_active_waits_for_ci(self):
        decision = decide_ci_action(
            result_state="ACTIVE",
            classification="WORKFLOW",
        )
        self.assertEqual("WAIT_FOR_CI", decision.action)

    def test_green_requires_acceptance_evaluation(self):
        decision = decide_ci_action(
            result_state="GREEN",
            classification="WORKFLOW",
        )
        self.assertEqual("EVALUATE_ACCEPTANCE", decision.action)

    def test_infrastructure_red_retries_without_product_change(self):
        decision = decide_ci_action(
            result_state="RED",
            classification="INFRASTRUCTURE",
        )
        self.assertEqual("RETRY_INFRASTRUCTURE", decision.action)

    def test_build_red_dispatches_bounded_repair(self):
        decision = decide_ci_action(
            result_state="RED",
            classification="BUILD",
        )
        self.assertEqual("DISPATCH_REPAIR", decision.action)

    def test_emulator_red_requires_runtime_classification(self):
        decision = decide_ci_action(
            result_state="RED",
            classification="EMULATOR",
        )
        self.assertEqual("RUNTIME_CLASSIFICATION_REQUIRED", decision.action)

    def test_failure_budget_escalates_before_more_repair(self):
        decision = decide_ci_action(
            result_state="RED",
            classification="BUILD",
            similar_failures=3,
            failure_budget=3,
        )
        self.assertEqual("ARCHITECTURE_REVIEW", decision.action)


if __name__ == "__main__":
    unittest.main()
