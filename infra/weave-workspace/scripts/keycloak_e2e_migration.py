#!/usr/bin/env python3
"""Run the bounded Keycloak migration for one proven-empty isolated E2E namespace."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from compose_env import ContractError, load_context
from compose_runtime import (
    _write_migration_bootstrap_secret,
    compose,
    prepare,
    script,
)
from keycloak_migration import migration_inputs, require_completed_migration
from keycloak_migration_backup import create_backup_proof


def apply(context) -> None:
    if context.environment != "e2e" or context.isolated_namespace is None:
        raise ContractError("disposable Keycloak migration requires one isolated E2E context")
    empty_proof = os.environ.get("WEAVE_E2E_EMPTY_NAMESPACE_PROOF", "").strip()
    if not empty_proof:
        raise ContractError("disposable Keycloak migration requires an empty-namespace proof")
    proof_path = Path(empty_proof).expanduser().absolute()
    if proof_path.is_symlink() or not proof_path.is_file():
        raise ContractError("empty-namespace proof is missing or unsafe")

    script(context, "init_secrets.py")
    script(context, "render_config.py")
    prepare(context)
    inputs = migration_inputs(context)
    try:
        require_completed_migration(context)
        print("WEAVE_KEYCLOAK_MIGRATION_RESULT state=already-complete supportSafe=true")
        return
    except ContractError:
        pass

    compose(context, "up", "-d", "--wait", "--wait-timeout", "600", "keycloak")
    precondition_proof = create_backup_proof(context)
    credential = context.secret_root / "keycloak-realm-migration-bootstrap-secret"
    _write_migration_bootstrap_secret(context, credential)
    try:
        compose(context, "stop", "--timeout", "30", "keycloak")
        compose(
            context,
            "run",
            "--rm",
            "--no-deps",
            "keycloak-realm-migration-bootstrap",
        )
        compose(context, "up", "-d", "--wait", "--wait-timeout", "600", "keycloak")
        compose(
            context,
            "run",
            "--rm",
            "--no-deps",
            "keycloak-realm-migration",
            "keycloak-realm-migration",
            "--artifact-root=/run/weave-generated",
            f"--manifest-digest={inputs.manifest_digest}",
            f"--baseline-digest={inputs.baseline_digest}",
            f"--target-revision={inputs.target_revision}",
            "--environment=e2e",
            f"--candidate-commit={context.env['WEAVE_CANDIDATE_COMMIT']}",
            f"--compose-project={context.env['WEAVE_COMPOSE_PROJECT']}",
            "--keycloak-base-url=http://keycloak:8080",
            "--bootstrap-secret-file=/run/secrets/keycloak-realm-migration-bootstrap-secret",
            "--backup-proof-file=/run/weave-generated/keycloak/migrations/"
            + precondition_proof.name,
            "--timeout=PT10S",
        )
        require_completed_migration(context)
        print("WEAVE_KEYCLOAK_MIGRATION_RESULT state=complete supportSafe=true")
    finally:
        if credential.exists() or credential.is_symlink():
            credential.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file", required=True)
    args = parser.parse_args()
    try:
        context = load_context("e2e", args.root, args.env_file)
        apply(context)
        return 0
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"WEAVE_E2E_KEYCLOAK_MIGRATION_ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
