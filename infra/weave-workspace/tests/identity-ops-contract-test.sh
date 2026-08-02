#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "${ROOT_DIR}/tests/identity_ops_contract_test.py"
