#!/usr/bin/env python3
"""Run the fenced Keycloak sanitizer and its loopback-only control endpoint."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import signal
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from admin_sanitizer import SanitizerDenied, SanitizerPolicy, serve
from lease_control import DatabaseLeaseVerifier, LeaseError


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _assert_profile(path: Path, expected_revision: str) -> None:
    value = json.loads(path.read_text(encoding="utf-8"))
    projection = dict(value)
    declared = projection.pop("revision", None)
    observed = "sha256:" + hashlib.sha256(_canonical(projection)).hexdigest()
    if declared != expected_revision or observed != expected_revision:
        raise LeaseError("sanitizer profile does not match the rendered, corpus-pinned revision")


def control_handler(
    policy: SanitizerPolicy,
    verifier: DatabaseLeaseVerifier,
    upstream_host: str,
    upstream_port: int,
) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, _format: str, *_args: object) -> None:
            return

        def _reply(self, status: int, value: object | None = None) -> None:
            payload = b"" if value is None else _canonical(value)
            self.send_response(status)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            if payload:
                self.wfile.write(payload)

        def do_POST(self) -> None:  # noqa: N802
            try:
                if self.path == "/probe-expired-token":
                    if int(self.headers.get("Content-Length", "0")) != 0:
                        raise SanitizerDenied("control-body-size")
                    verifier()
                    self._reply(200, policy.probe_expired_token(upstream_host, upstream_port))
                    return
                if self.path != "/register":
                    raise SanitizerDenied("control-path")
                length = int(self.headers.get("Content-Length", "0"))
                if length < 2 or length > 4096:
                    raise SanitizerDenied("control-body-size")
                value = json.loads(self.rfile.read(length))
                if not isinstance(value, dict) or set(value) != {
                    "method", "target", "bodyDigest", "binding"
                }:
                    raise SanitizerDenied("control-body-shape")
                binding = value["binding"]
                if not isinstance(binding, dict):
                    raise SanitizerDenied("control-binding-shape")
                verifier()
                key = policy.register_request(
                    str(value["method"]),
                    str(value["target"]),
                    str(value["bodyDigest"]),
                    binding,
                )
                self._reply(200, {"registeredRequestDigest": key})
            except (SanitizerDenied, LeaseError, ValueError, json.JSONDecodeError):
                self._reply(403)

        def do_GET(self) -> None:  # noqa: N802
            try:
                if self.path != "/summary":
                    raise SanitizerDenied("control-path")
                verifier()
                self._reply(200, policy.summary())
            except (SanitizerDenied, LeaseError):
                self._reply(403)

    return Handler


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile-path", type=Path, required=True)
    parser.add_argument("--profile-revision", required=True)
    parser.add_argument("--mode", choices=("plan", "apply", "verify", "tombstone"), required=True)
    parser.add_argument("--temporary-client-id", required=True)
    parser.add_argument("--proxy-bind", required=True)
    parser.add_argument("--proxy-port", type=int, default=8080)
    parser.add_argument("--control-port", type=int, default=9080)
    parser.add_argument("--upstream-host", default="keycloak")
    parser.add_argument("--upstream-port", type=int, default=8080)
    parser.add_argument("--database-host", default="postgres")
    parser.add_argument("--database-port", type=int, default=5432)
    parser.add_argument("--database-name", default="postgres")
    parser.add_argument("--database-user", required=True)
    parser.add_argument("--database-password-file", type=Path, required=True)
    parser.add_argument("--secret-root", type=Path, required=True)
    parser.add_argument("--lock-key", required=True)
    parser.add_argument("--lease-id", required=True)
    parser.add_argument("--reconciliation-id", required=True)
    parser.add_argument("--fencing-token", type=int, required=True)
    args = parser.parse_args()
    try:
        _assert_profile(args.profile_path, args.profile_revision)
        verifier = DatabaseLeaseVerifier(
            host=args.database_host,
            port=args.database_port,
            database=args.database_name,
            username=args.database_user,
            password_file=args.database_password_file,
            lock_key=args.lock_key,
            lease_id=args.lease_id,
            reconciliation_id=args.reconciliation_id,
            fencing_token=args.fencing_token,
        )
        verifier()
        proxy, policy = serve(
            args.profile_path,
            args.mode,
            args.temporary_client_id,
            args.upstream_host,
            args.upstream_port,
            verifier,
            args.proxy_bind,
            args.secret_root,
            args.proxy_port,
        )
        control = ThreadingHTTPServer(
            ("127.0.0.1", args.control_port),
            control_handler(policy, verifier, args.upstream_host, args.upstream_port),
        )
        stopped = threading.Event()

        def stop(_signal: int, _frame: object) -> None:
            if not stopped.is_set():
                stopped.set()
                threading.Thread(target=proxy.shutdown, daemon=True).start()
                threading.Thread(target=control.shutdown, daemon=True).start()

        signal.signal(signal.SIGTERM, stop)
        signal.signal(signal.SIGINT, stop)
        proxy_thread = threading.Thread(target=proxy.serve_forever, daemon=True)
        proxy_thread.start()
        control.serve_forever()
        proxy.shutdown()
        proxy.server_close()
        control.server_close()
        proxy_thread.join(timeout=5)
        return 0
    except (LeaseError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"WEAVE_KEYCLOAK_SANITIZER_ERROR {error}", file=os.sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
