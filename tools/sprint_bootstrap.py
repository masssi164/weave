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
import re
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



DELIVERY_STAGES = ('Open', 'To Do', 'In Progress', 'Review', 'E2E Testing', 'HIL', 'Main')
DELIVERY_FIELD = 'Delivery stage'


def verify_delivery_stage(repo: str, owner: str, stage: str, sha: str,
                          item_url: str, association_pr: int, promotion_pr: int | None,
                          token: str) -> dict[str, Any]:
    """Project existing evidence; never derive acceptance from issue/branch status."""
    if __package__:
        from . import verify_main_promotion_evidence as evidence
    else:
        import verify_main_promotion_evidence as evidence
    if stage not in DELIVERY_STAGES or not evidence.SHA.fullmatch(sha):
        raise ValueError('An exact candidate and supported delivery stage are required')
    canonical_item = re.fullmatch(r'https://github\.com/' + re.escape(repo) + r'/(issues|pull)/([1-9][0-9]*)', item_url)
    if canonical_item is None:
        raise ValueError('Item must be an exact canonical same-repository issue or PR URL')
    pr = evidence.api(f'/repos/{repo}/pulls/{association_pr}', token)
    if pr.get('head', {}).get('repo', {}).get('full_name') != repo:
        raise ValueError('Associated PR must belong to the same repository')
    if item_url != pr.get('html_url'):
        association = gh_json(['pr', 'view', str(association_pr), '--repo', repo,
                               '--json', 'closingIssuesReferences'])
        if item_url not in {issue['url'] for issue in association['closingIssuesReferences']}:
            issue_match = re.fullmatch(r'https://github\.com/' + re.escape(repo) + r'/issues/([1-9][0-9]*)', item_url)
            if issue_match is None:
                raise ValueError('Item is not an explicitly associated same-repository issue')
            number = int(issue_match.group(1))
            timeline = evidence.paged(f'/repos/{repo}/issues/{number}/timeline', token)
            cross_references = [entry for entry in timeline
                                if entry.get('event') == 'cross-referenced'
                                and entry.get('source', {}).get('issue', {}).get('html_url') == pr.get('html_url')
                                and entry.get('source', {}).get('issue', {}).get('pull_request')]
            # Closing keywords are not required: release stories can remain open
            # while their reviewed implementation PR advances through Dev.
            body = pr.get('body') or ''
            reference = re.search(r'(?<![A-Za-z0-9_/#])#' + str(number) + r'(?![0-9])', body)
            url_reference = re.search(re.escape(item_url) + r'(?![A-Za-z0-9_/?#])', body)
            if not cross_references or (not reference and not url_reference):
                raise ValueError('Item is not explicitly associated in the PR and issue timeline')
    source = pr.get('merge_commit_sha') if pr.get('merged') else pr.get('head', {}).get('sha')
    if not isinstance(source, str) or not evidence.SHA.fullmatch(source):
        raise ValueError('Associated PR source is unavailable')
    compare = evidence.api(f'/repos/{repo}/compare/{source}...{sha}', token)
    if compare.get('status') not in {'ahead', 'identical'}:
        raise ValueError('Candidate does not contain the explicitly associated PR source')
    tree, spec = evidence.candidate_identity(repo, sha, token)
    report = {'candidateCommit': sha, 'sourceTree': tree, 'specificationCommit': spec,
              'associationPr': association_pr, 'stage': stage}
    if stage in {'Open', 'To Do', 'In Progress', 'Review'}:
        if pr.get('state') != 'open':
            raise ValueError('Early issue/review projection requires an open associated PR')
        if stage == 'Review' and pr.get('draft') is not False:
            raise ValueError('Draft PR is not review-ready')
        return report
    branch = evidence.api(f'/repos/{repo}/branches/dogfood', token)
    if branch.get('protected') is not True or branch.get('commit', {}).get('sha') != sha:
        raise ValueError('Candidate is not the exact protected Dogfood head')
    if stage == 'E2E Testing':
        runs = evidence.api(f'/repos/{repo}/actions/workflows/live-stack-e2e.yml/runs?branch=dogfood&head_sha={sha}&event=push&per_page=100', token)
        matching = [run for run in runs.get('workflow_runs', [])
                    if run.get('head_sha') == sha and run.get('head_branch') == 'dogfood'
                    and run.get('event') == 'push'
                    and run.get('path') == '.github/workflows/live-stack-e2e.yml']
        if not matching:
            raise ValueError('Exact Dogfood automated testing has not started')
        latest = max(matching, key=lambda run: (run.get('created_at', ''), run.get('id', 0)))
        report.update(qualificationRunId=latest['id'], qualificationRunAttempt=latest['run_attempt'])
        return report
    qualification = evidence.qualified_run(repo, sha, tree, spec, token)
    preparation = evidence.prepared_run(repo, sha, tree, spec, qualification, token)
    report.update(qualificationRunId=qualification['id'],
                  qualificationRunAttempt=qualification['run_attempt'],
                  preparationRunId=preparation['id'])
    if stage == 'Main':
        if not promotion_pr:
            raise ValueError('Main projection requires an explicit merged promotion PR')
        promotion = evidence.api(f'/repos/{repo}/pulls/{promotion_pr}', token)
        if (promotion.get('merged') is not True or promotion.get('base', {}).get('ref') != 'main'
                or promotion.get('base', {}).get('repo', {}).get('full_name') != repo
                or promotion.get('head', {}).get('repo', {}).get('full_name') != repo):
            raise ValueError('Main promotion is not a merged same-repository Main PR')
        promotion_sha = promotion.get('head', {}).get('sha', '')
        if evidence.candidate_identity(repo, promotion_sha, token) != (tree, spec):
            raise ValueError('Promotion source differs from qualified candidate')
        comments = evidence.paged(f'/repos/{repo}/issues/{promotion_pr}/comments', token)
        if not evidence.owner_human_pass(comments, owner, sha, preparation['updated_at']):
            raise ValueError('Explicit latest owner human acceptance is missing or failed')
        main = evidence.api(f'/repos/{repo}/branches/main', token)
        main_sha = main.get('commit', {}).get('sha', '')
        if main.get('protected') is not True or evidence.candidate_identity(repo, main_sha, token) != (tree, spec):
            raise ValueError('Protected Main does not contain the qualified tree/specification')
        merged = promotion.get('merge_commit_sha', '')
        if not evidence.SHA.fullmatch(merged):
            raise ValueError('Merged promotion source is invalid')
        if evidence.api(f'/repos/{repo}/compare/{merged}...{main_sha}', token).get('status') not in {'ahead', 'identical'}:
            raise ValueError('Promotion merge is not in protected Main')
        report.update(promotionPr=promotion_pr, mainCommit=main_sha)
    return report


