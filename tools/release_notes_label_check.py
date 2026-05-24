#!/usr/bin/env python3
"""Validate that a pull request chose exactly one release-notes label."""

from __future__ import annotations

import json
import os
import sys

RELEASE_NOTES_LABELS = {
    "release-notes-feature",
    "release-notes-bugfix",
    "release-notes-skip",
}


def load_labels() -> list[str]:
    if len(sys.argv) > 1:
        return sys.argv[1:]

    raw = os.environ.get("PR_LABELS_JSON")
    if raw is None:
        print("release-notes-label-check: PR_LABELS_JSON is not set", file=sys.stderr)
        raise SystemExit(2)

    try:
        labels = json.loads(raw)
    except json.JSONDecodeError as error:
        print(f"release-notes-label-check: invalid PR_LABELS_JSON: {error}", file=sys.stderr)
        raise SystemExit(2) from error

    if not isinstance(labels, list) or not all(isinstance(label, str) for label in labels):
        print("release-notes-label-check: PR_LABELS_JSON must be a JSON array of strings", file=sys.stderr)
        raise SystemExit(2)

    return labels


def main() -> None:
    labels = load_labels()
    selected = sorted(RELEASE_NOTES_LABELS.intersection(labels))

    if len(selected) != 1:
        allowed = ", ".join(sorted(RELEASE_NOTES_LABELS))
        actual = ", ".join(selected) if selected else "none"
        print(
            "release-notes-label-check: every PR must have exactly one "
            f"release-notes label before review/merge. Allowed: {allowed}. Found: {actual}.",
            file=sys.stderr,
        )
        raise SystemExit(1)

    print(f"release-notes-label-check: ok ({selected[0]})")


if __name__ == "__main__":
    main()
