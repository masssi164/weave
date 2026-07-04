#!/usr/bin/env python3
"""Validate support-safe dogfood activation mail evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


SECRET_PATTERN = re.compile(
    r"(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|"
    r"bearer\s+|client[_-]?secret|secretref://|credential[_-]?url)",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Check the support-safe activation evidence emitted by "
            "infra/weave-workspace/activate-user.sh and verify Mailpit captured "
            "the corresponding local identity mail without printing action links."
        )
    )
    parser.add_argument("--activation-evidence-file", required=True, type=Path)
    parser.add_argument("--expected-invite-ref", required=True)
    parser.add_argument(
        "--expected-email-sha256",
        help="Optional SHA-256 of the expected recipient email. Defaults to the activation evidence hash.",
    )
    parser.add_argument(
        "--mailpit-api",
        default="http://127.0.0.1:8025/api/v1/messages",
        help="Mailpit messages API URL.",
    )
    parser.add_argument(
        "--mailpit-fixture",
        type=Path,
        help="Fixture-only Mailpit payload for unit tests; live gates should use --mailpit-api.",
    )
    args = parser.parse_args()

    evidence = read_json_file(args.activation_evidence_file)
    assert_support_safe("activation evidence", evidence)

    require(
        evidence.get("schemaVersion") == "weave.dogfood.activation-invite.v1",
        "activation evidence schema mismatch",
    )
    require(
        evidence.get("inviteRef") == args.expected_invite_ref,
        "activation inviteRef does not match the dogfood handoff",
    )
    require(evidence.get("supportSafe") is True, "activation evidence is not supportSafe=true")
    require(
        evidence.get("qrOrDeeplinkCarriesSecret") is False,
        "activation evidence must prove QR/deeplink carries no activation secret",
    )
    require(
        evidence.get("appStoresActivationSecret") is False,
        "activation evidence must prove the app does not store the activation secret",
    )

    activation = object_field(evidence, "activation")
    require(
        activation.get("mode") == "keycloak-required-actions-email",
        "activation mode must be Keycloak required-action email",
    )
    require(activation.get("mailSent") is True, "activation mail was not sent")
    required_actions = string_list(activation.get("requiredActions"), "activation.requiredActions")
    require(
        "UPDATE_PASSWORD" in required_actions,
        "activation required actions must include UPDATE_PASSWORD",
    )

    expected_email_sha = (
        args.expected_email_sha256
        or string_field(evidence, "emailSha256", "activation email hash")
    )
    require(
        re.fullmatch(r"[0-9a-f]{64}", expected_email_sha) is not None,
        "expected email hash must be lowercase SHA-256",
    )

    mailpit_payload = (
        read_json_file(args.mailpit_fixture)
        if args.mailpit_fixture is not None
        else fetch_json(args.mailpit_api)
    )
    messages = mailpit_messages(mailpit_payload)
    matched_message = find_matching_message(
        messages,
        expected_email_sha256=expected_email_sha,
        mailpit_api=args.mailpit_api,
        fixture_mode=args.mailpit_fixture is not None,
    )
    require(matched_message is not None, "Mailpit did not contain the expected activation mail")

    result = {
        "schemaVersion": "weave.dogfood.activation-mail-check.v1",
        "inviteRef": args.expected_invite_ref,
        "requiredActions": required_actions,
        "mailMessageMatched": True,
        "supportSafe": True,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    print(
        "DOGFOOD_ACTIVATION_MAIL_RESULT "
        f"inviteRef={args.expected_invite_ref} "
        "mailMessageMatched=true "
        f"requiredActions={','.join(required_actions)} "
        "supportSafe=true"
    )
    return 0


def read_json_file(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path} is not valid JSON: {exc}") from exc
    except OSError as exc:
        raise SystemExit(f"{path} could not be read: {exc}") from exc


def fetch_json(url: str) -> Any:
    with urllib.request.urlopen(url, timeout=10) as response:
        return json.load(response)


def assert_support_safe(name: str, payload: Any) -> None:
    serialized = json.dumps(payload, sort_keys=True)
    require(not SECRET_PATTERN.search(serialized), f"{name} contains secret-like text")
    require("login-actions/action-token" not in serialized, f"{name} contains an activation action link")


def object_field(payload: dict[str, Any], key: str) -> dict[str, Any]:
    value = payload.get(key)
    require(isinstance(value, dict), f"{key} must be an object")
    return value


def string_field(payload: dict[str, Any], key: str, label: str) -> str:
    value = payload.get(key)
    require(isinstance(value, str) and value.strip(), f"{label} is missing")
    return value


def string_list(value: Any, label: str) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    result = []
    for entry in value:
        require(isinstance(entry, str) and entry.strip(), f"{label} entries must be strings")
        result.append(entry.strip())
    return result


def mailpit_messages(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        messages = payload.get("messages")
        if isinstance(messages, list):
            return [entry for entry in messages if isinstance(entry, dict)]
    if isinstance(payload, list):
        return [entry for entry in payload if isinstance(entry, dict)]
    raise SystemExit("Mailpit API response did not contain messages")


def find_matching_message(
    messages: list[dict[str, Any]],
    *,
    expected_email_sha256: str,
    mailpit_api: str,
    fixture_mode: bool,
) -> dict[str, Any] | None:
    for message in messages:
        addresses = extract_addresses(message)
        if any(address_matches_hash(address, expected_email_sha256) for address in addresses):
            return message

        if fixture_mode:
            continue
        message_id = message_id_from(message)
        if not message_id:
            continue
        detail = fetch_json(message_detail_url(mailpit_api, message_id))
        addresses = extract_addresses(detail)
        if any(address_matches_hash(address, expected_email_sha256) for address in addresses):
            return message
    return None


def message_id_from(message: dict[str, Any]) -> str | None:
    for key in ("ID", "Id", "id"):
        value = message.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def message_detail_url(messages_url: str, message_id: str) -> str:
    parsed = urllib.parse.urlparse(messages_url)
    path = parsed.path.rstrip("/")
    if path.endswith("/messages"):
        path = path[: -len("/messages")] + "/message"
    return urllib.parse.urlunparse(
        parsed._replace(path=f"{path}/{urllib.parse.quote(message_id)}")
    )


def extract_addresses(payload: Any) -> list[str]:
    addresses: list[str] = []
    if isinstance(payload, dict):
        for key, value in payload.items():
            normalized_key = key.lower()
            if normalized_key in {"address", "email"} and isinstance(value, str):
                addresses.append(value.strip())
            elif normalized_key in {"to", "recipients"}:
                addresses.extend(extract_addresses(value))
            elif isinstance(value, (dict, list)):
                addresses.extend(extract_addresses(value))
    elif isinstance(payload, list):
        for entry in payload:
            addresses.extend(extract_addresses(entry))
    return [address for address in addresses if "@" in address]


def address_matches_hash(address: str, expected_sha256: str) -> bool:
    candidates = {address.strip(), address.strip().lower()}
    return any(hashlib.sha256(candidate.encode("utf-8")).hexdigest() == expected_sha256 for candidate in candidates)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    sys.exit(main())
