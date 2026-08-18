#!/usr/bin/env python3
"""Write one run-scoped Docker registry authority without using host keychains."""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
import stat


REGISTRY_PATTERN = re.compile(r"^[A-Za-z0-9._:-]+$")


def _required_environment(name: str) -> str:
    value = os.environ.get(name, "")
    if not value or any(character in value for character in ("\0", "\r", "\n")):
        raise ValueError(f"{name} is missing or contains unsupported characters")
    return value


def write_config(
    output: Path,
    registry: str,
    actor: str,
    token: str,
    cli_plugin_dir: Path,
) -> None:
    if not output.is_absolute():
        raise ValueError("Docker authority output must be an absolute path")
    if output.name != "config.json":
        raise ValueError("Docker authority output must be named config.json")
    if not REGISTRY_PATTERN.fullmatch(registry):
        raise ValueError("Docker registry contains unsupported characters")
    if not actor or any(character in actor for character in ("\0", "\r", "\n")):
        raise ValueError("GitHub actor is missing or malformed")
    if not token or any(character in token for character in ("\0", "\r", "\n")):
        raise ValueError("GitHub package token is missing or malformed")
    if not cli_plugin_dir.is_absolute():
        raise ValueError("Docker CLI plugin directory must be an absolute path")
    if cli_plugin_dir.is_symlink() or not cli_plugin_dir.is_dir():
        raise ValueError("Docker CLI plugin directory must be a real directory")

    parent = output.parent
    if parent.is_symlink() or not parent.is_dir():
        raise ValueError("Docker authority parent must be a real directory")
    parent_stat = parent.stat()
    if parent_stat.st_uid != os.getuid():
        raise ValueError("Docker authority parent has an unexpected owner")
    if stat.S_IMODE(parent_stat.st_mode) != 0o700:
        raise ValueError("Docker authority parent mode must be 0700")

    encoded_authority = base64.b64encode(
        f"{actor}:{token}".encode("utf-8")
    ).decode("ascii")
    payload = json.dumps(
        {
            "auths": {registry: {"auth": encoded_authority}},
            "cliPluginsExtraDirs": [str(cli_plugin_dir)],
        },
        separators=(",", ":"),
        sort_keys=True,
    ) + "\n"

    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(output, flags, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        try:
            os.close(descriptor)
        except OSError:
            pass
        output.unlink(missing_ok=True)
        raise

    output_stat = output.lstat()
    if not stat.S_ISREG(output_stat.st_mode):
        output.unlink(missing_ok=True)
        raise ValueError("Docker authority output is not a regular file")
    if (
        output_stat.st_uid != os.getuid()
        or stat.S_IMODE(output_stat.st_mode) != 0o600
    ):
        output.unlink(missing_ok=True)
        raise ValueError("Docker authority output owner or mode is unsafe")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--registry", default="ghcr.io")
    parser.add_argument("--cli-plugin-dir", type=Path, required=True)
    args = parser.parse_args()

    actor = _required_environment("GITHUB_ACTOR")
    token = _required_environment("GHCR_TOKEN")
    write_config(
        args.output,
        args.registry,
        actor,
        token,
        args.cli_plugin_dir,
    )
    print(
        "DOCKER_AUTH_CONFIG_WRITTEN "
        f"registry={args.registry} mode=0600 credentialStore=run-scoped-file"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
