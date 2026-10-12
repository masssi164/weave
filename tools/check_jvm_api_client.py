#!/usr/bin/env python3
"""Check generated JVM client coverage and deterministic Java generation."""

import json
from pathlib import Path
import re
import sys


def java_sources(root: Path) -> dict[str, bytes]:
    source_root = root / "src/main/java"
    return {
        str(path.relative_to(source_root)): path.read_bytes()
        for path in source_root.rglob("*.java")
    }


def main() -> None:
    contract, generated, comparison = map(Path, sys.argv[1:4])
    audience = sys.argv[4] if len(sys.argv) > 4 else "user"
    if audience not in {"user", "admin"}:
        raise SystemExit("JVM API client audience must be user or admin")
    document = json.loads(contract.read_text(encoding="utf-8"))
    first = java_sources(generated)
    second = java_sources(comparison)
    if first != second:
        different = sorted(key for key in first.keys() | second.keys() if first.get(key) != second.get(key))
        raise SystemExit(f"JVM {audience.title()} client generation is not reproducible: {different}")

    package = f"com/massimotter/weave/{audience}api"
    expected_models = set(document["components"]["schemas"])
    missing_models = sorted(
        model for model in expected_models if f"{package}/model/{model}.java" not in first
    )
    api_sources = "\n".join(
        content.decode("utf-8")
        for path, content in first.items()
        if path.startswith(f"{package}/api/")
    )
    operations = {
        value["operationId"]
        for path in document["paths"].values()
        for method, value in path.items()
        if method.lower() in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
    }
    missing_operations = sorted(
        operation
        for operation in operations
        if not re.search(r"\b" + re.escape(operation) + r"\s*\(", api_sources)
    )
    if missing_models or missing_operations:
        raise SystemExit(
            f"JVM {audience.title()} client coverage incomplete: models={missing_models}, operations={missing_operations}"
        )
    print(
        f"Generated JVM {audience.title()} client is reproducible: {len(expected_models)} models, "
        f"{len(operations)} operations, {len(first)} Java sources"
    )


if __name__ == "__main__":
    main()
