#!/usr/bin/env python3
"""Verify exact dogfood deployment and separately reported human results."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime
from urllib.parse import urlencode
from urllib.request import Request, urlopen

SHA = re.compile(r"^[0-9a-f]{40}$")
COMMENT_HEADER = "Weave human test v1"
REQUIRED_SURFACES = (
    "sign-in",
    "chat",
    "files",
    "calendar",
    "accessibility",
    "revoke-regrant",
    "identity-continuity",
)
REQUIRED_JOBS = {"Full Compose E2E", "Deploy dogfood with Compose"}


def parse_human_comment(body: str, dogfood_sha: str) -> bool:
    """Accept only a complete, exact-commit human pass, not E2E prose."""
    lines = [line.strip() for line in body.splitlines() if line.strip()]
    if not lines or lines[0] != COMMENT_HEADER:
        return False
    fields: dict[str, str] = {}
    for line in lines[1:]:
        key, separator, value = line.partition(":")
        if not separator or key.strip() in fields:
            return False
        fields[key.strip()] = value.strip()
    return (
        fields.get("dogfood") == dogfood_sha
        and {"dogfood", *REQUIRED_SURFACES} <= set(fields)
        and set(fields) <= {"dogfood", *REQUIRED_SURFACES, "notes"}
        and all(fields[surface] == "passed" for surface in REQUIRED_SURFACES)
    )


def deployed_run(runs: list[dict], dogfood_sha: str) -> dict | None:
    """Use the newest exact dogfood push; an older green run cannot mask failure."""
    matching = [
        run
        for run in runs
        if run.get("head_sha") == dogfood_sha
        and run.get("head_branch") == "dogfood"
        and run.get("event") == "push"
    ]
    if not matching:
        return None
    latest = max(matching, key=lambda run: (run.get("created_at", ""), run.get("id", 0)))
    return latest if latest.get("status") == "completed" and latest.get("conclusion") == "success" else None


def required_jobs_passed(jobs: list[dict]) -> bool:
    for name in REQUIRED_JOBS:
        matching = [job for job in jobs if job.get("name") == name]
        if len(matching) != 1 or matching[0].get("conclusion") != "success":
            return False
    return True


def matches_promotion_pr(pr: dict, repository: str, candidate_sha: str) -> bool:
    """Bind human evidence to the exact same-repository main PR."""
    return (
        pr.get("base", {}).get("ref") == "main"
        and pr.get("base", {}).get("repo", {}).get("full_name") == repository
        and pr.get("head", {}).get("sha") == candidate_sha
        and pr.get("head", {}).get("repo", {}).get("full_name") == repository
        and pr.get("state") == "open"
    )


def owner_human_pass(comments: list[dict], owner: str, sha: str, deployed_at: str) -> bool:
    completed = datetime.fromisoformat(deployed_at.replace("Z", "+00:00"))
    owner_results = [
        comment
        for comment in comments
        if comment.get("user", {}).get("login", "").casefold() == owner.casefold()
        and comment.get("author_association") == "OWNER"
        and comment.get("body", "").strip().startswith(COMMENT_HEADER)
        and datetime.fromisoformat(comment["created_at"].replace("Z", "+00:00")) >= completed
    ]
    if not owner_results:
        return False
    latest = max(owner_results, key=lambda comment: (comment["created_at"], comment.get("id", 0)))
    return parse_human_comment(latest.get("body", ""), sha)


def api(path: str, token: str) -> object:
    request = Request(
        f"https://api.github.com{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urlopen(request, timeout=20) as response:
        return json.load(response)


def paged(path: str, token: str, key: str | None = None) -> list[dict]:
    items: list[dict] = []
    for page in range(1, 11):
        separator = "&" if "?" in path else "?"
        response = api(f"{path}{separator}per_page=100&page={page}", token)
        batch = response.get(key) if key is not None else response
        if not isinstance(batch, list):
            raise ValueError("Expected a paginated GitHub list")
        items.extend(batch)
        if len(batch) < 100:
            break
        if page == 10:
            raise ValueError("GitHub evidence list exceeds the bounded verification window")
    return items


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--dogfood-sha", required=True)
    parser.add_argument("--pr-number", type=int, required=True)
    args = parser.parse_args()
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token or not SHA.fullmatch(args.candidate_sha) or not SHA.fullmatch(args.dogfood_sha) or args.pr_number < 1:
        parser.error("A GitHub token, exact candidate/dogfood SHAs and promotion PR number are required")

    repo = args.repository
    pr = api(f"/repos/{repo}/pulls/{args.pr_number}", token)
    if not matches_promotion_pr(pr, repo, args.candidate_sha):
        print("Human result does not belong to the exact open main promotion PR", file=sys.stderr)
        return 1
    query = urlencode({"branch": "dogfood", "head_sha": args.dogfood_sha, "event": "push"})
    response = api(f"/repos/{repo}/actions/workflows/live-stack-e2e.yml/runs?{query}&per_page=100", token)
    run = deployed_run(response.get("workflow_runs", []), args.dogfood_sha)
    if run is None:
        print("No successful latest push-triggered Full Compose E2E for exact dogfood head", file=sys.stderr)
        return 1
    jobs = paged(f"/repos/{repo}/actions/runs/{run['id']}/jobs?filter=latest", token, "jobs")
    if not required_jobs_passed(jobs):
        print("Exact dogfood E2E or deployment job did not pass", file=sys.stderr)
        return 1
    comments = paged(f"/repos/{repo}/issues/{args.pr_number}/comments", token)
    if not owner_human_pass(comments, args.owner, args.dogfood_sha, run["updated_at"]):
        print("Owner human result for exact deployed dogfood commit is absent or incomplete", file=sys.stderr)
        return 1
    print(f"Exact dogfood {args.dogfood_sha}: E2E, deployment and owner human result passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
