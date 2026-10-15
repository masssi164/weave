#!/usr/bin/env python3
"""Qualify both real native clients within the existing isolated testApp run."""
from __future__ import annotations

import json
import os
import subprocess
import signal
import shutil
from pathlib import Path

from run_ios_simulator_product_acceptance import inspect_toolchain

ROOT = Path(__file__).resolve().parents[2]


def main() -> int:
    output = Path(os.environ['WEAVE_TEST_APP_RUN_ROOT']) / 'native-qualification.json'
    identity = json.loads((ROOT / 'specs/weave-specs.lock.json').read_text())
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if commit != os.environ['WEAVE_CANDIDATE_COMMIT']:
        raise RuntimeError('Native source differs from the exact candidate')
    if subprocess.check_output(['git', 'status', '--porcelain=v1', '--untracked-files=all'], cwd=ROOT, text=True).strip():
        raise RuntimeError('Native qualification requires a clean exact source checkout')
    toolchain = inspect_toolchain()
    rustup = shutil.which('rustup')
    if rustup is None:
        raise RuntimeError('Native qualification requires the pinned rustup toolchain')
    native_env = os.environ.copy()
    native_env['RUSTUP_TOOLCHAIN'] = toolchain['rustToolchain']
    native_env['PATH'] = str(Path(rustup).parent) + os.pathsep + native_env['PATH']
    evidence = {
        'schemaVersion': 'weave.native-qualification/v2',
        'candidateCommit': commit,
        'sourceTree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=ROOT, text=True).strip(),
        'specificationCommit': identity['specCorpus']['gitCommit'],
        'macos': {'status': 'not-run'},
        'iosSimulator': {'status': 'not-run', 'rustTarget': 'aarch64-apple-ios-sim',
                         'collaboration': {'status': 'not-run', 'cleanupComplete': False}},
        'cleanupComplete': False,
        'supportSafe': True,
        'containsSecretValues': False,
        'toolchain': toolchain,
    }
    collaboration_path = ROOT / 'client/build/private-ios-qualification' / ('dual-ios-result-' + os.environ['WEAVE_E2E_RUN_ID'] + '.json')
    try:
        for platform, script in [('macos', 'run_native_product_acceptance.py'), ('iosSimulator', 'run_dual_ios_collaboration.py')]:
            arguments = ['python3', str(ROOT / 'client/tool' / script)]
            if platform == 'iosSimulator':
                if collaboration_path.is_symlink() or collaboration_path.exists():
                    raise RuntimeError('Stale or unsafe collaboration result path')
                arguments += ['--output', str(collaboration_path)]
            process = subprocess.Popen(arguments, start_new_session=True, env=native_env)
            def interrupted(number, _frame):
                # Python SIGINT invokes each runner's finally cleanup, including
                # the iOS run-owned simulator and macOS checkout app.
                try:
                    os.killpg(process.pid, signal.SIGINT)
                    process.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                raise SystemExit(128 + number)
            old_term = signal.signal(signal.SIGTERM, interrupted)
            old_int = signal.signal(signal.SIGINT, interrupted)
            try:
                status = process.wait()
            finally:
                signal.signal(signal.SIGTERM, old_term)
                signal.signal(signal.SIGINT, old_int)
            evidence[platform]['status'] = 'passed' if status == 0 else 'failed'
            if platform == 'iosSimulator' and collaboration_path.is_file() and not collaboration_path.is_symlink():
                evidence[platform]['collaboration'] = json.loads(collaboration_path.read_text())
                if status == 0 and evidence[platform]['collaboration'].get('status') != 'passed':
                    raise RuntimeError('Missing actual collaboration proof')
            if status:
                return status
        if subprocess.check_output(['git', 'status', '--porcelain=v1', '--untracked-files=all'], cwd=ROOT, text=True).strip():
            raise RuntimeError('Native build changed the exact source checkout')
        evidence['cleanupComplete'] = evidence['iosSimulator']['collaboration'].get('cleanupComplete') is True
        if not evidence['cleanupComplete']:
            raise RuntimeError('Dual simulator cleanup is unverified')
        return 0
    finally:
        output.write_text(json.dumps(evidence, indent=2) + '\n')
        if collaboration_path.is_file() and not collaboration_path.is_symlink():
            collaboration_path.unlink()


if __name__ == '__main__':
    raise SystemExit(main())
