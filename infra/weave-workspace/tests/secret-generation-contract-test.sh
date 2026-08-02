#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
export ROOT_DIR

python3 - <<'PY'
import os
import re
import sys
from pathlib import Path

root = Path(os.environ["ROOT_DIR"])
sys.path.insert(0, str(root / "scripts"))

import init_secrets  # noqa: E402

assert set(init_secrets.CLI_ARGUMENT_SECRETS) == {
    "nextcloud-admin-password",
    "nextcloud-db-password",
}

values = {init_secrets._random_secret().decode("ascii").strip() for _ in range(512)}
assert len(values) == 512
assert all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{64}", value) for value in values)
assert all(not value.startswith("-") for value in values)
assert init_secrets._valid_cli_argument_secret(b"fixture-value\n")
assert not init_secrets._valid_cli_argument_secret(b"-option-shaped\n")
assert not init_secrets._valid_cli_argument_secret(b"")
assert not init_secrets._valid_cli_argument_secret(b"line-one\nline-two\n")
PY

printf '%s\n' "secret generation contract tests passed"
