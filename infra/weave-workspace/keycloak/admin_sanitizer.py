#!/usr/bin/env python3
"""Closed, request-bound Keycloak Admin REST sanitizer.

The sanitizer is a trusted supervisor component. The untrusted/disposable kcadm
container can reach this listener, but cannot reach Keycloak or the host. Each
non-credential request must first be registered by the run-bound supervisor as
an exact method, path, query-value and request-body-digest tuple.
"""

from __future__ import annotations

import collections
import hashlib
import http.client
import json
import re
import stat
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable
from urllib.parse import parse_qsl, unquote, urlsplit


class SanitizerDenied(RuntimeError):
    pass


SECRET_REFS = {
    "secretref:keycloak/weave-backend-jwk": "keycloak-weave-backend-jwk.json",
    "secretref:keycloak/weave-mcp-server-jwk": "keycloak-weave-mcp-server-jwk.json",
    "secretref:keycloak/weave-identity-admin": "keycloak-weave-identity-admin",
    "secretref:keycloak/weave-agent-runtime-admin": "keycloak-weave-agent-runtime-admin",
    "secretref:keycloak/nextcloud": "keycloak-nextcloud",
    "secretref:keycloak/matrix-mas": "keycloak-matrix-mas",
    "secretref:smtp/username": "smtp-username",
    "secretref:smtp/password": "smtp-password",
}
PRIVATE_JWK_FIELDS = frozenset({"d", "p", "q", "dp", "dq", "qi", "oth", "key_ops"})


