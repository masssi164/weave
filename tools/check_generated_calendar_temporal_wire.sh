#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLUTTER_BIN="${FLUTTER_BIN:-$(command -v flutter || true)}"
if [[ -z "${FLUTTER_BIN}" && -x "${HOME}/flutter/bin/flutter" ]]; then
  FLUTTER_BIN="${HOME}/flutter/bin/flutter"
fi
if [[ -z "${FLUTTER_BIN}" ]]; then
  echo 'flutter not found; install Flutter or set FLUTTER_BIN' >&2
  exit 127
fi

cd "${REPOSITORY_ROOT}/client"
for zone in UTC Europe/Berlin Pacific/Apia; do
  echo "Generated Calendar temporal wire tests: ${zone}"
  TZ="${zone}" "${FLUTTER_BIN}" test --no-pub \
    test/integrations/weave_api/data/services/generated_calendar_time_value_test.dart
done
