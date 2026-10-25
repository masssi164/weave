#!/usr/bin/env python3
"""Resolve one selected JVM inventory; this does not yet select production domain packaging."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path


class DomainSelectionError(ValueError):
    """Invalid domain selection or inconsistent build-source identity."""


def normalize(value: str, available: list[str]) -> list[str]:
    tokens = [token.strip() for token in value.split(',')]
    if tokens == ['all']:
        return sorted(available)
    if not tokens or any(not token or not re.fullmatch(r'[a-z][a-z0-9-]*', token)
                         for token in tokens):
        raise DomainSelectionError('domains must be all or a comma-separated domain list')
    if 'all' in tokens:
        raise DomainSelectionError('all cannot be combined with individual domains')
    if set(tokens) - set(available):
        raise DomainSelectionError('unknown domain; available: ' + ','.join(sorted(available)))
    return sorted(set(tokens))


def load_catalog(root: Path) -> dict:
    try:
        catalog = json.loads((root / 'server/domains/catalog.json').read_text())
    except (OSError, ValueError) as failure:
        raise DomainSelectionError('domain catalog is missing or invalid JSON') from failure
    domains = catalog.get('domains') if isinstance(catalog, dict) else None
    if (not isinstance(domains, dict) or not domains
            or any(not re.fullmatch(r'[a-z][a-z0-9-]*', name) or name == 'all'
                   for name in domains)):
        raise DomainSelectionError('catalog must define non-empty named domains')
    for inventory in domains.values():
        tests = inventory.get('server_unit_tests') if isinstance(inventory, dict) else None
        tasks = inventory.get('additional_unit_tasks') if isinstance(inventory, dict) else None
        if (not isinstance(tests, list) or not tests
                or any(not isinstance(name, str)
                       or not re.fullmatch(r'(?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*Test', name)
                       for name in tests)
                or len(tests) != len(set(tests)) or not isinstance(tasks, list)
                or any(not isinstance(task, str) or not re.fullmatch(r':[a-z][a-z0-9-]*:test', task)
                       for task in tasks)):
            raise DomainSelectionError('catalog must declare explicit non-empty JVM test inventories')
    return catalog


def resolve(catalog: dict, requested: str | None, branch: str | None) -> dict:
    available = sorted(catalog['domains'])
    if requested is not None and requested.strip():
        return {'domains': normalize(requested, available), 'selectionSource': 'explicit'}
    pin = catalog.get('selection', {})
    if not isinstance(pin, dict):
        raise DomainSelectionError('catalog selection must be an object')
    if branch and pin.get('pinned_branch') == branch:
        pinned = pin.get('pinned_domains')
        if (not isinstance(pinned, list) or not pinned
                or any(not isinstance(domain, str) or not domain.strip() or ',' in domain
                       for domain in pinned)):
            raise DomainSelectionError('matching branch pin must provide non-empty pinned_domains')
        return {'domains': normalize(','.join(pinned), available), 'selectionSource': 'pin'}
    return {'domains': available, 'selectionSource': 'all'}


def source_branch(root: Path, branch: str | None, commit: str | None) -> str | None:
    if (branch is None) != (commit is None):
        raise DomainSelectionError('source-branch and source-commit must be supplied together')
    if not (root / '.git').exists():
        if branch is not None:
            raise DomainSelectionError('explicit source identity requires Git checkout metadata')
        return None
    head = subprocess.run(['git', '-C', str(root), 'rev-parse', '--verify', 'HEAD'],
                          capture_output=True, text=True, check=False)
    symbolic = subprocess.run(['git', '-C', str(root), 'symbolic-ref', '--quiet', '--short', 'HEAD'],
                              capture_output=True, text=True, check=False)
    if head.returncode != 0 or symbolic.returncode not in (0, 1):
        raise DomainSelectionError('could not verify checkout source identity')
    current = symbolic.stdout.strip() if symbolic.returncode == 0 else None
    if branch is None:
        return current
    if branch.startswith('refs/') and not branch.startswith('refs/heads/'):
        raise DomainSelectionError('source-branch must identify a branch, not another ref type')
    if branch.startswith('refs/heads/'):
        branch = branch.removeprefix('refs/heads/')
    valid_branch = subprocess.run(['git', 'check-ref-format', '--branch', branch],
                                 capture_output=True, text=True, check=False)
    if (valid_branch.returncode != 0 or valid_branch.stdout.strip() != branch
            or not re.fullmatch(r'[0-9a-f]{40}', commit or '')
            or head.stdout.strip() != commit or (current and current != branch)):
        raise DomainSelectionError('source branch/commit does not match the checkout')
    return branch


def resolve_checkout(root: Path, requested: str | None = None,
                     branch: str | None = None, commit: str | None = None) -> dict:
    # An explicit selection needs no branch discovery, but supplied identity assertions remain binding.
    needs_identity = not requested or not requested.strip() or branch is not None or commit is not None
    actual_branch = source_branch(root, branch, commit) if needs_identity else None
    return resolve(load_catalog(root), requested, actual_branch)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--domains', help='all or comma-separated domains; empty means pin/all fallback')
    parser.add_argument('--source-branch', help='trusted source branch paired with the checked-out commit')
    parser.add_argument('--source-commit', help='exact checked-out commit for a trusted branch assertion')
    parser.add_argument('--format', choices=['json', 'csv'], default='json')
    args = parser.parse_args()
    try:
        selection = resolve_checkout(args.root, args.domains, args.source_branch, args.source_commit)
    except DomainSelectionError as failure:
        parser.exit(2, f'domain-selection: {failure}\n')
    print(','.join(selection['domains']) if args.format == 'csv' else json.dumps(selection, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
