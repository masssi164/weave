#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

python3 "${REPOSITORY_ROOT}/gradle/tasks/bootstrap-owner-test.py"
