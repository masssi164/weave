#!/usr/bin/env python3
"""Verify exact dogfood deployment and separately reported human results."""

from __future__ import annotations

import argparse
import base64
import io
import zipfile
import json
import os
import re
import sys
from datetime import datetime
from urllib.parse import urlencode
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen

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
    def result_time(comment: dict) -> datetime:
        return max(
            datetime.fromisoformat(comment[field].replace("Z", "+00:00"))
            for field in ("created_at", "updated_at") if comment.get(field)
        )

    owner_results = [
        comment
        for comment in comments
        if comment.get("user", {}).get("login", "").casefold() == owner.casefold()
        and comment.get("author_association") == "OWNER"
        and comment.get("body", "").strip().startswith(COMMENT_HEADER)
        and result_time(comment) >= completed
    ]
    if not owner_results:
        return False
    latest = max(owner_results, key=lambda comment: (result_time(comment), comment.get("id", 0)))
    return parse_human_comment(latest.get("body", ""), sha)


def resolve_merge_group(entries: list[dict], candidate_sha: str, repository: str) -> dict:
    """Bind the tested synthetic head to one current, same-repository queued PR."""
    matching = [entry for entry in entries
                if (entry.get("headCommit") or {}).get("oid") == candidate_sha]
    if len(matching) != 1:
        raise ValueError("Merge candidate must match exactly one current main queue entry")
    pr = matching[0].get("pullRequest") or {}
    if not (
        pr.get("state") == "OPEN" and pr.get("baseRefName") == "main"
        and (pr.get("baseRepository") or {}).get("nameWithOwner") == repository
        and (pr.get("headRepository") or {}).get("nameWithOwner") == repository
        and SHA.fullmatch(pr.get("headRefOid", ""))
        and isinstance(pr.get("number"), int) and pr["number"] > 0
        and pr.get("headRefName")
    ):
        raise ValueError("Merge queue evidence must belong to an open same-repository main PR")
    return pr


