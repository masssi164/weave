#!/usr/bin/env python3
"""Keep module build files declarative and task/dependency ownership explicit."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THIN_BUILD_FILES = (
    ROOT / "build.gradle",
    ROOT / "server" / "build.gradle",
    ROOT / "weave-mcp-server" / "build.gradle",
    ROOT / "weave-product-e2e" / "build.gradle",
)


def fail(message: str) -> None:
    raise SystemExit(f"gradle-structure-check: {message}")


def main() -> None:
    for build_file in THIN_BUILD_FILES:
        source = build_file.read_text(encoding="utf-8")
        line_count = len(source.splitlines())
        if line_count > 25:
            fail(f"{build_file.relative_to(ROOT)} has {line_count} lines; maximum is 25")
        if "tasks.register" in source or "tasks.named" in source:
            fail(f"{build_file.relative_to(ROOT)} contains task implementation")
        if "dependencies {" in source:
            fail(f"{build_file.relative_to(ROOT)} contains dependency implementation")

    module_contracts = {
        "server": (
            "gradle/scripts/java-and-dependencies.gradle",
            "gradle/tasks/verification.gradle",
        ),
        "weave-mcp-server": (
            "gradle/scripts/java-and-dependencies.gradle",
            "gradle/tasks/verification.gradle",
        ),
        "weave-product-e2e": (
            "gradle/scripts/java-and-dependencies.gradle",
            "gradle/tasks/product-flow.gradle",
        ),
    }
    for module, relative_paths in module_contracts.items():
        build_source = (ROOT / module / "build.gradle").read_text(encoding="utf-8")
        for relative_path in relative_paths:
            target = ROOT / module / relative_path
            if not target.is_file():
                fail(f"{target.relative_to(ROOT)} is missing")
            apply_expression = f'apply from: "${{projectDir}}/{relative_path}"'
            if apply_expression not in build_source:
                fail(f"{module}/build.gradle does not apply its owned {relative_path}")

    print("gradle-structure-check: passed")


if __name__ == "__main__":
    main()
