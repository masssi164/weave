#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
REPOSITORY_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly REPOSITORY_ROOT
PYTHON_BIN="$(command -v python3)"
readonly PYTHON_BIN
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-keycloak-builder-test.XXXXXX")"
readonly TEMP_ROOT
trap 'rm -rf -- "$TEMP_ROOT"' EXIT

export REPOSITORY_ROOT TEMP_ROOT
"${PYTHON_BIN}" - <<'PY'
import importlib.util
import os
import stat
from pathlib import Path

repository = Path(os.environ["REPOSITORY_ROOT"])
temporary = Path(os.environ["TEMP_ROOT"])
script = repository / "infra/weave-workspace/scripts/build_keycloak_image.py"
spec = importlib.util.spec_from_file_location("build_keycloak_image", script)
assert spec is not None and spec.loader is not None
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

assert module.UPSTREAM_COMMIT == "6c73e3027811d9c7b22683edd825e839272e9547"
assert module.ARCHIVE_SHA256 == "32267c4f45db91874c46a097415c336d137ee184d25c3481a513905a92669186"
assert module.STOCK_SERVICES_SHA256 == "052169f7907a21f4e26679bca5c7365627db91b071a7a2fcaeee00230e6b1419"
assert module.SPEC_COMMIT == "1625cfec7bf031b8ed08128a6383a3c5ce2e1f10"
try:
    module.resolve_candidate(repository, "1" * 40)
except SystemExit as failure:
    assert "differs from the local source HEAD" in str(failure)
else:
    raise AssertionError("Keycloak builder accepted evidence for a different source commit")

evidence = temporary / "evidence.json"
module.atomic_write(evidence, {"containsSecretValues": False, "supportSafe": True})
assert stat.S_IMODE(evidence.stat().st_mode) == 0o600

patch = repository / module.PATCH_RELATIVE
dockerfile = repository / module.DOCKERFILE_RELATIVE
assert patch.is_file() and dockerfile.is_file()
patch_text = patch.read_text(encoding="utf-8")
dockerfile_text = dockerfile.read_text(encoding="utf-8")
assert "WeaveWorkloadClientRegistrationExecutorFactory" in patch_text
assert "weave-workload-client-registration-enforcer" in patch_text
assert "context instanceof AdminClientRegisteredContext" in patch_text
assert "representation.setScope(null)" in patch_text
assert "attributes.entrySet().removeIf(entry -> entry.getValue() == null)" in patch_text
assert "client.removeAttribute(ClientSecretConstants.CLIENT_SECRET_CREATION_TIME)" in patch_text
assert "FIXED_ATTRIBUTES.forEach(client::setAttribute)" in patch_text
assert "OIDCConfigAttributes.USE_RFC9068_ACCESS_TOKEN_HEADER_TYPE" in patch_text
assert "OIDCConfigAttributes.ACCESS_TOKEN_LIFESPAN" in patch_text
assert "attributes.putIfAbsent(" in patch_text
assert "@@ -92,4 +93,9 @@" in patch_text
assert "@@ -94,0" not in patch_text
assert "keycloak-server-spi-private" not in patch_text
assert "FROM ${WEAVE_KEYCLOAK_BASE} AS builder" in dockerfile_text
assert "kc.sh build --db=postgres" in dockerfile_text
assert "com.massimotter.weave.keycloak-patch-sha256" in dockerfile_text
PY

printf 'build-keycloak-image-helper-test: ok\n'
