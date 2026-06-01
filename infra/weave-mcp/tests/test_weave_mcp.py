from __future__ import annotations

import json
import base64
import hashlib
import hmac
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from weave_mcp.app import WeaveMcpGateway, serve
from weave_mcp.config import WeaveMcpConfig
from weave_mcp.schemas.common import McpDenied

PROJECTION_HMAC_SECRET = "dev-runtime-profile-projection-secret"

RUNTIME_PROFILE_PROJECTION = {
    "runtimeProfileHash": "sha256:runtime-profile",
    "profileVersion": "v-local-rc-evidence",
    "enabled": True,
    "revoked": False,
    "serverKey": "weave-domain-tools",
    "transport": "streamable-http",
    "endpointRef": "internal://weave-mcp/streamable-http",
    "credentialRef": "credentialref://weave/mcp/weave-domain-tools/runtime-token",
    "capabilityGrants": [
        "weaver.admin_readiness_read",
        "weaver.runtime_profile_read",
        "weaver.calendar_read",
        "weaver.boards_write",
    ],
    "allowedTools": [
        "admin.get_readiness",
        "weaver.get_runtime_profile_projection",
        "calendar.search_events",
        "boards.comment",
    ],
    "auditRef": "audit://mcp/runtime-profile/local-rc-evidence",
    "supportSafe": True,
    "rawEndpointExposed": False,
}


def encoded_projection(profile: dict[str, object] | None = None) -> str:
    signed_profile = dict(profile or RUNTIME_PROFILE_PROJECTION)
    signed_profile.pop("projectionSignature", None)
    payload = json.dumps(signed_profile, sort_keys=True, separators=(",", ":")).encode("utf-8")
    digest = hmac.new(PROJECTION_HMAC_SECRET.encode("utf-8"), payload, hashlib.sha256).hexdigest()
    signed_profile["projectionSignature"] = f"hmac-sha256:{digest}"
    body = json.dumps(signed_profile, sort_keys=True).encode("utf-8")
    return base64.urlsafe_b64encode(body).decode("utf-8").rstrip("=")


HEADERS = {
    "Authorization": "Bearer dev-runtime-token",
    "X-Weave-Org-Id": "org:dogfood",
    "X-Weave-User-Ref": "user:support-safe",
    "X-Weave-Runtime-Profile": "sha256:runtime-profile",
    "X-Weave-Runtime-Profile-Projection": encoded_projection(),
}


