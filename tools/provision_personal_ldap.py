#!/usr/bin/env python3
"""Provision the personal directory account only in protected physical preparation.

No personal login, token request or app-session injection is performed.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'infra/weave-workspace/scripts'))
from compose_env import ContractError, compose_environment, load_context
from compose_runtime import _dogfood_session_lock, _service_container, inspect_resource
from artifact_consumers import candidate_composition, load_runtime_images, verify_runtime_container
from build_artifacts import ArtifactError


def require_personal_resources(context) -> None:
    """Accept fixed session ownership while requiring full persistent LDAP provenance."""
    if (context.environment != 'dogfood' or context.isolated_namespace is not None
            or context.env['WEAVE_COMPOSE_PROJECT'] != 'weave-dogfood'):
        raise ContractError('fixed-personal-dogfood-required')
    for key in ('WEAVE_LDAP_DATA_VOLUME', 'WEAVE_LDAP_CONFIG_VOLUME'):
        if inspect_resource(context, 'volume', context.env[key]) != (True, True):
            raise ContractError('owned-persistent-directory-required')
    for kind, key, component in (
            ('network', 'WEAVE_DOCKER_NETWORK', 'network'),
            ('volume', 'WEAVE_DB_DATA_VOLUME', 'postgres'),
            ('volume', 'WEAVE_NATIVE_FILES_DATA_VOLUME', 'files-native'),
            ('volume', 'WEAVE_MAILPIT_DATA_VOLUME', 'mail')):
        result = subprocess.run(['docker', kind, 'inspect', context.env[key],
                                 '--format', '{{json .Labels}}'],
                                capture_output=True, text=True, timeout=30, check=False)
        observed = json.loads(result.stdout) if result.returncode == 0 else {}
        expected = {'managed': 'true', 'environment': 'dogfood', 'scope': 'resettable-session',
                    'stack': 'weave', 'component': component}
        if not isinstance(observed, dict) or any(
                observed.get('com.massimotter.weave.' + key) != value
                for key, value in expected.items()):
            raise ContractError('owned-fixed-dogfood-session-required')


def provision(candidate: str) -> None:
    # Remove the value before invoking any artifact/Docker/Git child process.
    value = os.environ.pop('DOGFOOD_USER_SECRET', '')
    credential = value.encode('utf-8')
    del value
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('GITHUB_REF') != 'refs/heads/dogfood'
            or os.environ.get('RUNNER_NAME') != 'weave-live-mac-mini'
            or not re.fullmatch(r'[0-9a-f]{40}', candidate)):
        raise ContractError('protected-physical-preparation-required')
    if not 8 <= len(credential) <= 4096 or any(c in credential for c in (b'\0', b'\r', b'\n')):
        raise ContractError('DOGFOOD_USER_SECRET-required-as-initial-password')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    branch = subprocess.check_output(['git', 'rev-parse', 'origin/dogfood'], cwd=ROOT, text=True).strip()
    if head != candidate or branch != candidate or subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT):
        raise ContractError('clean-current-dogfood-candidate-required')
    candidate_composition(candidate)
    os.environ.update(load_runtime_images(candidate))
    context = load_context('dogfood', ROOT / 'infra/weave-workspace', os.environ.get('WEAVE_ENV_FILE'))
    with _dogfood_session_lock(context):
        require_personal_resources(context)
        verify_runtime_container(_service_container(context, 'backend', deadline=time.monotonic() + 30), candidate)
        directory = _service_container(context, 'ldap', deadline=time.monotonic() + 30)
        if (directory.get('Image') != context.env.get('WEAVE_LDAP_IMAGE')
                or not re.fullmatch(r'[0-9a-f]{64}', directory.get('Id', ''))):
            raise ContractError('qualified-directory-container-required')
        command = ['docker', 'exec', '--interactive', '--user', context.env['WEAVE_RUNTIME_UID'] + ':0',
                   directory['Id'], 'python3', '/opt/weave/ldap/provision.py', 'personal',
                   '--environment', 'dogfood', '--domain', context.env['WEAVE_TENANT_DOMAIN']]
        result = subprocess.run(command, input=credential, capture_output=True, timeout=60,
                                env=compose_environment(context), check=False)
        del credential
        if (result.returncode or result.stdout.strip() !=
                b'LDAP_PROVISION_RESULT status=passed phase=personal credentials=preserved'):
            raise ContractError('personal-directory-provisioning-blocked')
        current = _service_container(context, 'ldap', deadline=time.monotonic() + 30)
        if current.get('Id') != directory['Id'] or current.get('Image') != directory['Image']:
            raise ContractError('directory-changed-during-personal-preparation')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', required=True)
    args = parser.parse_args()
    try:
        provision(args.candidate)
    except (ArtifactError, ContractError, OSError, ValueError, subprocess.SubprocessError):
        print('PERSONAL_LDAP_RESULT status=blocked authenticationEvidence=false', file=sys.stderr)
        return 1
    print('PERSONAL_LDAP_RESULT status=passed username=massimotter authenticationEvidence=false')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
