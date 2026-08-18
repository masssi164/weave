#!/usr/bin/env python3
"""Run a disposable, support-safe Synapse/Application Service compatibility probe."""

from __future__ import annotations

import argparse
import hashlib
import http.server
import json
import os
import re
import secrets
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


CONTRACT_VERSION = "synapse-compatibility-probe-v1"
SUPPORTED_TARGETS = frozenset(("1.136.0", "1.156.0"))
EXPECTED_ROOM_VERSION = "10"
FORBIDDEN_OUTPUT_KEYS = frozenset(
    (
        "accessToken",
        "callbackBody",
        "ciphertext",
        "deviceId",
        "eventId",
        "memberContent",
        "providerRoomId",
        "roomId",
        "transactionId",
        "userId",
    )
)


class ProbeError(RuntimeError):
    """Raised when a disposable compatibility invariant is not proven."""


def _canonical_semantic_events(root: dict[str, Any]) -> str:
    events = root.get("events")
    if not isinstance(events, list):
        raise ProbeError("Application Service callback did not contain an event array")

    def normalize(value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: normalize(child)
                for key, child in sorted(value.items())
                if key != "age"
            }
        if isinstance(value, list):
            return [normalize(child) for child in value]
        return value

    canonical = json.dumps(
        normalize(events), sort_keys=True, separators=(",", ":"), ensure_ascii=False
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


@dataclass
class Delivery:
    semantic_hash: str
    event_count: int
    event_types: frozenset[str]
    state_types: frozenset[str]
    successful: bool


@dataclass
class CallbackCapture:
    deliveries: dict[str, list[Delivery]] = field(default_factory=dict)
    condition: threading.Condition = field(default_factory=threading.Condition)
    outage_injected: bool = False

    def record(self, transaction_key: str, root: dict[str, Any]) -> bool:
        events = root.get("events")
        if not isinstance(events, list):
            raise ProbeError("Application Service callback did not contain events")
        event_types = frozenset(
            event.get("type")
            for event in events
            if isinstance(event, dict) and isinstance(event.get("type"), str)
        )
        state_types = frozenset(
            event.get("type")
            for event in events
            if isinstance(event, dict)
            and isinstance(event.get("type"), str)
            and "state_key" in event
        )
        with self.condition:
            attempts = self.deliveries.setdefault(transaction_key, [])
            successful = self.outage_injected
            self.outage_injected = True
            attempts.append(
                Delivery(
                    semantic_hash=_canonical_semantic_events(root),
                    event_count=len(events),
                    event_types=event_types,
                    state_types=state_types,
                    successful=successful,
                )
            )
            self.condition.notify_all()
            return successful

    def wait_for(self, predicate: Callable[["CallbackCapture"], bool], timeout: float) -> None:
        deadline = time.monotonic() + timeout
        with self.condition:
            while not predicate(self):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise ProbeError("Application Service compatibility evidence timed out")
                self.condition.wait(timeout=min(remaining, 1.0))

    def successful_event_types(self) -> frozenset[str]:
        return frozenset(
            event_type
            for attempts in self.deliveries.values()
            for delivery in attempts
            if delivery.successful
            for event_type in delivery.event_types
        )

    def successful_state_types(self) -> frozenset[str]:
        return frozenset(
            event_type
            for attempts in self.deliveries.values()
            for delivery in attempts
            if delivery.successful
            for event_type in delivery.state_types
        )

    def stable_retry_observed(self) -> bool:
        return any(
            len(attempts) >= 2
            and len({attempt.semantic_hash for attempt in attempts}) == 1
            and len({attempt.event_count for attempt in attempts}) == 1
            for attempts in self.deliveries.values()
        )

    def support_safe_diagnostics(self) -> str:
        event_types = self.successful_event_types()
        return ",".join(
            (
                f"deliveryCount={sum(len(value) for value in self.deliveries.values())}",
                f"transactionCount={len(self.deliveries)}",
                "attemptsPerTransaction="
                f"{';'.join(str(value) for value in sorted(len(attempts) for attempts in self.deliveries.values()))}",
                f"successfulDeliveryCount={sum(1 for value in self.deliveries.values() for delivery in value if delivery.successful)}",
                f"retryObserved={str(self.stable_retry_observed()).lower()}",
                "canonicalAliasObserved="
                f"{str('m.room.canonical_alias' in event_types).lower()}",
                "unknownStateObserved="
                f"{str('org.example.future_state' in event_types).lower()}",
                f"messageObserved={str('m.room.message' in event_types).lower()}",
                f"eventTypes={';'.join(sorted(event_types))}",
            )
        )


class _ApplicationServiceHandler(http.server.BaseHTTPRequestHandler):
    capture: CallbackCapture
    hs_token: str

    def _authenticated(self) -> bool:
        authorization = self.headers.get("Authorization", "")
        bearer = authorization.removeprefix("Bearer ") if authorization.startswith("Bearer ") else ""
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        legacy = query.get("access_token", [""])[0]
        return bool(
            (bearer and secrets.compare_digest(bearer, self.hs_token))
            or (legacy and secrets.compare_digest(legacy, self.hs_token))
        )

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if not self._authenticated():
            self._respond(401)
            return
        if "/users/" in self.path or "/rooms/" in self.path:
            self._respond(200)
        else:
            self._respond(404)

    def do_PUT(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if not self._authenticated():
            self._respond(401)
            return
        match = re.search(r"/transactions/([^/?]+)", self.path)
        if match is None:
            self._respond(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > 4 * 1024 * 1024:
            self._respond(400)
            return
        try:
            root = json.loads(self.rfile.read(length))
            if not isinstance(root, dict):
                raise ValueError("callback root is not an object")
            successful = self.capture.record(match.group(1), root)
        except (json.JSONDecodeError, ProbeError, ValueError):
            self._respond(400)
            return
        # The first attempt proves outage containment. The identical retry is
        # then accepted, producing a real homeserver transaction replay.
        self._respond(200 if successful else 503)

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def _respond(self, status: int) -> None:
        body = b"{}"
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)


def _run(*args: str, capture: bool = False, timeout: int = 180) -> str:
    completed = subprocess.run(
        args,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.PIPE if capture else subprocess.DEVNULL,
        timeout=timeout,
    )
    return completed.stdout.strip() if capture else ""


def _request_json(
    method: str,
    url: str,
    *,
    body: dict[str, Any] | None = None,
    token: str | None = None,
    timeout: float = 10,
) -> dict[str, Any]:
    payload = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=payload, method=method)
    request.add_header("Content-Type", "application/json")
    if token is not None:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            value = json.loads(response.read())
    except (urllib.error.URLError, json.JSONDecodeError, OSError) as error:
        raise ProbeError("Synapse compatibility request failed") from error
    if not isinstance(value, dict):
        raise ProbeError("Synapse compatibility response was not an object")
    return value


def _wait_for_synapse(origin: str, timeout: float = 90) -> dict[str, Any]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            return _request_json("GET", f"{origin}/_matrix/client/versions", timeout=3)
        except ProbeError:
            time.sleep(1)
    raise ProbeError("Synapse compatibility target did not become ready")


def _write_private_config(
    data_dir: Path,
    *,
    server_name: str,
    callback_port: int,
    as_token: str,
    hs_token: str,
    registration_secret: str,
) -> None:
    registration = {
        "id": "weave-compatibility-probe",
        "url": f"http://host.docker.internal:{callback_port}",
        "as_token": as_token,
        "hs_token": hs_token,
        "sender_localpart": "_weave_compatibility",
        "rate_limited": False,
        "receive_ephemeral": False,
        "namespaces": {
            "users": [
                {
                    "exclusive": True,
                    "regex": rf"@_weave_.*:{re.escape(server_name)}",
                }
            ],
            "aliases": [
                {
                    "exclusive": True,
                    "regex": rf"#_weave_.*:{re.escape(server_name)}",
                }
            ],
            "rooms": [],
        },
    }
    registration_path = data_dir / "weave-compatibility-appservice.yaml"
    registration_path.write_text(json.dumps(registration, indent=2) + "\n", encoding="utf-8")
    os.chmod(registration_path, 0o600)

    homeserver_path = data_dir / "homeserver.yaml"
    homeserver = homeserver_path.read_text(encoding="utf-8")
    replacement = f'registration_shared_secret: "{registration_secret}"'
    if re.search(r"(?m)^registration_shared_secret:.*$", homeserver):
        homeserver = re.sub(
            r"(?m)^registration_shared_secret:.*$", replacement, homeserver
        )
    else:
        homeserver += "\n" + replacement + "\n"
    homeserver += (
        "\napp_service_config_files:\n"
        "  - /data/weave-compatibility-appservice.yaml\n"
    )
    homeserver_path.write_text(homeserver, encoding="utf-8")


def _container_host_port(container_name: str) -> int:
    value = _run("docker", "port", container_name, "8008/tcp", capture=True)
    match = re.search(r":([0-9]+)$", value.splitlines()[0] if value else "")
    if match is None:
        raise ProbeError("Synapse compatibility port was not published")
    return int(match.group(1))


def _login_admin(origin: str, username: str, password: str) -> str:
    response = _request_json(
        "POST",
        f"{origin}/_matrix/client/v3/login",
        body={
            "type": "m.login.password",
            "identifier": {"type": "m.id.user", "user": username},
            "password": password,
        },
    )
    token = response.get("access_token")
    if not isinstance(token, str) or not token:
        raise ProbeError("Synapse compatibility admin login did not return a token")
    return token


def _support_safe_result(
    *, target_version: str, reported_version: str, room_version: str, capture: CallbackCapture
) -> dict[str, Any]:
    event_types = capture.successful_event_types()
    state_types = capture.successful_state_types()
    result: dict[str, Any] = {
        "contractVersion": CONTRACT_VERSION,
        "targetVersion": target_version,
        "reportedVersion": reported_version,
        "matrixRoomVersion": room_version,
        "applicationServiceRegistrationProfile":
            "exclusive-user-alias-namespaces-rooms-empty-receive-ephemeral-false-v1",
        "transactionRetryObserved": capture.stable_retry_observed(),
        "sameTransactionSemanticSet": capture.stable_retry_observed(),
        "canonicalAliasStateObserved": "m.room.canonical_alias" in state_types,
        "stateKeyPresenceObserved": bool(state_types),
        "unknownValidStateObserved": "org.example.future_state" in state_types,
        "plaintextInEncryptedRoomObserved": "m.room.message" in event_types,
        "supportSafe": True,
        "observedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    required_true = (
        "transactionRetryObserved",
        "sameTransactionSemanticSet",
        "canonicalAliasStateObserved",
        "stateKeyPresenceObserved",
        "unknownValidStateObserved",
        "plaintextInEncryptedRoomObserved",
    )
    result["status"] = (
        "passed"
        if target_version == reported_version
        and room_version == EXPECTED_ROOM_VERSION
        and all(result[key] is True for key in required_true)
        else "failed"
    )
    signature_material = json.dumps(
        {key: value for key, value in result.items() if key != "observedAt"},
        sort_keys=True,
        separators=(",", ":"),
    )
    result["signatureSha256"] = hashlib.sha256(
        signature_material.encode("utf-8")
    ).hexdigest()
    _assert_support_safe(result)
    return result


def _normalized_synapse_version(value: Any) -> str:
    match = re.match(r"^(\d+\.\d+\.\d+)", str(value).strip())
    return match.group(1) if match else ""


def _support_safe_failure(target_version: str, stable_code: str) -> dict[str, Any]:
    result: dict[str, Any] = {
        "contractVersion": CONTRACT_VERSION,
        "targetVersion": target_version,
        "status": "failed",
        "stableCode": stable_code,
        "supportSafe": True,
        "observedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    signature_material = json.dumps(
        {key: value for key, value in result.items() if key != "observedAt"},
        sort_keys=True,
        separators=(",", ":"),
    )
    result["signatureSha256"] = hashlib.sha256(
        signature_material.encode("utf-8")
    ).hexdigest()
    _assert_support_safe(result)
    return result


def _assert_support_safe(value: Any) -> None:
    if isinstance(value, dict):
        forbidden = FORBIDDEN_OUTPUT_KEYS.intersection(value)
        if forbidden:
            raise ProbeError("Compatibility evidence contains forbidden fields")
        for child in value.values():
            _assert_support_safe(child)
    elif isinstance(value, list):
        for child in value:
            _assert_support_safe(child)


def probe(target_version: str, callback_timeout: float = 120) -> dict[str, Any]:
    if target_version not in SUPPORTED_TARGETS:
        raise ProbeError("Unsupported Synapse compatibility target")
    image = f"matrixdotorg/synapse:v{target_version}"
    suffix = secrets.token_hex(6)
    server_name = f"compat-{target_version.replace('.', '-')}-{suffix}.invalid"
    container_name = f"weave-synapse-compat-{target_version.replace('.', '-')}-{suffix}"
    as_token = secrets.token_urlsafe(32)
    hs_token = secrets.token_urlsafe(32)
    registration_secret = secrets.token_urlsafe(32)
    admin_password = secrets.token_urlsafe(24)
    capture = CallbackCapture()
    handler = type(
        "ApplicationServiceHandler",
        (_ApplicationServiceHandler,),
        {"capture": capture, "hs_token": hs_token},
    )
    # Synapse runs in a disposable container and reaches this authenticated
    # callback via host.docker.internal. The random homeserver token prevents
    # other local/LAN callers from injecting compatibility evidence.
    server = http.server.ThreadingHTTPServer(("0.0.0.0", 0), handler)
    callback_port = int(server.server_address[1])
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()

    try:
        with tempfile.TemporaryDirectory(prefix="weave-synapse-compat-") as temporary:
            data_dir = Path(temporary)
            os.chmod(data_dir, 0o777)
            _run(
                "docker",
                "run",
                "--rm",
                "--env",
                f"SYNAPSE_SERVER_NAME={server_name}",
                "--env",
                "SYNAPSE_REPORT_STATS=no",
                "--volume",
                f"{data_dir}:/data",
                image,
                "generate",
                timeout=300,
            )
            _write_private_config(
                data_dir,
                server_name=server_name,
                callback_port=callback_port,
                as_token=as_token,
                hs_token=hs_token,
                registration_secret=registration_secret,
            )
            _run(
                "docker",
                "run",
                "--rm",
                "--detach",
                "--name",
                container_name,
                "--add-host",
                "host.docker.internal:host-gateway",
                "--publish",
                "127.0.0.1::8008",
                "--volume",
                f"{data_dir}:/data",
                image,
            )
            origin = f"http://127.0.0.1:{_container_host_port(container_name)}"
            versions = _wait_for_synapse(origin)
            _run(
                "docker",
                "exec",
                container_name,
                "register_new_matrix_user",
                "--config",
                "/data/homeserver.yaml",
                "--user",
                "compatibility-admin",
                "--password",
                admin_password,
                "--admin",
                "http://127.0.0.1:8008",
            )
            admin_token = _login_admin(origin, "compatibility-admin", admin_password)
            virtual_user = f"@_weave_compatibility:{server_name}"
            user_query = urllib.parse.urlencode({"user_id": virtual_user})
            room = _request_json(
                "POST",
                f"{origin}/_matrix/client/v3/createRoom?{user_query}",
                token=as_token,
                body={
                    "visibility": "private",
                    "preset": "private_chat",
                    "room_alias_name": "_weave_compatibility",
                    "name": "Compatibility fixture",
                    "initial_state": [
                        {
                            "type": "m.room.encryption",
                            "state_key": "",
                            "content": {"algorithm": "m.megolm.v1.aes-sha2"},
                        }
                    ],
                },
            )
            room_id = room.get("room_id")
            if not isinstance(room_id, str) or not room_id:
                raise ProbeError("Synapse compatibility room creation failed")
            encoded_room = urllib.parse.quote(room_id, safe="")
            _request_json(
                "PUT",
                f"{origin}/_matrix/client/v3/rooms/{encoded_room}/state/"
                f"org.example.future_state/compatibility?{user_query}",
                token=as_token,
                body={"enabled": True},
            )
            _request_json(
                "PUT",
                f"{origin}/_matrix/client/v3/rooms/{encoded_room}/send/"
                f"m.room.message/compatibility?{user_query}",
                token=as_token,
                body={"msgtype": "m.text", "body": "private compatibility fixture"},
            )
            required_types = frozenset(
                ("m.room.canonical_alias", "org.example.future_state", "m.room.message")
            )
            try:
                capture.wait_for(
                    lambda current: required_types.issubset(
                        current.successful_event_types()
                    )
                    and current.stable_retry_observed(),
                    timeout=callback_timeout,
                )
            except ProbeError as error:
                raise ProbeError(
                    "Application Service evidence timed out ("
                    f"{capture.support_safe_diagnostics()})"
                ) from error
            create_state = _request_json(
                "GET",
                f"{origin}/_matrix/client/v3/rooms/{encoded_room}/state/"
                f"m.room.create?{user_query}",
                token=as_token,
            )
            room_version = str(create_state.get("room_version", ""))
            server_metadata = versions.get("server")
            reported = _normalized_synapse_version(
                server_metadata.get("version", "")
                if isinstance(server_metadata, dict)
                else ""
            )
            if not reported:
                # Client versions does not require a server-version field. The
                # target is still verified from Synapse's authenticated admin
                # endpoint below when available.
                server_version = _request_json(
                    "GET", f"{origin}/_synapse/admin/v1/server_version", token=admin_token
                )
                reported = _normalized_synapse_version(
                    server_version.get("server_version", "")
                )
            return _support_safe_result(
                target_version=target_version,
                reported_version=reported,
                room_version=room_version,
                capture=capture,
            )
    finally:
        subprocess.run(
            ("docker", "stop", "--time", "5", container_name),
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
        )
        server.shutdown()
        server.server_close()
        server_thread.join(timeout=5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=sorted(SUPPORTED_TARGETS), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    exit_code = 0
    try:
        result = probe(args.target)
        exit_code = 0 if result["status"] == "passed" else 1
    except ProbeError:
        result = _support_safe_failure(
            args.target, "COMPATIBILITY_INVARIANT_UNPROVEN"
        )
        exit_code = 1
    except subprocess.CalledProcessError:
        result = _support_safe_failure(args.target, "PROVIDER_PROCESS_FAILED")
        exit_code = 1
    except subprocess.TimeoutExpired:
        result = _support_safe_failure(args.target, "PROVIDER_PROCESS_TIMEOUT")
        exit_code = 1
    except OSError:
        result = _support_safe_failure(args.target, "LOCAL_PROBE_RUNTIME_ERROR")
        exit_code = 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(
        f"SYNAPSE_COMPATIBILITY_RESULT target={args.target} "
        f"status={result['status']} supportSafe=true",
        flush=True,
    )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
