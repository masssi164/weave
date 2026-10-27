#!/usr/bin/env python3
"""Report ownership candidates for production Java sources. Read-only, advisory.

Path/package heuristics are not proof of ownership and never authorize deletion.
"""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

LEGACY_PROJECTS = frozenset({
    "weave-application-core", "weave-files-core", "weave-persistence-jpa",
    "weave-runtime-provider-adapters", "weave-runtime-security-adapters",
})
PACKAGE = re.compile(r"(?m)^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*;")
ENTITY = re.compile(r"(?m)^\s*@(?:jakarta\.persistence\.)?Entity\b")
REPOSITORY = re.compile(r"\b(?:JpaRepository|CrudRepository|PagingAndSortingRepository)\b")
DOMAINS = {"files", "chat", "calendar"}
FUTURE = {"boards", "office", "devops", "agentruntime", "portability", "transfer", "interop"}


def expected_owner(package: str, filename: str) -> str:
    parts = package.split(".")
    if "backend" not in parts:
        return "review"
    parts = parts[parts.index("backend") + 1:]
    if not parts:
        return "review"
    first = parts[0]
    if first in DOMAINS | FUTURE:
        return "portability" if first == "transfer" else first
    if first == "service" and len(parts) > 1:
        if parts[1] in DOMAINS | FUTURE:
            return "portability" if parts[1] == "transfer" else parts[1]
    if first == "persistence" and len(parts) > 2 and parts[1] == "jpa":
        if parts[2] == "matrix":
            return "core" if filename.startswith("MatrixRevokedSession") else "chat"
        if parts[2] in DOMAINS | FUTURE:
            return parts[2]
        if parts[2] in {"audit", "identity", "security", "schema", "provider", "readiness"}:
            return "core"
    if first == "model" and len(parts) > 1 and parts[1] in DOMAINS | FUTURE:
        return parts[1]
    if first in {"controller", "config", "model"}:
        for domain in sorted(DOMAINS | FUTURE):
            if filename.lower().startswith(domain) or (domain == "chat" and filename.startswith("Matrix")):
                return domain
        return "review"
    if first in {"identity", "security", "spaces", "audit", "context", "schema",
                 "domainregistry", "providerbinding", "operation"}:
        return "core"
    return "review"


def audit(root: Path) -> dict:
    entries = []
    locations = [root / "server"] + [root / p for p in sorted(LEGACY_PROJECTS)]
    for source_root in locations:
        if not source_root.is_dir():
            continue
        for file in sorted(source_root.rglob("*.java")):
            relative = file.relative_to(root).as_posix()
            if "/src/main/java/" not in relative or any(s in {"build", ".gradle"} for s in file.parts):
                continue
            source = file.read_text(encoding="utf-8")
            match = PACKAGE.search(source)
            package = match.group(1) if match else ""
            project = relative.split("/src/main/java/")[0]
            entries.append({
                "path": relative,
                "project": project,
                "suggestedOwner": {"agentruntime": "agent-runtime"}.get(
                    expected_owner(package, file.name), expected_owner(package, file.name)),
                "entity": bool(ENTITY.search(source)),
                "jpaRepository": bool(REPOSITORY.search(source)),
                "nominalType": (package + "." if package else "") + file.stem,
            })
    by_project = Counter(e["project"] for e in entries)
    by_owner = Counter(e["suggestedOwner"] for e in entries)
    possible_dupes = defaultdict(list)
    for entry in entries:
        possible_dupes[entry["nominalType"]].append(entry["path"])
    incorrect = [
        e["path"] for e in entries
        if (e["project"].startswith("server/core") and e["suggestedOwner"] not in {"core", "review"})
        or (e["project"].startswith("server/domains/") and
            e["suggestedOwner"] not in {"review", e["project"].split("/")[2]})
    ]
    return {
        "schemaVersion": "weave.domain-ownership-inventory.v1",
        "note": "Advisory paths only; review every match before moving or deleting.",
        "javaSources": len(entries),
        "byPhysicalProject": dict(sorted(by_project.items())),
        "bySuggestedOwner": dict(sorted(by_owner.items())),
        "legacyProjectsPresent": sorted(p for p in LEGACY_PROJECTS if (root / p).is_dir()),
        "possibleDuplicateTypes": {
            name: sorted(paths) for name, paths in sorted(possible_dupes.items()) if len(paths) > 1
        },
        "possibleWrongOwnerPaths": sorted(incorrect),
        "entityCandidates": [
            {"path": e["path"], "suggestedOwner": e["suggestedOwner"]}
            for e in entries if e["entity"]
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(json.dumps(audit(args.root.resolve()), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
