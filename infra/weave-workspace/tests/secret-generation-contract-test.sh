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
import tempfile
from pathlib import Path
from types import SimpleNamespace

root = Path(os.environ["ROOT_DIR"])
sys.path.insert(0, str(root / "scripts"))

import init_secrets  # noqa: E402

assert set(init_secrets.CLI_ARGUMENT_SECRETS) == {
    "nextcloud-admin-password",
    "nextcloud-db-password",
}
assert set(init_secrets.SMTP_SECRETS) == {"smtp-password"}
assert 'f"mail.{context.env[\'WEAVE_TENANT_DOMAIN\']}"' in (
    root / "scripts/init_secrets.py"
).read_text(encoding="utf-8")

values = {init_secrets._random_secret().decode("ascii").strip() for _ in range(512)}
assert len(values) == 512
assert all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{64}", value) for value in values)
assert all(not value.startswith("-") for value in values)
assert init_secrets._valid_cli_argument_secret(b"fixture-value\n")
assert not init_secrets._valid_cli_argument_secret(b"-option-shaped\n")
assert not init_secrets._valid_cli_argument_secret(b"")
assert not init_secrets._valid_cli_argument_secret(b"line-one\nline-two\n")

with tempfile.TemporaryDirectory() as temporary:
    temporary_root = Path(temporary)
    env = {
        "WEAVE_TENANT_DOMAIN": "weave.test",
        "WEAVE_PUBLIC_URL": "https://weave.test:44443",
        "WEAVE_API_ORIGIN": "https://api.weave.test:44443",
        "WEAVE_AUTH_URL": "https://auth.weave.test:44443",
        "WEAVE_MATRIX_URL": "https://matrix.weave.test:44443",
        "WEAVE_FILES_URL": "https://files.weave.test:44443",
    }
    staging = temporary_root / "staging"
    init_secrets._generate_tls(
        SimpleNamespace(environment="dev", tls_root=staging, env=env)
    )
    generated_root = temporary_root / "generated"
    retired = generated_root / "01-infrastructure/caddy/certs"
    retired.mkdir(parents=True)
    for source, target in (
        ("ca.pem", "weave-local-ca.pem"),
        ("ca-key.pem", "weave-local-ca-key.pem"),
        ("cert.pem", "weave.test.pem"),
        ("key.pem", "weave.test-key.pem"),
    ):
        (retired / target).write_bytes((staging / source).read_bytes())
    canonical = generated_root / "tls"
    init_secrets._generate_tls(
        SimpleNamespace(
            environment="dogfood",
            generated_root=generated_root,
            tls_root=canonical,
            env=env,
        )
    )
    assert (canonical / "ca.pem").read_bytes() == (staging / "ca.pem").read_bytes()
    assert (canonical / "cert.pem").read_bytes() == (staging / "cert.pem").read_bytes()
    assert (canonical / "mailpit-cert.pem").is_file()
    init_secrets._generate_tls(
        SimpleNamespace(
            environment="dogfood",
            generated_root=generated_root,
            tls_root=canonical,
            env=env,
        )
    )
PY

printf '%s\n' "secret generation contract tests passed"
