#!/usr/bin/env python3
"""Create a bounded support-safe Compose diagnostic bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, canonical_json, compose_environment, load_context


def _write(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _compose_model(context: ComposeContext) -> dict[str, object]:
    result = subprocess.run(
        [*context.compose_base_command, "config", "--format", "json"],
        cwd=context.root,
        env=compose_environment(context),
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    model = json.loads(result.stdout)
    services = model.get("services", {})
    volumes = model.get("volumes", {})
    networks = model.get("networks", {})
    if not all(isinstance(value, dict) for value in (services, volumes, networks)):
        raise ContractError("normalized Compose model is malformed")
    return {
        "modelDigest": "sha256:" + hashlib.sha256(canonical_json(model)).hexdigest(),
        "services": {
            name: {
                "image": value.get("image"),
                "profiles": value.get("profiles", []),
                "restart": value.get("restart"),
                "readOnly": value.get("read_only", False),
            }
            for name, value in sorted(services.items())
            if isinstance(value, dict)
        },
        "volumeNames": sorted(
            str(value.get("name", name)) for name, value in volumes.items() if isinstance(value, dict)
        ),
        "networkNames": sorted(
            str(value.get("name", name)) for name, value in networks.items() if isinstance(value, dict)
        ),
    }


def _ps(context: ComposeContext) -> object:
    result = subprocess.run(
        [*context.compose_base_command, "ps", "--all", "--format", "json"],
        cwd=context.root,
        env=compose_environment(context),
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    try:
        rows = json.loads(result.stdout)
        rows = rows if isinstance(rows, list) else [rows]
    except json.JSONDecodeError:
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    allowed = ("Service", "Name", "Image", "State", "Health", "ExitCode", "Publishers")
    return [{key: row.get(key) for key in allowed if key in row} for row in rows if isinstance(row, dict)]


def _safe_evidence(context: ComposeContext, relative: str) -> dict[str, object] | None:
    path = context.generated_root / relative
    if path.is_symlink() or not path.is_file():
        return None
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("containsSecretValues") is not False:
        raise ContractError(f"evidence {relative} is not explicitly support-safe")
    return value


def create(context: ComposeContext, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    timestamp = datetime.now(timezone.utc)
    basename = f"weave-compose-support-{context.profile}-{timestamp.strftime('%Y%m%dT%H%M%SZ')}"
    destination = output_dir / f"{basename}.tar.gz"
    if destination.exists() or destination.is_symlink():
        raise ContractError("support-bundle destination already exists")
    with tempfile.TemporaryDirectory(prefix="weave-compose-support-") as directory:
        root = Path(directory) / basename
        root.mkdir(mode=0o700)
        model = _compose_model(context)
        _write(root / "compose-model-summary.json", model)
        _write(root / "compose-ps.json", _ps(context))
        included: list[str] = []
        for relative, target in (
            ("operator/readiness.json", "operator-readiness.json"),
            ("nextcloud/readiness.json", "nextcloud-readiness.json"),
        ):
            evidence = _safe_evidence(context, relative)
            if evidence is not None:
                _write(root / target, evidence)
                included.append(target)
        manifest = {
            "schemaVersion": "weave.compose-support-bundle.v1",
            "profile": context.profile,
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
            "createdAt": timestamp.isoformat().replace("+00:00", "Z"),
            "modelDigest": model["modelDigest"],
            "publicCoordinates": {
                "WEAVE_ADMIN_CONSOLE_URL": context.env["WEAVE_ADMIN_CONSOLE_URL"],
                "WEAVE_PROVIDER_PROFILE": context.env["WEAVE_PROVIDER_PROFILE"],
            },
            "includedEvidence": sorted(included),
            "excluded": [
                "container environment",
                "raw logs",
                "mounted secrets",
                "bearer assertions",
                "provider response bodies",
                "signed receipt payloads",
            ],
            "containsSecretValues": False,
            "supportSafe": True,
        }
        _write(root / "manifest.json", manifest)
        with tarfile.open(destination, "x:gz") as archive:
            archive.add(root, arcname=basename, recursive=True)
    os.chmod(destination, 0o600)
    return destination


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "test", "prod"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        output = create(context, (args.output_dir or context.generated_root / "support-bundles").resolve())
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_SUPPORT_BUNDLE_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"support-bundle: created {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
