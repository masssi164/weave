#!/usr/bin/env python3
"""Disposable exact-version ``kcadm`` boundary with no direct Keycloak route."""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Callable
from urllib.parse import urlencode

from admin_sanitizer import SECRET_REFS


class KcadmError(RuntimeError):
    pass


class ProtectedKcadm:
    """Own the private network, trusted sanitizer sidecar, and kcadm process.

    The sanitizer is dual-homed.  ``kcadm`` is attached only to an internal
    network and has no Docker socket, host gateway, published port, or route to
    the Compose network.
    """

    def __init__(
        self,
        *,
        image: str,
        sanitizer_image: str,
        code_root: Path,
        profile_path: Path,
        secret_root: Path,
        required_secret_refs: tuple[str, ...],
        profile_revision: str,
        mode: str,
        temporary_client_id: str,
        temporary_client_secret: str,
        namespace: str,
        compose_network: str,
        control_db_user: str,
        control_db_password_file: Path,
        lock_key: str,
        lease_id: str,
        reconciliation_id: str,
        fencing_token: int,
        assert_lease: Callable[[], None],
        runtime_uid: int,
        runtime_gid: int,
    ) -> None:
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{7,62}", namespace):
            raise KcadmError("invalid protected-run namespace")
        if mode not in {"plan", "apply", "verify", "tombstone"}:
            raise KcadmError("invalid protected reconciliation mode")
        self.image = image
        self.sanitizer_image = sanitizer_image
        self.code_root = code_root.resolve()
        self.profile_path = profile_path.resolve()
        self.secret_root = secret_root.resolve()
        if (
            not required_secret_refs
            or tuple(sorted(set(required_secret_refs))) != required_secret_refs
            or any(reference not in SECRET_REFS for reference in required_secret_refs)
        ):
            raise KcadmError("required Keycloak SecretRef projection is invalid")
        self.required_secret_refs = required_secret_refs
        self.profile_revision = profile_revision
        self.mode = mode
        self.temporary_client_id = temporary_client_id
        self.temporary_client_secret = temporary_client_secret
        self.namespace = namespace
        self.compose_network = compose_network
        self.control_db_user = control_db_user
        self.control_db_password_file = control_db_password_file.resolve()
        self.lock_key = lock_key
        self.lease_id = lease_id
        self.reconciliation_id = reconciliation_id
        self.fencing_token = fencing_token
        self.assert_lease = assert_lease
        if runtime_uid < 1 or runtime_gid < 1:
            raise KcadmError("sanitizer runtime identity must be non-root")
        self.runtime_uid = runtime_uid
        self.runtime_gid = runtime_gid
        self.network = f"{namespace}-network"
        self.container = f"{namespace}-kcadm"
        self.sanitizer = f"{namespace}-sanitizer"
        self.direct_route_denied = False
        self.secret_projection: Path | None = None
        self.projected_secret_count = 0
        self.secret_projection_destroyed = False

    @property
    def _labels(self) -> tuple[str, ...]:
        return (
            "--label", "com.massimotter.weave.managed=true",
            "--label", f"com.massimotter.weave.namespace={self.namespace}",
            "--label", "com.massimotter.weave.scope=protected-keycloak-reconciliation",
        )

    def __enter__(self) -> "ProtectedKcadm":
        self.assert_lease()
        subprocess.run(
            ["docker", "network", "create", "--internal", *self._labels, self.network],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        try:
            self._create_secret_projection()
            self._create_sanitizer()
            self._create_kcadm()
            self._assert_direct_route_denied()
            self._login()
            return self
        except Exception:
            self.close()
            raise

    def _create_secret_projection(self) -> None:
        projection = Path(tempfile.mkdtemp(prefix=f"{self.namespace}-secretrefs-"))
        os.chmod(projection, 0o700)
        try:
            os.chown(projection, self.runtime_uid, self.runtime_gid)
            projected_names: set[str] = set()
            for reference in self.required_secret_refs:
                name = SECRET_REFS[reference]
                if name in projected_names:
                    continue
                source = self.secret_root / name
                if source.is_symlink() or not source.is_file() or stat.S_IMODE(source.stat().st_mode) != 0o600:
                    raise KcadmError(f"Keycloak SecretRef source is unsafe: {reference}")
                target = projection / name
                descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                try:
                    with os.fdopen(descriptor, "wb") as stream:
                        stream.write(source.read_bytes())
                        stream.flush()
                        os.fsync(stream.fileno())
                    os.chmod(target, 0o600)
                    os.chown(target, self.runtime_uid, self.runtime_gid)
                finally:
                    if target.exists() and not target.is_file():
                        target.unlink(missing_ok=True)
                projected_names.add(name)
            observed = {path.name for path in projection.iterdir() if path.is_file() and not path.is_symlink()}
            if observed != projected_names or any(path.is_symlink() for path in projection.iterdir()):
                raise KcadmError("run-scoped Keycloak SecretRef projection is not exact")
            self.secret_projection = projection
            self.projected_secret_count = len(projected_names)
        except Exception:
            for path in projection.iterdir():
                if path.is_file() and not path.is_symlink():
                    path.unlink()
            projection.rmdir()
            raise

    def _create_sanitizer(self) -> None:
        if self.secret_projection is None:
            raise KcadmError("run-scoped Keycloak SecretRef projection was not created")
        command = [
            "docker", "create", "--name", self.sanitizer,
            "--user", f"{self.runtime_uid}:{self.runtime_gid}",
            "--read-only", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges:true",
            "--network", self.network, "--network-alias", "sanitizer",
            "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=16m,mode=700",
            "--mount", f"type=bind,src={self.code_root},dst=/opt/weave/keycloak,readonly",
            "--mount", f"type=bind,src={self.profile_path},dst=/run/weave/sanitizer-profile.json,readonly",
            "--mount", f"type=bind,src={self.secret_projection},dst=/run/weave/secrets,readonly",
            "--mount", f"type=bind,src={self.control_db_password_file},dst=/run/secrets/control-db-password,readonly",
            *self._labels,
            "--entrypoint", "/bin/sh", self.sanitizer_image,
            "-euc", "trap 'exit 0' TERM INT; while :; do sleep 3600; done",
        ]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        subprocess.run(["docker", "start", self.sanitizer], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(
            ["docker", "network", "connect", self.compose_network, self.sanitizer],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        inspect = subprocess.run(
            [
                "docker", "inspect", self.sanitizer, "--format",
                "{{with index .NetworkSettings.Networks \"" + self.network + "\"}}{{.IPAddress}}{{end}}",
            ],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout.strip()
        if not re.fullmatch(r"(?:[0-9]{1,3}\.){3}[0-9]{1,3}", inspect):
            raise KcadmError("sanitizer private-network address is unavailable")
        daemon = [
            "docker", "exec", "--detach", self.sanitizer,
            "python3", "/opt/weave/keycloak/sanitizer_daemon.py",
            "--profile-path", "/run/weave/sanitizer-profile.json",
            "--profile-revision", self.profile_revision,
            "--mode", self.mode,
            "--temporary-client-id", self.temporary_client_id,
            "--proxy-bind", inspect,
            "--database-user", self.control_db_user,
            "--database-password-file", "/run/secrets/control-db-password",
            "--secret-root", "/run/weave/secrets",
            "--lock-key", self.lock_key,
            "--lease-id", self.lease_id,
            "--reconciliation-id", self.reconciliation_id,
            "--fencing-token", str(self.fencing_token),
        ]
        subprocess.run(daemon, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        for _ in range(50):
            result = self._control("GET", "/summary", None, check=False)
            if result.returncode == 0:
                return
            time.sleep(0.1)
        raise KcadmError("sanitizer control endpoint did not become ready")

    def _create_kcadm(self) -> None:
        command = [
            "docker", "create", "--name", self.container,
            "--read-only", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges:true",
            "--network", self.network,
            "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=16m,mode=700",
            *self._labels,
            "--entrypoint", "/bin/bash", self.image,
            "-euc", "trap 'exit 0' TERM INT; while :; do sleep 3600; done",
        ]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        subprocess.run(["docker", "start", self.container], check=True, stdout=subprocess.DEVNULL)
        inspected = subprocess.run(
            ["docker", "inspect", self.container, "--format", "{{json .Config.Env}}"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout
        if b"KC_CLI_CLIENT_SECRET" in inspected or self.temporary_client_secret.encode("utf-8") in inspected:
            raise KcadmError("temporary authority leaked into the kcadm container configuration")

    def _assert_direct_route_denied(self) -> None:
        probes = (
            "keycloak:8080",
            "postgres:5432",
            "host.docker.internal:8080",
        )
        for target in probes:
            host, port = target.rsplit(":", 1)
            result = subprocess.run(
                [
                    "docker", "exec", self.container, "/bin/bash", "-euc",
                    f"exec 3<>/dev/tcp/{host}/{port}",
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if result.returncode == 0:
                raise KcadmError("kcadm acquired a forbidden direct network route")
        self.direct_route_denied = True

    def _login(self) -> None:
        self.assert_lease()
        environment = dict(os.environ)
        environment["KC_CLI_CLIENT_SECRET"] = self.temporary_client_secret
        result = subprocess.run(
            [
                "docker", "exec", "--env", "KC_CLI_CLIENT_SECRET", self.container, "/bin/bash", "-euc",
                "exec /opt/keycloak/bin/kcadm.sh config credentials "
                "--config /tmp/kcadm.config --server http://sanitizer:8080 "
                f"--realm master --client {self.temporary_client_id} "
                "--client-secret \"$KC_CLI_CLIENT_SECRET\"",
            ],
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        environment.pop("KC_CLI_CLIENT_SECRET", None)
        self._assert_safe_output(result)
        if result.returncode != 0:
            raise KcadmError("temporary kcadm authority could not authenticate through the sanitizer")

    def _assert_safe_output(self, result: subprocess.CompletedProcess[bytes]) -> None:
        secret = self.temporary_client_secret.encode("utf-8")
        if secret and secret in result.stdout + result.stderr:
            raise KcadmError("temporary credential value reached kcadm output")

    def _control(
        self,
        method: str,
        path: str,
        value: dict[str, object] | None,
        *,
        check: bool = True,
    ) -> subprocess.CompletedProcess[bytes]:
        code = (
            "import json,sys,urllib.request; "
            "data=sys.stdin.buffer.read() or None; "
            f"r=urllib.request.Request('http://127.0.0.1:9080{path}',data=data,method='{method}',"
            "headers={'Content-Type':'application/json'}); "
            "sys.stdout.buffer.write(urllib.request.urlopen(r,timeout=5).read())"
        )
        payload = b"" if value is None else json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        result = subprocess.run(
            ["docker", "exec", "--interactive", self.sanitizer, "python3", "-c", code],
            input=payload,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if check and result.returncode != 0:
            raise KcadmError("sanitizer control operation failed")
        return result

    def _register(
        self,
        method: str,
        endpoint: str,
        query: dict[str, str],
        body: bytes | None,
        binding: dict[str, str],
    ) -> None:
        target = endpoint
        if query:
            target += "?" + urlencode(sorted(query.items()))
        digest = "none" if not body else "sha256:" + hashlib.sha256(body).hexdigest()
        self._control(
            "POST",
            "/register",
            {"method": method, "target": target, "bodyDigest": digest, "binding": binding},
        )

    def execute(self, action: dict[str, object]) -> object | None:
        self.assert_lease()
        method = action.get("method")
        endpoint = action.get("endpoint")
        if method not in {"GET", "POST", "PUT", "DELETE"} or not isinstance(endpoint, str):
            raise KcadmError("invalid kcadm action")
        query = action.get("query", {})
        if not isinstance(query, dict) or any(
            not isinstance(key, str) or not isinstance(value, str) for key, value in query.items()
        ):
            raise KcadmError("invalid kcadm query")
        body_value = action.get("body")
        body = None
        if body_value is not None:
            if not isinstance(body_value, (dict, list)):
                raise KcadmError("invalid kcadm request body")
            body = json.dumps(
                body_value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
            ).encode("utf-8")
        binding_value = action.get("binding", {})
        if not isinstance(binding_value, dict) or any(
            not isinstance(key, str) or not isinstance(value, str)
            for key, value in binding_value.items()
        ):
            raise KcadmError("invalid semantic action binding")
        self._register(method, endpoint, query, body, binding_value)
        verb = {"GET": "get", "POST": "create", "PUT": "update", "DELETE": "delete"}[method]
        arguments = [verb, endpoint, "--config", "/tmp/kcadm.config"]
        for key, value in sorted(query.items()):
            arguments.extend(("--query", f"{key}={value}"))
        if body is not None:
            arguments.extend(("--file", "-"))
        command = ["docker", "exec"]
        if body is not None:
            command.append("--interactive")
        command.extend((self.container, "/opt/keycloak/bin/kcadm.sh", *arguments))
        result = subprocess.run(command, input=body, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self._assert_safe_output(result)
        if result.returncode != 0:
            if action.get("allowNotFound") is True and b"404" in result.stdout + result.stderr:
                return None
            raise KcadmError("sanitized kcadm operation failed")
        if not result.stdout.strip():
            return None
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError as error:
            raise KcadmError("sanitized kcadm response is not JSON") from error

    def assert_new_grant_denied(self) -> None:
        environment = dict(os.environ)
        environment["KC_CLI_CLIENT_SECRET"] = self.temporary_client_secret
        result = subprocess.run(
            [
                "docker", "exec", "--env", "KC_CLI_CLIENT_SECRET", self.container, "/bin/bash", "-euc",
                "exec /opt/keycloak/bin/kcadm.sh config credentials --config /tmp/new-grant.config "
                "--server http://sanitizer:8080 --realm master "
                f"--client {self.temporary_client_id} --client-secret \"$KC_CLI_CLIENT_SECRET\"",
            ],
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self._assert_safe_output(result)
        environment.pop("KC_CLI_CLIENT_SECRET", None)
        self.temporary_client_secret = ""
        if result.returncode == 0 or b"invalid_client" not in result.stdout + result.stderr:
            raise KcadmError("deleted temporary authority still acquired a new grant")

    def assert_expired_token_rejected(self, timeout_seconds: int = 900) -> None:
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            result = self._control("POST", "/probe-expired-token", None)
            try:
                value = json.loads(result.stdout)
            except json.JSONDecodeError as error:
                raise KcadmError("expired-token control proof is malformed") from error
            if value == {"httpStatus": 401, "status": "expired-token-rejected"}:
                return
            if (
                not isinstance(value, dict)
                or value.get("status") != "pending-expiry"
                or not isinstance(value.get("waitSeconds"), int)
                or not 1 <= int(value["waitSeconds"]) <= 5
            ):
                raise KcadmError("expired-token control proof failed closed")
            time.sleep(int(value["waitSeconds"]))
        raise KcadmError("last access token did not reach the required rejection proof in time")

    def summary(self) -> dict[str, object]:
        result = self._control("GET", "/summary", None)
        try:
            value = json.loads(result.stdout)
        except json.JSONDecodeError as error:
            raise KcadmError("sanitizer summary is malformed") from error
        if not isinstance(value, dict):
            raise KcadmError("sanitizer summary is malformed")
        value["directKeycloakRoute"] = "denied" if self.direct_route_denied else "unproven"
        value["projectedSecretRefCount"] = self.projected_secret_count
        return value

    def close(self) -> None:
        for container in (self.container, self.sanitizer):
            subprocess.run(
                ["docker", "container", "rm", "--force", container],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        subprocess.run(
            ["docker", "network", "rm", self.network],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self.temporary_client_secret = ""
        if self.secret_projection is not None and self.secret_projection.exists():
            for path in self.secret_projection.iterdir():
                if path.is_symlink() or not path.is_file():
                    raise KcadmError("unexpected entry blocked SecretRef projection destruction")
                path.unlink()
            self.secret_projection.rmdir()
        self.secret_projection_destroyed = self.secret_projection is None or not self.secret_projection.exists()

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()
