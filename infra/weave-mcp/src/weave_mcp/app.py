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
        return RuntimeContext.from_headers(headers, self.config.runtime_token)

    def discover_tools(self, headers: dict[str, str]) -> dict[str, Any]:
        ctx = self.context_from_headers(headers)
        tools = [tool for tool in discover(ctx) if tool["enabledForRuntime"]]
        return {"serverInfo": self.server_info, "tools": tools, "supportSafe": True}

    def invoke_tool(self, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any]:
        ctx = self.context_from_headers(headers)
        name = str(body.get("tool", ""))
        payload = body.get("input") if isinstance(body.get("input"), dict) else {}
        return {"result": invoke(name, ctx, payload, self.client), "supportSafe": True}


class _Handler(BaseHTTPRequestHandler):
    gateway: WeaveMcpGateway

    def _headers(self) -> dict[str, str]:
        return {key.lower(): value for key, value in self.headers.items()}

    def _send(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
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
