#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
python3 "${ROOT}/infra/weave-workspace/tests/dogfood_public_setup_test.py" -v
python3 "${ROOT}/tools/dogfood_prepare_test.py" -v
