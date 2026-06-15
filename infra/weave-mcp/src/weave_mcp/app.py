from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from .client import WeaveBackendClient
from .config import WeaveMcpConfig
from .schemas.common import McpDenied, RuntimeContext
from .tools.registry import discover, invoke


class WeaveMcpGateway:
    def __init__(self, config: WeaveMcpConfig):
        config.validate()
        self.config = config
        self.client = WeaveBackendClient(config.backend_base_url)

    @property
    def server_info(self) -> dict[str, Any]:
        return {
            "name": "Weave Governed Domain Tools",
            "serverKey": self.config.server_key,
            "transport": self.config.transport,
            "enabled": self.config.enabled,
            "backendAuthority": "weave-backend",
            "endpointRef": self.config.internal_endpoint_ref,
            "rawEndpointExposed": False,
        }

    def context_from_headers(self, headers: dict[str, str]) -> RuntimeContext:
        if not self.config.enabled:
            raise McpDenied("mcp-server-disabled-by-org-policy")
        return RuntimeContext.from_headers(
            headers, self.config.runtime_token, self.config.runtime_profile_projection_hmac_secret
        )

    def discover_tools(self, headers: dict[str, str]) -> dict[str, Any]:
        ctx = self.context_from_headers(headers)
        tools = [tool for tool in discover(ctx) if tool["enabledForRuntime"]]
        return {"serverInfo": self.server_info, "tools": tools, "supportSafe": True}

    def invoke_tool(self, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any]:
        ctx = self.context_from_headers(headers)
        name = str(body.get("tool", ""))
        payload = body.get("input") if isinstance(body.get("input"), dict) else {}
        return {"result": invoke(name, ctx, payload, self.client), "supportSafe": True}

    def handle_jsonrpc(self, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any] | None:
        """Handle the minimal MCP Streamable HTTP JSON-RPC surface used by OpenClaw.

        The existing /mcp/discover and /mcp/invoke endpoints are Weave-local REST
        compatibility endpoints. /mcp is the protocol boundary: initialize,
        notifications/initialized, tools/list, and tools/call delegate to the same
        governed RuntimeProfile policy path as the compatibility endpoints.
        """

        request_id = body.get("id")
        method = str(body.get("method", ""))
        if method == "notifications/initialized":
            return None
        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": {"name": self.server_info["name"], "version": "0.1.0"},
                },
            }
        if method == "tools/list":
            discovered = self.discover_tools(headers)
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "tools": [
                        {
                            "name": tool["name"],
                            "description": tool.get("description", ""),
                            "inputSchema": tool.get("inputSchema", {"type": "object", "properties": {}}),
                        }
                        for tool in discovered["tools"]
                    ]
                },
            }
        if method == "tools/call":
            params = body.get("params") if isinstance(body.get("params"), dict) else {}
            name = str(params.get("name", ""))
            arguments = params.get("arguments") if isinstance(params.get("arguments"), dict) else {}
            result = self.invoke_tool(headers, {"tool": name, "input": arguments})["result"]
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "content": [{"type": "text", "text": json.dumps(result, sort_keys=True)}],
                    "isError": False,
                },
            }
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "error": {"code": -32601, "message": "method not found"},
        }


class _Handler(BaseHTTPRequestHandler):
    gateway: WeaveMcpGateway

    def _headers(self) -> dict[str, str]:
        return {key.lower(): value for key, value in self.headers.items()}

    def _send(self, status: int, payload: dict[str, Any] | None) -> None:
        body = b"" if payload is None else json.dumps(payload, sort_keys=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Weave-MCP-Transport", "streamable-http")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, status: int, reason: str, audit_ref: str = "audit://mcp/denied/support-safe") -> None:
        self._send(status, {"error": reason, "auditRef": audit_ref, "supportSafe": True})

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler name
        try:
            if self.path == "/health":
                self._send(200, {"status": "ok", **self.gateway.server_info})
            elif self.path == "/mcp/discover":
                self._send(200, self.gateway.discover_tools(self._headers()))
            else:
                self._error(404, "not-found")
        except McpDenied as error:
            self._error(403, error.reason, error.audit_ref)

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler name
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
            if self.path == "/mcp/invoke":
                self._send(200, self.gateway.invoke_tool(self._headers(), body))
            elif self.path == "/mcp":
                response = self.gateway.handle_jsonrpc(self._headers(), body)
                self._send(202 if response is None else 200, response)
            else:
                self._error(404, "not-found")
        except McpDenied as error:
            self._error(403, error.reason, error.audit_ref)
        except json.JSONDecodeError:
            self._error(400, "invalid-json")

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
        return


def make_handler(gateway: WeaveMcpGateway) -> type[_Handler]:
    class Handler(_Handler):
        pass

    Handler.gateway = gateway
    return Handler


def serve(config: WeaveMcpConfig, host: str = "127.0.0.1", port: int = 8765) -> ThreadingHTTPServer:
    gateway = WeaveMcpGateway(config)
    return ThreadingHTTPServer((host, port), make_handler(gateway))


def main() -> None:
    parser = argparse.ArgumentParser(description="Weave MCP Streamable HTTP gateway")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    httpd = serve(WeaveMcpConfig.from_env(), args.host, args.port)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
