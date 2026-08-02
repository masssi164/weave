#!/usr/bin/env python3
"""Enforce immutable, reviewed GitHub Action pins and supported runtimes."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "tools" / "workflow_action_runtime_manifest.json"
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RELEASE_PATTERN = re.compile(r"^v[0-9]+\.[0-9]+\.[0-9]+$")
USES_PATTERN = re.compile(
    r"^\s*(?:-\s+)?uses:\s+([^#\s]+)(?:\s+#\s*(\S+))?\s*$"
)
SUPPORTED_RUNTIMES = frozenset({"node24", "composite-shell"})


class PolicyError(ValueError):
    """Raised when the workflow action inventory violates supply-chain policy."""


@dataclass(frozen=True)
class ActionPin:
    action: str
    commit: str
    release: str
    runtime: str
    source: str


def load_manifest(path: Path) -> dict[str, ActionPin]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PolicyError(f"cannot read action manifest {path}: {exc}") from exc
    if document.get("schemaVersion") != 1:
        raise PolicyError("action manifest schemaVersion must be 1")
    if not document.get("reviewedAt") or not document.get("policy"):
        raise PolicyError("action manifest must declare reviewedAt and policy")

    pins: dict[str, ActionPin] = {}
    for entry in document.get("actions", []):
        try:
            pin = ActionPin(
                action=entry["action"],
                commit=entry["commit"],
                release=entry["release"],
                runtime=entry["runtime"],
                source=entry["source"],
            )
        except KeyError as exc:
            raise PolicyError(f"action manifest entry is incomplete: {entry}") from exc
        if pin.action in pins:
            raise PolicyError(f"duplicate action manifest entry: {pin.action}")
        if not SHA_PATTERN.fullmatch(pin.commit):
            raise PolicyError(f"{pin.action} commit is not a lowercase 40-character SHA")
        if not RELEASE_PATTERN.fullmatch(pin.release):
            raise PolicyError(f"{pin.action} release is not an exact semantic version tag")
        if pin.runtime not in SUPPORTED_RUNTIMES:
            raise PolicyError(f"{pin.action} uses unsupported runtime {pin.runtime!r}")
        if pin.source != f"https://github.com/{pin.action}":
            raise PolicyError(f"{pin.action} source must identify its canonical GitHub repository")
        pins[pin.action] = pin
    if not pins:
        raise PolicyError("action manifest must inventory at least one external action")
    return pins


def workflow_paths(root: Path) -> list[Path]:
    workflow_root = root / ".github" / "workflows"
    paths = sorted((*workflow_root.glob("*.yml"), *workflow_root.glob("*.yaml")))
    if not paths:
        raise PolicyError(f"no GitHub workflows found under {workflow_root}")
    return paths


def validate(root: Path, manifest_path: Path) -> tuple[int, int]:
    pins = load_manifest(manifest_path)
    usage_count = {action: 0 for action in pins}
    references = 0

    for workflow in workflow_paths(root):
        for line_number, line in enumerate(
            workflow.read_text(encoding="utf-8").splitlines(), start=1
        ):
            match = USES_PATTERN.match(line)
            if not match:
                continue
            reference, release_comment = match.groups()
            if reference.startswith("./"):
                continue
            references += 1
            if "@" not in reference:
                raise PolicyError(
                    f"{workflow.relative_to(root)}:{line_number}: external action has no ref"
                )
            action, commit = reference.rsplit("@", 1)
            pin = pins.get(action)
            if pin is None:
                raise PolicyError(
                    f"{workflow.relative_to(root)}:{line_number}: unreviewed action {action}"
                )
            if commit != pin.commit:
                raise PolicyError(
                    f"{workflow.relative_to(root)}:{line_number}: {action} must use "
                    f"reviewed commit {pin.commit}, got {commit}"
                )
            if release_comment != pin.release:
                raise PolicyError(
                    f"{workflow.relative_to(root)}:{line_number}: {action} pin comment "
                    f"must be {pin.release!r}, got {release_comment!r}"
                )
            usage_count[action] += 1

    unused = sorted(action for action, count in usage_count.items() if count == 0)
    if unused:
        raise PolicyError(f"stale action manifest entries are not used: {', '.join(unused)}")
    return len(pins), references


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        actions, references = validate(args.root.resolve(), args.manifest.resolve())
    except PolicyError as exc:
        print(f"workflow-action-runtime-check: {exc}", file=sys.stderr)
        return 1
    print(
        "WORKFLOW_ACTION_RUNTIME_RESULT "
        f"status=passed actions={actions} references={references} "
        "node20=false immutablePins=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
