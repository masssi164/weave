#!/usr/bin/env python3
"""Explicit bounded development Space setup; ordinary startup never calls it."""

from __future__ import annotations

import json
import os
import re
import secrets
import shutil
import ssl
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from compose_env import ComposeContext, ContractError
from bootstrap_https import open_https

ROUTE = "/api/bootstrap/default-space"
HEADER = "X-Weave-Space-Bootstrap-Token"
MOUNT = "/run/secrets/weave/bootstrap-space"
RESPONSE_FLAGS = ("ownerAccessVerified", "filesReady", "calendarReady", "supportSafe")
PREREQUISITE_CODES = frozenset((
    "default-space-capabilities-not-ready", "default-space-owner-access-conflict",
    "default-space-organization-mismatch", "default-space-owner-unavailable",
    "default-space-owner-verification-unavailable", "default-space-bootstrap-unauthorized",
    "default-space-bootstrap-unavailable",
))


def _git(context: ComposeContext, *arguments: str) -> str:
    return subprocess.run(
        ["git", *arguments], cwd=context.repository_root, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout.strip()


def _identity(context: ComposeContext) -> dict[str, str]:
    commit = _git(context, "rev-parse", "HEAD")
    tree = _git(context, "rev-parse", "HEAD^{tree}")
    pin = json.loads((context.repository_root / "specs/weave-specs.lock.json").read_text())[
        "specCorpus"
    ]["gitCommit"]
    if any(not re.fullmatch(r"[0-9a-f]{40}", value) for value in (commit, tree, pin)):
        raise ContractError("Space preparation requires immutable candidate identity")
    if commit != context.env["WEAVE_CANDIDATE_COMMIT"] or pin != context.env["WEAVE_SPEC_COMMIT"]:
        raise ContractError("Space preparation candidate differs from the selected context")
    if _git(context, "status", "--porcelain=v1", "--untracked-files=all"):
        raise ContractError("Space preparation requires a clean exact candidate checkout")
    source = context.env["WEAVE_IMAGE_SOURCE_COMMIT"]
    if os.environ.get("WEAVE_BUILD_ARTIFACT_ROOT"):
        sys.path.insert(0, str(context.repository_root / "tools"))
        from artifact_consumers import ArtifactError, candidate_composition
        try:
            composition = candidate_composition(commit)
            if composition["components"]["runtime-linux-amd64"]["sourceCommit"] != source:
                raise ArtifactError("runtime-producer-mismatch")
        except (ArtifactError, KeyError):
            raise ContractError("Space preparation source differs from the candidate composition") from None
    elif _git(context, "rev-parse", source + "^{tree}") != tree:
        raise ContractError("Space preparation backend source differs from the candidate tree")
    return {"candidateCommit": commit, "sourceTree": tree, "specificationCommit": pin}


def _tls(context: ComposeContext) -> ssl.SSLContext:
    tls = ssl.create_default_context(cafile=str(context.tls_root / "ca.pem"))
    # Match the existing reviewed local-CA compatibility policy, retaining CA,
    # hostname and certificate verification.
    tls.verify_flags &= ~ssl.VERIFY_X509_STRICT
    return tls


def _open(context: ComposeContext, request: urllib.request.Request, timeout: int):
    if context.environment == "e2e" and context.isolated_namespace is None:
        raise ContractError("Space bootstrap HTTPS requires the owned isolated E2E context")
    return open_https(request, _tls(context),
                      connect_loopback=context.environment == "e2e", timeout=timeout)


def _request(context: ComposeContext, token: str) -> dict[str, Any]:
    request = urllib.request.Request(
        context.env["WEAVE_API_ORIGIN"].rstrip("/") + ROUTE,
        data=b"{}", method="POST", headers={"Content-Type": "application/json", HEADER: token},
    )
    try:
        with _open(context, request, 30) as response:
            if response.status != 200:
                raise ContractError("Space setup did not return its success status")
            payload_bytes = response.read(8193)
    except urllib.error.HTTPError as error:
        # Provider responses may contain private identity material. Publish only
        # the HTTP status and closed support-code list, never bodies or headers.
        status = error.code
        code = ""
        try:
            diagnostic = error.read(8193)
            if len(diagnostic) <= 8192:
                projection = json.loads(diagnostic)
                candidate = projection.get("code") if isinstance(projection, dict) else None
                if isinstance(candidate, str) and candidate in PREREQUISITE_CODES:
                    code = "; " + candidate
        except (ValueError, UnicodeError, OSError):
            pass
        finally:
            error.close()
        raise ContractError(f"Space setup prerequisite failed (HTTP {status}{code})") from None
    except (OSError, urllib.error.URLError):
        raise ContractError("Space setup could not reach the reviewed HTTPS endpoint") from None
    if len(payload_bytes) > 8192:
        raise ContractError("Space setup response exceeded its bounded size")
    try:
        payload = json.loads(payload_bytes)
    except (ValueError, UnicodeError):
        raise ContractError("Space setup did not return bounded JSON evidence") from None
    required = {
        "schemaVersion": "weave.default-space-bootstrap/v1", "status": "passed",
        "spaceRef": "workspace-default", "containsSecretValues": False,
    }
    if (not isinstance(payload, dict) or any(payload.get(key) != value for key, value in required.items())
            or payload.get("containsSecretValues") is not False
            or any(payload.get(key) is not True for key in RESPONSE_FLAGS)
            or not isinstance(payload.get("outcome"), str)
            or payload.get("outcome") not in {"CREATED", "VERIFIED"}):
        raise ContractError("Space setup did not verify current owner Files and Calendar access")
    # Whitelist evidence rather than retaining arbitrary backend identity fields.
    return {**required, **{key: True for key in RESPONSE_FLAGS}, "outcome": payload["outcome"]}


def _disabled(context: ComposeContext, *, deadline: float | None = None) -> bool:
    import compose_runtime as runtime

    request = urllib.request.Request(
        context.env["WEAVE_API_ORIGIN"].rstrip("/") + ROUTE, data=b"{}", method="POST",
        headers={"Content-Type": "application/json", HEADER: secrets.token_urlsafe(32)},
    )
    try:
        with runtime.bootstrap_probe_deadline(deadline):
            with _open(context, request, 10):
                return False
    except urllib.error.HTTPError as error:
        disabled = error.code == 404
        error.close()
        return disabled
    except (OSError, urllib.error.URLError):
        return False


def execute(context: ComposeContext, extra: list[str]) -> None:
    # Imported lazily to keep compose_runtime's command dispatch acyclic.
    import compose_runtime as runtime

    if not ((context.environment == "dogfood" and context.isolated_namespace is None)
            or (context.environment == "e2e" and context.isolated_namespace is not None)):
        raise ContractError("bootstrap-space requires persistent dogfood or owned isolated E2E")
    if len(extra) != 2 or extra[0] != "--evidence-file" or not extra[1]:
        raise ContractError("bootstrap-space requires --evidence-file <private-json>")
    evidence = Path(extra[1]).expanduser().absolute()
    if any(path.is_symlink() for path in (evidence, *evidence.parents)):
        raise ContractError("Space preparation evidence must not traverse a symlink")
    if evidence.exists():
        if not evidence.is_file() or evidence.stat().st_uid != os.getuid():
            raise ContractError("Space preparation evidence must be an owned regular file")
        evidence.unlink()
    identity = _identity(context)
    with runtime._dogfood_session_lock(context), runtime.bootstrap_termination_cleanup():
        if context.environment == "e2e":
            runtime.require_existing_owned_resources(context)
        runtime.prepare(context)
        if context.environment == "dogfood":
            runtime.verify_dogfood_identity(context)
        before = runtime._service_container(context, "backend")
        if runtime._bootstrap_boundary_present(before):
            raise ContractError("Space preparation found competing bootstrap authority")
        if any(str(value).startswith("SPRING_APPLICATION_JSON=")
               for value in before.get("Config", {}).get("Env", []) or []):
            raise ContractError("Space preparation cannot replace existing application JSON configuration")
        image = str(before.get("Image", ""))
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", image):
            raise ContractError("Space preparation requires exact backend image identity")
        image_labels = before.get("Config", {}).get("Labels", {}) or {}
        if os.environ.get("WEAVE_BUILD_ARTIFACT_ROOT"):
            sys.path.insert(0, str(context.repository_root / "tools"))
            from artifact_consumers import ArtifactError, verify_runtime_container
            try:
                verify_runtime_container(before, identity["candidateCommit"])
            except ArtifactError:
                raise ContractError("Space preparation running artifact differs from the candidate composition") from None
            expected = {"com.massimotter.weave.candidate-commit": identity["candidateCommit"],
                        "com.massimotter.weave.spec-digest": context.env["WEAVE_SPEC_DIGEST"],
                        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
                        "com.docker.compose.project": context.env["WEAVE_COMPOSE_PROJECT"],
                        "com.docker.compose.service": "backend"}
            if any(image_labels.get(key) != value for key, value in expected.items()):
                raise ContractError("Space preparation runtime ownership differs from the candidate")
            # The bounded override and restoration consume this exact verified image, even when its
            # original producer differs from the current candidate and process overrides expired.
            context.env["WEAVE_BACKEND_IMAGE"] = image
        else:
            configured = subprocess.run(
                ["docker", "image", "inspect", context.env["WEAVE_BACKEND_IMAGE"], "--format", "{{.Id}}"],
                check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            ).stdout.strip()
            if configured != image:
                raise ContractError("Space preparation running image differs from the exact candidate")
            if (image_labels.get("org.opencontainers.image.revision") != context.env["WEAVE_IMAGE_SOURCE_COMMIT"]
                    or image_labels.get("com.massimotter.weave.spec-digest") != context.env["WEAVE_SPEC_DIGEST"]):
                raise ContractError("Space preparation backend provenance differs from the candidate")
        operation: Path | None = None
        override: Path | None = None
        result: dict[str, Any] | None = None
        primary_error: BaseException | None = None
        try:
            selected_operation = context.secret_root / (".space-bootstrap-" + secrets.token_hex(12))
            selected_operation.mkdir(mode=0o700)
            # Cleanup owns this path only after this invocation created it.
            operation = selected_operation
            uid, gid = int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"])
            os.chown(operation, uid, gid)
            token_path = operation / "token"
            credential = secrets.token_urlsafe(48)
            descriptor = os.open(token_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "w", encoding="ascii") as stream:
                stream.write(credential + "\n")
                stream.flush()
                os.fsync(stream.fileno())
            os.chown(token_path, uid, gid)
            override = context.generated_root / "operations" / (operation.name + ".compose.json")
            runtime._private_json(override, {"services": {"backend": {
                "environment": {"SPRING_APPLICATION_JSON": json.dumps({"weave": {"spaces": {
                    "bootstrap": {"enabled": True, "environment": context.environment,
                                  "token-file": MOUNT + "/token"}
                }}})},
                "volumes": [{"type": "bind", "source": str(operation), "target": MOUNT, "read_only": True}],
            }}})
            runtime._bootstrap_override_command(
                context, override, "up", "-d", "--no-deps", "--force-recreate", "--wait",
                "--wait-timeout", "180", "backend",
            )
            result = _request(context, credential)
        except BaseException as error:
            primary_error = error
        finally:
            runtime.bootstrap_cleanup_started()
            restoration_deadline = runtime.bootstrap_restoration_deadline()
            restoration_error: BaseException | None = None
            try:
                restored = runtime._canonical_backend(context, deadline=restoration_deadline)
                if runtime._bootstrap_boundary_present(restored):
                    raise ContractError("Space preparation retained bootstrap authority or mount")
                if restored.get("Image") != image:
                    raise ContractError("Space preparation changed the canonical backend image")
                if not _disabled(context, deadline=restoration_deadline):
                    raise ContractError("Space bootstrap endpoint remained available after cleanup")
            except BaseException as error:
                restoration_error = error
            try:
                if operation is not None and operation.exists():
                    shutil.rmtree(operation)
            except BaseException as error:
                restoration_error = error
            try:
                if override is not None:
                    override.unlink(missing_ok=True)
            except BaseException as error:
                restoration_error = error
            if restoration_error is not None:
                raise ContractError("Space preparation could not remove its exceptional authority") from restoration_error
        if primary_error is not None:
            if isinstance(primary_error, ContractError):
                raise primary_error
            raise ContractError("Space preparation failed; canonical authority was restored") from primary_error
        if result is None or operation is None or operation.exists() or override is None or override.exists():
            raise ContractError("Space preparation completed without verified cleanup")
        runtime._private_json(evidence, {
            **result, **identity, "schemaVersion": "weave.default-space-preparation/v1",
            "environment": context.environment, "verifiedAtUtc": datetime.now(timezone.utc).isoformat(),
            "bootstrapAuthorityAbsent": True, "bootstrapMountAbsent": True,
            "tokenAbsent": True, "canonicalImageUnchanged": True,
        })
        print("WEAVE_DEFAULT_SPACE_PREPARATION status=passed ownerAccessVerified=true "
              "filesReady=true calendarReady=true bootstrapAuthorityAbsent=true tokenAbsent=true supportSafe=true")
