#!/usr/bin/env python3
"""Correct OpenAPI Generator 7.17.0's enum-array query helper type error.

This is deliberately narrow and fails if the generator output changes. The
upstream Java template uses ``String`` for an array whose generated element
type is an enum, so the generated full User client otherwise cannot compile.
"""

from pathlib import Path
import sys


def main() -> None:
    generated = Path(sys.argv[1])
    model = generated / "src/main/java/com/massimotter/weave/userapi/model/BoardProviderCapabilities.java"
    source = model.read_text(encoding="utf-8")
    expected = "for (String _item : getSupported())", "for (String _item : getUnsupported())"
    if any(source.count(item) != 1 for item in expected):
        raise SystemExit("OpenAPI Generator enum-array template changed; inspect before updating the patch")
    for item in expected:
        source = source.replace(item, item.replace("String", "var"))
    model.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    main()
