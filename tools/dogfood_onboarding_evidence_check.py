#!/usr/bin/env python3
"""Validate ordinary-member onboarding from external native product evidence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from dogfood_functional_smoke import functional_smoke_passed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--functional-evidence-file", required=True, type=Path)
    parser.add_argument("--expected-candidate", required=True)
    parser.add_argument("--expected-source-tree", required=True)
    parser.add_argument("--expected-specification", required=True)
    parser.add_argument("--qualification-run-id", required=True, type=int)
    parser.add_argument("--qualification-run-attempt", required=True, type=int)
    parser.add_argument("--stage", required=True, choices=("deployment", "physical"))
    args = parser.parse_args()

    try:
        receipt = json.loads(args.functional_evidence_file.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        parser.error(f"functional evidence is unavailable or invalid: {type(error).__name__}")

    if not functional_smoke_passed(
        receipt,
        args.expected_candidate,
        args.expected_source_tree,
        args.expected_specification,
        args.qualification_run_id,
        args.qualification_run_attempt,
        args.stage,
    ):
        parser.error("exact-candidate native product evidence is incomplete")
    if args.stage == "physical" and receipt.get("ownerEntryObserved") is not True:
        parser.error("physical owner entry has not been observed")

    print(
        "DOGFOOD_MEMBER_ONBOARDING_RESULT "
        f"status=passed stage={args.stage} proof=ordinary-product-ui "
        "ownerActivation=not-proven supportSafe=true"
    )
    print(
        "DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT "
        "chatState=usable filesState=usable proof=ordinary-product-ui supportSafe=true"
    )
    print(
        "DOGFOOD_SESSION_CONTINUITY_RESULT "
        "status=passed proof=retained-product-state supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
