#!/usr/bin/env python3
"""Create one private, disposable Compose context for the Fresh product proof."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import socket
from pathlib import Path


PORT_NAMES = (
    "WEAVE_PROXY_HTTP_HOST_PORT",
    "WEAVE_PROXY_HTTPS_HOST_PORT",
    "WEAVE_KEYCLOAK_HOST_PORT",
    "WEAVE_KEYCLOAK_MANAGEMENT_HOST_PORT",
    "WEAVE_MAILPIT_WEB_HOST_PORT",
    "WEAVE_MAS_HOST_PORT",
    "WEAVE_SYNAPSE_HOST_PORT",
    "WEAVE_NEXTCLOUD_HOST_PORT",
    "WEAVE_BACKEND_HOST_PORT",
    "WEAVE_MCP_HOST_PORT",
)
RUN_ID = re.compile(r"^[a-z0-9][a-z0-9-]{5,39}$")


def reserve_ports(count: int) -> list[int]:
    sockets: list[socket.socket] = []
    try:
        for _ in range(count):
            listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            listener.bind(("127.0.0.1", 0))
            sockets.append(listener)
        return [listener.getsockname()[1] for listener in sockets]
    finally:
        for listener in sockets:
            listener.close()


def atomic_private_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def update_environment(template: str, values: dict[str, str]) -> str:
    result: list[str] = []
    seen: set[str] = set()
    for line in template.splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            result.append(line)
            continue
        name, _ = line.split("=", 1)
        if name in values:
            result.append(f"{name}={values[name]}")
            seen.add(name)
        else:
            result.append(line)
    missing = sorted(set(values) - seen)
    if missing:
        raise SystemExit(
            "WEAVE_TEST_APP_CONTEXT_ERROR template is missing " + ", ".join(missing)
        )
    return "\n".join(result) + "\n"


def shell_assignment(name: str, value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_./:@+-]+", value):
        raise SystemExit(f"WEAVE_TEST_APP_CONTEXT_ERROR unsafe shell value for {name}")
    return f"{name}={value}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()

    repository = args.repository_root.resolve()
    output_root = args.output_root.resolve()
    if not RUN_ID.fullmatch(args.run_id):
        raise SystemExit(
            "WEAVE_TEST_APP_CONTEXT_ERROR run ID must match "
            "[a-z0-9][a-z0-9-]{5,39}"
        )
    namespace = (
        "weave-e2e-"
        + hashlib.sha256(args.run_id.encode("ascii")).hexdigest()[:16]
    )
    run_root = output_root / namespace
    env_file = run_root / "test.env"
    hosts_file = run_root / "hosts"
    evidence_file = run_root / "weave-test-app-evidence.json"
    teardown_file = run_root / "teardown-evidence.json"
    ports = dict(zip(PORT_NAMES, map(str, reserve_ports(len(PORT_NAMES)))))
    https_port = ports["WEAVE_PROXY_HTTPS_HOST_PORT"]
    http_port = ports["WEAVE_PROXY_HTTP_HOST_PORT"]
    placeholder = "sha256:" + "a" * 64
    values = {
        **ports,
        "WEAVE_DEPLOYMENT_CONTEXT": "disposable",
        "WEAVE_DEPLOYMENT_SCOPE": "persistent-test",
        "WEAVE_PUBLIC_URL": f"https://weave.test:{https_port}",
        "WEAVE_API_ORIGIN": f"https://api.weave.test:{https_port}",
        "WEAVE_API_URL": f"https://api.weave.test:{https_port}/api",
        "WEAVE_AUTH_URL": f"https://auth.weave.test:{https_port}",
        "WEAVE_MATRIX_URL": f"https://matrix.weave.test:{https_port}",
        "WEAVE_FILES_URL": f"https://files.weave.test:{https_port}",
        "WEAVE_FILES_PUBLIC_AUTHORITY": f"files.weave.test:{https_port}",
        "WEAVE_KEYCLOAK_IMAGE": placeholder,
        "WEAVE_IDENTITY_OPS_IMAGE": placeholder,
        "WEAVE_BACKEND_IMAGE": placeholder,
        "WEAVE_MCP_IMAGE": placeholder,
    }
    template = (
        repository
        / "infra/weave-workspace/environments/test.env.example"
    ).read_text(encoding="utf-8")
    atomic_private_write(env_file, update_environment(template, values))
    atomic_private_write(
        hosts_file,
        "127.0.0.1 weave.test api.weave.test auth.weave.test\n",
    )

    generated_root = (
        repository
        / "infra/weave-workspace/.generated/isolated"
        / namespace
    )
    assignments = {
        "WEAVE_E2E_RUN_ID": args.run_id,
        "WEAVE_E2E_RUN_NAMESPACE": namespace,
        "WEAVE_ENV_FILE": str(env_file),
        "WEAVE_TEST_APP_RUN_ROOT": str(run_root),
        "WEAVE_TEST_APP_HOSTS_FILE": str(hosts_file),
        "WEAVE_TEST_APP_EVIDENCE_PATH": str(evidence_file),
        "WEAVE_TEST_APP_TEARDOWN_EVIDENCE_PATH": str(teardown_file),
        "WEAVE_TEST_APP_GENERATED_ROOT": str(generated_root),
        "WEAVE_TEST_APP_SECRET_ROOT": str(generated_root / "secrets"),
        "WEAVE_TEST_APP_TLS_ROOT": str(generated_root / "tls"),
        "WEAVE_TEST_APP_PRODUCT_ORIGIN": values["WEAVE_PUBLIC_URL"],
        "WEAVE_TEST_APP_API_ORIGIN": values["WEAVE_API_ORIGIN"],
        "WEAVE_TEST_APP_ISSUER": values["WEAVE_AUTH_URL"] + "/realms/weave",
        "WEAVE_TEST_APP_MCP_ENDPOINT": values["WEAVE_API_ORIGIN"] + "/mcp",
        "WEAVE_TEST_APP_MAILPIT_API": (
            "http://127.0.0.1:"
            + ports["WEAVE_MAILPIT_WEB_HOST_PORT"]
            + "/api/v1"
        ),
        "WEAVE_TEST_APP_LOCAL_CA_URL": (
            f"http://127.0.0.1:{http_port}/weave-local-ca.pem"
        ),
        **ports,
    }
    print("\n".join(shell_assignment(name, value) for name, value in assignments.items()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