class SecretResolver:
    """Resolve only corpus-declared SecretRefs inside the trusted memory boundary."""

    def __init__(self, root: Path) -> None:
        if root.is_symlink() or not root.is_dir():
            raise SanitizerDenied("secret-root")
        self.root = root
        self.resolution_count = 0

    def _read(self, reference: str) -> str:
        name = SECRET_REFS.get(reference)
        if name is None:
            raise SanitizerDenied("unknown-secret-ref")
        path = self.root / name
        if path.is_symlink() or not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o600:
            raise SanitizerDenied("secret-ref-file")
        value = path.read_text(encoding="utf-8").strip()
        if not value:
            raise SanitizerDenied("empty-secret-ref")
        self.resolution_count += 1
        return value

    def _public_jwks(self, reference: str) -> str:
        try:
            private = json.loads(self._read(reference))
        except json.JSONDecodeError as error:
            raise SanitizerDenied("private-jwk-json") from error
        if not isinstance(private, dict) or private.get("kty") != "RSA":
            raise SanitizerDenied("private-jwk-shape")
        public = {key: value for key, value in private.items() if key not in PRIVATE_JWK_FIELDS}
        if not all(isinstance(public.get(name), str) and public[name] for name in ("kid", "kty", "n", "e")):
            raise SanitizerDenied("public-jwk-shape")
        public["use"] = "sig"
        return json.dumps({"keys": [public]}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

    def _resolve(self, value: object) -> object:
        if isinstance(value, str):
            if value.startswith("secretref:"):
                return self._read(value)
            prefix = "public-jwks:"
            if value.startswith(prefix):
                return self._public_jwks(value.removeprefix(prefix))
            return value
        if isinstance(value, list):
            return [self._resolve(item) for item in value]
        if isinstance(value, dict):
            return {key: self._resolve(item) for key, item in value.items()}
        return value

    def resolve_body(self, body: bytes, content_type: str) -> bytes:
        if not body or content_type.split(";", 1)[0].strip().lower() != "application/json":
            return body
        try:
            value = json.loads(body)
        except json.JSONDecodeError as error:
            raise SanitizerDenied("secret-resolution-json") from error
        resolved = self._resolve(value)
        return json.dumps(resolved, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


@dataclass(frozen=True)
class Operation:
    operation_id: str
    projection: str
    body_policy: str
    path_variables: dict[str, str]


def _template_regex(template: str, prefix: bool = False) -> re.Pattern[str]:
    pieces: list[str] = []
    offset = 0
    for match in re.finditer(r"\{([A-Za-z][A-Za-z0-9]*)\}", template):
        pieces.append(re.escape(template[offset : match.start()]))
        pieces.append(f"(?P<{match.group(1)}>[^/]+)")
        offset = match.end()
    pieces.append(re.escape(template[offset:]))
    suffix = r"(?:/.*)?" if prefix else ""
    return re.compile("^" + "".join(pieces) + suffix + "$")


def _pointer_match(template: str, pointer: str) -> bool:
    expected = template.strip("/").split("/") if template != "/" else []
    observed = pointer.strip("/").split("/") if pointer != "/" else []

    def matches(left: int, right: int) -> bool:
        if left == len(expected):
            return right == len(observed)
        token = expected[left]
        if token == "**":
            return matches(left + 1, right) or (
                right < len(observed) and matches(left, right + 1)
            )
        if right >= len(observed):
            return False
        return (token == "*" or token == observed[right]) and matches(left + 1, right + 1)

    return matches(0, 0)


def _flatten(value: object, pointer: str = "") -> list[tuple[str, object]]:
    if isinstance(value, dict):
        if not value:
            return [(pointer or "/", {})]
        result: list[tuple[str, object]] = []
        for key, item in value.items():
            escaped = key.replace("~", "~0").replace("/", "~1")
            result.extend(_flatten(item, f"{pointer}/{escaped}"))
        return result
    if isinstance(value, list):
        if not value:
            return [(pointer or "/", [])]
        result = []
        for index, item in enumerate(value):
            result.extend(_flatten(item, f"{pointer}/{index}"))
        return result
    return [(pointer or "/", value)]


def _pointer_ancestors(pointer: str) -> list[str]:
    if pointer == "/":
        return [pointer]
    tokens = pointer.strip("/").split("/")
    return ["/" + "/".join(tokens[:end]) for end in range(1, len(tokens) + 1)]


def _set_pointer(target: object, pointer: str, value: object) -> object:
    tokens = [] if pointer == "/" else pointer.strip("/").split("/")
    if not tokens:
        return value
    if target is None:
        target = [] if tokens[0].isdigit() else {}
    current = target
    for index, raw in enumerate(tokens):
        token = raw.replace("~1", "/").replace("~0", "~")
        final = index == len(tokens) - 1
        next_is_index = not final and tokens[index + 1].isdigit()
        if isinstance(current, list):
            position = int(token)
            while len(current) <= position:
                current.append(None)
            if final:
                current[position] = value
            else:
                if current[position] is None:
                    current[position] = [] if next_is_index else {}
                current = current[position]
        elif isinstance(current, dict):
            if final:
                current[token] = value
            else:
                current = current.setdefault(token, [] if next_is_index else {})
        else:
            raise SanitizerDenied("projection encountered a scalar parent")
    return target


def _body_digest(body: bytes) -> str:
    return "none" if not body else "sha256:" + hashlib.sha256(body).hexdigest()


def _request_key(method: str, raw_target: str, body_digest: str) -> str:
    parsed = urlsplit(raw_target)
    query = sorted(
        parse_qsl(parsed.query, keep_blank_values=True, strict_parsing=True)
        if parsed.query
        else []
    )
    value = [method, parsed.path, query, body_digest]
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return "sha256:" + hashlib.sha256(encoded).hexdigest()


class SanitizerPolicy:
    def __init__(
        self,
        profile: dict[str, object],
        mode: str,
        temporary_client_id: str,
    ) -> None:
        self.profile = profile
        self.mode = mode
        self.temporary_client_id = temporary_client_id
        self.maximum_body = int(profile["bodyHandling"]["maximumBodyBytes"])
        self.projections = {item["projectionId"]: item for item in profile["projections"]}
        classes = profile["executionPolicy"]["operationClasses"]
        self.operation_classes = {
            operation: class_name for class_name, operations in classes.items() for operation in operations
        }
        self.allowed = profile["allowedOperations"]
        self.forbidden = profile["forbiddenOperations"]
        self.mandatory_drop = profile["mandatoryDropPointerTemplates"]
        self.lock = threading.Lock()
        self.authorized_requests: dict[str, collections.deque[dict[str, str]]] = {}
        self.audit: list[dict[str, object]] = []
        self.forbidden_attempts = 0
        self.dropped_fields = 0
        self.unknown_fields = 0
        self._last_access_token: str | None = None
        self._last_access_token_expires_at: float | None = None
        self._expired_token_rejected = False

    def register_request(
        self,
        method: str,
        target: str,
        body_digest: str,
        binding: dict[str, str] | None = None,
    ) -> str:
        if method not in {"GET", "POST", "PUT", "DELETE"}:
            raise SanitizerDenied("registration-method")
        if body_digest != "none" and not re.fullmatch(r"sha256:[0-9a-f]{64}", body_digest):
            raise SanitizerDenied("registration-body-digest")
        operation, _ = self._match_profile(method, target)
        if operation.operation_id == "master-token":
            raise SanitizerDenied("credential-request-cannot-be-preauthorized")
        selected_binding = binding or {}
        if any(not isinstance(key, str) or not isinstance(value, str) for key, value in selected_binding.items()):
            raise SanitizerDenied("registration-binding-shape")
        key = _request_key(method, target, body_digest)
        with self.lock:
            self.authorized_requests.setdefault(key, collections.deque()).append(dict(selected_binding))
        return key

    def resolve(self, method: str, raw_target: str, body: bytes, content_type: str) -> Operation:
        operation, parsed_query = self._match_profile(method, raw_target)
        self._validate_body(operation.body_policy, body, content_type)
        if operation.operation_id == "master-token":
            pairs = dict(parse_qsl(body.decode("utf-8"), keep_blank_values=True, strict_parsing=True))
            if (
                pairs.get("client_id") != self.temporary_client_id
                or pairs.get("grant_type") != "client_credentials"
                or not pairs.get("client_secret")
            ):
                raise SanitizerDenied("bootstrap-client-binding")
            return operation
        key = _request_key(method, raw_target, _body_digest(body))
        with self.lock:
            bindings = self.authorized_requests.get(key)
            if not bindings:
                raise SanitizerDenied("request-not-bound-by-supervisor")
            binding = bindings.popleft()
            if not bindings:
                self.authorized_requests.pop(key, None)
        self._validate_semantic_binding(operation, binding, body)
        del parsed_query
        return operation

    def _validate_semantic_binding(
        self, operation: Operation, binding: dict[str, str], body: bytes
    ) -> None:
        if operation.operation_id not in {"groups", "group-children"} or not body:
            return
        try:
            value = json.loads(body)
        except json.JSONDecodeError as error:
            raise SanitizerDenied("group-body-json") from error
        resource_key = binding.get("resourceKey", "")
        parent_key = binding.get("parentResourceKey", "")
        if not re.fullmatch(r"group:[a-z0-9-]+", resource_key) or set(value) != {"name"}:
            raise SanitizerDenied("group-desired-state-binding")
        name = value.get("name")
        if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._ -]{1,255}", name):
            raise SanitizerDenied("group-name")
        if operation.operation_id == "groups":
            if parent_key:
                raise SanitizerDenied("top-level-group-parent")
            if operation.path_variables:
                raise SanitizerDenied("top-level-group-path")
        else:
            if not re.fullmatch(r"group:[a-z0-9-]+", parent_key):
                raise SanitizerDenied("child-group-parent")
            provider_parent = operation.path_variables.get("groupUuid", "")
            if not re.fullmatch(r"[0-9a-fA-F-]{36}", provider_parent):
                raise SanitizerDenied("child-group-parent-provider-id")

    def _match_profile(self, method: str, raw_target: str) -> tuple[Operation, list[tuple[str, str]]]:
        parsed = urlsplit(raw_target)
        if parsed.fragment or "//" in parsed.path or "\\" in parsed.path:
            raise SanitizerDenied("path-normalization")
        lowered = parsed.path.lower()
        if "%2f" in lowered or "%5c" in lowered or unquote(parsed.path) != parsed.path:
            raise SanitizerDenied("encoded-or-multiple-decode-path")
        if any(segment in (".", "..") for segment in parsed.path.split("/")):
            raise SanitizerDenied("dot-segment")
        query = (
            parse_qsl(parsed.query, keep_blank_values=True, strict_parsing=True)
            if parsed.query
            else []
        )
        names = [name for name, _ in query]
        if len(names) != len(set(names)):
            raise SanitizerDenied("duplicate-query-parameter")
        for item in self.forbidden:
            regex = _template_regex(item["pathTemplate"], item["match"] == "prefix")
            if regex.fullmatch(parsed.path) and ("*" in item["methods"] or method in item["methods"]):
                self.forbidden_attempts += 1
                raise SanitizerDenied(str(item["reasonCode"]))
        for item in self.allowed:
            match = _template_regex(item["pathTemplate"]).fullmatch(parsed.path)
            if not match or method not in item["methods"]:
                continue
            operation_id = str(item["operationId"])
            if not set(names).issubset(set(item["queryAllowlist"])):
                raise SanitizerDenied("unlisted-query-parameter")
            self._validate_query_binding(operation_id, method, query)
            operation_class = self.operation_classes[operation_id]
            allowed_methods = self.profile["executionPolicy"][operation_class][self.mode]
            if method not in allowed_methods:
                raise SanitizerDenied("mode-method-matrix")
            method_policy = item["methods"][method]
            return (
                Operation(
                    operation_id,
                    str(method_policy["responseProjectionRef"]),
                    str(method_policy["requestBodyPolicy"]),
                    match.groupdict(),
                ),
                query,
            )
        raise SanitizerDenied("operation-not-allowlisted")

    def _validate_query_binding(
        self, operation_id: str, method: str, query: list[tuple[str, str]]
    ) -> None:
        binding = self.profile["bindingSemantics"]["operationQueryBindings"].get(operation_id)
        expected = {} if method != "GET" or binding is None else binding["requiredExactQuery"]
        observed = dict(query)
        if set(observed) != set(expected):
            raise SanitizerDenied("query-binding-shape")
        for name, required in expected.items():
            value = observed[name]
            if required == "$page.first":
                if not value.isdigit() or int(value) % 100 != 0:
                    raise SanitizerDenied("query-page-offset")
            elif required == "$currentAuthority.clientId":
                if value != self.temporary_client_id:
                    raise SanitizerDenied("query-current-authority")
            elif value != required:
                raise SanitizerDenied("query-binding-value")

    def _validate_body(self, policy: str, body: bytes, content_type: str) -> None:
        if len(body) > self.maximum_body:
            raise SanitizerDenied("body-too-large")
        if policy == "none":
            if body:
                raise SanitizerDenied("body-forbidden")
            return
        media_type = content_type.split(";", 1)[0].strip().lower()
        if media_type not in self.profile["bodyHandling"]["allowedContentTypes"]:
            raise SanitizerDenied("content-type")
        if policy == "bootstrap-secret-form":
            if media_type != "application/x-www-form-urlencoded":
                raise SanitizerDenied("bootstrap-content-type")
            pairs = parse_qsl(body.decode("utf-8"), keep_blank_values=True, strict_parsing=True)
            names = [name for name, _ in pairs]
            if len(names) != len(set(names)) or set(names) != {"client_id", "client_secret", "grant_type"}:
                raise SanitizerDenied("bootstrap-form-shape")
        elif policy != "desired-state-only":
            raise SanitizerDenied("unknown-body-policy")
        else:
            try:
                value = json.loads(body)
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise SanitizerDenied("desired-state-body-json") from error
            if not isinstance(value, (dict, list)) or body != json.dumps(
                value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
            ).encode("utf-8"):
                raise SanitizerDenied("desired-state-body-canonical")

    def project(self, operation: Operation, _status: int, _headers: dict[str, str], body: bytes) -> bytes:
        if operation.projection == "status-only" or not body:
            return b""
        value = json.loads(body)
        if operation.projection == "private-token":
            if not isinstance(value, dict):
                raise SanitizerDenied("private-token-shape")
            token = value.get("access_token")
            expires_in = value.get("expires_in")
            if (
                not isinstance(token, str)
                or not token
                or not isinstance(expires_in, int)
                or not 1 <= expires_in <= 900
            ):
                raise SanitizerDenied("private-token-lifetime")
            with self.lock:
                self._last_access_token = token
                self._last_access_token_expires_at = time.time() + expires_in
                self._expired_token_rejected = False
        projection = self.projections[operation.projection]
        retained = projection["retainedPointerTemplates"]
        dropped = projection["droppedPointerTemplates"]
        result: object = None
        dropped_count = 0
        unknown_count = 0
        for pointer, item in _flatten(value):
            mandatory = any(_pointer_match(template, pointer) for template in self.mandatory_drop)
            explicitly_dropped = any(_pointer_match(template, pointer) for template in dropped)
            allowed = any(
                _pointer_match(template, ancestor)
                for template in retained
                for ancestor in _pointer_ancestors(pointer)
            )
            if mandatory or explicitly_dropped:
                dropped_count += 1
                continue
            if not allowed:
                unknown_count += 1
                continue
            result = _set_pointer(result, pointer, item)
        with self.lock:
            self.dropped_fields += dropped_count
            self.unknown_fields += unknown_count
        if result is None:
            result = [] if isinstance(value, list) else {}
        return json.dumps(result, sort_keys=True, separators=(",", ":")).encode("utf-8")

    def probe_expired_token(self, upstream_host: str, upstream_port: int) -> dict[str, object]:
        """Prove that the final issued token is rejected after its expiry.

        The bearer remains inside this sanitizer process and is cleared as
        soon as the 401 proof succeeds.  Neither the control response nor the
        summary includes the assertion.
        """

        with self.lock:
            token = self._last_access_token
            expires_at = self._last_access_token_expires_at
            already_rejected = self._expired_token_rejected
        if already_rejected:
            return {"status": "expired-token-rejected", "httpStatus": 401}
        if token is None or expires_at is None:
            raise SanitizerDenied("no-private-token-for-expiry-proof")
        remaining = expires_at - time.time()
        if remaining > 0:
            return {"status": "pending-expiry", "waitSeconds": min(5, max(1, int(remaining) + 1))}
        connection = http.client.HTTPConnection(upstream_host, upstream_port, timeout=15)
        try:
            connection.request(
                "GET",
                "/admin/realms/weave",
                headers={
                    "Authorization": "Bearer " + token,
                    "Accept": "application/json",
                    "Cache-Control": "no-store",
                },
            )
            response = connection.getresponse()
            response.read(self.maximum_body + 1)
            if response.status != 401:
                raise SanitizerDenied("expired-token-not-rejected")
        finally:
            connection.close()
        with self.lock:
            self._last_access_token = None
            self._expired_token_rejected = True
        return {"status": "expired-token-rejected", "httpStatus": 401}

    def record(self, operation: Operation, method: str, status: int, request_bytes: int, response_bytes: int) -> None:
        with self.lock:
            self.audit.append(
                {
                    "operationId": operation.operation_id,
                    "method": method,
                    "profile": self.mode,
                    "status": status,
                    "requestBytes": request_bytes,
                    "projectedResponseBytes": response_bytes,
                }
            )

    def summary(self) -> dict[str, object]:
        with self.lock:
            audit = list(self.audit)
            pending = sum(len(values) for values in self.authorized_requests.values())
            forbidden_attempts = self.forbidden_attempts
            dropped_fields = self.dropped_fields
            unknown_fields = self.unknown_fields
            expires_at = self._last_access_token_expires_at
            expired_token_rejected = self._expired_token_rejected
        encoded = json.dumps(audit, sort_keys=True, separators=(",", ":")).encode("utf-8")
        summary: dict[str, object] = {
            "operationCount": len(audit),
            "responseCount": len(audit),
            "forbiddenOperationAttempts": forbidden_attempts,
            "secretEndpointCalls": 0,
            "droppedFieldCount": dropped_fields,
            "unknownFieldCount": unknown_fields,
            "pendingAuthorizedRequestCount": pending,
            "operationAuditDigest": "sha256:" + hashlib.sha256(encoded).hexdigest(),
            "rawRequestBodyBytesPersisted": 0,
            "rawResponseBodyBytesPersisted": 0,
            "stdoutBodyBytes": 0,
            "stderrBodyBytes": 0,
            "expiredTokenRejected": expired_token_rejected,
        }
        if expires_at is not None:
            summary["lastAccessTokenExpiresAt"] = datetime.fromtimestamp(
                expires_at, timezone.utc
            ).isoformat().replace("+00:00", "Z")
        return summary


def handler_factory(
    policy: SanitizerPolicy,
    resolver: SecretResolver,
    upstream_host: str,
    upstream_port: int,
    assert_lease: Callable[[], None],
) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, _format: str, *_args: object) -> None:
            return

        def do_GET(self) -> None:  # noqa: N802
            self._handle("GET")

        def do_POST(self) -> None:  # noqa: N802
            self._handle("POST")

        def do_PUT(self) -> None:  # noqa: N802
            self._handle("PUT")

        def do_DELETE(self) -> None:  # noqa: N802
            self._handle("DELETE")

        def _deny(self, status: int = 403) -> None:
            self.send_response(status)
            self.send_header("Content-Length", "0")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()

        def _handle(self, method: str) -> None:
            connection: http.client.HTTPConnection | None = None
            try:
                assert_lease()
                length = int(self.headers.get("Content-Length", "0"))
                if length < 0 or length > policy.maximum_body:
                    raise SanitizerDenied("body-size")
                body = self.rfile.read(length) if length else b""
                operation = policy.resolve(method, self.path, body, self.headers.get("Content-Type", ""))
                connection = http.client.HTTPConnection(upstream_host, upstream_port, timeout=15)
                forwarded_headers = {"Accept": "application/json", "Cache-Control": "no-store"}
                if self.headers.get("Authorization"):
                    forwarded_headers["Authorization"] = self.headers["Authorization"]
                if body:
                    forwarded_headers["Content-Type"] = self.headers["Content-Type"]
                forwarded_body = resolver.resolve_body(body, self.headers.get("Content-Type", ""))
                connection.request(method, self.path, body=forwarded_body or None, headers=forwarded_headers)
                response = connection.getresponse()
                raw = response.read(policy.maximum_body + 1)
                if len(raw) > policy.maximum_body:
                    raise SanitizerDenied("upstream-body-too-large")
                headers = {name.lower(): value for name, value in response.getheaders()}
                projected = policy.project(operation, response.status, headers, raw)
                self.send_response(response.status)
                if "location" in headers:
                    location_uuid = headers["location"].rstrip("/").rsplit("/", 1)[-1]
                    if re.fullmatch(r"[0-9a-fA-F-]{36}", location_uuid):
                        self.send_header("Location", "/" + location_uuid.lower())
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(projected)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                if projected:
                    self.wfile.write(projected)
                policy.record(operation, method, response.status, len(body), len(projected))
            except (SanitizerDenied, ValueError, json.JSONDecodeError, OSError, http.client.HTTPException):
                self._deny()
            finally:
                if connection is not None:
                    connection.close()

    return Handler


def serve(
    profile_path: Path,
    mode: str,
    temporary_client_id: str,
    upstream_host: str,
    upstream_port: int,
    assert_lease: Callable[[], None],
    bind_host: str,
    secret_root: Path,
    bind_port: int = 8080,
) -> tuple[ThreadingHTTPServer, SanitizerPolicy]:
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    policy = SanitizerPolicy(profile, mode, temporary_client_id)
    resolver = SecretResolver(secret_root)
    server = ThreadingHTTPServer(
        (bind_host, bind_port), handler_factory(policy, resolver, upstream_host, upstream_port, assert_lease)
    )
    return server, policy
