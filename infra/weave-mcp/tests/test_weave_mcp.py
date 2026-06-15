from __future__ import annotations

import json
import base64
import hashlib
import hmac
import threading
import unittest
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from unittest.mock import patch

from weave_mcp.app import WeaveMcpGateway, serve
from weave_mcp.config import WeaveMcpConfig
from weave_mcp.schemas.common import McpDenied

PROJECTION_HMAC_SECRET = "dev-runtime-profile-projection-secret"


def future_iso(minutes: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")


RUNTIME_PROFILE_PROJECTION = {
    "runtimeProfileHash": "sha256:runtime-profile",
    "runtimeProfileFetchRef": "weave-runtime-profile://sha256:runtime-profile",
    "profileVersion": "v-local-rc-evidence",
    "expiresAt": future_iso(60),
    "enabled": True,
    "revoked": False,
    "serverKey": "weave-domain-tools",
    "transport": "streamable-http",
    "endpointRef": "internal://weave-mcp/streamable-http",
    "credentialRef": "credentialref://weave/mcp/weave-domain-tools/runtime-token",
    "runtimeTokenRef": "credentialref://weave/runtime/short-lived/local-rc-evidence",
    "runtimeTokenExpiresAt": future_iso(10),
    "capabilityGrants": [
        "weaver.admin_readiness_read",
        "weaver.runtime_profile_read",
        "weaver.calendar_read",
        "weaver.calendar_create_event",
        "weaver.files_read",
        "weaver.chat_read",
        "weaver.chat_send",
        "weaver.boards_write",
    ],
    "allowedTools": [
        "admin.get_readiness",
        "weaver.get_runtime_profile_projection",
        "calendar.search_events",
        "calendar.create_event",
        "files.search",
        "files.read",
        "chat.list_threads",
        "chat.send_message",
        "boards.comment",
    ],
    "alwaysAllowGrants": ["always-allow://weave/calendar.create_event/org-dogfood/user-support-safe"],
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
        self.assertIn("calendar.create_event", tools)
        self.assertIn("files.search", tools)
        self.assertIn("files.read", tools)
        self.assertIn("chat.list_threads", tools)
        self.assertIn("chat.send_message", tools)
        self.assertIn("boards.comment", tools)
        self.assertTrue(tools["calendar.create_event"]["meta"]["approval"] == "required")
        self.assertTrue(tools["chat.send_message"]["meta"]["approval"] == "required")
        self.assertTrue(tools["boards.comment"]["meta"]["approval"] == "required")
        self.assertTrue(all(tool["meta"]["transport"] == "streamable-http" for tool in tools.values()))
        discovery_text = json.dumps(body, sort_keys=True).lower()
        self.assertNotIn("raw_files_provider", discovery_text)
        self.assertNotIn("raw_calendar_provider", discovery_text)
        self.assertNotIn("providerref", discovery_text)
        self.assertNotIn("credentialref://", discovery_text)
        self.assertFalse(any(tool["name"].startswith(("raw_files_provider.", "raw_calendar_provider.")) for tool in tools.values()))

    def test_user_weave_chat_send_uses_only_profile_governed_domain_tools(self) -> None:
        profile = {
            **RUNTIME_PROFILE_PROJECTION,
            "allowedTools": ["chat.list_threads", "chat.send_message", "calendar.search_events", "files.search"],
            "capabilityGrants": ["weaver.chat_read", "weaver.chat_send", "weaver.calendar_read", "weaver.files_read"],
        }
        headers = {key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(profile)}.items()}

        discovered = self.gateway().discover_tools(headers)
        tool_names = {tool["name"] for tool in discovered["tools"]}
        self.assertEqual(tool_names, {"chat.list_threads", "chat.send_message", "calendar.search_events", "files.search"})
        discovery_text = json.dumps(discovered, sort_keys=True).lower()
        for forbidden in ["raw_chat_provider.", "raw_files_provider.", "raw_calendar_provider.", "providerref", "credentialref://"]:
            self.assertNotIn(forbidden, discovery_text)

        listed = self.gateway().invoke_tool(headers, {"tool": "chat.list_threads", "input": {"channelId": "channels.weave-chat"}})
        self.assertEqual(listed["result"]["threads"][0]["threadRef"], "chat-thread://weave/support-safe/pa-weaver")
        with self.assertRaises(McpDenied) as denied:
            self.gateway().invoke_tool(
                headers,
                {"tool": "chat.send_message", "input": {"threadRef": "chat-thread://weave/support-safe/pa-weaver", "body": "Hello Weaver"}},
            )
        self.assertEqual(denied.exception.reason, "approval-required-for-chat.send_message")

        sent = self.gateway().invoke_tool(
            headers,
            {
                "tool": "chat.send_message",
                "input": {
                    "threadRef": "chat-thread://weave/support-safe/pa-weaver",
                    "body": "Hello Weaver",
                    "approvalReceiptRef": "approval://chat-send/1",
                },
            },
        )
        self.assertEqual(sent["result"]["decision"], "accepted-for-weave-chat-domain-send")
        self.assertEqual(sent["result"]["channelId"], "channels.weave-chat")
        self.assertFalse(sent["result"]["providerMutationPerformedByMcp"])
        self.assertFalse(sent["result"]["rawProviderChannelExposed"])
        self.assertNotIn("Hello Weaver", json.dumps(sent, sort_keys=True))

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
        self.assertEqual(raised.exception.audit_ref, "audit://mcp/runtime-profile/local-rc-evidence")

    def test_stale_or_overbroad_runtime_profile_projection_fails_closed(self) -> None:
        stale = {**RUNTIME_PROFILE_PROJECTION, "expiresAt": future_iso(-1), "runtimeTokenExpiresAt": future_iso(-2)}
        with self.assertRaises(McpDenied) as expired:
            self.gateway(enabled=True).discover_tools({key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(stale)}.items()})
        self.assertEqual(expired.exception.reason, "runtime-profile-expired-or-stale")

        overbroad = {**RUNTIME_PROFILE_PROJECTION, "allowedTools": ["calendar.search_events", "write calendar"]}
        with self.assertRaises(McpDenied) as denied:
            self.gateway(enabled=True).discover_tools({key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(overbroad)}.items()})
        self.assertEqual(denied.exception.reason, "runtime-profile-overbroad-tool-grant")

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

        projection = self.gateway().invoke_tool(headers, {"tool": "weaver.get_runtime_profile_projection", "input": {}})
        self.assertIn("mcp", projection["result"])
        self.assertIn("servers", projection["result"]["mcp"])
        self.assertEqual(
            projection["result"]["mcp"]["servers"]["weave-domain-tools"]["endpointRef"],
            "internal://weave-mcp/streamable-http",
        )
        self.assertEqual(
            projection["result"]["mcp"]["servers"]["weave-domain-tools"]["credentialRef"],
            "credentialref://weave/mcp/weave-domain-tools/runtime-token",
        )
        self.assertNotIn("channels.weave-chat", json.dumps(projection, sort_keys=True))

        with self.assertRaises(McpDenied):
            self.gateway().invoke_tool(headers, {"tool": "boards.comment", "input": {"taskRef": "task://one", "body": "ok"}})

        accepted = self.gateway().invoke_tool(
            headers,
            {"tool": "boards.comment", "input": {"taskRef": "task://one", "body": "ok", "approvalReceiptRef": "approval://board-comment/1"}},
        )
        self.assertEqual(accepted["result"]["decision"], "accepted-for-backend-action-request")
        self.assertFalse(accepted["result"]["providerMutationPerformedByMcp"])

    def test_calendar_create_event_requires_approval_or_persistent_scoped_always_allow(self) -> None:
        headers = {key.lower(): value for key, value in HEADERS.items()}
        request = {"tool": "calendar.create_event", "input": {"title": "Testereignis", "startsAt": "19:00"}}
        with self.assertRaises(McpDenied) as missing_approval:
            self.gateway().invoke_tool(headers, request)
        self.assertEqual(missing_approval.exception.reason, "approval-required-for-calendar.create_event")

        unsigned_grant_profile = {**RUNTIME_PROFILE_PROJECTION, "alwaysAllowGrants": []}
        unsigned_headers = {key.lower(): value for key, value in {**HEADERS, "X-Weave-Runtime-Profile-Projection": encoded_projection(unsigned_grant_profile)}.items()}
        with self.assertRaises(McpDenied) as forged_always_allow:
            self.gateway().invoke_tool(
                unsigned_headers,
                {
                    "tool": "calendar.create_event",
                    "input": {
                        "title": "Forged",
                        "startsAt": "19:00",
                        "alwaysAllowGrantRef": "always-allow://weave/calendar.create_event/org-dogfood/user-support-safe",
                    },
                },
            )
        self.assertEqual(forged_always_allow.exception.reason, "approval-required-for-calendar.create_event")

        created = self.gateway().invoke_tool(
            headers,
            {
                "tool": "calendar.create_event",
                "input": {
                    "title": "Testereignis",
                    "startsAt": "19:00",
                    "alwaysAllowGrantRef": "always-allow://weave/calendar.create_event/org-dogfood/user-support-safe",
                },
            },
        )
        self.assertEqual(created["result"]["decision"], "created-test-fixture-event")
        self.assertTrue(created["result"]["readbackVerified"])
        self.assertIn("Audit: audit://mcp/calendar-create/support-safe", created["result"]["finalChatAnswer"])
        self.assertFalse(created["result"]["providerMutationPerformedByMcp"])

        # Simulate a later session: same signed profile scope plus persistent always-allow grant still works.
        later_session = self.gateway().invoke_tool(
            {key.lower(): value for key, value in HEADERS.items()},
            {
                "tool": "calendar.create_event",
                "input": {
                    "title": "Folgetermin",
                    "startsAt": "19:00",
                    "alwaysAllowGrantRef": "always-allow://weave/calendar.create_event/org-dogfood/user-support-safe",
                },
            },
        )
        readback = self.gateway().invoke_tool(
            headers,
            {"tool": "calendar.search_events", "input": {"eventRef": later_session["result"]["eventRef"]}},
        )
        self.assertTrue(readback["result"]["readbackVerified"])

    def test_calendar_search_delegates_to_backend_calendar_facade(self) -> None:
        class BackendResponse:
            @contextmanager
            def __call__(self, request: Request, timeout: int = 0):
                self.request = request
                yield self

            def read(self) -> bytes:
                return json.dumps(
                    {
                        "items": [
                            {
                                "id": "calendar-event-1",
                                "title": "Private title",
                                "startsAt": "2026-06-12T08:00:00Z",
                                "endsAt": "2026-06-12T08:30:00Z",
                                "allDay": False,
                                "scope": {"type": "workspace"},
                            },
                            {
                                "id": "support-safe-seeded",
                                "title": "Support-safe seeded calendar check",
                                "startsAt": "2026-06-12T09:00:00Z",
                                "endsAt": "2026-06-12T09:30:00Z",
                                "allDay": False,
                                "scope": {"type": "workspace"},
                                "supportSafe": True,
                            },
                        ]
                    }
                ).encode("utf-8")

        backend = BackendResponse()
        headers = {key.lower(): value for key, value in HEADERS.items()}
        with patch("weave_mcp.client.urlopen", backend):
            result = self.gateway().invoke_tool(
                headers,
                {"tool": "calendar.search_events", "input": {"from": "2026-06-12T00:00:00Z", "to": "2026-06-13T00:00:00Z"}},
            )["result"]

        self.assertIn("/api/calendar/events?from=2026-06-12T00%3A00%3A00Z&to=2026-06-13T00%3A00%3A00Z", backend.request.full_url)
        self.assertEqual(backend.request.headers["Authorization"], "Bearer dev-runtime-token")
        self.assertTrue(result["providerSourceMappedByBackend"])
        self.assertTrue(result["redactedItems"])
        self.assertEqual(result["items"][0]["titlePresent"], True)
        self.assertNotIn("Private title", repr(result))
        self.assertEqual(result["items"][1]["supportSafeTitle"], "Support-safe seeded calendar check")

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

            initialize = Request(
                base + "/mcp",
                method="POST",
                headers={**HEADERS, "Content-Type": "application/json", "Accept": "application/json, text/event-stream"},
                data=json.dumps({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}}).encode(),
            )
            with urlopen(initialize, timeout=5) as response:
                rpc = json.loads(response.read())
            self.assertEqual(rpc["result"]["capabilities"]["tools"]["listChanged"], False)

            list_tools = Request(
                base + "/mcp",
                method="POST",
                headers={**HEADERS, "Content-Type": "application/json", "Accept": "application/json, text/event-stream"},
                data=json.dumps({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}).encode(),
            )
            with urlopen(list_tools, timeout=5) as response:
                rpc = json.loads(response.read())
            tools = {tool["name"]: tool for tool in rpc["result"]["tools"]}
            self.assertIn("calendar.search_events", tools)
            self.assertIn("inputSchema", tools["calendar.search_events"])

            call_tool = Request(
                base + "/mcp",
                method="POST",
                headers={**HEADERS, "Content-Type": "application/json", "Accept": "application/json, text/event-stream"},
                data=json.dumps(
                    {
                        "jsonrpc": "2.0",
                        "id": 3,
                        "method": "tools/call",
                        "params": {"name": "calendar.search_events", "arguments": {"query": "heute"}},
                    }
                ).encode(),
            )
            with urlopen(call_tool, timeout=5) as response:
                rpc = json.loads(response.read())
            self.assertFalse(rpc["result"]["isError"])
            self.assertEqual(rpc["result"]["content"][0]["type"], "text")
            self.assertIn("redactedItems", rpc["result"]["content"][0]["text"])
        finally:
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    unittest.main()
