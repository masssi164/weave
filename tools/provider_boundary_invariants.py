#!/usr/bin/env python3
"""Preserve the Native Provider Gate's source invariants in ordinary Core CI."""
from __future__ import annotations

import argparse
import os
import re
from pathlib import Path

# Build output is not source authority. This guard runs before compilation in CI.
GENERATED_DIRECTORIES = {'build', 'target', '.git', 'node_modules', '.dart_tool'}
FILESYSTEM_STORE = 'server/src/main/java/com/massimotter/weave/backend/service/files/FilesystemBlobStore.java'
FORBIDDEN_FILES = (
    'server/src/main/java/com/massimotter/weave/backend/service/files/S3BlobStore.java',
    'server/src/test/java/com/massimotter/weave/backend/service/files/S3BlobStoreTest.java',
)
FORBIDDEN_CONTENT = (
    ('protocol-sdk-coupling', r'matrix-sdk|matrix_sdk', ('rust/matrix-protocol',)),
    ('snapshot-persistence-authority', r'MatrixE2eeSnapshot(Store|JpaEntity|JpaRepository)',
     ('server/src/main', 'weave-persistence-jpa/src/main')),
    ('retired-matrix-coupling', r'weave\.chat\.matrix-facade\.enabled|facadeEnabled|weave_matrix_core',
     ('server', 'gradle', 'rust', 'Cargo.toml', 'Cargo.lock')),
    ('native-s3-configuration', r'S3_COMPATIBLE|weave\.files\.native\.blob-store',
     ('server', 'weave-files-core', 'weave-application-core', 'docs', 'README.md')),
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
