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
import hashlib
import json
import os
import stat
import subprocess
import sys
from pathlib import Path

repository = Path(os.environ["REPOSITORY_ROOT"])
temporary = Path(os.environ["TEMP_ROOT"])
script = repository / "infra/weave-workspace/scripts/build_keycloak_image.py"
spec = importlib.util.spec_from_file_location("build_keycloak_image", script)
assert spec is not None and spec.loader is not None
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

assert module.UPSTREAM_COMMIT == "6c73e3027811d9c7b22683edd825e839272e9547"
assert module.UPSTREAM_TAG == "26.7.0"
assert module.ARCHIVE_SHA256 == "32267c4f45db91874c46a097415c336d137ee184d25c3481a513905a92669186"
assert module.STOCK_SERVICES_SHA256 == "052169f7907a21f4e26679bca5c7365627db91b071a7a2fcaeee00230e6b1419"
specification_commit, specification_digest = module.specification_pin(repository)
assert specification_commit == "6bbfb0ec1d85bdd9e24a9ce7785cb5c506c9edf0"
assert specification_digest == "sha256:" + hashlib.sha256(
    (repository / "specs/weave-specs.lock.json").read_bytes()
).hexdigest()
assert module.DOWNSTREAM_TEST_CLASSES == (
    "org.keycloak.services.clientpolicy.executor.WeaveWorkloadClientRegistrationExecutorTest",
    "org.keycloak.services.clientregistration.WeaveClientRegistrationAuthTest",
    "org.keycloak.services.clientregistration.oidc.WeaveRegistrationHandoffTest",
)
assert module.parse_upstream_tag_resolution(
    f"{module.UPSTREAM_COMMIT}\trefs/tags/{module.UPSTREAM_TAG}\n"
) == module.UPSTREAM_COMMIT
for invalid_resolution in (
    "",
    f"{'1' * 40}\trefs/tags/{module.UPSTREAM_TAG}\n",
    f"{module.UPSTREAM_COMMIT}\trefs/heads/{module.UPSTREAM_TAG}\n",
):
    try:
        module.parse_upstream_tag_resolution(invalid_resolution)
    except SystemExit:
        pass
    else:
        raise AssertionError("Keycloak builder accepted an invalid upstream tag resolution")
try:
    module.resolve_candidate(repository, "1" * 40)
except SystemExit as failure:
    assert "differs from the local source HEAD" in str(failure)
else:
    raise AssertionError("Keycloak builder accepted evidence for a different source commit")

evidence = temporary / "evidence.json"
module.atomic_write(evidence, {"containsSecretValues": False, "supportSafe": True})
assert stat.S_IMODE(evidence.stat().st_mode) == 0o600

