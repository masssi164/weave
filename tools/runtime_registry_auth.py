#!/usr/bin/env python3
"""Run-scoped GHCR credentials for isolated Mac mini artifact consumers.

The token never enters persistent Docker config, Compose, diagnostic artifacts,
or the environment of the application being tested.
"""
from __future__ import annotations

import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit

from build_artifacts import ArtifactError


def runner_temp() -> Path:
    selected = os.environ.get("RUNNER_TEMP", "")
    directory = Path(selected)
    if (not selected or any(character in selected for character in "\r\n\0")
            or not directory.is_absolute() or not directory.is_dir()
            or directory.is_symlink() or directory.stat().st_uid != os.getuid()):
        raise ArtifactError("runtime-registry-runner-temp-invalid")
    return directory.resolve()


def _config_path(selected: str) -> Path:
    root = runner_temp()
    path = Path(selected)
    if (not selected or not path.is_absolute() or path.parent != root
            or not re.fullmatch(r"weave-ghcr-[A-Za-z0-9_-]+", path.name)
            or path.is_symlink() or not path.is_dir()
            or path.stat().st_uid != os.getuid()):
        raise ArtifactError("runtime-registry-cleanup-path-invalid")
    return path


def local_docker_host() -> str:
    """Preserve the selected daemon without importing ambient registry credentials."""
    context = os.environ.get("DOCKER_CONTEXT", "")
    host = os.environ.get("DOCKER_HOST", "") if not context else ""
    try:
        if not host:
            if not context:
                context = subprocess.run(["docker", "context", "show"], check=True,
                                         capture_output=True, text=True, timeout=30).stdout.strip()
            if not context or any(character in context for character in "\r\n\0"):
                raise ArtifactError("runtime-registry-docker-context-invalid")
            records = json.loads(subprocess.run(["docker", "context", "inspect", "--", context],
                                                check=True, capture_output=True, text=True,
                                                timeout=30).stdout)
            if not isinstance(records, list) or len(records) != 1:
                raise ArtifactError("runtime-registry-docker-context-invalid")
            host = records[0]["Endpoints"]["docker"]["Host"]
        if not isinstance(host, str):
            raise ArtifactError("runtime-registry-docker-context-invalid")
        parsed = urlsplit(host)
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError):
        raise ArtifactError("runtime-registry-docker-context-unavailable") from None
    if (any(character in host for character in "\r\n\0")
            or parsed.scheme != "unix" or parsed.netloc or not parsed.path.startswith("/")
            or parsed.query or parsed.fragment
            or os.environ.get("DOCKER_TLS_VERIFY") or os.environ.get("DOCKER_TLS")):
        raise ArtifactError("runtime-registry-local-docker-socket-required")
    return host


def setup() -> None:
    token = os.environ.get("GHCR_TOKEN", "")
    actor = os.environ.get("GITHUB_ACTOR", "")
    output = Path(os.environ.get("GITHUB_ENV", ""))
    if (not token or any(char in token for char in "\n\r\0")
            or not re.fullmatch(r"[A-Za-z0-9-]+", actor)
            or not output.is_absolute() or not output.is_file() or output.is_symlink()):
        raise ArtifactError("runtime-registry-auth-input-invalid")
    host = local_docker_host()
    config = Path(tempfile.mkdtemp(prefix="weave-ghcr-", dir=runner_temp()))
    config.chmod(0o700)
    try:
        # docker login on an empty config may autodetect osxkeychain and persist
        # credentials outside this directory. Explicit auth keeps this file as
        # the only credential store; no credential helper or keychain is called.
        credential_file = config / "config.json"
        encoded = base64.b64encode((actor + ":" + token).encode()).decode("ascii")
        descriptor = os.open(credential_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, "w") as credentials:
            json.dump({"auths": {"ghcr.io": {"auth": encoded}}}, credentials)
        with output.open("a") as environment:
            environment.write("WEAVE_RUNTIME_DOCKER_CONFIG=" + str(config) + "\n")
            # Job-local environment only: pulls, inspections, Compose and ledger
            # pruning use the same already-selected local daemon. --config alone
            # would otherwise lose a named Docker Desktop or Colima context.
            environment.write("DOCKER_CONTEXT=\nDOCKER_HOST=" + host + "\n")
    except OSError:
        cleanup(str(config))
        raise ArtifactError("runtime-registry-auth-unavailable") from None
    except BaseException:
        cleanup(str(config))
        raise


def cleanup(selected: str) -> None:
    path = _config_path(selected)
    entries = list(path.iterdir())
    if any(entry.name != "config.json" or entry.is_symlink() or not entry.is_file()
           for entry in entries):
        raise ArtifactError("runtime-registry-cleanup-unexpected-entry")
    for entry in entries:
        entry.unlink()
    path.rmdir()


if __name__ == "__main__":
    try:
        if len(sys.argv) == 2 and sys.argv[1] == "setup":
            setup()
        elif len(sys.argv) == 2 and sys.argv[1] == "cleanup":
            if os.environ.get("WEAVE_RUNTIME_DOCKER_CONFIG"):
                cleanup(os.environ["WEAVE_RUNTIME_DOCKER_CONFIG"])
        else:
            raise ArtifactError("runtime-registry-auth-command-invalid")
    except ArtifactError as error:
        raise SystemExit("WEAVE_RUNTIME_REGISTRY_AUTH_ERROR " + str(error)) from None
