#!/usr/bin/env python3
"""Qualify both real native clients within the existing isolated testApp run."""
from __future__ import annotations

import json
import os
import subprocess
import signal
import shutil
import sys
from pathlib import Path

from run_ios_simulator_product_acceptance import inspect_toolchain

ROOT = Path(__file__).resolve().parents[2]


def interrupt_native_process(process, *, graceful_seconds=60, reap_seconds=10) -> bool:
    """Allow the owned runner to finalize evidence within the Java owner's 90s wait."""
    try:
        os.killpg(process.pid, signal.SIGINT)
    except ProcessLookupError:
        process.wait(timeout=reap_seconds)
        return True
    try:
        process.wait(timeout=graceful_seconds)
        return True
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=reap_seconds)
        return False


def main() -> int:
    if os.environ.get('WEAVE_NATIVE_LOCAL_INSPECTION') == 'true':
        # Inspection cannot enter qualification artifact construction or PASS paths.
        import run_dual_ios_collaboration as dual
        output = dual.ios.verified_private_parent() / ('dual-ios-result-' + os.environ['WEAVE_E2E_RUN_ID'] + '.json')
        return dual.main(['--output', str(output)]) or 70
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
            interrupted_cleanup = False
            def interrupted(number, _frame):
                nonlocal interrupted_cleanup
                # Do not reenter bounded cleanup if the owner signals again.
                signal.signal(signal.SIGTERM, signal.SIG_IGN)
                signal.signal(signal.SIGINT, signal.SIG_IGN)
                try:
                    interrupted_cleanup = interrupt_native_process(process)
                finally:
                    raise SystemExit(128 + number)
            old_term = signal.signal(signal.SIGTERM, interrupted)
            old_int = signal.signal(signal.SIGINT, interrupted)
            status = None
            try:
                status = process.wait()
            finally:
                pending_failure = sys.exc_info()[1]
                signal.signal(signal.SIGTERM, old_term)
                signal.signal(signal.SIGINT, old_int)
                evidence[platform]['status'] = 'passed' if status == 0 else 'failed'
                if platform == 'iosSimulator' and collaboration_path.is_file() and not collaboration_path.is_symlink():
                    try:
                        collaboration = json.loads(collaboration_path.read_text())
                        if not isinstance(collaboration, dict):
                            raise ValueError('Invalid collaboration result')
                        evidence[platform]['collaboration'] = collaboration
                    except (OSError, ValueError):
                        if pending_failure is None:
                            raise RuntimeError('Native collaboration result could not be collected') from None
                        pending_failure.add_note('Native collaboration result could not be collected')
                if pending_failure is not None and interrupted_cleanup and platform == 'iosSimulator':
                    evidence['cleanupComplete'] = evidence[platform]['collaboration'].get('cleanupComplete') is True
            if platform == 'iosSimulator' and status == 0 and evidence[platform]['collaboration'].get('status') != 'passed':
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
        pending_failure = sys.exc_info()[1]
        collection_failure = None
        try:
            if collaboration_path.is_file() and not collaboration_path.is_symlink():
                collaboration_path.unlink()
        except OSError:
            evidence['cleanupComplete'] = False
            collection_failure = RuntimeError('Native collaboration private result cleanup failed')
        try:
            output.write_text(json.dumps(evidence, indent=2) + '\n')
        except OSError:
            if collection_failure is None:
                collection_failure = RuntimeError('Native qualification evidence could not be written')
            else:
                collection_failure.add_note('Native qualification evidence could not be written')
        if collection_failure is not None:
            if pending_failure is None:
                raise collection_failure from None
            pending_failure.add_note(str(collection_failure))


if __name__ == '__main__':
    raise SystemExit(main())
