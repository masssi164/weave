#!/usr/bin/env python3
"""Create or audit Weave sprint control-plane state.

The script intentionally treats GitHub as delivery/evidence truth and the pinned
Weave Specification Corpus as fachliche specification truth. It can audit an
existing sprint or, with --apply, create missing milestone/issues/project-board
links from a JSON plan.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPO = "masssi164/weave"
DEFAULT_PROJECT_OWNER = "masssi164"
DEFAULT_PROJECT_NUMBER = 2


def fail(message: str) -> None:
    print(f"sprint-bootstrap: {message}", file=sys.stderr)
    raise SystemExit(1)


def run(args: list[str], *, check: bool = True) -> str:
    proc = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if check and proc.returncode != 0:
        fail(f"command failed: {' '.join(args)}\n{proc.stdout}")
    return proc.stdout.strip()


def gh_json(args: list[str]) -> Any:
    out = run(["gh", *args])
    try:
        return json.loads(out) if out else None
    except json.JSONDecodeError as exc:
        fail(f"gh returned invalid JSON for {' '.join(args)}: {exc}\n{out[:500]}")


def load_plan(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid plan JSON: {path}: {exc}")
    for key in ["sprint", "milestone", "issues"]:
        if key not in data:
            fail(f"plan missing required key: {key}")
    if not isinstance(data["issues"], list) or not data["issues"]:
        fail("plan.issues must be a non-empty list")
    return data


def issue_view(repo: str, number: int) -> dict[str, Any] | None:
    out = run([
        "gh", "issue", "view", str(number), "--repo", repo,
        "--json", "number,title,state,url,labels,milestone,body",
    ], check=False)
    if not out:
        return None
    if out.startswith("GraphQL") or "not found" in out.lower():
        return None
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return None


def milestone_by_title(repo: str, title: str) -> dict[str, Any] | None:
    milestones = gh_json(["api", f"repos/{repo}/milestones?state=all&per_page=100"])
    for milestone in milestones:
        if milestone.get("title") == title:
            return milestone
    return None


def project_context(owner: str, project_number: int) -> tuple[str, dict[str, Any], dict[str, str]]:
    projects = gh_json(["project", "list", "--owner", owner, "--format", "json", "--limit", "50"])["projects"]
    project = next((p for p in projects if p.get("number") == project_number), None)
    if not project:
        fail(f"GitHub project not found: owner={owner} number={project_number}")
    fields = gh_json(["project", "field-list", str(project_number), "--owner", owner, "--format", "json", "--limit", "50"])["fields"]
    status_field = next((f for f in fields if f.get("name") == "Status"), None)
    status_options = {o["name"]: o["id"] for o in (status_field or {}).get("options", [])}
    return project["id"], status_field or {}, status_options


def project_items(owner: str, project_number: int) -> dict[int, dict[str, Any]]:
    data = gh_json(["project", "item-list", str(project_number), "--owner", owner, "--format", "json", "--limit", "200"])
    result: dict[int, dict[str, Any]] = {}
    for item in data.get("items", []):
        content = item.get("content") or {}
        number = content.get("number")
        if isinstance(number, int):
            result[number] = item
    return result


def ensure_project_status(owner: str, project_number: int, issue_url: str, issue_number: int, status: str, apply: bool) -> tuple[str, str]:
    items = project_items(owner, project_number)
    item = items.get(issue_number)
    if not item:
        if not apply:
            return "missing", "not in project"
        run(["gh", "project", "item-add", str(project_number), "--owner", owner, "--url", issue_url, "--format", "json"])
        items = project_items(owner, project_number)
        item = items.get(issue_number)
        if not item:
            return "missing", "project item add did not materialize"
    current = item.get("status") or ""
    if status and current != status:
        if not apply:
            return "wrong-status", f"{current!r} != {status!r}"
        project_id, status_field, status_options = project_context(owner, project_number)
        option_id = status_options.get(status)
        if not status_field or not option_id:
            return "wrong-status", f"status option not available: {status}"
        run([
            "gh", "project", "item-edit",
            "--id", item["id"],
            "--project-id", project_id,
            "--field-id", status_field["id"],
            "--single-select-option-id", option_id,
        ])
        return "ok", f"status set {status}"
    return "ok", current or "present"


def create_issue(repo: str, issue: dict[str, Any], milestone_title: str, apply: bool) -> dict[str, Any] | None:
    if not apply:
        return None
    labels = []
    for label in issue.get("labels", []):
        labels.extend(["--label", label])
    args = [
        "gh", "issue", "create", "--repo", repo,
        "--title", issue["title"],
        "--body", issue.get("body", "Created by tools/sprint_bootstrap.py"),
        "--milestone", milestone_title,
        *labels,
    ]
    url = run(args)
    number = int(url.rstrip("/").split("/")[-1])
    return issue_view(repo, number)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--project-owner", default=DEFAULT_PROJECT_OWNER)
    parser.add_argument("--project-number", type=int, default=DEFAULT_PROJECT_NUMBER)
    parser.add_argument("--apply", action="store_true", help="Create missing milestone/issues/project links")
    parser.add_argument("--audit-only", action="store_true", help="Fail if expected state is missing; default when --apply is absent")
    args = parser.parse_args()

    plan = load_plan(args.plan)
    apply = args.apply
    milestone_title = plan["milestone"]["title"]
    expected_project_status = plan.get("project", {}).get("defaultStatus", "Todo")

    report: dict[str, Any] = {
        "schemaVersion": 1,
        "repo": args.repo,
        "project": {"owner": args.project_owner, "number": args.project_number},
        "plan": str(args.plan),
        "apply": apply,
        "milestone": {},
        "issues": [],
        "ok": True,
    }

    milestone = milestone_by_title(args.repo, milestone_title)
    if not milestone:
        if apply:
            run([
                "gh", "api", f"repos/{args.repo}/milestones", "--method", "POST",
                "-f", f"title={milestone_title}",
                "-f", f"description={plan['milestone'].get('description', '')}",
            ])
            milestone = milestone_by_title(args.repo, milestone_title)
        else:
            report["ok"] = False
            report["milestone"] = {"title": milestone_title, "status": "missing"}
    if milestone:
        report["milestone"] = {
            "title": milestone.get("title"),
            "number": milestone.get("number"),
            "state": milestone.get("state"),
            "open_issues": milestone.get("open_issues"),
            "closed_issues": milestone.get("closed_issues"),
            "html_url": milestone.get("html_url"),
        }

    for expected in plan["issues"]:
        actual = issue_view(args.repo, expected["number"]) if expected.get("number") else None
        if not actual and expected.get("title"):
            # Avoid fuzzy matching; sprint plans should pin created issue numbers after first apply.
            actual = create_issue(args.repo, expected, milestone_title, apply)
        entry: dict[str, Any] = {"expected": {k: expected.get(k) for k in ["number", "title"]}}
        if not actual:
            entry["status"] = "missing"
            report["ok"] = False
            report["issues"].append(entry)
            continue
        labels = {label["name"] for label in actual.get("labels", [])}
        missing_labels = sorted(set(expected.get("labels", [])) - labels)
        milestone_ok = (actual.get("milestone") or {}).get("title") == milestone_title
        project_status, project_note = ensure_project_status(
            args.project_owner, args.project_number, actual["url"], actual["number"], expected.get("projectStatus", expected_project_status), apply
        )
        ok = not missing_labels and milestone_ok and project_status == "ok"
        if not ok:
            report["ok"] = False
        entry.update({
            "number": actual["number"],
            "title": actual["title"],
            "state": actual["state"],
            "url": actual["url"],
            "milestoneOk": milestone_ok,
            "missingLabels": missing_labels,
            "projectStatus": project_status,
            "projectNote": project_note,
            "ok": ok,
        })
        report["issues"].append(entry)

    print(json.dumps(report, indent=2, ensure_ascii=False))
    if not report["ok"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
