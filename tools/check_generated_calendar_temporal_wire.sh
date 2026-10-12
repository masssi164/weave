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
# Pure generated DTO tests need no Flutter engine or native package hooks. Use
# the pinned SDK VM and the locked package:test public entry point directly.
IFS=$'\t' read -r DART_VM TEST_RUNNER < <(python3 - "${FLUTTER_BIN}" <<'PYTHON'
import json
import os
import shutil
import sys
from pathlib import Path
from urllib.parse import unquote, urljoin, urlparse

config = Path('.dart_tool/package_config.json').resolve()
if not config.is_file():
    raise SystemExit('Run Flutter dependency resolution before the generated DTO tests')
package = next((value for value in json.loads(config.read_text())['packages'] if value['name'] == 'test'), None)
if package is None:
    raise SystemExit('Locked package:test is missing from the package configuration')
runner = Path(unquote(urlparse(urljoin(config.as_uri(), package['rootUri'])).path)) / 'bin/test.dart'
flutter = shutil.which(sys.argv[1])
if flutter is None:
    raise SystemExit('Configured Flutter executable is missing')
vm = Path(flutter).resolve().parent / 'cache/dart-sdk/bin/dartvm'
if not vm.is_file() or not os.access(vm, os.X_OK) or not runner.is_file():
    raise SystemExit('Pinned Dart VM or locked package:test entry point is missing')
print(f'{vm}\t{runner}')
PYTHON
)
for zone in UTC Europe/Berlin Pacific/Apia; do
  echo "Generated Calendar temporal wire tests: ${zone}"
  TZ="${zone}" "${DART_VM}" --packages=.dart_tool/package_config.json "${TEST_RUNNER}" \
    test/integrations/weave_api/data/services/generated_calendar_time_value_test.dart
done
