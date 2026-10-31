#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
python3 "$ROOT/infra/weave-workspace/tests/ldap_tls_test.py"
