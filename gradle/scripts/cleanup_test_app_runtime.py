#!/usr/bin/env python3
"""Remove only the generated, run-scoped SecretRef tree of one testApp run."""

from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path


NAMESPACE = re.compile(r"^weave-e2e-[0-9a-f]{16}$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--namespace", required=True)
    args = parser.parse_args()
    if not NAMESPACE.fullmatch(args.namespace):
        raise SystemExit("WEAVE_TEST_APP_CLEANUP_ERROR invalid isolated namespace")
    repository = args.repository_root.resolve()
    isolated_root = (
        repository / "infra/weave-workspace/.generated/isolated"
    ).resolve()
    target = isolated_root / args.namespace
    if target.parent != isolated_root or target.is_symlink():
        raise SystemExit("WEAVE_TEST_APP_CLEANUP_ERROR unsafe generated-state target")
    if target.exists():
        if not target.is_dir():
            raise SystemExit(
                "WEAVE_TEST_APP_CLEANUP_ERROR generated-state target is not a directory"
            )
        shutil.rmtree(target)
    output_root = args.output_root.resolve()
    run_root = output_root / args.namespace
    if run_root.parent != output_root or run_root.is_symlink():
        raise SystemExit("WEAVE_TEST_APP_CLEANUP_ERROR unsafe run-output target")
    if run_root.exists():
        if not run_root.is_dir():
            raise SystemExit(
                "WEAVE_TEST_APP_CLEANUP_ERROR run-output target is not a directory"
            )
        for generated_input in ("test.env", "hosts"):
            candidate = run_root / generated_input
            if candidate.is_symlink():
                raise SystemExit(
                    "WEAVE_TEST_APP_CLEANUP_ERROR generated input is a symlink"
                )
            if candidate.exists():
                if not candidate.is_file():
                    raise SystemExit(
                        "WEAVE_TEST_APP_CLEANUP_ERROR generated input is not a file"
                    )
                candidate.unlink()
        try:
            run_root.rmdir()
        except OSError:
            # Sanitized evidence may remain under the exact run directory.
            pass
    print(
        "testApp runtime cleanup: removed exact generated SecretRef and input state "
        + args.namespace
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
