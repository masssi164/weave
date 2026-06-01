from __future__ import annotations

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class WeaveMcpConfig:
    enabled: bool = False
    transport: str = "streamable-http"
    backend_base_url: str = "http://weave-backend.internal/api"
    runtime_token: str = "dev-runtime-token"
    server_key: str = "weave-domain-tools"
    internal_endpoint_ref: str = "internal://weave-mcp/streamable-http"

    @staticmethod
    def from_env() -> "WeaveMcpConfig":
        return WeaveMcpConfig(
            enabled=os.environ.get("WEAVE_MCP_ENABLED", "false").lower() == "true",
            transport=os.environ.get("WEAVE_MCP_TRANSPORT", "streamable-http"),
            backend_base_url=os.environ.get("WEAVE_BACKEND_BASE_URL", "http://weave-backend.internal/api"),
            runtime_token=os.environ.get("WEAVE_MCP_RUNTIME_TOKEN", "dev-runtime-token"),
            server_key=os.environ.get("WEAVE_MCP_SERVER_KEY", "weave-domain-tools"),
            internal_endpoint_ref=os.environ.get("WEAVE_MCP_ENDPOINT_REF", "internal://weave-mcp/streamable-http"),
        )

    def validate(self) -> None:
        if self.transport != "streamable-http":
            raise ValueError("Weave MCP runtime transport must be streamable-http")
        if self.backend_base_url.startswith(("http://", "https://")) is False:
            raise ValueError("backend_base_url must be an HTTP(S) URL")
