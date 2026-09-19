from __future__ import annotations

from dataclasses import dataclass
from typing import Literal


CiAction = Literal[
    "WAIT_FOR_CI",
    "EVALUATE_ACCEPTANCE",
    "RETRY_INFRASTRUCTURE",
    "DISPATCH_REPAIR",
    "RUNTIME_CLASSIFICATION_REQUIRED",
    "ARCHITECTURE_REVIEW",
    "REVIEW_REQUIRED",
]


@dataclass(frozen=True)
class CiActionDecision:
    action: CiAction
    reason: str


def decide_ci_action(
    *,
    result_state: str,
    classification: str,
    similar_failures: int = 0,
    failure_budget: int = 3,
) -> CiActionDecision:
    """Translate persisted CI evidence into the next safe Control Tower action.

    This function is intentionally side-effect free. It decides what should happen;
    later orchestration code performs the action exactly once.
    """
    if result_state == "ACTIVE":
        return CiActionDecision("WAIT_FOR_CI", "ci_still_active")

    if result_state == "GREEN":
        return CiActionDecision("EVALUATE_ACCEPTANCE", "ci_green_requires_feature_acceptance")

    if result_state != "RED":
        return CiActionDecision("REVIEW_REQUIRED", f"unknown_ci_state:{result_state}")

    if similar_failures >= failure_budget:
        return CiActionDecision(
            "ARCHITECTURE_REVIEW",
            f"failure_budget_exhausted:{similar_failures}",
        )

    if classification == "INFRASTRUCTURE":
        return CiActionDecision("RETRY_INFRASTRUCTURE", "infra_failure_no_product_change")

    if classification == "BUILD":
        return CiActionDecision("DISPATCH_REPAIR", "build_failure_repairable")

    if classification == "EMULATOR":
        return CiActionDecision(
            "RUNTIME_CLASSIFICATION_REQUIRED",
            "emulator_failure_must_be_classified_before_product_change",
        )

    return CiActionDecision(
        "REVIEW_REQUIRED",
        f"unhandled_red_classification:{classification}",
    )