def project_delivery_transition(args: argparse.Namespace) -> None:
    """Dry run by default; resolve live IDs, reverify, then mutate one associated item."""
    token = run(['gh', 'auth', 'token'])
    if not token:
        raise ValueError('GitHub credential is unavailable')
    # Read permission failure must occur before evidence requests or mutation.
    projects = gh_json(['project', 'list', '--owner', args.project_owner, '--format', 'json', '--limit', '100'])
    project = next((value for value in projects['projects'] if value['number'] == args.project_number), None)
    if project is None:
        raise ValueError('Project is unavailable')
    fields = gh_json(['project', 'field-list', str(args.project_number), '--owner', args.project_owner,
                      '--format', 'json', '--limit', '100'])['fields']
    matches = [field for field in fields if field['name'] == DELIVERY_FIELD]
    if len(matches) != 1 or matches[0].get('type') != 'ProjectV2SingleSelectField':
        raise ValueError('Unique Delivery stage single-select field is required')
    field = matches[0]
    options = {option['name']: option['id'] for option in field['options']}
    if set(options) != set(DELIVERY_STAGES):
        raise ValueError('Delivery stage options do not match the accepted workflow')
    items = gh_json(['project', 'item-list', str(args.project_number), '--owner', args.project_owner,
                     '--format', 'json', '--limit', '10000'])
    matches = [item for item in items['items'] if item.get('content', {}).get('url') == args.item_url]
    if len(matches) != 1 or len(items['items']) != items.get('totalCount'):
        raise ValueError('Unique associated Project item or complete inventory is unavailable')
    item = matches[0]
    def verify():
        return verify_delivery_stage(args.repo, args.project_owner, args.delivery_stage,
                                     args.candidate_sha, args.item_url, args.association_pr,
                                     args.promotion_pr, token)
    report = verify()
    value = gh_json(['api', 'graphql', '-f',
                     'query=query($id:ID!) { node(id:$id) { ... on ProjectV2Item { project { id } fieldValueByName(name:"Delivery stage") { ... on ProjectV2ItemFieldSingleSelectValue { name } } } } }',
                     '-f', 'id=' + item['id']])['data']['node']
    if value.get('project', {}).get('id') != project['id']:
        raise ValueError('Project item ownership changed')
    current = (value.get('fieldValueByName') or {}).get('name', '')
    report.update(projectNumber=args.project_number, itemUrl=args.item_url,
                  previousStage=current, apply=args.apply, changed=False)
    if args.apply and current != args.delivery_stage:
        # Do not reuse an earlier readiness observation across the write boundary.
        verify()
        run(['gh', 'project', 'item-edit', '--id', item['id'], '--project-id', project['id'],
             '--field-id', field['id'], '--single-select-option-id', options[args.delivery_stage]])
        report['changed'] = True
    print(json.dumps(report, indent=2))

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--delivery-stage", choices=DELIVERY_STAGES)
    parser.add_argument("--item-url")
    parser.add_argument("--candidate-sha")
    parser.add_argument("--association-pr", type=int)
    parser.add_argument("--promotion-pr", type=int)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--project-owner", default=DEFAULT_PROJECT_OWNER)
    parser.add_argument("--project-number", type=int, default=DEFAULT_PROJECT_NUMBER)
    parser.add_argument("--apply", action="store_true", help="Create missing milestone/issues/project links")
    parser.add_argument("--audit-only", action="store_true", help="Fail if expected state is missing; default when --apply is absent")
    args = parser.parse_args()

    if args.delivery_stage:
        if args.plan or not args.item_url or not args.candidate_sha or not args.association_pr:
            parser.error("Delivery projection requires --item-url, --candidate-sha and --association-pr; no plan")
        project_delivery_transition(args)
        return
    if not args.plan:
        parser.error("--plan is required for sprint bootstrap/audit")
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
