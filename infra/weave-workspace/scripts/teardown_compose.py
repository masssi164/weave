#!/usr/bin/env python3
"""Destroy only an exactly owned disposable Compose namespace."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context


VOLUME_KEYS = (
    "WEAVE_CADDY_DATA_VOLUME", "WEAVE_CADDY_CONFIG_VOLUME", "WEAVE_DB_DATA_VOLUME",
    "WEAVE_KEYCLOAK_DATA_VOLUME", "WEAVE_MAILPIT_DATA_VOLUME", "WEAVE_NEXTCLOUD_DATA_VOLUME",
    "WEAVE_SYNAPSE_DATA_VOLUME", "WEAVE_MATRIX_APPSERVICE_VOLUME",
    "WEAVE_RUNTIME_STATE_VOLUME",
)


def _labels(kind: str, name: str) -> dict[str, str] | None:
    result = subprocess.run(
        ["docker", kind, "inspect", name, "--format", "{{json .Labels}}"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        return None
    value = json.loads(result.stdout)
    return value if isinstance(value, dict) else {}


def _assert_owned(context: ComposeContext, kind: str, name: str) -> bool:
    observed = _labels(kind, name)
    if observed is None:
        return False
    expected = {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": "test",
        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
        "com.massimotter.weave.scope": "isolated",
    }
    if any(observed.get(key) != value for key, value in expected.items()):
        raise ContractError(f"refusing to remove unowned Docker {kind} {name}")
    return True


def teardown(context: ComposeContext, *, dry_run: bool) -> dict[str, object]:
    if context.isolated_namespace is None or context.env.get("WEAVE_STACK_SCOPE") != "isolated":
        raise ContractError("destructive teardown is restricted to a run-scoped isolated test project")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if re.fullmatch(r"[0-9a-f]{40}", candidate) is None:
        raise ContractError("isolated teardown requires exact WEAVE_CANDIDATE_COMMIT evidence")

    volumes = [context.env[key] for key in VOLUME_KEYS]
    network = context.env["WEAVE_DOCKER_NETWORK"]
    existing_volumes = [name for name in volumes if _assert_owned(context, "volume", name)]
    network_exists = _assert_owned(context, "network", network)
    if not dry_run:
        subprocess.run(
            [*context.compose_base_command, "down", "--remove-orphans"],
            cwd=context.root,
            env=compose_environment(context),
            check=True,
        )
        for name in existing_volumes:
            subprocess.run(["docker", "volume", "rm", name], check=True, stdout=subprocess.DEVNULL)
        if network_exists:
            subprocess.run(["docker", "network", "rm", network], check=True, stdout=subprocess.DEVNULL)

    return {
        "schemaVersion": "weave.compose-isolated-teardown.v1",
        "profile": context.profile,
        "namespace": context.isolated_namespace,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "candidateCommit": candidate,
        "completedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "dryRun": dry_run,
        "removedVolumeNames": [] if dry_run else sorted(existing_volumes),
        "networkRemoved": bool(network_exists and not dry_run),
        "ownershipLabelsVerified": True,
        "containsSecretValues": False,
        "supportSafe": True,
    }


def _evidence_output_path(context: ComposeContext, explicit: Path | None) -> Path:
    if explicit is not None:
        return explicit.resolve()
    configured = os.environ.get("WEAVE_TEARDOWN_EVIDENCE_FILE", "")
    if configured:
        return Path(configured).resolve()
    return (context.generated_root / "teardown/evidence.json").resolve()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("test",))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    parser.add_argument("--isolated", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--evidence-file", type=Path)
    args = parser.parse_args()
    try:
        if not args.isolated:
            raise ContractError("teardown requires the explicit --isolated intent")
        context = load_context(args.profile, args.root, args.env_file)
        value = teardown(context, dry_run=args.dry_run)
        output = _evidence_output_path(context, args.evidence_file)
        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = output.with_suffix(output.suffix + ".tmp")
        temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.chmod(temporary, 0o600)
        os.replace(temporary, output)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_TEARDOWN_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"teardown: isolated namespace handled; evidence={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
