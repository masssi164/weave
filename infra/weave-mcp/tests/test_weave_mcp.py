from __future__ import annotations

import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from weave_mcp.app import WeaveMcpGateway, serve
from weave_mcp.config import WeaveMcpConfig
from weave_mcp.schemas.common import McpDenied


HEADERS = {
    "Authorization": "Bearer dev-runtime-token",
    "X-Weave-Org-Id": "org:dogfood",
    "X-Weave-User-Ref": "user:support-safe",
    "X-Weave-Runtime-Profile": "sha256:runtime-profile",
    "X-Weave-Capabilities": "weaver.admin_readiness_read,weaver.runtime_profile_read,weaver.calendar_read,weaver.boards_write",
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

    def test_disabled_or_missing_context_fails_closed(self) -> None:
        with self.assertRaises(McpDenied):
            self.gateway(enabled=False).discover_tools({key.lower(): value for key, value in HEADERS.items()})
        with self.assertRaises(McpDenied):
            self.gateway(enabled=True).discover_tools({"authorization": "Bearer dev-runtime-token"})

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
        finally:
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    unittest.main()