class WeaveMcpGatewayTest(unittest.TestCase):
    def gateway(self, enabled: bool = True) -> WeaveMcpGateway:
        return WeaveMcpGateway(WeaveMcpConfig(enabled=enabled))

    def test_streamable_http_config_is_primary_runtime_transport(self) -> None:
        gateway = self.gateway()
        self.assertEqual(gateway.server_info["transport"], "streamable-http")
        self.assertFalse(gateway.server_info["rawEndpointExposed"])
        self.assertEqual(gateway.server_info["backendAuthority"], "weave-backend")

    def test_discovery_filters_by_runtime_capability_grants(self) -> None:
        body = self.gateway().discover_tools({key.lower(): value for key, value in HEADERS.items()})
        tools = {tool["name"]: tool for tool in body["tools"]}
        self.assertIn("admin.get_readiness", tools)
        self.assertIn("weaver.get_runtime_profile_projection", tools)
        self.assertIn("calendar.search_events", tools)
        self.assertIn("boards.comment", tools)
        self.assertTrue(tools["boards.comment"]["meta"]["approval"] == "required")
        self.assertTrue(all(tool["meta"]["transport"] == "streamable-http" for tool in tools.values()))

    def test_discovery_uses_runtime_profile_projection_not_caller_grant_headers(self) -> None:
        profile = {**RUNTIME_PROFILE_PROJECTION, "allowedTools": ["calendar.search_events"], "capabilityGrants": ["weaver.calendar_read"]}
        headers = {key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(profile)}.items()}
        headers["x-weave-capabilities"] = "weaver.boards_write,weaver.admin_readiness_read"

        body = self.gateway().discover_tools(headers)
        tools = {tool["name"]: tool for tool in body["tools"]}

        self.assertEqual(set(tools), {"calendar.search_events"})

        with self.assertRaises(McpDenied) as raised:
            self.gateway().invoke_tool(headers, {"tool": "boards.comment", "input": {"approvalReceiptRef": "approval://board-comment/1"}})
        self.assertEqual(raised.exception.reason, "capability-not-granted")

    def test_disabled_or_missing_context_fails_closed(self) -> None:
        with self.assertRaises(McpDenied):
            self.gateway(enabled=False).discover_tools({key.lower(): value for key, value in HEADERS.items()})
        with self.assertRaises(McpDenied):
            self.gateway(enabled=True).discover_tools({"authorization": "Bearer dev-runtime-token"})
        revoked = {**RUNTIME_PROFILE_PROJECTION, "revoked": True}
        with self.assertRaises(McpDenied) as raised:
            self.gateway(enabled=True).discover_tools({key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(revoked)}.items()})
        self.assertEqual(raised.exception.reason, "runtime-profile-disabled-or-revoked")

    def test_malformed_or_tampered_runtime_profile_projection_fails_closed(self) -> None:
        malformed_headers = {key.lower(): value for key, value in HEADERS.items()}
        malformed_headers["x-weave-runtime-profile-projection"] = "not-valid-base64%%%"
        with self.assertRaises(McpDenied) as malformed:
            self.gateway(enabled=True).discover_tools(malformed_headers)
        self.assertEqual(malformed.exception.reason, "invalid-runtime-profile-projection")

        tampered = dict(RUNTIME_PROFILE_PROJECTION)
        tampered["projectionSignature"] = "hmac-sha256:bad"
        tampered_headers = {key.lower(): value for key, value in HEADERS.items()}
        tampered_headers["x-weave-runtime-profile-projection"] = base64.urlsafe_b64encode(
            json.dumps(tampered, sort_keys=True).encode("utf-8")
        ).decode("utf-8").rstrip("=")
        with self.assertRaises(McpDenied) as raised:
            self.gateway(enabled=True).discover_tools(tampered_headers)
        self.assertEqual(raised.exception.reason, "invalid-runtime-profile-projection-signature")

    def test_invocation_is_support_safe_and_write_stub_requires_approval(self) -> None:
        headers = {key.lower(): value for key, value in HEADERS.items()}
        readiness = self.gateway().invoke_tool(headers, {"tool": "admin.get_readiness", "input": {}})
        self.assertTrue(readiness["result"]["supportSafe"])
        self.assertFalse(readiness["result"]["normalMembersMayConfigureMcpServers"])
        self.assertNotIn("Bearer ", repr(readiness))
        self.assertNotIn("openclaw.json", repr(readiness))

        with self.assertRaises(McpDenied):
            self.gateway().invoke_tool(headers, {"tool": "boards.comment", "input": {"taskRef": "task://one", "body": "ok"}})

        accepted = self.gateway().invoke_tool(
            headers,
            {"tool": "boards.comment", "input": {"taskRef": "task://one", "body": "ok", "approvalReceiptRef": "approval://board-comment/1"}},
        )
        self.assertEqual(accepted["result"]["decision"], "accepted-for-backend-action-request")
        self.assertFalse(accepted["result"]["providerMutationPerformedByMcp"])

    def test_local_streamable_http_server_discovery_and_invocation(self) -> None:
        httpd = serve(WeaveMcpConfig(enabled=True), port=0)
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        try:
            base = f"http://127.0.0.1:{httpd.server_address[1]}"
            req = Request(base + "/mcp/discover", headers=HEADERS)
            with urlopen(req, timeout=5) as response:
                self.assertEqual(response.headers["X-Weave-MCP-Transport"], "streamable-http")
                payload = json.loads(response.read())
            self.assertEqual(payload["serverInfo"]["transport"], "streamable-http")
            self.assertIn("calendar.search_events", {tool["name"] for tool in payload["tools"]})

            invoke = Request(
                base + "/mcp/invoke",
                method="POST",
                headers={**HEADERS, "Content-Type": "application/json"},
                data=json.dumps({"tool": "calendar.search_events", "input": {"query": "demo"}}).encode(),
            )
            with urlopen(invoke, timeout=5) as response:
                result = json.loads(response.read())
            self.assertTrue(result["supportSafe"])
            self.assertTrue(result["result"]["redactedItems"])

            denied = Request(
                base + "/mcp/invoke",
                method="POST",
                headers={**HEADERS, "Content-Type": "application/json"},
                data=json.dumps({"tool": "boards.comment", "input": {"taskRef": "task://one"}}).encode(),
            )
            with self.assertRaises(HTTPError) as raised:
                urlopen(denied, timeout=5)
            self.assertEqual(raised.exception.code, 403)
            error = json.loads(raised.exception.read())
            raised.exception.close()
            self.assertEqual(error["auditRef"], "audit://mcp/denied/support-safe")
            self.assertTrue(error["supportSafe"])
        finally:
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    unittest.main()
