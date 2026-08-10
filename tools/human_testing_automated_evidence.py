#!/usr/bin/env python3
"""Build support-safe automated human-testing evidence from live and Simulator proofs."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


COMMIT = re.compile(r"^[0-9a-f]{40}$")
HASH = re.compile(r"^sha256:[0-9a-f]{64}$")
IMMUTABLE_IMAGE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
JWT = re.compile(r"(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])")
EMAIL = re.compile(r"(?<![A-Za-z0-9.!#$%&'*+/=?^_`{|}~-])[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+(?![A-Za-z0-9-])")
CANDIDATE_COMPONENTS = {"server", "mcp-server", "keycloak-runtime"}
REALM_DEFINITION_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "containsSecrets",
}
REALM_EVIDENCE_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
    "candidateRealmDefinitionMatched",
    "environmentRealmRenderStable",
    "semanticReadbackVerified",
    "containsSecrets",
}
REALM_EVIDENCE_DIGEST_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
}
ISOLATED_VOLUME_SUFFIXES = {
    "caddy_data",
    "caddy_config",
    "db_data",
    "keycloak_data",
    "mailpit_data",
    "nextcloud_data",
    "synapse_data",
    "matrix_chat_appservice_runtime",
    "runtime_state",
}
REQUIRED_PASS_FACTS = (
    "freshAuthorizationCodePkce",
    "chatPassed",
    "filesPassed",
    "calendarPassed",
    "homePassed",
    "profilePassed",
    "outsiderDenied",
    "canonicalJpaVerified",
    "nativePersistenceVerified",
    "idempotencyVerified",
    "cleanupComplete",
)
SIMULATOR_SURFACES = ("home", "chat", "files", "calendar", "settings", "profile")
FORBIDDEN_EVIDENCE_KEYS = {
    "accesstoken",
    "authorization",
    "clientsecret",
    "password",
    "privatekey",
    "refreshtoken",
    "registrationaccesstoken",
}


class EvidenceError(ValueError):
    """Raised when input evidence cannot support the requested claim."""


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise EvidenceError(f"missing {label}: {path}") from error
    except json.JSONDecodeError as error:
        raise EvidenceError(f"invalid {label} JSON: {error}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be a JSON object")
    return value


def require_commit(value: object, label: str) -> str:
    normalized = str(value).lower()
    if COMMIT.fullmatch(normalized) is None:
        raise EvidenceError(f"{label} must be a full lowercase commit")
    return normalized


def require_same_identity(left: dict[str, Any], right: dict[str, Any]) -> None:
    for key in ("candidateCommit", "sourceCandidateCommit", "specCorpusCommit"):
        if require_commit(left.get(key), f"live.{key}") != require_commit(
            right.get(key), f"simulator.{key}"
        ):
            raise EvidenceError(f"live and Simulator evidence disagree on {key}")


def require_support_safe(value: dict[str, Any], label: str) -> None:
    if value.get("supportSafe") is not True:
        raise EvidenceError(f"{label} must declare supportSafe=true")
    require_values_safe(value, label)


def require_values_safe(value: object, label: str) -> None:
    def walk(node: object) -> None:
        if isinstance(node, dict):
            for key, child in node.items():
                normalized = re.sub(r"[^a-z]", "", str(key).lower())
                status_only_authorization = (
                    normalized == "authorization"
                    and child in {"passed", "failed", "blocked", "not_run"}
                )
                if normalized in FORBIDDEN_EVIDENCE_KEYS and not status_only_authorization:
                    raise EvidenceError(f"{label} contains forbidden evidence key {key!r}")
                walk(child)
        elif isinstance(node, list):
            for child in node:
                walk(child)
        elif isinstance(node, str):
            require_safe_text(node, label)

    walk(value)


def require_safe_text(value: str, label: str) -> None:
    lowered = value.lower()
    if (
        "-----begin " in lowered
        or "authorization:" in lowered
        or "bearer " in lowered
        or JWT.search(value) is not None
        or EMAIL.search(value) is not None
        or any(marker in value for marker in ("/Users/", "/home/", "/private/var/"))
    ):
        raise EvidenceError(f"{label} contains a secret-like or private value")
    if value.startswith(("http://", "https://")):
        parsed = urlparse(value)
        sensitive_query = re.search(
            r"(?:^|&)(?:access_token|auth|code|credential|jwt|password|secret|signature|token)=",
            parsed.query,
            flags=re.IGNORECASE,
        )
        if parsed.username is not None or parsed.password is not None or sensitive_query is not None:
            raise EvidenceError(f"{label} contains a credential-bearing URL")


def scan_path(path: Path) -> None:
    try:
        raw = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as error:
        raise EvidenceError(f"cannot scan evidence path: {path}") from error
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        require_safe_text(raw, str(path))
        return
    require_values_safe(parsed, str(path))
    if isinstance(parsed, dict) and parsed.get("supportSafe") is not True:
        raise EvidenceError(f"{path} must declare supportSafe=true")


def require_https_run_url(value: str) -> str:
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise EvidenceError("run URL must be an uncredentialed HTTPS reference")
    return value.rstrip("/")


def require_candidate_manifest(
    manifest: dict[str, Any], product: dict[str, Any], source: str, spec: str
) -> tuple[str, dict[str, str], dict[str, Any]]:
    require_support_safe(manifest, "candidate manifest")
    images = manifest.get("images")
    definition = manifest.get("realmDefinition")
    if (
        manifest.get("schemaVersion") != "weave.release.candidate-manifest.v4"
        or manifest.get("commit") != source
        or manifest.get("specificationCommit") != spec
        or not isinstance(images, list)
        or not isinstance(definition, dict)
        or set(definition) != REALM_DEFINITION_FIELDS
        or HASH.fullmatch(str(definition.get("semanticRealmSourceDigest", ""))) is None
        or HASH.fullmatch(str(definition.get("migrationDefinitionDigest", ""))) is None
        or definition.get("containsSecrets") is not False
    ):
        raise EvidenceError("candidate manifest belongs to another source or specification")
    by_component: dict[str, str] = {}
    for item in images:
        if not isinstance(item, dict):
            raise EvidenceError("candidate manifest image entry must be an object")
        component = item.get("component")
        reference = item.get("reference")
        if (
            not isinstance(component, str)
            or component in by_component
            or not isinstance(reference, str)
            or IMMUTABLE_IMAGE.fullmatch(reference) is None
        ):
            raise EvidenceError("candidate manifest contains a mutable or duplicate image")
        by_component[component] = reference
    if set(by_component) != CANDIDATE_COMPONENTS:
        raise EvidenceError("candidate manifest must contain the exact three runtime images")
    serialized = json.dumps(
        manifest, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    digest = "sha256:" + hashlib.sha256(serialized).hexdigest()
    if product.get("candidateManifestDigest") != digest:
        raise EvidenceError("product evidence does not match the candidate manifest digest")
    return digest, dict(sorted(by_component.items())), dict(definition)


def require_realm_evidence(value: Any, definition: dict[str, Any]) -> dict[str, Any]:
    if (
        not isinstance(value, dict)
        or set(value) != REALM_EVIDENCE_FIELDS
        or any(
            HASH.fullmatch(str(value.get(field, ""))) is None
            for field in REALM_EVIDENCE_DIGEST_FIELDS
        )
        or value.get("semanticRealmSourceDigest")
        != definition.get("semanticRealmSourceDigest")
        or value.get("migrationDefinitionDigest")
        != definition.get("migrationDefinitionDigest")
        or value.get("candidateRealmDefinitionMatched") is not True
        or value.get("environmentRealmRenderStable") is not True
        or value.get("semanticReadbackVerified") is not True
        or value.get("containsSecrets") is not False
    ):
        raise EvidenceError("runtime realm evidence is incomplete or not candidate-bound")
    return dict(value)


def require_runtime_image_evidence(
    runtime: dict[str, Any],
    *,
    candidate: str,
    source: str,
    spec: str,
    manifest_digest: str,
    compose_project: object,
    images: dict[str, str],
    realm_definition: dict[str, Any],
) -> dict[str, Any]:
    require_support_safe(runtime, "runtime image evidence")
    entries = runtime.get("images")
    realm_evidence = require_realm_evidence(runtime.get("realmEvidence"), realm_definition)
    if (
        runtime.get("schemaVersion") != "weave.test-app-runtime-images/v2"
        or runtime.get("candidateCommit") != candidate
        or runtime.get("sourceCandidateCommit") != source
        or runtime.get("specificationCommit") != spec
        or runtime.get("candidateManifestDigest") != manifest_digest
        or runtime.get("composeProject") != compose_project
        or runtime.get("manifestBound") is not True
        or runtime.get("realmEvidenceVerified") is not True
        or runtime.get("credentialsIncluded") is not False
        or runtime.get("containsSecretValues") is not False
        or not isinstance(entries, list)
        or len(entries) != len(CANDIDATE_COMPONENTS)
    ):
        raise EvidenceError(
            "runtime image evidence is not bound to the exact candidate and semantic realm evidence"
        )
    by_component: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise EvidenceError("runtime image evidence contains an invalid image entry")
        component = entry.get("component")
        if not isinstance(component, str) or component in by_component:
            raise EvidenceError("runtime image evidence contains a duplicate image component")
        by_component[component] = entry
    if set(by_component) != CANDIDATE_COMPONENTS:
        raise EvidenceError("runtime image evidence does not contain the exact three images")
    for component, reference in images.items():
        entry = by_component[component]
        if (
            entry.get("immutableReference") != reference
            or entry.get("matchesCandidate") is not True
            or HASH.fullmatch(str(entry.get("localImageId", ""))) is None
            or entry.get("observedImageId") != entry.get("localImageId")
        ):
            raise EvidenceError(f"runtime {component} image does not match the exact candidate")
    return realm_evidence


def require_teardown(
    teardown: dict[str, Any], candidate: str, candidate_manifest_digest: str
) -> str:
    require_support_safe(teardown, "teardown evidence")
    if teardown.get("schemaVersion") != "weave.compose-isolated-teardown.v1":
        raise EvidenceError("teardown evidence has the wrong schema")
    if require_commit(teardown.get("candidateCommit"), "teardown.candidateCommit") != candidate:
        raise EvidenceError("teardown evidence targets another lane candidate")
    teardown_manifest_digest = teardown.get("candidateManifestDigest")
    if (
        not isinstance(teardown_manifest_digest, str)
        or HASH.fullmatch(teardown_manifest_digest) is None
        or teardown_manifest_digest != candidate_manifest_digest
    ):
        raise EvidenceError("teardown evidence targets another candidate manifest")
    if teardown.get("dryRun") is not False or teardown.get("ownershipLabelsVerified") is not True:
        raise EvidenceError("teardown evidence does not prove owned resource removal")
    if teardown.get("containsSecretValues") is not False:
        raise EvidenceError("teardown evidence is not secret-free")
    compose_status = teardown.get("composeDownStatus")
    fallback_attempted = teardown.get("fallbackAttempted")
    if compose_status not in {"passed", "failed", "timed-out"}:
        raise EvidenceError("teardown evidence has no bounded Compose result")
    if not isinstance(fallback_attempted, bool):
        raise EvidenceError("teardown evidence has no exact fallback result")
    if compose_status in {"failed", "timed-out"} and fallback_attempted is not True:
        raise EvidenceError("failed Compose teardown did not use the owned-resource fallback")
    count_fields = (
        "observedContainerCount",
        "fallbackObservedContainerCount",
        "removedContainerCount",
        "remainingContainerCount",
        "remainingVolumeCount",
        "remainingNetworkCount",
        "remainingOwnedResources",
    )
    if any(
        not isinstance(teardown.get(field), int) or teardown[field] < 0
        for field in count_fields
    ):
        raise EvidenceError("teardown evidence has an invalid resource count")
    if any(
        teardown.get(field) != 0
        for field in (
            "remainingContainerCount",
            "remainingVolumeCount",
            "remainingNetworkCount",
            "remainingOwnedResources",
        )
    ):
        raise EvidenceError("teardown evidence left an isolated owned resource")
    namespace = teardown.get("namespace")
    if not isinstance(namespace, str) or re.fullmatch(r"weave-e2e-[0-9a-f]{16}", namespace) is None:
        raise EvidenceError("teardown evidence has no valid isolated namespace")
    volume_prefix = namespace.replace("-", "_")
    expected_volumes = {f"{volume_prefix}_{suffix}" for suffix in ISOLATED_VOLUME_SUFFIXES}
    removed_volumes = teardown.get("removedVolumeNames")
    if (
        not isinstance(removed_volumes, list)
        or not all(isinstance(volume, str) for volume in removed_volumes)
        or set(removed_volumes) != expected_volumes
        or len(removed_volumes) != len(expected_volumes)
        or teardown.get("networkRemoved") is not True
        or teardown.get("removedNetworkName") != f"{namespace}_network"
    ):
        raise EvidenceError("teardown evidence did not remove the exact volume and network set")
    return namespace


def build_live(
    product: dict[str, Any],
    teardown: dict[str, Any],
    candidate_manifest: dict[str, Any],
    runtime_image_evidence: dict[str, Any],
    run_url: str,
) -> dict[str, Any]:
    require_support_safe(product, "product evidence")
    if product.get("schemaVersion") != "weave.test-app-product-flow/v2":
        raise EvidenceError("product evidence has the wrong schema")
    candidate = require_commit(product.get("candidateCommit"), "product.candidateCommit")
    source = require_commit(product.get("sourceCandidateCommit"), "product.sourceCandidateCommit")
    spec = require_commit(product.get("specificationCommit"), "product.specificationCommit")
    compose_project = product.get("composeProject")
    manifest_digest, images, definition = require_candidate_manifest(
        candidate_manifest, product, source, spec
    )
    realm_evidence = require_runtime_image_evidence(
        runtime_image_evidence,
        candidate=candidate,
        source=source,
        spec=spec,
        manifest_digest=manifest_digest,
        compose_project=compose_project,
        images=images,
        realm_definition=definition,
    )
    if product.get("credentialsIncluded") is not False or product.get("actionLinksIncluded") is not False:
        raise EvidenceError("product evidence is not support-safe")
    if product.get("activation") != "keycloak-required-actions-real-chromium":
        raise EvidenceError("product evidence does not prove the real Keycloak browser flow")
    if product.get("humanOAuth") != "authorization_code_pkce_s256":
        raise EvidenceError("product evidence does not prove Authorization Code with PKCE")
    if product.get("workloadOAuth") != "client_credentials_private_key_jwt":
        raise EvidenceError("product evidence does not prove workload private_key_jwt")
    for key in (
        "postgresRestartObserved",
        "runtimeStateRestartObserved",
        "runtimeStateFixtureRestored",
        "sameJpaCellAfterRestart",
        "sameMcpCellAfterRestart",
        "revocationDenied",
        "regrantRestored",
        "sameHumanSubjectAfterRegrant",
        "samePersonRefAfterRegrant",
    ):
        if product.get(key) is not True:
            raise EvidenceError(f"product evidence does not prove {key}")

    collaboration = product.get("collaboration")
    if not isinstance(collaboration, dict) or collaboration.get("repeatCount") != 2:
        raise EvidenceError("product evidence must contain exactly two collaboration passes")
    if collaboration.get("selectedProviders") != {
        "chat": "weave-native", "files": "weave-native", "calendar": "weave-native"
    }:
        raise EvidenceError("default collaboration providers must all be weave-native")
    if collaboration.get("northboundFacades") != {
        "matrix": True, "webdav": True, "caldav": True
    }:
        raise EvidenceError("native collaboration must prove all northbound facades")
    if collaboration.get("southboundProviderDependencyObserved") is not False:
        raise EvidenceError("native collaboration observed a southbound provider dependency")
    hashes = collaboration.get("identityRefHashes")
    if not isinstance(hashes, dict) or set(hashes) != {"author", "collaborator", "outsider"}:
        raise EvidenceError("collaboration identity hashes are incomplete")
    if any(not isinstance(value, str) or HASH.fullmatch(value) is None for value in hashes.values()):
        raise EvidenceError("collaboration identities must use SHA-256 references")
    if len(set(hashes.values())) != 3:
        raise EvidenceError("collaboration identity references must be distinct")
    passes = collaboration.get("passes")
    if not isinstance(passes, list) or len(passes) != 2:
        raise EvidenceError("collaboration evidence must contain two passes")
    for expected, proof in enumerate(passes, start=1):
        if not isinstance(proof, dict) or proof.get("pass") != expected:
            raise EvidenceError("collaboration passes are not ordered one and two")
        for fact in REQUIRED_PASS_FACTS:
            if proof.get(fact) is not True:
                raise EvidenceError(f"collaboration pass {expected} does not prove {fact}")
        if proof.get("southboundProviderDependencyObserved") is not False:
            raise EvidenceError(f"collaboration pass {expected} observed a southbound provider dependency")
        revision = proof.get("nativeRevisionHash")
        if not isinstance(revision, str) or HASH.fullmatch(revision) is None:
            raise EvidenceError(f"collaboration pass {expected} has no native revision hash")
    if passes[0].get("restartContinuityVerified") is not False:
        raise EvidenceError("first collaboration pass must precede the service restart")
    if passes[1].get("restartContinuityVerified") is not True:
        raise EvidenceError("second collaboration pass must prove restart continuity")

    namespace = require_teardown(teardown, candidate, manifest_digest)
    if compose_project != namespace or teardown.get("composeProject") != namespace:
        raise EvidenceError("product, teardown, and isolated Compose namespace disagree")
    run_ref = require_https_run_url(run_url)
    live_ref = f"{run_ref}#isolated-live-product-flow"
    surface = lambda marker: {
        "status": "passed",
        "proofKinds": ["live-provider-backed"],
        "evidenceRefs": [f"{live_ref}-{marker}"],
    }
    return {
        "schemaVersion": "weave.human-testing-automated-live.v2",
        "supportSafe": True,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source,
        "specCorpusCommit": spec,
        "candidateManifestDigest": manifest_digest,
        "composeProject": compose_project,
        "images": images,
        "realmEvidence": realm_evidence,
        "liveE2eRunUrl": run_ref,
        "evidenceMode": "live-provider-backed",
        "isolatedNamespace": namespace,
        "surfaces": {
            "authenticationSession": surface("authentication-session"),
            "home": surface("home"),
            "chat": surface("chat"),
            "files": surface("files"),
            "calendar": surface("calendar"),
            "settings": {"status": "not_run", "proofKinds": [], "evidenceRefs": []},
            "profile": surface("profile"),
        },
        "collaboration": {
            "status": "passed",
            "identityRefHashes": hashes,
            "scenarioResults": {
                "authenticationShell": "passed",
                "home": "passed",
                "chat": "passed",
                "files": "passed",
                "calendar": "passed",
                "settingsProfile": "not_run",
                "failureContainment": "passed",
                "authorization": "passed",
            },
            "cleanupStatus": "passed",
            "repeatCount": 2,
            "nativeRevisionHashes": [proof["nativeRevisionHash"] for proof in passes],
            "restartContinuity": "passed",
            "southboundProviderDependencyObserved": False,
        },
        "evidenceRefs": [live_ref, f"artifact:teardown/{namespace}"],
        "blockers": [],
    }


def combine(live: dict[str, Any], simulator: dict[str, Any]) -> dict[str, Any]:
    require_support_safe(live, "live automated evidence")
    require_support_safe(simulator, "Simulator evidence")
    if live.get("schemaVersion") != "weave.human-testing-automated-live.v2":
        raise EvidenceError("live automated evidence has the wrong schema")
    if simulator.get("schemaVersion") != "weave.ios-simulator-current-surfaces.v1":
        raise EvidenceError("Simulator evidence has the wrong schema")
    if simulator.get("evidenceMode") != "fixture-ui" or simulator.get("freshSimulator") is not True:
        raise EvidenceError("Simulator evidence must be a fresh fixture-ui run")
    if simulator.get("cleanupStatus") != "passed" or simulator.get("remainingOwnedSimulators") != 0:
        raise EvidenceError("Simulator evidence does not prove exact cleanup")
    require_same_identity(live, simulator)
    simulator_surfaces = simulator.get("surfaces")
    if not isinstance(simulator_surfaces, dict):
        raise EvidenceError("Simulator surfaces are missing")
    for name in SIMULATOR_SURFACES:
        if simulator_surfaces.get(name) != "passed":
            raise EvidenceError(f"Simulator surface {name} did not pass")
    live_surfaces = live.get("surfaces")
    collaboration = live.get("collaboration")
    if not isinstance(live_surfaces, dict) or not isinstance(collaboration, dict):
        raise EvidenceError("live surface or collaboration evidence is missing")
    for name in ("authenticationSession", "home", "chat", "files", "calendar", "profile"):
        value = live_surfaces.get(name)
        if not isinstance(value, dict) or value.get("status") != "passed":
            raise EvidenceError(f"live surface {name} did not pass")
    if collaboration.get("status") != "passed" or collaboration.get("cleanupStatus") != "passed":
        raise EvidenceError("live collaboration or cleanup did not pass")
    manifest_digest = live.get("candidateManifestDigest")
    images = live.get("images")
    realm_evidence = live.get("realmEvidence")
    compose_project = live.get("composeProject")
    if (
        not isinstance(manifest_digest, str)
        or HASH.fullmatch(manifest_digest) is None
        or not isinstance(images, dict)
        or set(images) != CANDIDATE_COMPONENTS
        or any(not isinstance(value, str) or IMMUTABLE_IMAGE.fullmatch(value) is None for value in images.values())
        or not isinstance(realm_evidence, dict)
        or set(realm_evidence) != REALM_EVIDENCE_FIELDS
        or any(
            HASH.fullmatch(str(realm_evidence.get(field, ""))) is None
            for field in REALM_EVIDENCE_DIGEST_FIELDS
        )
        or realm_evidence.get("candidateRealmDefinitionMatched") is not True
        or realm_evidence.get("environmentRealmRenderStable") is not True
        or realm_evidence.get("semanticReadbackVerified") is not True
        or realm_evidence.get("containsSecrets") is not False
        or not isinstance(compose_project, str)
        or re.fullmatch(r"weave-e2e-[0-9a-f]{16}", compose_project) is None
    ):
        raise EvidenceError("live evidence lost its manifest, image, realm, or Compose identity")

    simulator_ref = simulator.get("evidenceRef")
    if not isinstance(simulator_ref, str) or not simulator_ref.startswith("artifact:"):
        raise EvidenceError("Simulator evidence reference must be an artifact reference")
    surfaces: dict[str, Any] = {}
    for name in ("authenticationSession", "home", "chat", "files", "calendar", "settings", "profile"):
        refs: list[str] = []
        live_value = live_surfaces.get(name)
        if isinstance(live_value, dict):
            refs.extend(
                value for value in live_value.get("evidenceRefs", []) if isinstance(value, str)
            )
        if name in SIMULATOR_SURFACES:
            refs.append(f"{simulator_ref}#{name}")
        proof_kinds: list[str] = []
        if isinstance(live_value, dict) and live_value.get("status") == "passed":
            proof_kinds.append("live-provider-backed")
        if name in SIMULATOR_SURFACES:
            proof_kinds.append("fixture-ui")
        surfaces[name] = {
            "status": "passed",
            "proofKinds": list(dict.fromkeys(proof_kinds)),
            "evidenceRefs": list(dict.fromkeys(refs)),
        }

    collaboration = dict(collaboration)
    scenario_results = dict(collaboration.get("scenarioResults", {}))
    scenario_results["settingsProfile"] = "passed"
    collaboration["scenarioResults"] = scenario_results
    refs = [
        *(value for value in live.get("evidenceRefs", []) if isinstance(value, str)),
        simulator_ref,
    ]
    return {
        "schemaVersion": 2,
        "supportSafe": True,
        "candidateCommit": live["candidateCommit"],
        "sourceCandidateCommit": live["sourceCandidateCommit"],
        "specCorpusCommit": live["specCorpusCommit"],
        "candidateManifestDigest": manifest_digest,
        "composeProject": compose_project,
        "images": images,
        "realmEvidence": dict(realm_evidence),
        "liveE2eRunUrl": live["liveE2eRunUrl"],
        "evidenceModes": ["live-provider-backed", "fixture-ui"],
        "surfaces": surfaces,
        "collaboration": collaboration,
        "evidenceRefs": list(dict.fromkeys(refs)),
        "blockers": [],
    }


def write_json(path: Path, value: dict[str, Any]) -> None:
    require_support_safe(value, "generated evidence")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    commands = value.add_subparsers(dest="command", required=True)
    live = commands.add_parser("live")
    live.add_argument("--product-evidence", type=Path, required=True)
    live.add_argument("--teardown-evidence", type=Path, required=True)
    live.add_argument("--candidate-manifest", type=Path, required=True)
    live.add_argument("--runtime-image-evidence", type=Path, required=True)
    live.add_argument("--run-url", required=True)
    live.add_argument("--output", type=Path, required=True)
    combined = commands.add_parser("combine")
    combined.add_argument("--live-evidence", type=Path, required=True)
    combined.add_argument("--simulator-evidence", type=Path, required=True)
    combined.add_argument("--output", type=Path, required=True)
    scan = commands.add_parser("scan")
    scan.add_argument("--path", type=Path, action="append", required=True)
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "scan":
            for path in args.path:
                scan_path(path)
            print(f"SUPPORT_SAFE_EVIDENCE_SCAN_RESULT status=passed files={len(args.path)}")
            return 0
        if args.command == "live":
            result = build_live(
                load_object(args.product_evidence, "product evidence"),
                load_object(args.teardown_evidence, "teardown evidence"),
                load_object(args.candidate_manifest, "candidate manifest"),
                load_object(args.runtime_image_evidence, "runtime image evidence"),
                args.run_url,
            )
            marker = "HUMAN_TESTING_LIVE_AUTOMATION_RESULT"
        else:
            result = combine(
                load_object(args.live_evidence, "live automated evidence"),
                load_object(args.simulator_evidence, "Simulator evidence"),
            )
            marker = "HUMAN_TESTING_AUTOMATED_EVIDENCE_RESULT"
        write_json(args.output, result)
        print(f"{marker} status=passed supportSafe=true")
        return 0
    except (EvidenceError, OSError) as error:
        print(f"human-testing-automated-evidence: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
