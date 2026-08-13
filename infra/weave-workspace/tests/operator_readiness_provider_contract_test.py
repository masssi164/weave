#!/usr/bin/env python3
"""Verify readiness follows the provider profiles selected by Compose."""

from __future__ import annotations

import sys
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from operator_check import _provider_probes, _runtime_services  # noqa: E402


def context(*profiles: str):
    return SimpleNamespace(
        active_profiles=profiles,
        environment="e2e",
        env={
            "WEAVE_MATRIX_URL": "https://matrix.weave.test",
            "WEAVE_FILES_URL": "https://files.weave.test",
        },
    )


def main() -> int:
    native = context("e2e", "storage-s3")
    assert _runtime_services(native, require_application=True) == [
        "postgres",
        "keycloak",
        "caddy",
        "runtime-state",
        "mailpit",
        "backend",
        "mcp",
    ]
    assert _provider_probes(native) == {}

    external = context(
        "e2e", "provider-matrix", "provider-nextcloud", "storage-s3"
    )
    assert _runtime_services(external, require_application=True) == [
        "postgres",
        "keycloak",
        "caddy",
        "mas",
        "synapse",
        "nextcloud",
        "runtime-state",
        "mailpit",
        "backend",
        "mcp",
    ]
    assert _provider_probes(external) == {
        "matrixVersions": "https://matrix.weave.test/_matrix/client/versions",
        "nextcloudStatus": "https://files.weave.test/status.php",
    }

    print("operator readiness provider contract tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
