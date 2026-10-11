#!/usr/bin/env python3
"""Withhold incomplete or unredacted testApp diagnostics from CI artifacts."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def discard(output_root: Path, diagnostics: Path) -> None:
    root = output_root.resolve()
    if diagnostics.name != "failure-diagnostics" or not diagnostics.parent.resolve().is_relative_to(root):
        raise ValueError("failure diagnostics path escaped the testApp output root")
    if diagnostics.is_symlink():
        diagnostics.unlink()
    elif diagnostics.exists():
        shutil.rmtree(diagnostics)
    diagnostics.mkdir(parents=True, exist_ok=True)
    (diagnostics / "diagnostics-withheld.txt").write_text(
        "Failure diagnostics were withheld because redaction or collection did not complete.\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--diagnostics-dir", required=True, type=Path)
    args = parser.parse_args()
    discard(args.output_root, args.diagnostics_dir)


if __name__ == "__main__":
    main()
