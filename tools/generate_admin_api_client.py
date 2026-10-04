#!/usr/bin/env python3
"""Generate the Admin HTTP client from the server-owned Admin OpenAPI artifact."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/openapi/weave-admin-openapi.json"
OUTPUT = ROOT / "admin-console/src/generated/admin-client"


def types_at(root: Path) -> dict[str, bytes]:
    return {
        str(path.relative_to(root)): normalized_typescript(path.read_text()).encode()
        for path in sorted(root.rglob("*.ts"))
    }


def normalized_typescript(source: str) -> str:
    # The pinned upstream templates emit whitespace-only JSDoc lines and extra
    # blank lines at EOF. Normalize them before storing or comparing output.
    return "\n".join(line.rstrip() for line in source.splitlines()).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="weave-admin-api-") as temporary:
        generated = Path(temporary)
        command = [
            "java", "-jar", str(args.jar), "generate",
            "-g", "typescript-fetch", "-i", str(CONTRACT), "-o", str(generated),
            "--additional-properties",
            "typescriptThreePlus=true,supportsES6=true,withInterfaces=true,"
            "modelPropertyNaming=original,enumPropertyNaming=original,"
            "hideGenerationTimestamp=true",
            "--global-property", "apis,models,supportingFiles",
        ]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
        expected = types_at(generated)
        if not expected:
            raise RuntimeError("Admin client generation produced no TypeScript sources")
        if args.check:
            if not OUTPUT.is_dir() or types_at(OUTPUT) != expected:
                print(
                    "Generated Admin HTTP client is stale. "
                    "Run ./gradlew generateAdminApiClient and commit the result.",
                    file=sys.stderr,
                )
                return 1
            print(f"Admin HTTP client is current ({len(expected)} TypeScript files)")
            return 0
        if OUTPUT.exists():
            shutil.rmtree(OUTPUT)
        for relative, content in expected.items():
            destination = OUTPUT / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
        print(f"Generated {len(expected)} Admin HTTP client sources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
