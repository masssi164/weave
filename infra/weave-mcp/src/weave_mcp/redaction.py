from __future__ import annotations

from typing import Any

FORBIDDEN_NEEDLES = (
    "Bearer ",
    "access_token",
    "refresh_token",
    "openclaw.json",
    "rawProviderPayload",
    "credentialBearingDownloadUrl",
    "secretValue",
)


def assert_support_safe(payload: Any) -> None:
    text = repr(payload)
    leaked = [needle for needle in FORBIDDEN_NEEDLES if needle in text]
    if leaked:
        raise ValueError(f"MCP payload is not support-safe: {', '.join(leaked)}")


def redact_string(value: str) -> str:
    if value.startswith(("http://", "https://")):
        return "endpoint-ref://redacted"
    return value
