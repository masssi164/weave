#!/usr/bin/env python3
"""Run or smoke-test the host Spring Boot server from generated dev coordinates."""

from __future__ import annotations

import argparse
import json
import os
import re
import signal
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


KEY = re.compile(r"^[A-Z][A-Z0-9_]*$")


def load_environment(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("generated backend/host.env is unavailable; prepare dev dependencies first")
    values: dict[str, str] = {}
    for number, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not original:
            continue
        if "=" not in original:
            raise RuntimeError(f"host.env:{number}: malformed coordinate")
        key, value = original.split("=", 1)
        if not KEY.fullmatch(key) or key in values or any(character in value for character in ("\x00", "\r", "\n")):
            raise RuntimeError(f"host.env:{number}: invalid coordinate")
        values[key] = value
    if values.get("SPRING_PROFILES_ACTIVE") != "dev" or "SPRING_DATASOURCE_URL" in values:
        raise RuntimeError("host dev must select application-dev H2 without a container PostgreSQL override")
    for required in (
        "SPRING_CONFIG_IMPORT",
        "WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL",
        "WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE",
        "WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE",
        "WEAVE_NEXTCLOUD_BASE_URL",
        "WEAVE_IDENTITY_KEYCLOAK_BASE_URL",
    ):
        if not values.get(required):
            raise RuntimeError(f"host.env omits {required}")
    return values


def server_command(repository: Path) -> list[str]:
    return [str(repository / "gradlew"), ":server:bootRun", "--console=plain"]


def ready() -> bool:
    try:
        with urllib.request.urlopen("http://127.0.0.1:8080/api/health/ready", timeout=2) as response:
            return response.status == 200
    except (urllib.error.URLError, TimeoutError):
        return False


def atomic_evidence(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("boot", "smoke"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    args = parser.parse_args()
    if not 30 <= args.timeout_seconds <= 600:
        raise SystemExit("WEAVE_HOST_DEV_ERROR timeout must be between 30 and 600 seconds")
    root = args.root.resolve()
    repository = root.parents[1]
    coordinates = load_environment(root / ".generated/dev/backend/host.env")
    environment = dict(os.environ)
    environment.update(coordinates)
    if args.mode == "boot":
        os.execve(server_command(repository)[0], server_command(repository), environment)
    started = datetime.now(timezone.utc)
    process = subprocess.Popen(server_command(repository), cwd=repository, env=environment)
    passed = False
    try:
        deadline = time.monotonic() + args.timeout_seconds
        while time.monotonic() < deadline:
            code = process.poll()
            if code is not None:
                raise RuntimeError(f"Spring Boot exited before readiness with status {code}")
            if ready():
                passed = True
                break
            time.sleep(1)
        if not passed:
            raise RuntimeError("Spring Boot did not become ready before the bounded timeout")
    finally:
        if process.poll() is None:
            process.send_signal(signal.SIGTERM)
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
    evidence = {
        "schemaVersion": "weave.host-dev-server-smoke.v1",
        "profile": "dev",
        "database": "h2-postgresql-mode",
        "providerDependencies": "compose-loopback",
        "startedAt": started.isoformat().replace("+00:00", "Z"),
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "ready": passed,
        "containsSecretValues": False,
        "supportSafe": True,
    }
    atomic_evidence(root / ".generated/dev/evidence/host-server-smoke.json", evidence)
    print("host dev server smoke: ready on H2 with live provider dependencies")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
