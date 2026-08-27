from __future__ import annotations

import hmac
import json
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

TOKEN_FILE = Path(os.environ.get("INTERNAL_API_TOKEN_FILE", "/run/secrets/internal_api_token"))
TOKEN = TOKEN_FILE.read_text(encoding="utf-8").strip()
if not TOKEN:
    raise RuntimeError("internal API token is empty")

ASSETS = {
    "home-core": {
        "assetId": "home-core",
        "kind": "platform",
        "status": "available",
        "displayName": "Home Core",
        "relatedServices": ["nextcloud", "tuwunel"],
    }
}

TOPOLOGY = {
    "scope": "private-e2e",
    "entities": [
        {
            "localKey": "system:home-core",
            "kind": "system",
            "displayName": "Home Core",
            "aliases": ["home-core"],
            "attributes": {"environment": "e2e"},
            "evidence": [
                {
                    "kind": "RUNTIME",
                    "reference": "urn:weave:e2e:internal-api:topology",
                }
            ],
        },
        {
            "localKey": "service:nextcloud",
            "kind": "service",
            "displayName": "Nextcloud",
            "attributes": {"status": "available"},
            "evidence": [
                {
                    "kind": "RUNTIME",
                    "reference": "urn:weave:e2e:internal-api:nextcloud",
                }
            ],
        },
        {
            "localKey": "service:tuwunel",
            "kind": "service",
            "displayName": "Tuwunel",
            "attributes": {"status": "available"},
            "evidence": [
                {
                    "kind": "RUNTIME",
                    "reference": "urn:weave:e2e:internal-api:tuwunel",
                }
            ],
        },
    ],
    "relations": [
        {
            "fromLocalKey": "system:home-core",
            "predicate": "contains",
            "toLocalKey": "service:nextcloud",
            "confidence": 1.0,
            "evidence": [
                {
                    "kind": "RUNTIME",
                    "reference": "urn:weave:e2e:internal-api:topology",
                }
            ],
        },
        {
            "fromLocalKey": "system:home-core",
            "predicate": "contains",
            "toLocalKey": "service:tuwunel",
            "confidence": 1.0,
            "evidence": [
                {
                    "kind": "RUNTIME",
                    "reference": "urn:weave:e2e:internal-api:topology",
                }
            ],
        },
    ],
}


class Handler(BaseHTTPRequestHandler):
    server_version = "WeavePrivateFixture/1"

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/health":
            self._send_json(HTTPStatus.OK, {"status": "UP"})
            return
        if not self._authorized():
            self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return
        if path.startswith("/v1/assets/"):
            asset_id = unquote(path.removeprefix("/v1/assets/"))
            asset = ASSETS.get(asset_id)
            if asset is None:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
                return
            self._send_json(HTTPStatus.OK, asset)
            return
        if path == "/v1/topology":
            self._send_json(HTTPStatus.OK, TOPOLOGY)
            return
        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def _authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        prefix = "Bearer "
        if not header.startswith(prefix):
            return False
        return hmac.compare_digest(header[len(prefix) :], TOKEN)

    def _send_json(self, status: HTTPStatus, value: object) -> None:
        body = json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
