#!/usr/bin/env python3
"""Preserve the Native Provider Gate's source invariants in ordinary Core CI."""
from __future__ import annotations

import argparse
import os
import re
from pathlib import Path

# Build output is not source authority. This guard runs before compilation in CI.
GENERATED_DIRECTORIES = {'build', 'target', '.git', 'node_modules', '.dart_tool'}
FILESYSTEM_STORE = 'server/domains/files/src/main/java/com/massimotter/weave/backend/files/provider/FilesystemBlobStore.java'
FORBIDDEN_FILES = (
    'server/app/src/main/java/com/massimotter/weave/backend/service/files/S3BlobStore.java',
    'server/app/src/test/java/com/massimotter/weave/backend/service/files/S3BlobStoreTest.java',
)
MIGRATIONS = (
    'V1__native_schema_baseline.sql',
    'V2__normalize_native_calendar_chat_state.sql',
    'V3__complete_native_provider_authority.sql',
    'V4__relax_legacy_calendar_timezone_projection.sql',
    'V5__matrix_shared_user_change_journal.sql',
    'V6__canonical_transfer_runs.sql',
)
MIGRATION_ROOT = 'server/app/src/main/resources/db/migration'
FORBIDDEN_CONTENT = (
    ('migration-public-schema', r'public\.', (MIGRATION_ROOT,)),
    ('unsafe-flyway-authority', r'baselineOnMigrate\(true\)|cleanDisabled\(false\)',
     ('server/app/src/main/java',)),
    ('protocol-sdk-coupling', r'matrix-sdk|matrix_sdk', ('rust/matrix-protocol',)),
    ('snapshot-persistence-authority', r'MatrixE2eeSnapshot(Store|JpaEntity|JpaRepository)',
     ('server/app/src/main', 'server/core/src/main',
      'server/domains/chat/persistence/src/main',
      'server/domains/agent-runtime/persistence/src/main',
      'server/domains/portability/persistence/src/main',
      'server/domains/files/src/main',
      'server/domains/files/persistence/src/main',
      'server/domains/calendar/persistence/src/main')),
    ('retired-matrix-coupling', r'weave\.chat\.matrix-facade\.enabled|facadeEnabled|weave_matrix_core',
     ('server', 'gradle', 'rust', 'Cargo.toml', 'Cargo.lock')),
    ('native-s3-configuration', r'S3_COMPATIBLE|weave\.files\.native\.blob-store',
     ('server', 'docs', 'README.md')),
    ('filesystem-aws-coupling', r'software.amazon.awssdk', (FILESYSTEM_STORE,)),
)


def source_files(root: Path, relative: str):
    path = root / relative
    if path.is_file():
        yield path
    elif path.is_dir():
        seen = set()
        for directory, directories, files in os.walk(path, followlinks=True):
            identity = (Path(directory).stat().st_dev, Path(directory).stat().st_ino)
            if identity in seen:
                directories[:] = []
                continue
            seen.add(identity)
            directories[:] = sorted(name for name in directories if name not in GENERATED_DIRECTORIES)
            for name in sorted(files):
                candidate = Path(directory) / name
                if candidate.is_file():
                    yield candidate


def violations(root: Path) -> list[str]:
    failures = []
    if (root / 'rust/matrix-core').is_dir():
        failures.append('retired-matrix-core: rust/matrix-core must not exist')
    for relative in FORBIDDEN_FILES:
        if (root / relative).exists():
            failures.append(f'retired-s3-source: {relative} must not exist')
    for migration in MIGRATIONS:
        path = root / MIGRATION_ROOT / migration
        if not path.is_file() or not path.read_bytes().strip():
            failures.append(f'migration-required: {MIGRATION_ROOT}/{migration}')
    snapshot_drop = root / MIGRATION_ROOT / MIGRATIONS[2]
    if (not snapshot_drop.is_file() or
            b'drop table if exists weave_matrix_e2ee_snapshots' not in snapshot_drop.read_bytes()):
        failures.append(f'migration-snapshot-drop-required: {MIGRATION_ROOT}/{MIGRATIONS[2]}')
    resources = root / 'server/app/src/main/resources'
    configs = [resources / 'application.yml', *sorted(resources.glob('application-*.yml'))]
    production_configs = [path for path in configs
                          if path.is_file() and path.name != 'application-dev-h2.yml']
    validate_found = False
    for path in production_configs:
        contents = path.read_bytes()
        if re.search(rb'ddl-auto:[ \t]*validate', contents):
            validate_found = True
        for line_number, line in enumerate(contents.splitlines(), 1):
            match = re.search(rb'ddl-auto[=:][ \t]*(update|create-drop|create)', line)
            if match:
                failures.append(f'unsafe-production-ddl: {path.relative_to(root)}:{line_number}: '
                                f'{match.group().decode("ascii")}')
    if not validate_found:
        failures.append('production-ddl-validate-required: server/app/src/main/resources/application*.yml')
    store = root / FILESYSTEM_STORE
    if not store.is_file() or b'org.apache.opendal' not in store.read_bytes():
        failures.append(f'filesystem-opendal-required: {FILESYSTEM_STORE}')
    for rule, expression, roots in FORBIDDEN_CONTENT:
        matcher = re.compile(expression.encode())
        for relative in roots:
            for path in source_files(root, relative):
                with path.open('rb') as stream:
                    for line_number, line in enumerate(stream, 1):
                        match = matcher.search(line)
                        if match:
                            # Report the prohibited identifier, never source lines or payloads.
                            failures.append(f'{rule}: {path.relative_to(root)}:{line_number}: '
                                            f'{match.group().decode("ascii")}')
    return failures


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    failures = violations(args.root)
    if failures:
        for failure in failures:
            print(f'provider-boundary-invariants: {failure}')
        raise SystemExit(1)
    print('provider-boundary-invariants: passed')


if __name__ == '__main__':
    main()
