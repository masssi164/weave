#!/usr/bin/env python3
"""Generate Weave release notes from merged pull request metadata."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
RELEASE_NOTES_LABELS = {
    "release-notes-feature",
    "release-notes-bugfix",
    "release-notes-skip",
}
CATEGORY_BY_LABEL = {
    "release-notes-feature": "Added",
    "release-notes-bugfix": "Fixed",
}
CATEGORIES = [
    "Added",
    "Changed",
    "Fixed",
    "Security",
    "Accessibility",
    "Migration/Operator Notes",
    "Known Issues",
]


@dataclass(frozen=True)
class PullRequest:
    number: int
    title: str
    url: str
    labels: set[str]
    merged_at: str
    author: str | None = None


def fail(message: str) -> None:
    print(f"release-notes-generate: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_datetime(value: str | None) -> datetime | None:
    if not value:
        return None
    normalized = value.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", normalized):
        normalized = f"{normalized}T00:00:00+00:00"
    if normalized.endswith("Z"):
        normalized = f"{normalized[:-1]}+00:00"
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        fail(f"invalid datetime {value!r}: {error}")
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def github_headers(token: str | None) -> dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "weave-release-notes-generator",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def github_get(url: str, token: str | None) -> Any:
    request = urllib.request.Request(url, headers=github_headers(token))
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        fail(f"GitHub API request failed ({error.code}) for {url}: {body}")
    except urllib.error.URLError as error:
        fail(f"GitHub API request failed for {url}: {error}")


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def detect_repo() -> str:
    try:
        remote = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        fail("--repo is required when origin remote cannot be detected")

    patterns = [
        r"github\.com[:/](?P<repo>[^/]+/[^/.]+)(?:\.git)?$",
        r"github\.com/(?P<repo>[^/]+/[^/.]+)(?:\.git)?$",
    ]
    for pattern in patterns:
        match = re.search(pattern, remote)
        if match:
            return match.group("repo")
    fail(f"could not detect GitHub owner/repo from origin remote: {remote}")


def fetch_merged_pull_requests(
    repo: str,
    base: str,
    limit: int,
    token: str | None,
    since: datetime | None,
    until: datetime | None,
) -> list[dict[str, Any]]:
    if limit < 1:
        fail("--limit must be at least 1")

    qualifiers = [f"repo:{repo}", "is:pr", "is:merged", f"base:{base}"]
    if since:
        qualifiers.append(f"merged:>={since.isoformat().replace('+00:00', 'Z')}")
    if until:
        qualifiers.append(f"merged:<={until.isoformat().replace('+00:00', 'Z')}")

    pulls: list[dict[str, Any]] = []
    page = 1
    while len(pulls) < limit:
        query = urllib.parse.urlencode(
            {
                "q": " ".join(qualifiers),
                "per_page": min(100, limit - len(pulls)),
                "page": page,
                "sort": "updated",
                "order": "desc",
            }
        )
        result = github_get(f"https://api.github.com/search/issues?{query}", token)
        if not isinstance(result, dict) or not isinstance(result.get("items"), list):
            fail("GitHub Search API did not return an issue search result")
        items = result["items"]
        if not items:
            break
        for item in items:
            number = item.get("number")
            if not isinstance(number, int):
                fail(f"GitHub Search API returned a PR without a numeric number: {item!r}")
            detail = github_get(f"https://api.github.com/repos/{repo}/pulls/{number}", token)
            if not isinstance(detail, dict):
                fail(f"GitHub API did not return PR details for #{number}")
            pulls.append(detail)
        page += 1

    return pulls


def label_names(raw_labels: Iterable[Any]) -> set[str]:
    names: set[str] = set()
    for label in raw_labels:
        if isinstance(label, str):
            names.add(label)
        elif isinstance(label, dict) and isinstance(label.get("name"), str):
            names.add(label["name"])
    return names


def normalize_pull_requests(items: Iterable[dict[str, Any]]) -> list[PullRequest]:
    prs: list[PullRequest] = []
    for item in items:
        merged_at = item.get("merged_at")
        if not merged_at:
            continue
        number = item.get("number")
        title = item.get("title")
        url = item.get("html_url") or item.get("url")
        if not isinstance(number, int) or not isinstance(title, str) or not isinstance(url, str):
            fail(f"pull request item is missing number/title/html_url: {item!r}")
        user = item.get("user")
        author = user.get("login") if isinstance(user, dict) and isinstance(user.get("login"), str) else None
        prs.append(
            PullRequest(
                number=number,
                title=" ".join(title.split()),
                url=url,
                labels=label_names(item.get("labels", [])),
                merged_at=merged_at,
                author=author,
            )
        )
    return prs


def filter_by_time(
    prs: Iterable[PullRequest], since: datetime | None, until: datetime | None
) -> list[PullRequest]:
    filtered: list[PullRequest] = []
    for pr in prs:
        merged_at = parse_datetime(pr.merged_at)
        if merged_at is None:
            continue
        if since and merged_at < since:
            continue
        if until and merged_at > until:
            continue
        filtered.append(pr)
    return sorted(filtered, key=lambda item: (parse_datetime(item.merged_at) or datetime.min.replace(tzinfo=timezone.utc), item.number))


def render_release_notes(prs: Iterable[PullRequest]) -> str:
    grouped: dict[str, list[PullRequest]] = {category: [] for category in CATEGORIES}
    errors: list[str] = []

    for pr in prs:
        selected = sorted(RELEASE_NOTES_LABELS.intersection(pr.labels))
        if len(selected) != 1:
            actual = ", ".join(selected) if selected else "none"
            errors.append(f"PR #{pr.number} has {len(selected)} release notes labels ({actual})")
            continue
        label = selected[0]
        if label == "release-notes-skip":
            continue
        grouped[CATEGORY_BY_LABEL[label]].append(pr)

    if errors:
        fail("invalid release notes labels in merged PR metadata:\n- " + "\n- ".join(errors))

    lines = [
        "# Unreleased",
        "",
        "Generated from merged pull request metadata with exactly one release-notes label per PR.",
        "Run `make release-notes-check` before review and regenerate this page when preparing a release draft.",
        "",
    ]
    for category in CATEGORIES:
        lines.append(f"## {category}")
        lines.append("")
        entries = grouped[category]
        if entries:
            for pr in entries:
                author = f" by @{pr.author}" if pr.author else ""
                lines.append(f"- {pr.title}{author} ([#{pr.number}]({pr.url})).")
        else:
            lines.append("- Nothing yet.")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def load_input(path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"input file not found: {path}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON input {path}: {error}")
    if not isinstance(data, list) or not all(isinstance(item, dict) for item in data):
        fail("input file must be a JSON array of pull request objects")
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="GitHub owner/repo; defaults to origin remote when fetching")
    parser.add_argument("--base", default="main", help="base branch to query when fetching from GitHub")
    parser.add_argument("--input", type=Path, help="read pull request JSON from a local fixture instead of GitHub")
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "release-notes" / "unreleased.md")
    parser.add_argument("--since", help="include PRs merged at or after this UTC date/datetime")
    parser.add_argument("--until", help="include PRs merged at or before this UTC date/datetime")
    parser.add_argument("--limit", type=int, default=100, help="maximum closed PRs to fetch from GitHub")
    parser.add_argument("--dry-run", action="store_true", help="print generated notes instead of writing --output")
    parser.add_argument("--check", action="store_true", help="fail if --output differs from generated content")
    args = parser.parse_args()

    if args.input:
        raw_items = load_input(args.input)
    else:
        repo = args.repo or detect_repo()
        token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        since = parse_datetime(args.since)
        until = parse_datetime(args.until)
        raw_items = fetch_merged_pull_requests(repo, args.base, args.limit, token, since, until)

    prs = filter_by_time(
        normalize_pull_requests(raw_items),
        parse_datetime(args.since),
        parse_datetime(args.until),
    )
    rendered = render_release_notes(prs)

    if args.dry_run:
        print(rendered, end="")
        return

    output = args.output if args.output.is_absolute() else ROOT / args.output
    if args.check:
        try:
            existing = output.read_text(encoding="utf-8")
        except FileNotFoundError:
            fail(f"--check output file does not exist: {display_path(output)}")
        if existing != rendered:
            fail(f"{display_path(output)} is not up to date with generated release notes")
        print(f"release-notes-generate: ok ({display_path(output)})")
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8")
    print(f"release-notes-generate: wrote {display_path(output)}")


if __name__ == "__main__":
    main()
