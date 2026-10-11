#!/usr/bin/env python3
"""Generate the Admin Console's separate User/Admin HTTP clients from server artifacts."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/openapi/weave-admin-openapi.json"
OUTPUT = ROOT / "admin-console/src/generated/admin-client"
PUBLIC_CONTRACT = ROOT / "contracts/openapi/weave-user-openapi.json"
PUBLIC_OUTPUT = ROOT / "admin-console/src/generated/user-client"


def types_at(root: Path) -> dict[str, bytes]:
    return {
        str(path.relative_to(root)): normalized_typescript(path.read_text()).encode()
        for path in sorted(root.rglob("*.ts"))
    }


def normalized_typescript(source: str) -> str:
    # The pinned upstream templates emit whitespace-only JSDoc lines and extra
    # blank lines at EOF. Normalize them before storing or comparing output.
    return "\n".join(line.rstrip() for line in source.splitlines()).rstrip() + "\n"


def check_operation_coverage(generated: dict[str, bytes], contract: Path, audience: str) -> None:
    document = json.loads(contract.read_text())
    implementations = "\n".join(
        contents.decode() for path, contents in generated.items() if path.startswith("apis/")
    )
    operation_ids = {
        operation["operationId"]
        for methods in document["paths"].values()
        for method, operation in methods.items()
        if method.lower() in {"get", "post", "put", "patch", "delete"}
    }
    missing = sorted(
        operation_id
        for operation_id in operation_ids
        if not re.search(rf"\basync\s+{re.escape(operation_id)}\(", implementations)
    )
    if missing:
        raise RuntimeError(f"{audience} client is missing generated operations: {missing}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--audience", choices=("admin", "user"), default="admin")
    args = parser.parse_args()
    contract = CONTRACT if args.audience == "admin" else PUBLIC_CONTRACT
    output = OUTPUT if args.audience == "admin" else PUBLIC_OUTPUT

    with tempfile.TemporaryDirectory(prefix=f"weave-{args.audience}-api-") as temporary:
        generated = Path(temporary)
        command = [
            "java", "-jar", str(args.jar), "generate",
            "-g", "typescript-fetch", "-i", str(contract), "-o", str(generated),
            "--additional-properties",
            "typescriptThreePlus=true,supportsES6=true,withInterfaces=true,"
            "modelPropertyNaming=original,enumPropertyNaming=original,"
            "hideGenerationTimestamp=true",
            "--global-property", "apis,models,supportingFiles",
        ]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
        expected = types_at(generated)
        if not expected:
            raise RuntimeError(f"{args.audience} client generation produced no TypeScript sources")
        check_operation_coverage(expected, contract, args.audience)
        if args.check:
            if not output.is_dir() or types_at(output) != expected:
                print(
                    f"Generated {args.audience} HTTP client is stale. "
                    "Run ./gradlew generateAdminApiClient generateAdminPublicUserApiClient and commit the result.",
                    file=sys.stderr,
                )
                return 1
            print(f"{args.audience} HTTP client is current ({len(expected)} TypeScript files)")
            return 0
        if output.exists():
            shutil.rmtree(output)
        for relative, content in expected.items():
            destination = output / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
        print(f"Generated {len(expected)} {args.audience} HTTP client sources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
