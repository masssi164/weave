#!/usr/bin/env python3
"""Run the host MCP boundary from generated, public dev coordinates."""

from __future__ import annotations

import os
import re
from pathlib import Path


KEY = re.compile(r"^[A-Z][A-Z0-9_]*$")


def load_environment(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("generated mcp/host.env is unavailable; prepare dev dependencies first")
    values: dict[str, str] = {}
    for number, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not original:
            continue
        key, separator, value = original.partition("=")
        if (
            not separator
            or not KEY.fullmatch(key)
            or key in values
            or any(character in value for character in ("\x00", "\r", "\n"))
        ):
            raise RuntimeError(f"host.env:{number}: invalid coordinate")
        values[key] = value
    for required in (
        "WEAVE_MCP_BACKEND_FILES_URI",
        "WEAVE_MCP_EXCHANGE_CLIENT_JWK_FILE",
        "WEAVE_MCP_TOKEN_URI",
        "WEAVE_OIDC_JWK_SET_URI",
    ):
        if not values.get(required):
            raise RuntimeError(f"host.env omits {required}")
    return values


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    repository = root.parents[1]
    environment = dict(os.environ)
    environment.update(load_environment(root / ".generated/dev/mcp/host.env"))
    command = [
        str(repository / "gradlew"),
        ":weave-mcp-server:bootRun",
        "--console=plain",
    ]
    os.chdir(repository)
    os.execve(command[0], command, environment)


if __name__ == "__main__":
    main()
