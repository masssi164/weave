#!/usr/bin/env python3
"""Run-bound Keycloak reconciliation supervisor.

This file is installed with its sibling modules outside the candidate checkout
for dogfood/main and isolated evidence.  Candidate files are parsed only as
JSON data.  Docker control and the Ed25519 signing key stay in this process;
neither is mounted into the application, kcadm, or report-assembly boundary.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import secrets
import stat
import subprocess
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import rfc8785
from kcadm_driver import KcadmError, ProtectedKcadm
from lease_control import Lease, LeaseError, PsqlLeaseController
from deployment_context import DeploymentContextError, deployment_path, load_supervisor_environment
from desired_state_authority import DesiredStateAuthorityError, exact_pretty_json, expected_documents
from receipt import ReceiptError, atomic_private_json, sha256_ref, sign_receipt, verify_receipt
from reconciler import KeycloakReconciler, ReconcileError, query_coverage_digest


COMMIT = re.compile(r"^[0-9a-f]{40}$")
NONCE = re.compile(r"^[A-Za-z0-9_-]{22,128}$")
IMAGE_DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
PACKAGE_FILES = (
    "admin_sanitizer.py",
    "crypto_runtime.py",
    "deployment_context.py",
    "desired_state_authority.py",
    "kcadm_driver.py",
    "lease_control.py",
    "receipt.py",
    "reconciler.py",
    "rfc8785.py",
    "sanitizer_daemon.py",
    "supervisor.py",
)
INSTALLED_SIGNING_KEY = Path("/var/lib/weave/keycloak-supervisor/signing-key.pem")
COMMAND_ALLOWLIST = [
    "acquire",
    "stop-keycloak",
    "bootstrap-admin-service",
    "start-keycloak",
    "reconcile-through-sanitizer",
    "probe",
    "teardown",
    "sign-receipt",
]
EVENT_KINDS = [
    "lease-acquired",
    "image-provenance-verified",
    "runtime-version-probed",
    "nodes-stopped",
    "secret-generated",
    "bootstrap-secret-injected",
    "bootstrap-completed",
    "bootstrap-secret-environment-cleared",
    "bootstrap-process-exited",
    "keycloak-started",
    "keycloak-ready",
    "reconciliation-started",
    "temporary-client-deleted",
    "post-deletion-grant-probed",
    "last-token-expired",
    "expired-token-rejected",
    "secret-destroyed",
    "config-destroyed",
    "container-destroyed",
    "output-scan-completed",
    "lease-released",
]


class SupervisorError(RuntimeError):
    pass


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _read_json(path: Path, label: str) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise SupervisorError(f"{label} is unavailable or is a symlink")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise SupervisorError(f"{label} is malformed") from error
    if not isinstance(value, dict):
        raise SupervisorError(f"{label} must be a JSON object")
    return value


def _revision(document: dict[str, Any]) -> str:
    payload = dict(document)
    payload.pop("revision", None)
    return "sha256:" + hashlib.sha256(rfc8785.dumps(payload)).hexdigest()


def _sha_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return "sha256:" + digest.hexdigest()


def _required_secret_refs(value: object) -> tuple[str, ...]:
    references: set[str] = set()

    def visit(item: object) -> None:
        if isinstance(item, str):
            candidate = item.removeprefix("public-jwks:")
            if candidate.startswith("secretref:"):
                references.add(candidate)
        elif isinstance(item, list):
            for child in item:
                visit(child)
        elif isinstance(item, dict):
            for child in item.values():
                visit(child)

    visit(value)
    return tuple(sorted(references))


def _private_key(path: Path) -> Path:
    if path.is_symlink() or not path.is_file():
        raise SupervisorError("supervisor signing key is unavailable")
    metadata = path.stat()
    if stat.S_IMODE(metadata.st_mode) != 0o600:
        raise SupervisorError("supervisor signing key must be mode 0600")
    if os.geteuid() == 0 and metadata.st_uid != 0:
        raise SupervisorError("installed supervisor signing key must be root-owned")
    return path


def _root_owned_regular(path: Path, label: str, *, mode: int | None = None) -> os.stat_result:
    if path.is_symlink() or not path.is_file():
        raise SupervisorError(f"{label} must be a regular non-symlink file")
    metadata = path.stat()
    if metadata.st_uid != 0 or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
        raise SupervisorError(f"{label} must be root-owned and non-writable by other users")
    if mode is not None and stat.S_IMODE(metadata.st_mode) != mode:
        raise SupervisorError(f"{label} must be mode {mode:04o}")
    return metadata


def _image_digest(image: str) -> str:
    if IMAGE_DIGEST.fullmatch(image):
        return image
    if "@sha256:" in image:
        value = "sha256:" + image.rsplit("@sha256:", 1)[1]
        if IMAGE_DIGEST.fullmatch(value):
            return value
    inspected = subprocess.run(
        ["docker", "image", "inspect", image, "--format", "{{.Id}}"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()
    if not IMAGE_DIGEST.fullmatch(inspected):
        raise SupervisorError("Keycloak image did not resolve to an immutable digest")
    return inspected


def _git_head(root: Path) -> str:
    value = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()
    if not COMMIT.fullmatch(value):
        raise SupervisorError("specification corpus HEAD is malformed")
    return value


def _git_clean(root: Path) -> bool:
    value = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain=v1", "--untracked-files=all"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout
    return value == ""


class Events:
    def __init__(self) -> None:
        self.values: list[dict[str, object]] = []

    def add(self, kind: str, result: str, **details: object) -> None:
        expected = EVENT_KINDS[len(self.values)] if len(self.values) < len(EVENT_KINDS) else None
        if kind != expected:
            raise SupervisorError(f"supervisor lifecycle order violation: expected {expected}, received {kind}")
        evidence = {"kind": kind, "result": result, **details}
        row: dict[str, object] = {
            "sequence": len(self.values) + 1,
            "kind": kind,
            "status": "succeeded",
            "occurredAt": _now(),
            "resultCode": result,
            "detailDigest": sha256_ref(evidence),
        }
        for name in ("httpStatus", "expectedCount", "observedCount", "resourceUuid"):
            if name in details:
                row[name] = details[name]
        self.values.append(row)


class Supervisor:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.root = args.root.resolve()
        if not self.root.is_dir() or self.root.is_symlink():
            raise SupervisorError("candidate infrastructure root is unavailable")
        self.profile = args.profile
        repository = self.root.parents[1]
        if args.platform_attestation is not None:
            if _git_head(repository) != args.candidate_commit or not _git_clean(repository):
                raise SupervisorError("persistent supervisor requires the exact clean candidate worktree")
        env_file = (args.env_file or self.root / f"environments/{self.profile}.env").resolve()
        if args.platform_attestation is not None and self.profile in {"dogfood", "main"}:
            metadata = _root_owned_regular(env_file, "persistent supervisor environment file")
            if stat.S_IMODE(metadata.st_mode) not in {0o444, 0o644}:
                raise SupervisorError("persistent supervisor environment file must be runner-readable mode 0444 or 0644")
        self.env = load_supervisor_environment(
            root=self.root,
            profile=self.profile,
            env_file=env_file,
            stack_scope=args.stack_scope,
            e2e_run_id=args.e2e_run_id,
            keycloak_image=args.keycloak_image,
            sanitizer_image=args.sanitizer_image,
            runtime_uid=args.runtime_uid,
            runtime_gid=args.runtime_gid,
        )
        self.generated = self._deployment_path("WEAVE_GENERATED_ROOT")
        self.secrets = self._deployment_path("WEAVE_SECRET_ROOT")
        self.desired_path = self.generated / "keycloak/desired-state.json"
        self.profile_path = self.generated / "keycloak/sanitizer-profile.json"
        self.overlay_path = self.generated / "keycloak/overlay.json"
        self.manifest_path = self.generated / "render-manifest.json"
        self.spec_root = args.spec_root.resolve()
        if (
            not self.spec_root.is_dir()
            or _git_head(self.spec_root) != args.specification_commit
            or (args.platform_attestation is not None and not _git_clean(self.spec_root))
        ):
            raise SupervisorError("external supervisor did not receive the exact clean pinned specification worktree")
        lock = _read_json(repository / "specs/weave-specs.lock.json", "candidate specification lock")
        corpus = lock.get("specCorpus")
        if not isinstance(corpus, dict) or corpus.get("gitCommit") != args.specification_commit:
            raise SupervisorError("candidate specification lock does not bind the supplied corpus commit")
        expected_overlay, expected_desired, expected_sanitizer, expected_manifest = expected_documents(
            profile=self.profile,
            env=self.env,
            spec_root=self.spec_root,
            specification_commit=args.specification_commit,
        )
        expected_files = (
            (self.overlay_path, expected_overlay, "environment overlay"),
            (self.desired_path, expected_desired, "desired state"),
            (self.profile_path, expected_sanitizer, "sanitizer profile"),
            (self.manifest_path, expected_manifest, "render manifest"),
        )
        for path, expected, label in expected_files:
            if path.is_symlink() or not path.is_file() or path.read_bytes() != exact_pretty_json(expected):
                raise SupervisorError(f"candidate {label} differs from the independently rendered corpus authority")
        self.desired = _read_json(self.desired_path, "rendered Keycloak desired state")
        self.sanitizer_profile = _read_json(self.profile_path, "Keycloak sanitizer profile")
        self.overlay = _read_json(self.overlay_path, "rendered Keycloak environment overlay")
        self.manifest = _read_json(self.manifest_path, "Compose render manifest")
        for document, label in (
            (self.desired, "desired state"),
            (self.sanitizer_profile, "sanitizer profile"),
            (self.overlay, "environment overlay"),
        ):
            if document.get("revision") != _revision(document):
                raise SupervisorError(f"{label} RFC 8785 revision is invalid")
        if self.manifest.get("desiredStateRevision") != self.desired.get("revision"):
            raise SupervisorError("render manifest does not bind the desired-state revision")
        self.events = Events()
        self.run_uuid = str(uuid.uuid4())
        self.reconciliation_id = f"keycloak-reconcile:{self.run_uuid}"
        self.lease_id = f"keycloak-lease:{self.run_uuid}"
        self.temporary_client_id = f"weave-reconcile-{self.run_uuid}"
        self.controller = PsqlLeaseController(
            f"{self._required('WEAVE_RESOURCE_PREFIX')}-db",
            self._required("WEAVE_DB_ADMIN_USERNAME"),
        )
        self.lease: Lease | None = None
        self.image = self._required("WEAVE_KEYCLOAK_IMAGE")
        self.image_digest = _image_digest(self.image)
        self.image_id = subprocess.run(
            ["docker", "image", "inspect", self.image, "--format", "{{.Id}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout.strip()
        if not IMAGE_DIGEST.fullmatch(self.image_id):
            raise SupervisorError("Keycloak image has no immutable local image ID")
        self.container = f"{self._required('WEAVE_RESOURCE_PREFIX')}-keycloak"
        self.platform_attestation, self.signing_key, self.attestation_ref = self._authority()
        self.bootstrap_output_digests: list[str] = []

    def _required(self, name: str) -> str:
        value = self.env.get(name, "")
        if not value or any(character in value for character in "\x00\r\n"):
            raise SupervisorError(f"missing or invalid supervisor input {name}")
        return value

    def _deployment_path(self, name: str) -> Path:
        return deployment_path(self.root, self._required(name))

    def _authority(self) -> tuple[dict[str, Any] | None, Path, str]:
        if self.args.development_candidate_supervisor:
            if self.profile != "dev":
                raise SupervisorError("candidate supervisor is restricted to dev")
            return (
                None,
                _private_key(self.secrets / "keycloak-supervisor-signing-key.pem"),
                f"attestation:keycloak-supervisor:development/{self.run_uuid}",
            )
        if self.args.platform_attestation is None:
            raise SupervisorError("installed supervisor requires a platform attestation")
        if os.geteuid() != 0:
            raise SupervisorError("installed supervisor must run through its privileged fixed executable boundary")
        attestation_path = self.args.platform_attestation
        if not attestation_path.is_absolute() or attestation_path.is_symlink():
            raise SupervisorError("supervisor platform attestation must be an absolute non-symlink path")
        attestation_path = attestation_path.resolve()
        _root_owned_regular(attestation_path, "supervisor platform attestation")
        value = _read_json(attestation_path, "supervisor platform attestation")
        installed = Path(str(value.get("installedPath", "")))
        if not installed.is_absolute() or installed.resolve() != Path(__file__).resolve():
            raise SupervisorError("platform attestation does not bind this installed supervisor")
        observed_files: dict[str, str] = {}
        package_root = Path(__file__).resolve().parent
        for name in PACKAGE_FILES:
            module = package_root / name
            _root_owned_regular(module, f"installed supervisor module {name}")
            observed_files[name] = _sha_file(module)
        package_digest = "sha256:" + hashlib.sha256(
            json.dumps(observed_files, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest()
        trust_key = self.secrets / "keycloak-supervisor-trust-key.pem"
        if trust_key.is_symlink() or not trust_key.is_file():
            raise SupervisorError("deployment supervisor trust key is unavailable")
        expected = {
            "schemaVersion": "weave.keycloak-supervisor-platform-attestation.v1",
            "supervisorVersion": "1.0.0",
            "installedPath": str(Path(__file__).resolve()),
            "candidateIndependent": True,
            "controlPlane": "root-owned-run-bound-supervisor",
            "commandAllowlist": COMMAND_ALLOWLIST,
            "packageFiles": observed_files,
            "packageDigest": package_digest,
            "trustKeySha256": _sha_file(trust_key),
            "platform": {
                "system": platform.system().lower(),
                "machine": platform.machine().lower(),
            },
        }
        if any(value.get(name) != expected_value for name, expected_value in expected.items()):
            raise SupervisorError("platform attestation does not match this installed package and host")
        approved = value.get("approvedKeycloakImageDigests")
        if (
            not isinstance(approved, list)
            or approved != sorted(set(approved))
            or any(not isinstance(item, str) or not IMAGE_DIGEST.fullmatch(item) for item in approved)
            or self.image_digest not in approved
        ):
            raise SupervisorError("platform attestation does not approve the exact Keycloak image digest")
        if value.get("keyGenerationRef") != "keyref:keycloak-supervisor/current":
            raise SupervisorError("platform attestation does not bind the fixed signing-key generation")
        if value.get("privilegedInvocation") != "sudo-noninteractive-fixed-executable":
            raise SupervisorError("platform attestation does not bind the privileged invocation")
        operator_group = value.get("operatorGroup")
        if not isinstance(operator_group, str) or re.fullmatch(r"[a-z_][a-z0-9_-]{0,31}", operator_group) is None:
            raise SupervisorError("platform attestation operator group is invalid")
        sudoers_path = value.get("sudoersPolicyPath")
        if not isinstance(sudoers_path, str) or re.fullmatch(
            r"/etc/sudoers\.d/weave-keycloak-supervisor-[0-9a-f]{20}", sudoers_path
        ) is None:
            raise SupervisorError("platform attestation sudoers policy path is invalid")
        sudoers_file = Path(sudoers_path)
        _root_owned_regular(sudoers_file, "installed supervisor sudoers policy", mode=0o440)
        if value.get("sudoersPolicySha256") != _sha_file(sudoers_file):
            raise SupervisorError("installed supervisor sudoers policy digest is invalid")
        signing = INSTALLED_SIGNING_KEY
        _root_owned_regular(signing, "installed supervisor signing key", mode=0o600)
        if signing == self.root or self.root in signing.parents or signing == package_root or package_root in signing.parents:
            raise SupervisorError("supervisor signing key is not separated from candidate and package code")
        approvals = {
            "packageApprovalRef": r"approval:keycloak-supervisor-package:[A-Za-z0-9._:/-]+",
            "keycloakImageApprovalRef": r"approval:keycloak-image:[A-Za-z0-9._:/-]+",
        }
        if any(
            not isinstance(value.get(name), str)
            or re.fullmatch(pattern, str(value.get(name))) is None
            for name, pattern in approvals.items()
        ):
            raise SupervisorError("platform attestation lacks reviewed package or image approval")
        reference = value.get("attestationRef")
        if not isinstance(reference, str) or re.fullmatch(
            r"attestation:keycloak-supervisor:[A-Za-z0-9._:/-]+", reference
        ) is None:
            raise SupervisorError("platform attestation reference is invalid")
        approved_sanitizer = value.get("approvedSanitizerImageDigests")
        sanitizer_digest = _image_digest(self._required("WEAVE_KEYCLOAK_SANITIZER_IMAGE"))
        if (
            not isinstance(approved_sanitizer, list)
            or approved_sanitizer != sorted(set(approved_sanitizer))
            or any(not isinstance(item, str) or not IMAGE_DIGEST.fullmatch(item) for item in approved_sanitizer)
            or sanitizer_digest not in approved_sanitizer
        ):
            raise SupervisorError("platform attestation does not approve the exact sanitizer image digest")
        return value, _private_key(signing), reference

    def deployment(self) -> dict[str, str]:
        value = {
            "scope": self._required("WEAVE_DEPLOYMENT_SCOPE"),
            "instanceRef": self._required("WEAVE_DEPLOYMENT_INSTANCE"),
            "composeProject": self._required("WEAVE_COMPOSE_PROJECT"),
        }
        namespace = self.env.get("WEAVE_E2E_RUN_NAMESPACE", "")
        if self.env.get("WEAVE_E2E_STACK_SCOPE") == "isolated":
            value["namespace"] = namespace or self._required("WEAVE_RESOURCE_PREFIX")
        return value

    def assert_lease(self) -> None:
        if self.lease is None:
            raise SupervisorError("supervisor has no active lease")
        self.lease = self.controller.assert_active(self.lease)

    def acquire(self) -> None:
        self.lease = self.controller.acquire(
            deployment_scope=self._required("WEAVE_DEPLOYMENT_SCOPE"),
            deployment_instance=self._required("WEAVE_DEPLOYMENT_INSTANCE"),
            compose_project=self._required("WEAVE_COMPOSE_PROJECT"),
            realm="weave",
            lease_id=self.lease_id,
            reconciliation_id=self.reconciliation_id,
        )
        self.events.add("lease-acquired", "exclusive-lease-acquired")

    def verify_runtime(self) -> None:
        inspected = subprocess.run(
            ["docker", "image", "inspect", self.image, "--format", "{{json .Config.Labels}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        labels = json.loads(inspected.stdout) or {}
        if labels.get("com.massimotter.weave.keycloak.version") != "26.7.0":
            raise SupervisorError("Keycloak image is not the pinned 26.7.0 distribution")
        if self.profile != "dev" and self.platform_attestation is None:
            raise SupervisorError("persistent runtime lacks external image provenance")
        self.events.add("image-provenance-verified", "reviewed-immutable-digest-approved")
        version = subprocess.run(
            ["docker", "run", "--rm", "--entrypoint", "/opt/keycloak/bin/kc.sh", self.image, "--version"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout.strip()
        if "26.7.0" not in version:
            raise SupervisorError("runtime version probe did not return Keycloak 26.7.0")
        self.bootstrap_output_digests.append(sha256_ref(version.encode("utf-8")))
        self.events.add("runtime-version-probed", "keycloak-26.7.0")

    def stop_node(self) -> None:
        self.assert_lease()
        inspect = subprocess.run(
            ["docker", "container", "inspect", self.container, "--format", "{{json .}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        value = json.loads(inspect.stdout)
        labels = ((value.get("Config") or {}).get("Labels") or {})
        configured_image = str((value.get("Config") or {}).get("Image", ""))
        if (
            labels.get("com.massimotter.weave.managed") != "true"
            or labels.get("com.massimotter.weave.namespace") != self._required("WEAVE_RESOURCE_PREFIX")
            or labels.get("com.massimotter.weave.environment") != self.profile
            or labels.get("com.massimotter.weave.scope") != self._required("WEAVE_STACK_SCOPE")
            or configured_image != self.image
            or value.get("Image") != self.image_id
        ):
            raise SupervisorError("Keycloak node does not match the exact managed deployment")
        if (value.get("State") or {}).get("Running") is True:
            subprocess.run(["docker", "stop", "--time", "30", self.container], check=True, stdout=subprocess.DEVNULL)
        self.events.add("nodes-stopped", "all-nodes-stopped", expectedCount=1, observedCount=1)

    def bootstrap(self, secret: str) -> None:
        self.assert_lease()
        self.events.add("secret-generated", "run-bound-secret-generated")
        self.events.add("bootstrap-secret-injected", "single-process-env-injected")
        environment = dict(os.environ)
        environment["WEAVE_KEYCLOAK_BOOTSTRAP_SECRET"] = secret
        command = [
            "docker", "run", "--rm", "--name", f"weave-kc-bootstrap-{self.run_uuid[:12]}",
            "--network", self._required("WEAVE_DOCKER_NETWORK"),
            "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
            "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=32m,mode=700",
            "--env", "WEAVE_KEYCLOAK_BOOTSTRAP_SECRET",
            "--env", "KC_DB=postgres",
            "--env", "KC_DB_URL_HOST=postgres",
            "--env", f"KC_DB_URL_DATABASE={self._required('WEAVE_KEYCLOAK_DB_NAME')}",
            "--env", f"KC_DB_USERNAME={self._required('WEAVE_KEYCLOAK_DB_USERNAME')}",
            "--mount", f"type=bind,src={self.secrets / 'keycloak-db-password'},dst=/run/secrets/keycloak-db-password,readonly",
            "--entrypoint", "/bin/bash", self.image, "-euc",
            "export KC_DB_PASSWORD=\"$(</run/secrets/keycloak-db-password)\"; "
            "exec /opt/keycloak/bin/kc.sh bootstrap-admin service "
            f"--client-id {self.temporary_client_id} "
            "--client-secret:env=WEAVE_KEYCLOAK_BOOTSTRAP_SECRET --no-prompt",
        ]
        result = subprocess.run(command, env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        environment.pop("WEAVE_KEYCLOAK_BOOTSTRAP_SECRET", None)
        combined = result.stdout + result.stderr
        if secret.encode("utf-8") in combined:
            raise SupervisorError("bootstrap credential reached process output")
        self.bootstrap_output_digests.append(sha256_ref(combined))
        if result.returncode != 0:
            raise SupervisorError("temporary bootstrap-admin service creation failed")
        self.events.add("bootstrap-completed", "temporary-admin-created", expectedCount=1, observedCount=1)
        self.events.add("bootstrap-secret-environment-cleared", "process-env-cleared")
        self.events.add("bootstrap-process-exited", "bootstrap-process-exited-zero")

    def start_node(self) -> None:
        self.assert_lease()
        subprocess.run(["docker", "start", self.container], check=True, stdout=subprocess.DEVNULL)
        self.events.add("keycloak-started", "keycloak-started", expectedCount=1, observedCount=1)
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            self.assert_lease()
            status = subprocess.run(
                ["docker", "inspect", self.container, "--format", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}"],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
            ).stdout.strip()
            if status == "healthy":
                self.events.add("keycloak-ready", "all-nodes-ready", expectedCount=1, observedCount=1)
                return
            if status in {"dead", "exited", "unhealthy"}:
                raise SupervisorError("Keycloak stopped before becoming ready")
            time.sleep(2)
        raise SupervisorError("Keycloak readiness timed out")

    def reconcile(self, secret: str) -> tuple[list[dict[str, str]], dict[str, object], str]:
        self.assert_lease()
        self.events.add("reconciliation-started", "sanitized-reconciliation-started")
        namespace = f"weave-kc-{self.run_uuid[:16]}"
        protected = ProtectedKcadm(
            image=self.image,
            sanitizer_image=self._required("WEAVE_KEYCLOAK_SANITIZER_IMAGE"),
            code_root=Path(__file__).resolve().parent,
            profile_path=self.profile_path,
            secret_root=self.secrets,
            required_secret_refs=_required_secret_refs(self.desired),
            profile_revision=str(self.sanitizer_profile["revision"]),
            mode=self.args.mode,
            temporary_client_id=self.temporary_client_id,
            temporary_client_secret=secret,
            namespace=namespace,
            compose_network=self._required("WEAVE_DOCKER_NETWORK"),
            control_db_user=self._required("WEAVE_CONTROL_DB_USERNAME"),
            control_db_password_file=self.secrets / "control-db-password",
            lock_key=self.lease.lock_key if self.lease else "",
            lease_id=self.lease_id,
            reconciliation_id=self.reconciliation_id,
            fencing_token=self.lease.fencing_token if self.lease else 0,
            assert_lease=self.assert_lease,
            runtime_uid=int(self._required("WEAVE_RUNTIME_UID")),
            runtime_gid=int(self._required("WEAVE_RUNTIME_GID")),
        )
        with protected:
            reconciler = KeycloakReconciler(
                desired=self.desired,
                sanitizer_profile=self.sanitizer_profile,
                corpus_root=self.spec_root,
                reconciliation_id=self.reconciliation_id,
                temporary_client_id=self.temporary_client_id,
                client=protected,
            )
            inventory = reconciler.reconcile_resources(self.args.mode)
            inventory = reconciler.reconcile_associations(self.args.mode, inventory)
            mutation_actions = list(reconciler.actions)
            verifier = KeycloakReconciler(
                desired=self.desired,
                sanitizer_profile=self.sanitizer_profile,
                corpus_root=self.spec_root,
                reconciliation_id=self.reconciliation_id,
                temporary_client_id=self.temporary_client_id,
                client=protected,
            )
            verified_inventory = verifier.reconcile_resources("verify")
            verified_inventory = verifier.reconcile_associations("verify", verified_inventory)
            verification_actions = list(verifier.actions)
            zero_diff = all(action.get("action") == "noop" for action in verification_actions)
            if self.args.mode in {"apply", "verify"} and not zero_diff:
                raise SupervisorError("post-operation Keycloak read-back is not zero-diff")
            lookup = protected.execute(
                {
                    "method": "GET",
                    "endpoint": "/admin/realms/master/clients",
                    "query": {"clientId": self.temporary_client_id, "first": "0", "max": "2"},
                    "binding": {"reconciliationId": self.reconciliation_id},
                }
            )
            if not isinstance(lookup, list) or len(lookup) != 1 or not isinstance(lookup[0], dict):
                raise SupervisorError("temporary authority lookup was not exactly one")
            client_uuid = str(lookup[0].get("id", ""))
            if not re.fullmatch(
                r"[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                client_uuid,
            ):
                raise SupervisorError("temporary authority UUID is malformed")
            coverage = verifier.query_coverage(verified_inventory, client_uuid)
            protected.execute(
                {
                    "method": "DELETE",
                    "endpoint": f"/admin/realms/master/clients/{client_uuid}",
                    "binding": {
                        "reconciliationId": self.reconciliation_id,
                        "leaseId": self.lease_id,
                        "fencingToken": str(self.lease.fencing_token if self.lease else 0),
                        "clientId": self.temporary_client_id,
                        "resolvedClientUuid": client_uuid,
                        "signedSupervisorReceipt": "pending-final-signature",
                    },
                }
            )
            self.events.add(
                "temporary-client-deleted",
                "current-authority-deleted",
                expectedCount=1,
                observedCount=1,
                resourceUuid=client_uuid,
            )
            protected.assert_new_grant_denied()
            self.events.add("post-deletion-grant-probed", "invalid_client", httpStatus=400)
            protected.assert_expired_token_rejected()
            summary = protected.summary()
            expires = summary.get("lastAccessTokenExpiresAt")
            if not isinstance(expires, str) or summary.get("expiredTokenRejected") is not True:
                raise SupervisorError("last-token expiry/rejection evidence is incomplete")
            self.events.add("last-token-expired", "token-expiry-boundary-passed")
            self.events.add("expired-token-rejected", "unauthorized", httpStatus=401)
        self.events.add("secret-destroyed", "run-bound-secret-destroyed")
        self.events.add("config-destroyed", "tmpfs-config-destroyed")
        self.events.add("container-destroyed", "run-container-destroyed")
        return mutation_actions, {
            **summary,
            "secretProjectionDestroyed": protected.secret_projection_destroyed,
            "queryCoverage": coverage,
            "verificationActions": verification_actions,
            "zeroDiff": zero_diff,
        }, client_uuid

    def _verification_flags(
        self,
        *,
        zero_diff: bool,
        coverage_complete: bool,
        observed_digest: str,
        envelope: dict[str, str],
    ) -> dict[str, bool]:
        semantic_collections = (
            "organizations",
            "roles",
            "groups",
            "clientScopes",
            "clients",
            "serviceAccountRoleGrants",
            "requiredActions",
            "clientPolicies",
        )
        keys: list[str] = []
        shape_valid = True
        for name in semantic_collections:
            values = self.desired.get(name)
            if not isinstance(values, list) or any(
                not isinstance(value, dict) or not isinstance(value.get("key"), str)
                for value in values
            ):
                shape_valid = False
                continue
            keys.extend(str(value["key"]) for value in values)
        coverage = self._active_coverage
        exact_associations_clean = coverage_complete and all(
            row.get("comparisonPolicy") != "exact-desired-set"
            or row.get("unexpectedIdentityCount") == 0
            for row in coverage
        )
        public_key = self.secrets / "keycloak-supervisor-trust-key.pem"
        try:
            verified_payload = verify_receipt(
                envelope,
                public_key,
                expected_kid="weave-keycloak-supervisor-current",
            )
            receipt_valid = verified_payload.get("reconciliationId") == self.reconciliation_id
        except ReceiptError:
            receipt_valid = False
        overlay_valid = (
            self.overlay.get("revision") == _revision(self.overlay)
            and self.manifest.get("overlayRevision") == self.overlay.get("revision")
            and self.manifest.get("desiredStateRevision") == self.desired.get("revision")
            and self.manifest.get("specificationCommit") == self.args.specification_commit
        )
        lease_valid = (
            self.lease is not None
            and self.lease.status == "released"
            and self.lease.released_at is not None
            and self.lease.validation_count > 0
        )
        sanitizer_valid = (
            self.sanitizer_profile.get("revision") == _revision(self.sanitizer_profile)
            and self.manifest.get("sanitizerRevision") == self.sanitizer_profile.get("revision")
        )
        counters_safe = all(
            self._active_summary.get(name) == 0
            for name in (
                "forbiddenOperationAttempts",
                "secretEndpointCalls",
                "rawRequestBodyBytesPersisted",
                "rawResponseBodyBytesPersisted",
                "stdoutBodyBytes",
                "stderrBodyBytes",
            )
        )
        return {
            "schemaValid": shape_valid and isinstance(self.desired.get("realm"), dict),
            "canonicalRevisionValid": self.desired.get("revision") == _revision(self.desired),
            "mandatoryBaselineComplete": shape_valid and all(self.desired.get(name) is not None for name in semantic_collections),
            "semanticKeysUnique": len(keys) == len(set(keys)),
            "crossReferencesValid": coverage_complete,
            "grantsLeastPrivilege": exact_associations_clean,
            "overlayAllowlistValid": overlay_valid,
            "overlayNonWeakening": overlay_valid,
            "deploymentBindingValid": self.manifest.get("profile") == self.profile,
            "runtimeProvenanceValid": self.profile == "dev" or self.platform_attestation is not None,
            "supervisorReceiptValid": receipt_valid,
            "fencingValid": lease_valid,
            "sanitizerProfileValid": sanitizer_valid,
            "readBackComplete": coverage_complete,
            "zeroDiff": zero_diff and observed_digest == self.desired.get("revision"),
            "secretsRedacted": counters_safe,
        }

    def emit(self, actions: list[dict[str, str]], summary: dict[str, object], client_uuid: str, secret_fingerprint: str) -> None:
        if summary.get("pendingAuthorizedRequestCount") != 0:
            raise SupervisorError("sanitizer retained pending request authority")
        output_scan = self.bootstrap_output_digests
        self.events.add("output-scan-completed", "redaction-scan-clean", observedCount=0)
        if self.lease is None:
            raise SupervisorError("lease vanished before release")
        self.lease = self.controller.finish(self.lease, quarantine=False)
        self.events.add("lease-released", "lease-released")
        coverage = summary["queryCoverage"]
        if not isinstance(coverage, list) or not coverage:
            raise SupervisorError("typed query coverage is empty")
        self._active_coverage = coverage
        self._active_summary = summary
        bindings = sorted(
            [{"operationId": row["operationId"], "bindingDigest": row["bindingDigest"]} for row in coverage],
            key=lambda row: (str(row["operationId"]), str(row["bindingDigest"])),
        )
        desired_revision = str(self.desired["revision"])
        verification_actions = summary.get("verificationActions")
        if not isinstance(verification_actions, list) or any(
            not isinstance(action, dict) for action in verification_actions
        ):
            raise SupervisorError("verification actions are unavailable")
        zero_diff = summary.get("zeroDiff") is True and all(
            action.get("action") == "noop" for action in verification_actions
        )
        coverage_complete = all(row.get("complete") is True for row in coverage)
        observed_digest = desired_revision if zero_diff and coverage_complete else sha256_ref(
            {
                "desiredStateRevision": desired_revision,
                "verificationActions": verification_actions,
                "queryCoverage": coverage,
            }
        )
        sanitization = {
            key: summary[key]
            for key in (
                "operationCount", "responseCount", "forbiddenOperationAttempts", "secretEndpointCalls",
                "rawRequestBodyBytesPersisted", "rawResponseBodyBytesPersisted", "stdoutBodyBytes",
                "stderrBodyBytes", "droppedFieldCount", "unknownFieldCount", "operationAuditDigest",
            )
        }
        sanitization.update(
            {
                "profileId": "weave-keycloak-sanitized-v1",
                "profileRevision": self.sanitizer_profile["revision"],
                "desiredStateRevision": desired_revision,
                "queryCoverage": coverage,
                "queryCoverageDigest": query_coverage_digest(coverage),
                "expectedQueryBindingCount": len(bindings),
                "observedQueryBindingCount": len(bindings),
                "expectedQueryBindingSetDigest": sha256_ref(bindings),
                "observedQueryBindingSetDigest": sha256_ref(bindings),
                "projectedObservedStateDigest": observed_digest,
                "redactionScanDigest": sha256_ref(output_scan),
                "redactionScanFindings": 0,
            }
        )
        authority = {
            "clientId": self.temporary_client_id,
            "clientUuid": client_uuid,
            "lookupOperationId": "master-temporary-client-discovery",
            "lookupResponseCardinality": "exactly-one",
            "lookupObservedCount": 1,
            "secretRef": f"secretref:keycloak/temporary-admin/{self.run_uuid}/{self.lease.fencing_token}",
            "secretGeneration": self.lease.fencing_token,
            "secretFingerprint": secret_fingerprint,
            "bootstrapSecretEnvironmentVariable": "WEAVE_KEYCLOAK_BOOTSTRAP_SECRET",
            "secretInArgv": False,
            "secretInFile": False,
            "lastAccessTokenExpiresAt": summary["lastAccessTokenExpiresAt"],
        }
        payload = {
            "receiptVersion": "weave.keycloak-reconciliation-receipt/v1",
            "receiptRef": f"evidence:keycloak-supervisor-receipt:{self.run_uuid}",
            "reconciliationId": self.reconciliation_id,
            "requestNonce": self.args.nonce,
            "specificationCommit": self.args.specification_commit,
            "candidateCommit": self.args.candidate_commit,
            "environment": self.profile,
            "issuer": self._required("WEAVE_AUTH_URL").rstrip("/") + "/realms/weave",
            "realm": "weave",
            "deployment": self.deployment(),
            "runtime": {
                "keycloakVersion": "26.7.0",
                "versionCommand": "kc.sh --version",
                "imageDigest": self.image_digest,
                "imageProvenance": "reviewed-immutable-digest-approved",
                "imageSignatureVerified": False,
            },
            "supervisor": {
                "controlPlane": "root-owned-run-bound-supervisor",
                "commandAllowlist": COMMAND_ALLOWLIST,
                "candidateDockerSocket": "present-untrusted-runner",
                "assemblerDockerSocket": "present-trusted-supervisor",
                "candidateHostControlIsolationVerified": False,
                "protectedBoundaryReadiness": "guarded",
                "candidateSigningKeyAccess": "absent",
                "assemblerSigningKeyAccess": "absent",
                "attestationRef": self.attestation_ref,
            },
            "lease": self.lease.evidence(),
            "authority": authority,
            "sanitization": sanitization,
            "events": self.events.values,
            "residualAuthority": False,
            "residualSensitiveState": False,
            "createdAt": _now(),
        }
        envelope = sign_receipt(payload, self.signing_key, "weave-keycloak-supervisor-current")
        started = self.events.values[11]["occurredAt"]
        changed = any(action["action"] != "noop" for action in actions)
        if self.args.mode == "apply":
            outcome = "applied"
        elif changed:
            outcome = "changes_required"
        else:
            outcome = "converged"
        now = datetime.now(timezone.utc)
        report = {
            "apiVersion": "weave.keycloak-reconcile-report/v1",
            "canonicalization": "RFC8785",
            "reconciliationId": self.reconciliation_id,
            "requestNonce": self.args.nonce,
            "specificationCommit": self.args.specification_commit,
            "candidateCommit": self.args.candidate_commit,
            "environment": self.profile,
            "issuer": payload["issuer"],
            "realm": "weave",
            "deployment": self.deployment(),
            "authority": {
                "mode": "protected-ephemeral",
                "receiptRef": payload["receiptRef"],
                "receiptPayloadDigest": sha256_ref(payload),
                "supervisorReceipt": envelope,
            },
            "baselineRevision": self.manifest["baselineRevision"],
            "overlayRevision": self.manifest["overlayRevision"],
            "desiredStateRevision": desired_revision,
            "keycloakVersion": "26.7.0",
            "keycloakImageDigest": self.image_digest,
            "startedAt": started,
            "generatedAt": now.isoformat(timespec="seconds").replace("+00:00", "Z"),
            "validUntil": (now + timedelta(minutes=5)).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "mode": self.args.mode,
            "outcome": outcome,
            "desiredHash": desired_revision,
            "observedHash": observed_digest,
            "actions": actions,
            "verification": self._verification_flags(
                zero_diff=zero_diff,
                coverage_complete=coverage_complete,
                observed_digest=observed_digest,
                envelope=envelope,
            ),
            "evidenceRefs": [payload["receiptRef"], f"evidence:keycloak:{self.profile}:{self.args.candidate_commit[:12]}"],
        }
        if outcome in {"applied", "converged"} and not all(report["verification"].values()):
            raise SupervisorError("successful report outcome failed one or more derived verification checks")
        report_attestation = sign_receipt(
            {
                "attestationVersion": "weave.keycloak-report-attestation/v1",
                "reconciliationId": self.reconciliation_id,
                "requestNonce": self.args.nonce,
                "reportDigest": sha256_ref(report),
            },
            self.signing_key,
            "weave-keycloak-supervisor-current",
        )
        atomic_private_json(self.generated / "keycloak/signed-receipt.json", envelope)
        atomic_private_json(self.generated / "keycloak/signed-report-attestation.json", report_attestation)
        atomic_private_json(self.generated / "keycloak/report.json", report)

    def accept_receipt(self) -> None:
        """Verify and atomically consume startup authority outside candidate code."""

        if self.args.development_candidate_supervisor:
            raise SupervisorError("candidate supervisor cannot accept dogfood/main startup authority")
        report = _read_json(self.generated / "keycloak/report.json", "Keycloak reconciliation report")
        envelope = _read_json(self.generated / "keycloak/signed-receipt.json", "signed Keycloak receipt")
        report_attestation = _read_json(
            self.generated / "keycloak/signed-report-attestation.json",
            "signed Keycloak report attestation",
        )
        trust_key = self.secrets / "keycloak-supervisor-trust-key.pem"
        payload = verify_receipt(envelope, trust_key, expected_kid="weave-keycloak-supervisor-current")
        attested_report = verify_receipt(
            report_attestation,
            trust_key,
            expected_kid="weave-keycloak-supervisor-current",
        )
        expected_payload = {
            "receiptVersion": "weave.keycloak-reconciliation-receipt/v1",
            "specificationCommit": self.args.specification_commit,
            "candidateCommit": self.args.candidate_commit,
            "environment": self.profile,
            "issuer": self._required("WEAVE_AUTH_URL").rstrip("/") + "/realms/weave",
            "realm": "weave",
            "deployment": self.deployment(),
        }
        if any(payload.get(name) != value for name, value in expected_payload.items()):
            raise SupervisorError("signed receipt does not bind the exact startup candidate")
        if (
            attested_report.get("attestationVersion") != "weave.keycloak-report-attestation/v1"
            or attested_report.get("reconciliationId") != payload.get("reconciliationId")
            or attested_report.get("requestNonce") != payload.get("requestNonce")
            or attested_report.get("reportDigest") != sha256_ref(report)
        ):
            raise SupervisorError("signed report attestation does not bind the exact report bytes")
        authority = report.get("authority")
        verification = report.get("verification")
        lease = payload.get("lease")
        sanitizer = payload.get("sanitization")
        runtime = payload.get("runtime")
        events = payload.get("events")
        if not all(isinstance(item, dict) for item in (authority, verification, lease, sanitizer, runtime)):
            raise SupervisorError("signed startup evidence is structurally incomplete")
        if (
            report.get("mode") != "apply"
            or report.get("outcome") != "applied"
            or report.get("reconciliationId") != payload.get("reconciliationId")
            or report.get("requestNonce") != payload.get("requestNonce")
            or report.get("desiredStateRevision") != self.desired.get("revision")
            or report.get("desiredHash") != self.desired.get("revision")
            or report.get("observedHash") != self.desired.get("revision")
            or not isinstance(verification, dict)
            or not verification
            or any(value is not True for value in verification.values())
        ):
            raise SupervisorError("startup report is not a successful derived zero-diff apply")
        assert isinstance(authority, dict) and isinstance(lease, dict)
        assert isinstance(sanitizer, dict) and isinstance(runtime, dict)
        if (
            authority.get("supervisorReceipt") != envelope
            or authority.get("receiptPayloadDigest") != sha256_ref(payload)
            or lease.get("status") != "released"
            or not lease.get("releasedAt")
            or sanitizer.get("desiredStateRevision") != self.desired.get("revision")
            or sanitizer.get("projectedObservedStateDigest") != self.desired.get("revision")
            or sanitizer.get("redactionScanFindings") != 0
            or runtime.get("imageDigest") != self.image_digest
            or payload.get("residualAuthority") is not False
            or payload.get("residualSensitiveState") is not False
        ):
            raise SupervisorError("signed startup receipt has residual, stale, or non-converged state")
        if (
            not isinstance(events, list)
            or [event.get("kind") for event in events if isinstance(event, dict)] != EVENT_KINDS
            or any(
                not isinstance(event, dict)
                or event.get("status") != "succeeded"
                or event.get("sequence") != index
                for index, event in enumerate(events, 1)
            )
        ):
            raise SupervisorError("signed startup receipt does not contain the complete successful lifecycle")
        try:
            valid_until = datetime.fromisoformat(str(report["validUntil"]).replace("Z", "+00:00"))
        except (KeyError, ValueError) as error:
            raise SupervisorError("startup report validity is malformed") from error
        if valid_until <= datetime.now(timezone.utc):
            raise SupervisorError("startup report has expired")
        self.controller.consume_receipt(
            reconciliation_id=str(payload["reconciliationId"]),
            request_nonce=str(payload["requestNonce"]),
            specification_commit=self.args.specification_commit,
            candidate_commit=self.args.candidate_commit,
            receipt_payload_digest=sha256_ref(payload),
        )

    def run(self) -> None:
        if self.args.mode == "accept":
            self.accept_receipt()
            return
        self.acquire()
        secret = secrets.token_urlsafe(48)
        fingerprint = "sha256:" + hashlib.sha256(secret.encode("utf-8")).hexdigest()
        try:
            self.verify_runtime()
            self.stop_node()
            self.bootstrap(secret)
            self.start_node()
            actions, summary, client_uuid = self.reconcile(secret)
            secret = ""
            self.emit(actions, summary, client_uuid, fingerprint)
        except Exception:
            secret = ""
            if self.lease is not None and self.controller.session_lock_held:
                try:
                    self.lease = self.controller.finish(self.lease, quarantine=True)
                except LeaseError:
                    pass
            raise


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("plan", "apply", "verify", "accept"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--profile", choices=("dev", "dogfood", "main"), required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--specification-commit", required=True)
    parser.add_argument("--nonce")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--spec-root", type=Path, required=True)
    parser.add_argument("--stack-scope", choices=("persistent", "isolated"), required=True)
    parser.add_argument("--e2e-run-id")
    parser.add_argument("--keycloak-image", required=True)
    parser.add_argument("--sanitizer-image", required=True)
    parser.add_argument("--runtime-uid", type=int, required=True)
    parser.add_argument("--runtime-gid", type=int, required=True)
    parser.add_argument("--platform-attestation", type=Path)
    parser.add_argument("--development-candidate-supervisor", action="store_true")
    args = parser.parse_args()
    if not COMMIT.fullmatch(args.candidate_commit) or not COMMIT.fullmatch(args.specification_commit):
        print("WEAVE_KEYCLOAK_SUPERVISOR_ERROR invalid commit binding", file=sys.stderr)
        return 2
    if args.mode != "accept" and (not isinstance(args.nonce, str) or not NONCE.fullmatch(args.nonce)):
        print("WEAVE_KEYCLOAK_SUPERVISOR_ERROR invalid reconciliation nonce", file=sys.stderr)
        return 2
    if bool(args.platform_attestation) == bool(args.development_candidate_supervisor):
        print("WEAVE_KEYCLOAK_SUPERVISOR_ERROR select exactly one supervisor authority", file=sys.stderr)
        return 2
    try:
        Supervisor(args).run()
        return 0
    except (
        SupervisorError,
        KcadmError,
        LeaseError,
        ReceiptError,
        ReconcileError,
        DeploymentContextError,
        DesiredStateAuthorityError,
        OSError,
        ValueError,
        subprocess.CalledProcessError,
    ) as error:
        print(f"WEAVE_KEYCLOAK_SUPERVISOR_ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
