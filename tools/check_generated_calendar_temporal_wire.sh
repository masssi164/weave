#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for zone in UTC Europe/Berlin Pacific/Apia; do
  echo "Generated Calendar temporal wire tests: ${zone}"
  TZ="${zone}" bash "${REPOSITORY_ROOT}/tools/run_pure_client_test.sh" \
    test/integrations/weave_api/data/services/generated_calendar_time_value_test.dart
  (
    cd "${REPOSITORY_ROOT}/client"
    TZ="${zone}" "${FLUTTER_BIN:-flutter}" test --no-pub \
      test/features/home/home_screen_test.dart \
      --plain-name 'preserves calendar wall-clock fields'
  )
done
