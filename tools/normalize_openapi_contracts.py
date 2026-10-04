#!/usr/bin/env python3
"""Normalize server-exported OpenAPI documents without changing schema semantics."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


DOCUMENTS = {
    "weave-openapi": "combined",
    "weave-user-openapi": "user",
    "weave-admin-openapi": "admin",
}
HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}


def read_document(path: Path) -> dict:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing OpenAPI export: {path}")
    document = json.loads(path.read_text(encoding="utf-8"))
    if not str(document.get("openapi", "")).startswith("3."):
        raise ValueError(f"Expected OpenAPI 3 document: {path}")
    return document


def operation_ids(document: dict, label: str) -> set[str]:
    seen: set[str] = set()
    for path, path_item in document.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS:
                continue
            operation_id = operation.get("operationId")
            if not isinstance(operation_id, str) or not operation_id.strip():
                raise ValueError(f"Missing operationId in {label}: {method.upper()} {path}")
            if operation_id in seen:
                raise ValueError(f"Duplicate operationId in {label}: {operation_id}")
            seen.add(operation_id)
    if not seen:
        raise ValueError(f"No operations in {label} OpenAPI document")
    return seen


def normalize(root: Path) -> None:
    export_dir = root / "build/openapi"
    target_dir = root / "contracts/openapi"
    documents = {
        label: read_document(export_dir / f"{name}.raw.json")
        for name, label in DOCUMENTS.items()
    }
    for label, document in documents.items():
        operation_ids(document, label)

    user_paths = set(documents["user"]["paths"])
    admin_paths = set(documents["admin"]["paths"])
    if user_paths & admin_paths:
        raise ValueError(f"User/Admin OpenAPI paths overlap: {sorted(user_paths & admin_paths)}")
    if any(path.startswith("/api/admin/") for path in user_paths):
        raise ValueError("Admin operation leaked into User OpenAPI")
    if any(not path.startswith(("/api/admin/", "/api/bootstrap/", "/api/migration/"))
           for path in admin_paths):
        raise ValueError("Non-admin operation leaked into Admin OpenAPI")

    target_dir.mkdir(parents=True, exist_ok=True)
    for name, label in DOCUMENTS.items():
        # Sorting object keys is stable; sorting arrays would change examples, required
        # values, and other ordered contract data.
        target = target_dir / f"{name}.json"
        target.write_text(
            json.dumps(documents[label], ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    normalize(parser.parse_args().root)
