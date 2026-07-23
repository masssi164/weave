#!/usr/bin/env python3
"""Build exact local trust images and converge the host-dev dependency stack."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
from pathlib import Path

from compose_env import load_context


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")


def select_source_env(root: Path, supplied: str | None) -> Path:
    candidate = Path(supplied).expanduser() if supplied else root / "environments/dev.env"
    if candidate.is_symlink() or not candidate.is_file():
        raise RuntimeError("reviewed dev environment file is missing or is a symlink")
    # Reuse the same closed parser and topology checks as compose.sh before any
    # dependency stack mutation.  This makes WEAVE_ENV_FILE a real operator
    # input instead of silently falling back to the checked-in defaults.
    return load_context("dev", root, str(candidate.resolve())).profile_env_file


def run_image_builder(command: list[str]) -> str:
    result = subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE)
    image_id = result.stdout.strip().splitlines()[-1]
    if not IMAGE_ID.fullmatch(image_id):
        raise RuntimeError("image builder did not return an immutable Docker image ID")
    return image_id


def write_runtime_env(source: Path, target: Path, replacements: dict[str, str]) -> None:
    lines: list[str] = []
    seen: set[str] = set()
    for original in source.read_text(encoding="utf-8").splitlines():
        if not original or original.startswith("#") or "=" not in original:
            lines.append(original)
            continue
        key, _value = original.split("=", 1)
        if key in replacements:
            lines.append(f"{key}={replacements[key]}")
            seen.add(key)
        else:
            lines.append(original)
    for key in sorted(set(replacements) - seen):
        lines.append(f"{key}={replacements[key]}")
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            stream.write("\n".join(lines) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
        os.chmod(target, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    root = args.root.resolve()
    repository = root.parents[1]
    candidate = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not COMMIT.fullmatch(candidate):
        raise SystemExit("WEAVE_DEV_PREPARE_ERROR repository HEAD is not an exact candidate")
    source_env = select_source_env(root, args.env_file or os.environ.get("WEAVE_ENV_FILE"))
    evidence = root / ".generated/dev/image-evidence"
    keycloak_image = run_image_builder(
        [
            "python3", str(root / "scripts/build_keycloak_image.py"),
            "--root", str(repository),
            "--candidate-commit", candidate,
            "--output", str(evidence / "keycloak.json"),
        ]
    )
    sanitizer_image = run_image_builder(
        [
            "python3", str(root / "scripts/build_sanitizer_image.py"),
            "--root", str(root),
            "--candidate-commit", candidate,
            "--output", str(evidence / "sanitizer.json"),
        ]
    )
    runtime_env = root / ".generated/dev/dev-runtime.env"
    write_runtime_env(
        source_env,
        runtime_env,
        {
            "WEAVE_KEYCLOAK_IMAGE": keycloak_image,
            "WEAVE_KEYCLOAK_IMAGE_DIGEST": keycloak_image,
            "WEAVE_KEYCLOAK_SANITIZER_IMAGE": sanitizer_image,
        },
    )
    environment = dict(os.environ)
    environment.update(
        {
            "WEAVE_CANDIDATE_COMMIT": candidate,
            "WEAVE_ENV_FILE": str(runtime_env),
        }
    )
    subprocess.run([str(root / "install.sh"), "dev"], cwd=root, env=environment, check=True)
    print(
        f"dev dependencies: ready; source {source_env}; "
        f"runtime environment {runtime_env} (no credential values)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
