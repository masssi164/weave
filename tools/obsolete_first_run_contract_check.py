#!/usr/bin/env python3
"""Reject obsolete first-run production architecture across client and server."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_RELATIVE_ROOTS = ("client/lib", "server/src/main")
PRODUCTION_ROOTS = tuple(ROOT / relative for relative in PRODUCTION_RELATIVE_ROOTS)
FORBIDDEN = (
    "firstRunStatusProvider",
    "FirstRunScreen",
    "/api/onboarding/status",
    "/first-run",
    "First-run status",
    "Erststart-Status",
    "Weave-Erststart-Status",
)


def main() -> int:
    findings: list[str] = []
    for root in PRODUCTION_ROOTS:
        for path in sorted(value for value in root.rglob("*") if value.is_file()):
            if path.suffix not in {".dart", ".java", ".kt", ".xml", ".yml", ".yaml", ".json"}:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for forbidden in FORBIDDEN:
                if forbidden in text:
                    findings.append(f"{path.relative_to(ROOT)} contains {forbidden!r}")
    for locale in ("app_en.arb", "app_de.arb"):
        path = ROOT / "client" / "lib" / "l10n" / locale
        payload = json.loads(path.read_text(encoding="utf-8"))
        for key in payload:
            if key.startswith("firstRun"):
                findings.append(f"{path.relative_to(ROOT)} contains obsolete key {key!r}")
    if findings:
        raise SystemExit("obsolete-first-run-contract-check: failed\n" + "\n".join(findings))
    print("OBSOLETE_FIRST_RUN_CONTRACT_REMOVED status=passed client=true server=true l10n=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
