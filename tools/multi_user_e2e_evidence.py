#!/usr/bin/env python3
"""Build support-safe two-pass collaboration evidence from live test outputs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "specs" / "weave-specs.lock.json"
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")
MARKER_HASH_PATTERN = re.compile(r"^[0-9a-f]{16,64}$")
EMAIL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
)
FORBIDDEN_EVIDENCE_KEYS = {
    "username",
    "email",
    "password",
    "accesstoken",
    "refreshtoken",
    "idtoken",
    "cookie",
    "secret",
    "roomid",
    "eventid",
    "filename",
    "rawresponse",
    "rawproviderresponse",
}
MARKERS = {
    "MULTI_USER_AUTH_SHELL_RESULT": "authenticationShell",
    "MULTI_USER_HOME_RESULT": "home",
    "MULTI_USER_CHAT_RESULT": "chat",
    "MULTI_USER_FILES_RESULT": "files",
    "MULTI_USER_CALENDAR_RESULT": "calendar",
    "MULTI_USER_SETTINGS_PROFILE_RESULT": "settingsProfile",
    "MULTI_USER_FAILURE_CONTAINMENT_RESULT": "failureContainment",
    "MULTI_USER_AUTHORIZATION_RESULT": "authorization",
}


class EvidenceError(ValueError):
    pass


def _normalized_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def require_support_safe_document(value: Any, label: str, path: str = "$") -> None:
    if isinstance(value, dict):
        for key, nested in value.items():
            if _normalized_key(str(key)) in FORBIDDEN_EVIDENCE_KEYS:
                raise EvidenceError(f"{label} contains forbidden sensitive field {path}.{key}")
            require_support_safe_document(nested, label, f"{path}.{key}")
        return
    if isinstance(value, list):
        for index, nested in enumerate(value):
            require_support_safe_document(nested, label, f"{path}[{index}]")
        return
    if isinstance(value, str):
        lowered = value.lower()
        if (
            EMAIL_PATTERN.search(value)
            or "bearer " in lowered
            or "password=" in lowered
            or "token=" in lowered
            or re.search(r"https?://[^/\s]+@", value)
        ):
            raise EvidenceError(f"{label} contains sensitive material at {path}")


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise EvidenceError(f"missing {label}: {path}") from error
    except json.JSONDecodeError as error:
        raise EvidenceError(f"invalid {label} JSON: {error}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    return value


def parse_marker_payloads(log_path: Path) -> dict[str, list[dict[str, Any]]]:
    try:
        lines = log_path.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError as error:
        raise EvidenceError(f"missing live collaboration log: {log_path}") from error
    payloads: dict[str, list[dict[str, Any]]] = {marker: [] for marker in MARKERS}
    for line in lines:
        for marker in MARKERS:
            prefix = f"{marker} "
            position = line.find(prefix)
            if position < 0:
                continue
            raw = line[position + len(prefix) :].strip()
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError as error:
                raise EvidenceError(f"{marker} payload is not JSON: {error}") from error
            if not isinstance(payload, dict):
                raise EvidenceError(f"{marker} payload must be an object")
            payloads[marker].append(payload)
    return payloads


def require_two_passes(payloads: dict[str, list[dict[str, Any]]]) -> dict[str, str]:
    statuses: dict[str, str] = {}
    common_run_hashes: set[str] = set()
    for marker, values in payloads.items():
        if len(values) != 2:
            raise EvidenceError(f"{marker} must be observed exactly twice; observed {len(values)}")
        for value in values:
            require_support_safe_document(value, f"{marker} payload")
        run_indices = {value.get("runIndex") for value in values}
        if run_indices != {1, 2}:
            raise EvidenceError(f"{marker} must contain runIndex 1 and 2")
        observed_statuses = {value.get("status") for value in values}
        if not observed_statuses <= {"passed", "blocked", "failed"}:
            raise EvidenceError(f"{marker} contains an unsupported result status")
        if any(value.get("supportSafe") is not True for value in values):
            raise EvidenceError(f"{marker} must be explicitly support-safe in both passes")
        statuses[marker] = (
            "failed"
            if "failed" in observed_statuses
            else "blocked"
            if "blocked" in observed_statuses
            else "passed"
        )
        run_hashes = {value.get("runHash") for value in values}
        if len(run_hashes) != 1 or not all(
            isinstance(value, str) and MARKER_HASH_PATTERN.fullmatch(value)
            for value in run_hashes
        ):
            raise EvidenceError(f"{marker} does not bind both passes to one run hash")
        common_run_hashes.update(run_hashes)
    if len(common_run_hashes) != 1:
        raise EvidenceError("all collaboration markers must bind to one run hash")
    return statuses


def _require_exact_facts(
    marker: str,
    payload: dict[str, Any],
    expected: dict[str, Any],
) -> None:
    for key, expected_value in expected.items():
        if payload.get(key) != expected_value:
            raise EvidenceError(
                f"{marker} passed evidence requires {key}={expected_value!r}"
            )


def _require_marker_hash(marker: str, payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not MARKER_HASH_PATTERN.fullmatch(value):
        raise EvidenceError(f"{marker} passed evidence requires a hashed {key}")
    return value


def require_passed_marker_facts(
    payloads: dict[str, list[dict[str, Any]]],
) -> None:
    """Reject a green marker that does not prove its claimed live behavior."""

    for marker, values in payloads.items():
        for payload in values:
            if payload.get("status") != "passed":
                continue

            if marker == "MULTI_USER_AUTH_SHELL_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "actorCount": 3,
                        "sessionRestoreCount": 3,
                        "shellReached": True,
                        "authorNavigationCount": 6,
                        "collaboratorNavigationCount": 6,
                        "authorAllDestinationsVisited": True,
                        "collaboratorAllDestinationsVisited": True,
                        "organizationDiscoveryCount": 3,
                        "authorOrganizationDiscovered": True,
                        "collaboratorOrganizationDiscovered": True,
                        "realDeviceStorageProfiles": True,
                    },
                )
                actor_hashes = {
                    _require_marker_hash(marker, payload, key)
                    for key in ("authorHash", "collaboratorHash", "outsiderHash")
                }
                if len(actor_hashes) != 3:
                    raise EvidenceError(
                        f"{marker} passed evidence requires three distinct actor hashes"
                    )
            elif marker == "MULTI_USER_HOME_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "authorObservedCount": 3,
                        "collaboratorObservedCount": 3,
                        "outsiderObservedCount": 0,
                        "sharedActivityCount": 3,
                        "authorizedProjectionMatches": True,
                        "actorPerspectiveMatches": True,
                        "itemLevelProjectionAvailable": True,
                        "unauthorizedActivityExcluded": True,
                    },
                )
            elif marker == "MULTI_USER_CHAT_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "authorMessageObserved": True,
                        "collaboratorReplyObserved": True,
                        "ciphertextOnlyTransport": True,
                        "outsiderDenied": True,
                        "messageCount": 2,
                        "messageCleanupComplete": True,
                        "redactedMessageCount": 2,
                        "roomMembershipCleanupComplete": True,
                    },
                )
                if payload.get("runIndex") == 1:
                    _require_exact_facts(
                        marker,
                        payload,
                        {"coldCollaboratorDeviceSetVerified": True},
                    )
            elif marker == "MULTI_USER_FILES_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "collaboratorObserved": True,
                        "authorUpdateObserved": True,
                        "outsiderDenied": True,
                        "outsiderReadDenied": True,
                        "outsiderMutationDenied": True,
                        "cleanupComplete": True,
                    },
                )
                checksums = {
                    _require_marker_hash(marker, payload, key)
                    for key in ("initialChecksumHash", "updatedChecksumHash")
                }
                if len(checksums) != 2 or any(
                    not HASH_PATTERN.fullmatch(value) for value in checksums
                ):
                    raise EvidenceError(
                        f"{marker} passed evidence requires two distinct SHA-256 checksums"
                    )
            elif marker == "MULTI_USER_CALENDAR_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "collaboratorObserved": True,
                        "authorUpdateObserved": True,
                        "outsiderDenied": True,
                        "outsiderReadDenied": True,
                        "outsiderMutationDenied": True,
                        "eventCount": 1,
                        "cleanupComplete": True,
                    },
                )
            elif marker == "MULTI_USER_SETTINGS_PROFILE_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "profileCount": 3,
                        "settingsPersisted": True,
                        "profilePersisted": True,
                        "identityIsolation": True,
                        "independentLogout": True,
                        "buildIdentityVisible": True,
                        "cleanupComplete": True,
                    },
                )
            elif marker == "MULTI_USER_FAILURE_CONTAINMENT_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "calendarUnavailable": True,
                        "realCapabilitySnapshot": True,
                        "unrelatedRouteCount": 5,
                        "shellPreserved": True,
                    },
                )
            elif marker == "MULTI_USER_AUTHORIZATION_RESULT":
                _require_exact_facts(
                    marker,
                    payload,
                    {
                        "chatDenied": True,
                        "filesDenied": True,
                        "calendarDenied": True,
                        "wrongWorkspaceVerified": True,
                        "missingCapabilityVerified": True,
                        "expiredTokenVerified": True,
                        "revokedSessionVerified": True,
                        "verifiedModeCount": 4,
                    },
                )


def require_run_url(value: str) -> None:
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise EvidenceError(
            "run URL must be an uncredentialed HTTPS evidence reference without query or fragment"
        )


def require_authorization_evidence(
    evidence: dict[str, Any], identity_refs: dict[str, str], namespace_hash: str
) -> None:
    require_support_safe_document(evidence, "authorization evidence")
    if evidence.get("schemaVersion") != "weave.isolated-e2e-authorization.v1":
        raise EvidenceError("authorization evidence schemaVersion is unsupported")
    for key, expected in (
        ("isolatedRuntimeVerified", True),
        ("markerOwnedIdentitiesVerified", True),
        ("persistentHumanChanged", False),
        ("rawIdentityIncluded", False),
        ("rawTokenIncluded", False),
        ("rawProviderPayloadIncluded", False),
        ("supportSafe", True),
    ):
        if evidence.get(key) is not expected:
            raise EvidenceError(f"authorization evidence {key} must be {expected}")
    authorization_namespace = evidence.get("namespaceSha256")
    if (
        not isinstance(authorization_namespace, str)
        or not HASH_PATTERN.fullmatch(authorization_namespace)
    ):
        raise EvidenceError("authorization namespace reference is not a SHA-256 hash")
    if authorization_namespace != namespace_hash:
        raise EvidenceError("authorization evidence is not bound to the identity namespace")
    completed_at = evidence.get("completedAtUtc")
    if not isinstance(completed_at, str) or not completed_at.endswith("Z"):
        raise EvidenceError("authorization evidence completedAtUtc must be a UTC timestamp")
    try:
        parsed_completed_at = datetime.fromisoformat(completed_at.replace("Z", "+00:00"))
    except ValueError as error:
        raise EvidenceError(
            "authorization evidence completedAtUtc must be a UTC timestamp"
        ) from error
    if parsed_completed_at.tzinfo != timezone.utc:
        raise EvidenceError("authorization evidence completedAtUtc must be a UTC timestamp")

    missing = evidence.get("missingCapability")
    if not isinstance(missing, dict):
        raise EvidenceError("authorization evidence missingCapability must be an object")
    missing_actor = missing.get("actorSha256")
    if not isinstance(missing_actor, str) or not HASH_PATTERN.fullmatch(missing_actor):
        raise EvidenceError("missing-capability actor reference is not a SHA-256 hash")
    if f"sha256:{missing_actor}" != identity_refs["collaborator"]:
        raise EvidenceError("missing-capability evidence is not bound to the collaborator")
    for key, expected in (
        ("calendarWriteStatus", 403),
        ("groupRemovedBeforeMint", True),
        ("freshTokenClaimExcludedGroup", True),
        ("supportSafeResponse", True),
        ("groupRestored", True),
    ):
        if missing.get(key) != expected:
            raise EvidenceError(f"missing-capability evidence {key} must be {expected}")

    expired = evidence.get("expiredToken")
    if not isinstance(expired, dict):
        raise EvidenceError("authorization evidence expiredToken must be an object")
    for key, expected in (
        ("boundedLifetimeVerified", True),
        ("realmSettingRestoredBeforeWait", True),
        ("chatStatus", 401),
        ("filesStatus", 401),
        ("calendarStatus", 401),
    ):
        if expired.get(key) != expected:
            raise EvidenceError(f"expired-token evidence {key} must be {expected}")

    revoked = evidence.get("revokedSession")
    if not isinstance(revoked, dict):
        raise EvidenceError("authorization evidence revokedSession must be an object")
    revoked_actor = revoked.get("actorSha256")
    if not isinstance(revoked_actor, str) or not HASH_PATTERN.fullmatch(revoked_actor):
        raise EvidenceError("revoked-session actor reference is not a SHA-256 hash")
    if f"sha256:{revoked_actor}" != identity_refs["author"]:
        raise EvidenceError("revoked-session evidence is not bound to the author")
    for key, expected in (
        ("matrixLogoutStatus", 200),
        ("tokenUnexpiredAtLogout", True),
        ("chatReuseStatus", 401),
    ):
        if revoked.get(key) != expected:
            raise EvidenceError(f"revoked-session evidence {key} must be {expected}")

    restoration = evidence.get("restoration")
    if not isinstance(restoration, dict):
        raise EvidenceError("authorization evidence restoration must be an object")
    for key in (
        "calendarEditorMembership",
        "realmAccessTokenLifespan",
        "weaveAppDirectAccessGrants",
    ):
        if restoration.get(key) is not True:
            raise EvidenceError(f"authorization restoration evidence {key} must be true")


def identity_hashes(identity: dict[str, Any]) -> tuple[dict[str, str], str]:
    require_support_safe_document(identity, "identity evidence")
    if identity.get("schemaVersion") != "weave.isolated-e2e-identities.v1":
        raise EvidenceError("identity evidence schemaVersion is unsupported")
    if identity.get("supportSafe") is not True or identity.get("credentialsIncluded") is not False:
        raise EvidenceError("identity evidence is not support-safe")
    if identity.get("persistentHumanIdentityChanged") is not False:
        raise EvidenceError("identity evidence does not prove persistent human isolation")
    namespace_hash = identity.get("namespaceSha256")
    if not isinstance(namespace_hash, str) or not HASH_PATTERN.fullmatch(namespace_hash):
        raise EvidenceError("identity namespace reference is not a SHA-256 hash")
    context = identity.get("contextAuthorization")
    if not isinstance(context, dict) or context.get("status") != "active_runtime_verified":
        raise EvidenceError("real ReBAC runtime membership was not verified")
    actors = identity.get("actors")
    if not isinstance(actors, list):
        raise EvidenceError("identity actors must be a list")
    result: dict[str, str] = {}
    contexts: dict[str, str] = {}
    for actor in actors:
        if not isinstance(actor, dict):
            continue
        role = actor.get("role")
        value = actor.get("subjectSha256", actor.get("identitySha256"))
        context_hash = actor.get("contextSha256")
        if role in {"author", "collaborator", "outsider"}:
            if not isinstance(value, str) or not HASH_PATTERN.fullmatch(value):
                raise EvidenceError(f"{role} identity reference is not a SHA-256 hash")
            if not isinstance(context_hash, str) or not HASH_PATTERN.fullmatch(context_hash):
                raise EvidenceError(f"{role} context reference is not a SHA-256 hash")
            result[role] = f"sha256:{value}"
            contexts[role] = context_hash
    if set(result) != {"author", "collaborator", "outsider"}:
        raise EvidenceError("identity evidence must contain author, collaborator, and outsider")
    if len(set(result.values())) != 3:
        raise EvidenceError("disposable actor hashes must be distinct")
    if contexts["author"] != contexts["collaborator"] or contexts["outsider"] == contexts["author"]:
        raise EvidenceError("author/collaborator must share a context and outsider must not")
    return result, namespace_hash


def require_cleanup(cleanup: dict[str, Any], namespace_hash: str) -> None:
    require_support_safe_document(cleanup, "cleanup evidence")
    if cleanup.get("schemaVersion") != "weave.isolated-e2e-identity-cleanup.v1":
        raise EvidenceError("cleanup evidence schemaVersion is unsupported")
    for key, expected in (
        ("supportSafe", True),
        ("persistentHumanIdentityChanged", False),
        ("broadCleanupPerformed", False),
        ("credentialsIncluded", False),
        ("rawProviderPayloadIncluded", False),
    ):
        if cleanup.get(key) is not expected:
            raise EvidenceError(f"cleanup evidence {key} must be {expected}")
    cleanup_namespace = cleanup.get("namespaceSha256")
    if not isinstance(cleanup_namespace, str) or not HASH_PATTERN.fullmatch(cleanup_namespace):
        raise EvidenceError("cleanup namespace reference is not a SHA-256 hash")
    if cleanup_namespace != namespace_hash:
        raise EvidenceError("cleanup evidence is not bound to the identity namespace")
    keycloak = cleanup.get("keycloak")
    if not isinstance(keycloak, dict) or keycloak.get("runMarkerVerified") is not True:
        raise EvidenceError("cleanup did not verify the immutable run marker")
    if keycloak.get("usersDeleted") != 3 or keycloak.get("groupsDeleted") != 2:
        raise EvidenceError("cleanup did not delete exactly three users and two run groups")


def require_calendar_outage_recovery(
    evidence: dict[str, Any], namespace_hash: str
) -> None:
    require_support_safe_document(evidence, "Calendar outage evidence")
    if evidence.get("schemaVersion") != "weave.isolated-calendar-outage-fixture.v2":
        raise EvidenceError("Calendar outage evidence schemaVersion is unsupported")
    for key, expected in (
        ("state", "restored"),
        ("calendarCollectionKind", "dedicated-non-default"),
        ("providerDefaultAutoProvisioningEligible", False),
        ("recoveryRequired", False),
        ("persistentDogfoodEligible", False),
        ("credentialsIncluded", False),
        ("rawIdentityIncluded", False),
        ("rawProviderPayloadIncluded", False),
        ("supportSafe", True),
    ):
        if evidence.get(key) != expected:
            raise EvidenceError(f"Calendar outage evidence {key} must be {expected}")
    if evidence.get("namespaceSha256") != namespace_hash:
        raise EvidenceError("Calendar outage evidence is not bound to the identity namespace")
    for key in ("actorSha256", "calendarSha256"):
        value = evidence.get(key)
        if not isinstance(value, str) or not HASH_PATTERN.fullmatch(value):
            raise EvidenceError(f"Calendar outage evidence {key} must be a SHA-256 hash")
    cached_health = evidence.get("cachedHealth")
    if not isinstance(cached_health, dict) or cached_health != {
        "calendarStatus": 2,
        "filesStatus": 2,
    }:
        raise EvidenceError(
            "Calendar outage recovery must finish with Calendar and Files available"
        )


def build_evidence(
    *,
    candidate: str,
    run_url: str,
    payloads: dict[str, list[dict[str, Any]]],
    hashes: dict[str, str],
    marker_statuses: dict[str, str],
) -> dict[str, Any]:
    spec_commit = load_object(LOCK, "spec lock").get("specCorpus", {}).get("gitCommit")
    if not isinstance(spec_commit, str) or not COMMIT_PATTERN.fullmatch(spec_commit):
        raise EvidenceError("pinned spec corpus commit is invalid")
    ref = run_url.rstrip("/")
    surfaces = {
        "authenticationSession": {"status": marker_statuses["MULTI_USER_AUTH_SHELL_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_AUTH_SHELL_RESULT"]},
        "home": {"status": marker_statuses["MULTI_USER_HOME_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_HOME_RESULT"]},
        "chat": {"status": marker_statuses["MULTI_USER_CHAT_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_CHAT_RESULT"]},
        "files": {"status": marker_statuses["MULTI_USER_FILES_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_FILES_RESULT"]},
        "calendar": {"status": marker_statuses["MULTI_USER_CALENDAR_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_CALENDAR_RESULT"]},
        "settings": {"status": marker_statuses["MULTI_USER_SETTINGS_PROFILE_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_SETTINGS_PROFILE_RESULT"]},
        "profile": {"status": marker_statuses["MULTI_USER_SETTINGS_PROFILE_RESULT"], "evidenceRefs": [f"{ref}#MULTI_USER_SETTINGS_PROFILE_RESULT"]},
    }
    scenario_results = {
        scenario: marker_statuses[marker]
        for marker, scenario in MARKERS.items()
    }
    observed_statuses = set(scenario_results.values())
    overall = (
        "failed"
        if "failed" in observed_statuses
        else "blocked"
        if "blocked" in observed_statuses
        else "passed"
    )
    blockers = [
        {
            "code": f"multi-user-{scenario}-not-passed",
            "summary": f"The live {scenario} collaboration scenario is {status}.",
            "candidateCommit": candidate,
        }
        for scenario, status in scenario_results.items()
        if status != "passed"
    ]
    return {
        "schemaVersion": 1,
        "supportSafe": True,
        "candidateCommit": candidate,
        "specCorpusCommit": spec_commit,
        "surfaces": surfaces,
        "collaboration": {
            "status": overall,
            "identityRefHashes": hashes,
            "scenarioResults": scenario_results,
            "cleanupStatus": "passed",
            "repeatCount": 2,
        },
        "evidenceRefs": [
            f"{ref}#multi-user-two-pass",
            "artifact:isolated-identities.json",
            "artifact:isolated-authorization.json",
            "artifact:isolated-calendar-outage.json",
            "artifact:isolated-cleanup.json",
        ],
        "blockers": blockers,
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--candidate-commit", required=True)
    value.add_argument("--run-url", required=True)
    value.add_argument("--test-log", type=Path, required=True)
    value.add_argument("--identity-evidence", type=Path, required=True)
    value.add_argument("--authorization-evidence", type=Path, required=True)
    value.add_argument("--calendar-outage-evidence", type=Path, required=True)
    value.add_argument("--cleanup-evidence", type=Path, required=True)
    value.add_argument("--output", type=Path, required=True)
    value.add_argument("--require-passed", action="store_true")
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    candidate = args.candidate_commit.lower()
    if not COMMIT_PATTERN.fullmatch(candidate):
        print("multi-user-e2e-evidence: candidate commit must be a full lowercase SHA-1", file=sys.stderr)
        return 64
    try:
        require_run_url(args.run_url)
        payloads = parse_marker_payloads(args.test_log)
        marker_statuses = require_two_passes(payloads)
        require_passed_marker_facts(payloads)
        hashes, namespace_hash = identity_hashes(
            load_object(args.identity_evidence, "identity evidence")
        )
        require_authorization_evidence(
            load_object(args.authorization_evidence, "authorization evidence"),
            hashes,
            namespace_hash,
        )
        require_calendar_outage_recovery(
            load_object(args.calendar_outage_evidence, "Calendar outage evidence"),
            namespace_hash,
        )
        require_cleanup(
            load_object(args.cleanup_evidence, "cleanup evidence"), namespace_hash
        )
        evidence = build_evidence(
            candidate=candidate,
            run_url=args.run_url,
            payloads=payloads,
            hashes=hashes,
            marker_statuses=marker_statuses,
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        status = evidence["collaboration"]["status"]
        print(
            "MULTI_USER_AUTOMATED_EVIDENCE_RESULT "
            f"status={status} repeatCount=2 actors=3 supportSafe=true"
        )
        return 1 if args.require_passed and status != "passed" else 0
    except EvidenceError as error:
        print(f"multi-user-e2e-evidence: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
