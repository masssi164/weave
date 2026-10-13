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
# Run source/DTO assertions on the pinned SDK VM without dispatching native
# package hooks. Flutter engine tests remain on the ordinary Flutter runner.
if [[ $# -ne 1 ]]; then
  echo "usage: run_pure_client_test.sh test/path_to_test.dart" >&2
  exit 2
fi
IFS=$'\t' read -r DART_VM TEST_RUNNER < <(python3 - "${FLUTTER_BIN}" "$1" <<'PYTHON'
import json
import os
import re
import shutil
import sys
from pathlib import Path
from urllib.parse import unquote, urljoin, urlparse

test = Path(sys.argv[2]).resolve()
if not test.is_file() or not test.is_relative_to(Path('test').resolve()):
    raise SystemExit('Expected an existing client/test Dart file')
if re.search(r"^\s*(?:import|export)\s+['\"](?:dart:ui|package:flutter(?:_test)?/)", test.read_text(), re.MULTILINE):
    raise SystemExit('Flutter engine tests must use the ordinary Flutter runner')
config = Path('.dart_tool/package_config.json').resolve()
if not config.is_file():
    raise SystemExit('Run Flutter dependency resolution before the pure client tests')
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
exec "${DART_VM}" --packages=.dart_tool/package_config.json "${TEST_RUNNER}" "$1"
