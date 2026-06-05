#!/usr/bin/env python3
"""Validate Sprint 30 hot-phase dogfood and agentic-governance evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SLOGAN = "Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms."
REQUIRED_SLOGAN_SURFACES = [
    "README.md",
    "docs/index.md",
    "docs/product-line-and-weaver-plan.md",
    "docs/product-trust-provider-choice-claim-matrix.md",
    "client/web/index.html",
]
FORBIDDEN_AUTONOMOUS_CLAIMS = [
    "customer-ready autonomous pa is available",
    "public/customer-ready autonomous pa is available",
    "unrestricted background action is available",
    "autonomous agent without approval is available",
]
FORBIDDEN_MEMBER_INPUTS = [
    "OIDC issuer",
    "OIDC client ID",
    "Matrix URL",
    "Nextcloud URL",
    "provider hostname",
    "TLS certificate",
    "provider diagnostic",
    "SecretRef",
    "credential URL",
]


def fail(message: str) -> None:
    print(f"sprint30-hot-phase-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing required file: {rel}")


def main() -> None:
    for rel in REQUIRED_SLOGAN_SURFACES:
        if SLOGAN not in read(rel):
            fail(f"missing exact slogan in {rel}")

    combined_claim_text = "\n".join(
        read(rel)
        for rel in [
            "README.md",
            "docs/product-line-and-weaver-plan.md",
            "docs/product-trust-provider-choice-claim-matrix.md",
            "docs/sprint-30-hot-phase-evidence-pack.md",
        ]
    ).lower()
    for claim in FORBIDDEN_AUTONOMOUS_CLAIMS:
        if claim in combined_claim_text:
            fail(f"unsupported autonomous-agent claim present: {claim}")

    fixture = json.loads(read("release/sprint-30-hot-phase/profile-driven-setup.fixture.json"))
    if fixture.get("scriptFamily", {}).get("entrypoint") != "weavectl profile apply":
        fail("single setup entrypoint must be weavectl profile apply")
    if not fixture.get("scriptFamily", {}).get("singlePipeline"):
        fail("setup fixture must declare a single pipeline")
    if not fixture.get("scriptFamily", {}).get("profileVariablesOnly"):
        fail("setup fixture must use profile variables only")

    profiles = {profile.get("name"): profile for profile in fixture.get("profiles", [])}
    if set(profiles) != {"dev", "local-lan-dogfood", "public-dogfood", "production"}:
        fail("fixture must define dev, local-lan-dogfood, public-dogfood, and production")
    lan = profiles["local-lan-dogfood"]
    lan_policy = lan.get("endpointPolicy", {})
    if lan.get("reachabilityMode") != "lan" or not lan.get("memberPhoneAllowed"):
        fail("local-lan-dogfood must be phone-reachable over LAN")
    for key in ["rejectLocalhostForPhone", "rejectLoopbackForPhone", "rejectMacOnlyLocalNamesForPhone"]:
        if not lan_policy.get(key):
            fail(f"local-lan-dogfood must set {key}")
    if lan_policy.get("publicDnsRequired") or lan_policy.get("trustedInternetTlsRequired"):
        fail("local-lan-dogfood must not require public DNS or trusted internet TLS")

    onboarding = fixture.get("memberOnboardingInvariant", {})
    if onboarding.get("requiredPath") != ["open handoff", "SSO", "Weave workspace home"]:
        fail("member onboarding required path must be handoff -> SSO -> workspace home")
    forbidden_inputs = set(onboarding.get("forbiddenMemberInputs", []))
    for item in FORBIDDEN_MEMBER_INPUTS:
        if item not in forbidden_inputs:
            fail(f"member onboarding fixture must forbid {item}")

    support_safe = fixture.get("supportSafeEvidence", {})
    for item in ["raw secrets", "credential URLs", "provider payloads", "member private content", "raw downstream errors"]:
        if item not in support_safe.get("forbidden", []):
            fail(f"support-safe evidence must forbid {item}")

    action_events = json.loads(read("release/sprint-30-hot-phase/weaver-mobile-action-events.fixture.json"))
    event_types = {event.get("type"): event for event in action_events.get("eventTypes", [])}
    for event_type in ["weaver.action_request.created", "weaver.action_request.receipt"]:
        if event_type not in event_types:
            fail(f"missing mobile action event contract: {event_type}")
    created = event_types["weaver.action_request.created"]
    if not created.get("requiresUserApproval") or not created.get("expiresClosed"):
        fail("action requests must require approval and expire closed")
    created_payload = created.get("payload", {})
    for key in ["requestId", "capability", "toolRef", "approvalChoices", "policyVersion", "runtimeProfileHash", "auditCorrelationRef"]:
        if key not in created_payload:
            fail(f"action request payload missing {key}")
    receipt_payload = event_types["weaver.action_request.receipt"].get("payload", {})
    for key in ["receiptRef", "auditRef", "revocationRef", "supportSafe"]:
        if key not in receipt_payload:
            fail(f"action receipt payload missing {key}")
    fail_closed_text = "\n".join(action_events.get("failClosedRules", []))
    for phrase in ["Unknown capability", "Expired requests deny by default"]:
        if phrase not in fail_closed_text:
            fail(f"mobile action contract missing fail-closed rule: {phrase}")
    for item in ["raw prompt", "member private content", "raw provider payload", "secret value", "credential URL", "raw downstream error"]:
        if item not in action_events.get("privacyBoundary", {}).get("forbiddenInPayload", []):
            fail(f"mobile action payload must forbid {item}")

    evidence_pack = read("docs/sprint-30-hot-phase-evidence-pack.md")
    for phrase in [
        "No separate test-only member flow",
        "rejects `127.0.0.1`, `localhost`, and Mac-only `.local` assumptions",
        "must never ask for OIDC issuer",
        "user rights plus organization-whitelisted tools/capabilities",
        "Unknown actions and capabilities fail closed",
    ]:
        if phrase not in evidence_pack:
            fail(f"evidence pack missing phrase: {phrase}")

    print("sprint30-hot-phase-check: ok")


if __name__ == "__main__":
    main()
