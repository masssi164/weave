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
    return validate_catalog(catalog)


def release_request(catalog: dict) -> str:
    """An explicit catalog release selection, independent of the actual source branch."""
    pin = catalog.get('selection', {}).get('pinned_domains')
    if not isinstance(pin, list) or not pin or any(not isinstance(item, str) for item in pin):
        raise DomainSelectionError('release selection requires catalog pinned_domains')
    return ','.join(resolve(catalog, ','.join(pin), None)['domains'])


def validate_catalog(catalog: dict) -> dict:
    """Validate a catalog loaded from the working tree or an exact Git candidate."""
    domains = catalog.get('domains') if isinstance(catalog, dict) else None
    if (not isinstance(domains, dict) or not domains
            or any(not re.fullmatch(r'[a-z][a-z0-9-]*', name) or name == 'all'
                   for name in domains)):
        raise DomainSelectionError('catalog must define non-empty named domains')
    for name, inventory in domains.items():
        tasks = inventory.get('unit_tasks') if isinstance(inventory, dict) else None
        if (not isinstance(tasks, list) or not tasks
                or any(not isinstance(task, str) or not re.fullmatch(r'(?::[a-z][a-z0-9-]*)+:test', task)
                       for task in tasks)
                or len(tasks) != len(set(tasks))
                or f':server:domains:{name}:test' not in tasks):
            raise DomainSelectionError('catalog must declare real owning domain JVM test tasks')
    for name, inventory in domains.items():
        required = inventory.get('required_domains', [])
        if (not isinstance(required, list)
                or any(not isinstance(dependency, str) or dependency not in domains or dependency == name
                       for dependency in required)
                or len(required) != len(set(required))):
            raise DomainSelectionError('catalog domain dependencies must name distinct other domains')
    validate_domain_dependencies(domains)
    return catalog


def validate_domain_dependencies(domains: dict) -> None:
    visited: set[str] = set()
    active: list[str] = []

    def visit(name: str) -> None:
        if name in active:
            cycle = active[active.index(name):] + [name]
            raise DomainSelectionError('catalog domain dependency cycle: ' + ' -> '.join(cycle))
        if name in visited:
            return
        active.append(name)
        for dependency in sorted(domains[name].get('required_domains', [])):
            visit(dependency)
        active.pop()
        visited.add(name)

    for name in sorted(domains):
        visit(name)


def matrix(catalog: dict, selection: dict, iteration: str = 'selected') -> dict:
    """Schedule a valid resolved selection without widening its selected domain set."""
    if iteration not in ('selected', 'individual'):
        raise DomainSelectionError('iteration must be selected or individual')
    selected = set(selection['domains'])
    names = ['selected'] if iteration == 'selected' else sorted(selected)
    rows = []
    for name in names:
        required = set(selected) if iteration == 'selected' else set()
        pending = [] if iteration == 'selected' else [name]
        while pending:
            dependency = pending.pop()
            if dependency in required:
                continue
            required.add(dependency)
            pending.extend(catalog['domains'][dependency].get('required_domains', []))
        if not required <= selected:
            raise DomainSelectionError('individual iteration cannot include domains outside the resolved selection')
        rows.append({'domain': name, 'domains': ','.join(sorted(required)),
                     'selection': ','.join(selection['domains']), 'selectionSource': selection['selectionSource']})
    return {'include': rows}


def resolve(catalog: dict, requested: str | None, branch: str | None) -> dict:
    available = sorted(catalog['domains'])
    if requested is not None and requested.strip():
        return validate_dependencies(catalog, normalize(requested, available), 'explicit')
    pin = catalog.get('selection', {})
    if not isinstance(pin, dict):
        raise DomainSelectionError('catalog selection must be an object')
    if branch and pin.get('pinned_branch') == branch:
        pinned = pin.get('pinned_domains')
        if (not isinstance(pinned, list) or not pinned
                or any(not isinstance(domain, str) or not domain.strip() or ',' in domain
                       for domain in pinned)):
            raise DomainSelectionError('matching branch pin must provide non-empty pinned_domains')
        return validate_dependencies(catalog, normalize(','.join(pinned), available), 'pin')
    return validate_dependencies(catalog, available, 'all')


def validate_dependencies(catalog: dict, selected: list[str], source: str) -> dict:
    for name in selected:
        missing = set(catalog['domains'][name].get('required_domains', [])) - set(selected)
        if missing:
            raise DomainSelectionError(f"domain {name} requires explicit selection of: {','.join(sorted(missing))}")
    return {'domains': selected, 'selectionSource': source}


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
                     branch: str | None = None, commit: str | None = None,
                     *, iteration: str | None = None) -> dict:
    # An explicit selection needs no branch discovery, but supplied identity assertions remain binding.
    needs_identity = not requested or not requested.strip() or branch is not None or commit is not None
    actual_branch = source_branch(root, branch, commit) if needs_identity else None
    catalog = load_catalog(root)
    selection = resolve(catalog, requested, actual_branch)
    return matrix(catalog, selection, iteration) if iteration is not None else selection


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--domains', help='all or comma-separated domains; empty means pin/all fallback')
    parser.add_argument('--release-selection', action='store_true', help='Explicitly request catalog pinned_domains without changing source identity')
    parser.add_argument('--source-branch', help='trusted source branch paired with the checked-out commit')
    parser.add_argument('--source-commit', help='exact checked-out commit for a trusted branch assertion')
    parser.add_argument('--format', choices=['json', 'csv', 'matrix'], default='json')
    parser.add_argument('--iteration', choices=['selected', 'individual'], default='selected',
                        help='matrix scheduling: one aggregate row or one row per domain with its required dependencies')
    args = parser.parse_args()
    try:
        if args.iteration != 'selected' and args.format != 'matrix':
            raise DomainSelectionError('--iteration individual requires --format matrix')
        if args.release_selection and args.domains:
            raise DomainSelectionError('--release-selection cannot override --domains')
        requested = release_request(load_catalog(args.root)) if args.release_selection else args.domains
        selection = resolve_checkout(args.root, requested, args.source_branch, args.source_commit,
                                     iteration=args.iteration if args.format == 'matrix' else None)
    except DomainSelectionError as failure:
        parser.exit(2, f'domain-selection: {failure}\n')
    print(','.join(selection['domains']) if args.format == 'csv' else json.dumps(selection, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
