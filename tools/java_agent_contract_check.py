#!/usr/bin/env python3
"""Check skill links and ratchet explicit startup provider selectors, not runtime semantics.

This source check recognizes Spring selector annotations, literal property lookups,
provider-named profiles and application YAML selectors. Computed names, arbitrary
custom conditions and programmatic routing still require architecture/behavior tests.
The finite reviewed baseline may shrink; this tool never regenerates it.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


BASELINE = 'tools/fixtures/java_agent_legacy_selectors.json'
SKILLS = ('weave-spring-architecture', 'weave-http-clients',
          'weave-config-and-beans', 'weave-native-aot')
STANDARD = 'docs/java-spring-standard.md'
# Required roots track actual extracted libraries, not the retired root Files project.
# The walk below still inspects every production source root, including future modules.
MODULES = ('server', 'server/core', 'server/core/model',
           'server/domains/agent-runtime/model', 'server/domains/agent-runtime/persistence',
           'server/domains/agent-runtime/provider', 'server/domains/agent-runtime/security',
           'server/domains/portability/model', 'server/domains/portability/persistence',
           'server/domains/chat/persistence',
           'server/domains/files/model', 'server/domains/files/persistence',
           'server/domains/calendar/model', 'server/domains/calendar/persistence',
           'weave-mcp-server', 'weave-product-e2e')
SKIP = {'.git', '.gradle', '.dart_tool', 'build', 'target', 'node_modules'}
STRING = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
JAVA_TOKEN = re.compile(rf'({STRING})|//[^\n]*|/\*[\s\S]*?\*/')
LITERAL = re.compile(STRING)
ANNOTATION = re.compile(r'@(?:[\w$]+\.)*(ConditionalOnProperty|ConditionalOnExpression|Value|Profile)\s*\(')
PROPERTY = re.compile(r'\bweave(?:\.[a-zA-Z][\w-]*)+\.provider(?![\w.-])')
ENV = re.compile(r'\$\{\s*WEAVE(?:_[A-Z0-9]+)+_PROVIDER\s*(?::[^}\n]*)?\}')
PROVIDER_PROFILE = re.compile(
    r'(?:^|[\s!&|,._-])(providers?|chat|files|calendar|boards|matrix|synapse|'
    r'nextcloud|openproject|weave-native|zulip|slack|sharepoint)(?:$|[\s&|,._-])', re.I)
LINK = re.compile(r'!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+[^)]*)?\)')
REFERENCE_LINK = re.compile(r'^\s*\[[^\]\n]+\]:\s*(<[^>]+>|\S+)', re.M)


def uncomment_java(source: str) -> str:
    return JAVA_TOKEN.sub(lambda match: match[0] if match[1] else
                          re.sub(r'[^\n]', ' ', match[0]), source)


def compact(source: str) -> str:
    """Ignore layout, but preserve whitespace inside quoted literals."""
    return re.sub(f'({STRING})|\\s+', lambda match: match[1] or '', source)


def annotations(source: str):
    literals = [(literal.start(), literal.end()) for literal in LITERAL.finditer(source)]
    for match in ANNOTATION.finditer(source):
        if any(start <= match.start() < end for start, end in literals):
            continue
        depth, cursor = 1, match.end()
        while cursor < len(source) and depth:
            literal = LITERAL.match(source, cursor)
            if literal:
                cursor = literal.end()
                continue
            depth += (source[cursor] == '(') - (source[cursor] == ')')
            cursor += 1
        yield match[1], match.start(), cursor, source[match.start():cursor]


def java_selectors(source: str):
    source = uncomment_java(source)
    covered = []
    for kind, start, end, annotation in annotations(source):
        prefix = re.search(r'\bprefix\s*=\s*"(weave[\w.-]*)"', annotation)
        split_property = prefix and any(
            PROPERTY.fullmatch(prefix[1].rstrip('.') + '.' + literal[1:-1])
            for literal in re.findall(r'"[^"\n]*"', annotation))
        profile = kind == 'Profile' and (
            PROVIDER_PROFILE.search(re.sub(r'["(){}]', ' ', annotation.split('(', 1)[1]))
            or re.search(r'\b(?:Chat|Calendar|Files)ProviderPort\b|\bBoardsRepository\b', source))
        if PROPERTY.search(annotation) or split_property or profile:
            covered.append((start, end))
            yield 'java-annotation', compact(annotation), source.count('\n', 0, start) + 1
    for literal in LITERAL.finditer(source):
        if any(start <= literal.start() < end for start, end in covered):
            continue
        if PROPERTY.search(literal[0]) or re.fullmatch(r'"WEAVE(?:_[A-Z0-9]+)+_PROVIDER"', literal[0]):
            yield 'java-literal', literal[0], source.count('\n', 0, literal.start()) + 1


def yaml_selectors(source: str):
    # Only lexical scalar/reference checks: this is deliberately not a YAML parser.
    token = re.compile(r'("(?:\\.|[^"\\])*"|\'(?:\'\'|[^\'])*\')|#[^\n]*')
    for line_number, raw in enumerate(source.splitlines(), 1):
        line = token.sub(lambda match: match[1] or '', raw).strip()
        scalar = re.match(r'(?P<key>[\w.-]*provider)\s*:\s*(?P<value>\S.*)', line)
        profile = re.match(r'(?:spring\.config\.activate\.)?on-profile\s*:\s*(.*)', line)
        if scalar and (scalar['key'] == 'provider' or PROPERTY.fullmatch(scalar['key'])):
            yield 'yaml-selector', compact(line), line_number
        elif profile and PROVIDER_PROFILE.search(profile[1].strip('"\'')):
            yield 'yaml-profile', compact(line), line_number
        else:
            for match in ENV.finditer(line):
                yield 'yaml-env', compact(match[0]), line_number


def source_paths(root: Path, failures: list[str]):
    for module in MODULES:
        path = root / module / 'src/main/java'
        if not path.is_dir() or not any(path.rglob('*.java')):
            failures.append(f'scan-root-missing-or-empty: {module}/src/main/java')
        if any(parent.is_symlink() for parent in (path, *path.parents) if parent != root and root in parent.parents):
            failures.append(f'source-symlink-not-scanned: {module}/src/main/java')
    for module in ('server', 'weave-mcp-server'):
        if not (root / module / 'src/main/resources/application.yml').is_file():
            failures.append(f'configuration-root-missing: {module}/src/main/resources/application.yml')
    for directory, directories, files in os.walk(root):
        relative = Path(directory).relative_to(root).as_posix()
        production = '/src/main/' in '/' + relative + '/'
        directories[:] = sorted(name for name in directories if production or name not in SKIP)
        if production:
            for name in directories + files:
                if (Path(directory) / name).is_symlink():
                    failures.append(f'source-symlink-not-scanned: {relative}/{name}')
        for name in sorted(files):
            path = Path(directory) / name
            if path.is_symlink():
                continue
            if '/src/main/java/' in '/' + path.relative_to(root).as_posix() and name.endswith('.java'):
                yield path, java_selectors
            elif production and '/resources' in relative and re.fullmatch(r'application[^/]*\.ya?ml', name):
                yield path, yaml_selectors


def selector_violations(root: Path) -> list[str]:
    failures = []
    try:
        baseline = json.loads((root / BASELINE).read_text())
        if baseline['schemaVersion'] != 1:
            raise ValueError('unsupported schema')
        allowed = Counter()
        for item in baseline['selectors']:
            key = item['path'], item['kind'], item['signature']
            if key in allowed or not isinstance(item['count'], int) or item['count'] < 1:
                raise ValueError('duplicate or invalid occurrence count')
            allowed[key] = item['count']
    except (OSError, ValueError, KeyError, TypeError) as error:
        return [f'legacy-selector-baseline-invalid: {BASELINE}: {type(error).__name__}']
    found = Counter()
    for path, detect in source_paths(root, failures):
        relative = path.relative_to(root).as_posix()
        for kind, signature, line in detect(path.read_text()):
            key = relative, kind, signature
            found[key] += 1
            if found[key] > allowed[key]:
                failures.append(f'new-startup-provider-selector: {relative}:{line}: {kind}')
    return failures


def skill_violations(root: Path) -> list[str]:
    failures = []
    if not (root / STANDARD).is_file():
        failures.append(f'shared-standard-missing: {STANDARD}')
    for name in SKILLS:
        path = root / '.agents/skills' / name / 'SKILL.md'
        relative = path.relative_to(root).as_posix()
        if not path.is_file():
            failures.append(f'skill-missing: {relative}')
            continue
        source = path.read_text()
        frontmatter = re.match(r'\A---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)', source)
        names = re.findall(r'^name:\s*(\S[^\n]*)$', frontmatter[1] if frontmatter else '', re.M)
        descriptions = re.findall(r'^description:[ \t]*(\S[^\n]*)$', frontmatter[1] if frontmatter else '', re.M)
        description = descriptions[0].strip().strip('"\'').strip() if len(descriptions) == 1 else ''
        if names != [name] or not description or description in ('|', '>', 'null', '~', '[]', '{}', 'true', 'false'):
            failures.append(f'skill-required-metadata-invalid: {relative}')
        targets = [match[1].strip('<>') for regex in (LINK, REFERENCE_LINK) for match in regex.finditer(source)]
        standard_linked = False
        for target in targets:
            parsed = urlsplit(target)
            if parsed.scheme in ('http', 'https', 'mailto') or parsed.netloc and not parsed.scheme:
                continue
            if parsed.scheme:
                failures.append(f'skill-unsupported-link-scheme: {relative}: {parsed.scheme}')
                continue
            if not parsed.path:
                continue
            destination = (path.parent / unquote(parsed.path)).resolve()
            if not destination.is_relative_to(root.resolve()) or not destination.is_file():
                failures.append(f'skill-local-link-missing-or-outside-repo: {relative}: {target}')
            standard_linked |= destination == (root / STANDARD).resolve()
        if not standard_linked:
            failures.append(f'skill-shared-standard-link-missing: {relative}')
    return failures


def violations(root: Path) -> list[str]:
    return selector_violations(root) + skill_violations(root)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    failures = violations(args.root)
    for failure in failures:
        print(f'java-agent-contract: {failure}')
    if failures:
        raise SystemExit(1)
    print('java-agent-contract: passed (static selector ratchet and skill links; not migration/native proof)')


if __name__ == '__main__':
    main()
