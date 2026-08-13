#!/usr/bin/env python3
"""Fail closed until the disposable-environment FGAP migration is qualified."""

from __future__ import annotations

import argparse
from pathlib import Path

from compose_env import load_context


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    root = args.root.resolve()
    # Validate the reviewed dev topology before returning the architectural
    # blocker. This prevents a malformed environment from being mistaken for
    # the sole reason development startup is unavailable.
    load_context("dev", root, args.env_file)
    raise SystemExit(
        "WEAVE_DEV_PREPARE_ERROR the deferred Keycloak FGAP migration is not "
        "qualified for disposable dev; no receipt or production backup proof "
        "will be fabricated"
    )


if __name__ == "__main__":
    raise SystemExit(main())