candidate = "a" * 40
projection = {
    "schemaVersion": "weave.downstream-keycloak-build-evidence.v1",
    "candidateCommit": candidate,
    "specificationCommit": specification_commit,
    "specificationLockDigest": "sha256:" + "1" * 64,
    "keycloakVersion": module.UPSTREAM_TAG,
    "upstreamCommit": module.UPSTREAM_COMMIT,
    "upstreamArchiveSha256": module.ARCHIVE_SHA256,
    "stockReference": module.STOCK_KEYCLOAK_REFERENCE,
    "stockServicesJarSha256": module.STOCK_SERVICES_SHA256,
    "patchSha256": "2" * 64,
    "patchedPaths": list(module.PATCHED_PATHS),
    "patchedServicesJarSha256": "3" * 64,
    "downstreamTestClasses": list(module.DOWNSTREAM_TEST_CLASSES),
    "downstreamTestCount": 12,
    "buildToolchain": {
        "javaVersion": "21.0.8",
        "javaVendor": "fixture",
        "mavenVersion": "3.9.11",
        "mavenWrapperPropertiesSha256": "4" * 64,
    },
    "providerId": "weave-workload-client-registration-enforcer",
}
canonical = json.dumps(
    projection, ensure_ascii=False, separators=(",", ":"), sort_keys=True
).encode("utf-8")
module.atomic_write(
    evidence,
    {
        "schemaVersion": "weave.downstream-keycloak-image.v1",
        "evidenceForCandidateCommit": candidate,
        "specificationCommit": specification_commit,
        "upstreamCommit": projection["upstreamCommit"],
        "upstreamArchiveSha256": projection["upstreamArchiveSha256"],
        "stockReference": projection["stockReference"],
        "stockServicesJarSha256": projection["stockServicesJarSha256"],
        "patchSha256": projection["patchSha256"],
        "patchedPaths": projection["patchedPaths"],
        "patchedServicesJarSha256": projection["patchedServicesJarSha256"],
        "downstreamPolicyTestClasses": projection["downstreamTestClasses"],
        "downstreamPolicyTestCount": projection["downstreamTestCount"],
        "buildToolchain": projection["buildToolchain"],
        "providerId": projection["providerId"],
        "canonicalBuildEvidence": projection,
        "canonicalBuildEvidenceDigest": (
            "sha256:" + hashlib.sha256(canonical).hexdigest()
        ),
        "containsSecretValues": False,
        "supportSafe": True,
    },
)
verifier = (
    repository
    / "infra/weave-workspace/scripts/verify_keycloak_build_evidence.py"
)
subprocess.run(
    [
        sys.executable,
        str(verifier),
        "--evidence",
        str(evidence),
        "--candidate-commit",
        candidate,
        "--specification-commit",
        specification_commit,
    ],
    check=True,
    stdout=subprocess.PIPE,
    text=True,
)
tampered = json.loads(evidence.read_text(encoding="utf-8"))
tampered["canonicalBuildEvidence"]["specificationLockDigest"] = (
    "sha256:" + "5" * 64
)
module.atomic_write(evidence, tampered)
rejected = subprocess.run(
    [
        sys.executable,
        str(verifier),
        "--evidence",
        str(evidence),
        "--candidate-commit",
        candidate,
        "--specification-commit",
        specification_commit,
    ],
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
)
assert rejected.returncode != 0
assert "canonical projection digest does not match" in (
    rejected.stdout + rejected.stderr
)

patch = repository / module.PATCH_RELATIVE
dockerfile = repository / module.DOCKERFILE_RELATIVE
assert patch.is_file() and dockerfile.is_file()
patch_text = patch.read_text(encoding="utf-8")
dockerfile_text = dockerfile.read_text(encoding="utf-8")
assert module.patch_paths(patch) == module.PATCHED_PATHS
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
assert (
    "META-INF/services/org.keycloak.services.clientpolicy.executor."
    "ClientPolicyExecutorProviderFactory"
) in patch_text
assert "keycloak-server-spi-private" not in patch_text
assert "rejectsUnapprovedScopesBeforeDescriptionConversion" in patch_text
assert "rejectsAnInjectedExtraEffectiveServiceAccountRole" in patch_text
assert "FROM ${WEAVE_KEYCLOAK_BASE} AS builder" in dockerfile_text
assert "kc.sh build --db=postgres" in dockerfile_text
assert "com.massimotter.weave.keycloak-patch-sha256" in dockerfile_text
assert "com.massimotter.weave.keycloak-build-evidence-digest" in dockerfile_text
first_from = dockerfile_text.index("FROM ${WEAVE_KEYCLOAK_BASE}")
second_from = dockerfile_text.index("FROM ${WEAVE_KEYCLOAK_BASE}", first_from + 1)
assert second_from < dockerfile_text.index(
    "ARG WEAVE_KEYCLOAK_BUILD_EVIDENCE_DIGEST"
)
assert "com.massimotter.weave.spec-digest" in dockerfile_text

services = temporary / "keycloak-services-26.7.0.jar"
services.write_bytes(b"fixture")
context = temporary / "prepared-context"
module.prepare_build_context(context, services, dockerfile)
assert (context / services.name).read_bytes() == b"fixture"
assert (context / "Dockerfile.runtime").read_text(encoding="utf-8") == dockerfile_text
assert stat.S_IMODE((context / services.name).stat().st_mode) == 0o600
try:
    module.prepare_build_context(context, services, dockerfile)
except SystemExit as failure:
    assert "already exists" in str(failure)
else:
    raise AssertionError("Keycloak builder overwrote a prepared build context")
PY

printf 'build-keycloak-image-helper-test: ok\n'