def queued_pr(repository: str, candidate_sha: str, token: str) -> dict:
    owner, name = repository.split("/", 1)
    request = Request(
        "https://api.github.com/graphql",
        data=json.dumps({
            "query": """query($owner:String!, $name:String!) {
              repository(owner:$owner, name:$name) { mergeQueue(branch:"main") {
                entries(first:100) { nodes { headCommit { oid } pullRequest {
                  number state baseRefName headRefOid headRefName
                  baseRepository { nameWithOwner } headRepository { nameWithOwner }
                } } pageInfo { hasNextPage } }
              } }
            }""",
            "variables": {"owner": owner, "name": name},
        }).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    with urlopen(request, timeout=20) as response:
        result = json.load(response)
    if result.get("errors"):
        raise ValueError("GitHub could not verify current merge queue state")
    queue = result.get("data", {}).get("repository", {}).get("mergeQueue")
    if not queue or queue["entries"]["pageInfo"]["hasNextPage"]:
        raise ValueError("Current main merge queue is absent or exceeds the verification window")
    return resolve_merge_group(queue["entries"]["nodes"], candidate_sha, repository)


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


def artifact_json(archive: bytes, basename: str) -> dict:
    """Read one bounded evidence object without extracting untrusted ZIP paths."""
    with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
        matches = [entry for entry in bundle.infolist()
                   if entry.filename.rsplit("/", 1)[-1] == basename and not entry.is_dir()]
        if len(matches) != 1 or matches[0].file_size > 1024 * 1024:
            raise ValueError("Evidence object is absent, ambiguous or oversized")
        value = json.loads(bundle.read(matches[0]))
    if not isinstance(value, dict):
        raise ValueError("Evidence object must be an object")
    return value


def native_toolchain_recorded(value: object) -> bool:
    """Require identities enforced by the exact candidate's native producer."""
    if not isinstance(value, dict):
        return False
    patterns = {
        "frameworkVersion": r"[0-9]+\.[0-9]+\.[0-9]+",
        "channel": r"stable",
        "frameworkRevision": r"[0-9a-f]{40}",
        "engineRevision": r"[0-9a-f]{40}",
        "dartSdkVersion": r"[0-9]+\.[0-9]+\.[0-9]+",
        "xcodeVersion": r"[0-9]+\.[0-9]+(?:\.[0-9]+)?",
        "xcodeBuild": r"[0-9]+[A-Z][0-9]+[a-z]?",
        "iosSimulatorSdkVersion": r"[0-9]+\.[0-9]+(?:\.[0-9]+)?",
        "rustToolchain": r"[0-9]+\.[0-9]+\.[0-9]+",
        "rustCompilerVersion": r"rustc [0-9]+\.[0-9]+\.[0-9]+ \([0-9a-f]{7,40} [0-9]{4}-[0-9]{2}-[0-9]{2}\)",
    }
    return all(isinstance(value.get(key), str) and re.fullmatch(pattern, value[key])
               for key, pattern in patterns.items()) and value["rustCompilerVersion"].startswith(
                   "rustc " + value["rustToolchain"] + " (")


def native_qualification_passed(value: dict, sha: str, tree: str, spec: str) -> bool:
    return (
        value.get("schemaVersion") == "weave.native-qualification/v1"
        and value.get("candidateCommit") == sha
        and value.get("sourceTree") == tree
        and value.get("specificationCommit") == spec
        and native_toolchain_recorded(value.get("toolchain"))
        and value.get("macos", {}).get("status") == "passed"
        and value.get("iosSimulator", {}).get("status") == "passed"
        and value.get("iosSimulator", {}).get("rustTarget") == "aarch64-apple-ios-sim"
        and value.get("cleanupComplete") is True
        and value.get("supportSafe") is True
        and value.get("containsSecretValues") is False
    )


def preparation_passed(value: dict, sha: str, tree: str, spec: str, run_id: int, run_attempt: int) -> bool:
    toolchain = value.get("toolchain")
    if not native_toolchain_recorded(toolchain):
        return False
    if toolchain.get("iosDeviceSdkVersion") != toolchain["iosSimulatorSdkVersion"]:
        return False
    return all(value.get(key) == expected for key, expected in {
        "schemaVersion": "weave.hil-preparation/v1", "candidateCommit": sha,
        "sourceTree": tree, "specificationCommit": spec,
        "qualificationRunId": run_id, "qualificationRunAttempt": run_attempt, "status": "passed", "install": "update_in_place",
        "supportSafe": True, "containsSecretValues": False,
    }.items()) and value.get("supportSafe") is True and value.get("containsSecretValues") is False and type(value.get("qualificationRunId")) is int and type(value.get("qualificationRunAttempt")) is int


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def download_artifact(repository: str, run_id: int, name: str, token: str) -> bytes:
    artifacts = paged(f"/repos/{repository}/actions/runs/{run_id}/artifacts", token, "artifacts")
    matching = [item for item in artifacts if item.get("name") == name]
    if len(matching) != 1 or matching[0].get("expired") is not False:
        raise ValueError("Required workflow artifact is missing, ambiguous or expired")
    item = matching[0]
    if not isinstance(item.get("id"), int) or not 0 < item.get("size_in_bytes", 0) <= 32 * 1024 * 1024:
        raise ValueError("Artifact identity or size is invalid")
    request = Request(f"https://api.github.com/repos/{repository}/actions/artifacts/{item['id']}/zip",
                      headers={"Authorization": f"Bearer {token}",
                               "Accept": "application/vnd.github+json"})
    try:
        response = build_opener(NoRedirect()).open(request, timeout=20)
    except HTTPError as error:
        if error.code != 302:
            raise ValueError("GitHub artifact download failed") from None
        location = error.headers.get("Location", "")
        if not location.startswith("https://"):
            raise ValueError("Artifact redirect must use HTTPS")
        # The signed storage redirect receives no GitHub bearer credential.
        response = urlopen(location, timeout=30)
    with response:
        content = response.read(32 * 1024 * 1024 + 1)
    if len(content) > 32 * 1024 * 1024:
        raise ValueError("Artifact exceeds the bounded download size")
    return content


def attempt_artifact(prefix: str, sha: str, run: dict) -> str:
    attempt = run.get("run_attempt")
    if type(attempt) is not int or attempt < 1:
        raise ValueError("Workflow attempt identity is missing")
    return f"{prefix}-{sha}-attempt-{attempt}"


def candidate_identity(repository: str, sha: str, token: str) -> tuple[str, str]:
    commit = api(f"/repos/{repository}/git/commits/{sha}", token)
    tree = commit.get("tree", {}).get("sha", "")
    lock = api(f"/repos/{repository}/contents/specs/weave-specs.lock.json?ref={sha}", token)
    if lock.get("encoding") != "base64":
        raise ValueError("Candidate specification lock is unavailable")
    spec = json.loads(base64.b64decode(lock["content"]))["specCorpus"]["gitCommit"]
    if not SHA.fullmatch(tree) or not isinstance(spec, str) or not SHA.fullmatch(spec):
        raise ValueError("Candidate tree/specification identity is invalid")
    return tree, spec


def qualified_run(repository: str, sha: str, tree: str, spec: str, token: str) -> dict:
    query = urlencode({"branch": "dogfood", "head_sha": sha, "event": "push"})
    response = api(f"/repos/{repository}/actions/workflows/live-stack-e2e.yml/runs?{query}&per_page=100", token)
    run = deployed_run(response.get("workflow_runs", []), sha)
    if run is None or run.get("path") != ".github/workflows/live-stack-e2e.yml":
        raise ValueError("No successful latest exact Dogfood qualification/deployment run")
    jobs = paged(f"/repos/{repository}/actions/runs/{run['id']}/jobs?filter=latest", token, "jobs")
    if not required_jobs_passed(jobs):
        raise ValueError("Exact qualification or deployment job did not pass")
    archive = download_artifact(repository, run["id"], attempt_artifact("full-compose-e2e", sha, run), token)
    if not native_qualification_passed(artifact_json(archive, "native-qualification.json"), sha, tree, spec):
        raise ValueError("Mandatory native qualification does not match the candidate")
    teardown = artifact_json(archive, "teardown-evidence.json")
    if (teardown.get("candidateCommit") != sha or teardown.get("dryRun") is not False
            or teardown.get("ownershipLabelsVerified") is not True
            or any(type(teardown.get(key)) is not int or teardown[key] != 0 for key in (
                "remainingOwnedResources", "remainingContainerCount", "remainingNetworkCount", "remainingVolumeCount"))):
        raise ValueError("Qualification does not prove exact-run cleanup")
    deployment = artifact_json(download_artifact(repository, run["id"], attempt_artifact("dogfood-compose", sha, run), token),
                               "deployment-result.json")
    expected = {"schemaVersion": "weave.dogfood-deployment/v1", "candidateCommit": sha,
                "sourceTree": tree, "specificationCommit": spec,
                "qualificationRunId": run["id"], "status": "passed"}
    if (any(deployment.get(key) != value for key, value in expected.items())
            or deployment.get("supportSafe") is not True
            or deployment.get("containsSecretValues") is not False):
        raise ValueError("Deployment evidence does not match exact qualification")
    return run


def prepared_run(repository: str, sha: str, tree: str, spec: str, qualification: dict, token: str) -> dict:
    runs = paged(f"/repos/{repository}/actions/workflows/ios-dogfood.yml/runs", token, "workflow_runs")
    matching = [run for run in runs if run.get("display_title") == f"Prepare Human Test {sha}"
                and run.get("event") == "workflow_dispatch"
                and run.get("path") == ".github/workflows/ios-dogfood.yml"]
    if not matching:
        raise ValueError("Physical preparation evidence is absent")
    run = max(matching, key=lambda item: (item.get("created_at", ""), item.get("id", 0)))
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        raise ValueError("Latest physical preparation did not succeed")
    if not qualification.get("updated_at") or not run.get("run_started_at") or run["run_started_at"] <= qualification["updated_at"]:
        raise ValueError("Physical preparation must start after current qualification/deployment")
    archive = download_artifact(repository, run["id"], attempt_artifact("prepare-human-test", sha, run), token)
    if not preparation_passed(artifact_json(archive, "preparation-result.json"), sha, tree, spec, qualification["id"], qualification["run_attempt"]):
        raise ValueError("Physical preparation does not match current qualification")
    return run


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--owner")
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--dogfood-sha")
    parser.add_argument("--pr-number", type=int)
    parser.add_argument("--resolve-merge-group", action="store_true")
    parser.add_argument("--qualification-only", action="store_true")
    args = parser.parse_args()
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token or not SHA.fullmatch(args.candidate_sha):
        parser.error("A GitHub token and exact candidate SHA are required")
    if args.resolve_merge_group:
        pr = queued_pr(args.repository, args.candidate_sha, token)
        print(f"{pr['number']}\t{pr['headRefOid']}\t{pr['headRefName']}")
        return 0
    if args.qualification_only:
        tree, spec = candidate_identity(args.repository, args.candidate_sha, token)
        run = qualified_run(args.repository, args.candidate_sha, tree, spec, token)
        print(f"{run['id']} {run['run_attempt']}")
        return 0
    if not args.owner or not SHA.fullmatch(args.dogfood_sha or "") or not args.pr_number or args.pr_number < 1:
        parser.error("A GitHub token, exact candidate/dogfood SHAs and promotion PR number are required")

    repo = args.repository
    pr = api(f"/repos/{repo}/pulls/{args.pr_number}", token)
    if not matches_promotion_pr(pr, repo, args.candidate_sha):
        print("Human result does not belong to the exact open main promotion PR", file=sys.stderr)
        return 1
    tree, spec = candidate_identity(repo, args.dogfood_sha, token)
    candidate_tree, candidate_spec = candidate_identity(repo, args.candidate_sha, token)
    if (candidate_tree, candidate_spec) != (tree, spec):
        raise ValueError("Promotion source tree or specification differs from qualified Dogfood")
    run = qualified_run(repo, args.dogfood_sha, tree, spec, token)
    preparation = prepared_run(repo, args.dogfood_sha, tree, spec, run, token)
    comments = paged(f"/repos/{repo}/issues/{args.pr_number}/comments", token)
    if not owner_human_pass(comments, args.owner, args.dogfood_sha, preparation["updated_at"]):
        print("Owner human result for exact deployed dogfood commit is absent or incomplete", file=sys.stderr)
        return 1
    print(f"Exact dogfood {args.dogfood_sha}: Compose, macOS, iOS, deployment, physical preparation and owner human result passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
