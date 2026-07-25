#!/usr/bin/env python3
"""Create one private, idempotent local member fixture without printing its secret."""

from __future__ import annotations

import argparse
import json
import os
import secrets
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    path = args.output.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.exists():
        metadata = path.lstat()
        if path.is_symlink() or not path.is_file() or metadata.st_mode & 0o777 != 0o600:
            raise SystemExit("WEAVE_TEST_USERS_ERROR existing output must be a regular mode-0600 non-symlink file")
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, list) or len(value) != 1 or value[0].get("username") != "weave-test-member":
            raise SystemExit("WEAVE_TEST_USERS_ERROR existing fixture is not the canonical local member")
    else:
        value = [
            {
                "username": "weave-test-member",
                "email": "weave-test-member@weave.test",
                "secret": secrets.token_urlsafe(32),
                "roles": ["member"],
            }
        ]
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2)
            stream.write("\n")
    print(f"test-user file: {path}")
    print("test-user username: weave-test-member")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
