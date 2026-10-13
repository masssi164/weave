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


def dual_ios_recovery_passed(value: object, actors: list[dict], authorization: dict) -> bool:
    """Require real process/transport recovery bound to the initial UI actors."""
    if not isinstance(value, dict):
        return False
    restored = value.get("actors")
    if (not isinstance(restored, list) or len(restored) != 2
            or not all(isinstance(actor, dict) for actor in restored)
            or {actor.get("role") for actor in restored if isinstance(actor.get("role"), str)} != {"A", "B"}):
        return False
    initial = {actor["role"]: actor for actor in actors}
    all_pids = [actor["processId"] for actor in actors]
    try:
        def timestamp(record: dict, key: str) -> datetime:
            result = datetime.fromisoformat(record[key].replace("Z", "+00:00"))
            if result.tzinfo is None:
                raise ValueError("Execution time needs a timezone")
            return result

        start, end = (timestamp(value["initialOverlap"], key) for key in ("startedAt", "endedAt"))
        if start >= end:
            return False
        for actor in restored:
            previous = initial[actor["role"]]
            pid = actor.get("restoredProcessId")
            if (type(pid) is not int or pid <= 0 or pid in all_pids
                    or type(actor.get("initialProcessId")) is not int
                    or actor.get("initialProcessId") != previous["processId"]):
                return False
            all_pids.append(pid)
            first_start, first_end, next_start, next_end = (
                timestamp(actor, key) for key in (
                    "initialStartedAt", "initialEndedAt", "restoredStartedAt", "restoredEndedAt"))
            if (first_start != timestamp(previous, "startedAt")
                    or first_end != timestamp(previous, "endedAt")
                    or not first_start <= start < end <= first_end <= next_start < next_end):
                return False
            for key in ("identityDigest", "matrixDeviceDigest", "storageDigest"):
                if actor.get("restored" + key[0].upper() + key[1:]) != previous[key]:
                    return False
            if any(actor.get(key) is not True for key in (
                    "previousProcessAbsent", "expiryDenied", "refreshObserved", "logoutIndependent")):
                return False
    except (KeyError, TypeError, ValueError, AttributeError):
        return False
    network = value.get("network")
    if not isinstance(network, dict) or network.get("interruptedActor") != "A":
        return False
    for key in ("affectedConnections", "actorAForwardedBytes", "actorBForwardedBytesDuringInterruption"):
        if type(network.get(key)) is not int or network[key] <= 0:
            return False
    if any(network.get(key) is not True for key in (
            "userApiFailureObserved", "matrixSendFailureObserved", "peerUiContinuityObserved",
            "restoreObserved", "matrixRetryObserved", "peerReceptionObserved")):
        return False
    grant = value.get("currentGrantRevocation")
    resource = authorization.get("resourceDigest")
    return (
        isinstance(grant, dict)
        and isinstance(resource, str) and re.fullmatch(r"[0-9a-f]{64}", resource) is not None
        and grant.get("status") == "passed"
        and grant.get("evidence") == "server-denial-and-flutter-readback"
        and grant.get("resourceDigest") == resource
        and grant.get("deniedIdentityDigest") == initial["B"]["identityDigest"]
        and all(grant.get(key) is True for key in ("denied", "regrantRestored", "uiReadback"))
    )


def dual_ios_collaboration_passed(value: object) -> bool:
    """Reject single-client or incomplete proof for the accepted October release."""
    if not isinstance(value, dict) or value.get("status") != "passed":
        return False
    digest = re.compile(r"[0-9a-f]{64}")
    app_digest = value.get("appBuildSha256")
    if not isinstance(app_digest, str) or not digest.fullmatch(app_digest):
        return False
    actors = value.get("actors")
    if not isinstance(actors, list) or len(actors) != 2 or not all(isinstance(a, dict) for a in actors):
        return False
    roles = [actor.get("role") for actor in actors]
    if not all(isinstance(role, str) for role in roles) or set(roles) != {"A", "B"}:
        return False
    for key in ("simulatorId", "identityDigest", "matrixDeviceDigest", "storageDigest"):
        values = [actor.get(key) for actor in actors]
        if not all(isinstance(item, str) for item in values) or len(set(values)) != 2:
            return False
        pattern = re.compile(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") if key == "simulatorId" else digest
        if not all(pattern.fullmatch(item) for item in values):
            return False
    processes = [actor.get("processId") for actor in actors]
    if not all(type(pid) is int and pid > 0 for pid in processes) or len(set(processes)) != 2:
        return False
    try:
        def interval(record: dict) -> tuple[datetime, datetime]:
            start, end = (datetime.fromisoformat(record[key].replace("Z", "+00:00"))
                          for key in ("startedAt", "endedAt"))
            if start.tzinfo is None or end.tzinfo is None or start >= end:
                raise ValueError("Invalid execution interval")
            return start, end
        overlap_start, overlap_end = interval(value["overlap"])
        for actor in actors:
            start, end = interval(actor)
            if not start <= overlap_start < overlap_end <= end:
                return False
    except (KeyError, TypeError, ValueError, AttributeError):
        return False
    scenarios = value.get("scenarios")
    if not isinstance(scenarios, dict):
        return False
    for name in ("authentication", "chat", "calendar", "files", "authorization", "recovery", "accessibility"):
        result = scenarios.get(name)
        if not isinstance(result, dict) or result.get("status") != "passed":
            return False
        if result.get("actors") != ["A", "B"]:
            return False
        if name == "authorization":
            denied_identity = result.get("deniedIdentityDigest")
            if (result.get("evidence") not in ("flutter-ui", "server-denial")
                    or not isinstance(denied_identity, str)
                    or not digest.fullmatch(denied_identity)
                    or denied_identity in {actor["identityDigest"] for actor in actors}
                    or result.get("denials") != {"chat": "passed", "files": "passed", "calendar": "passed"}):
                return False
        elif result.get("evidence") != "flutter-ui":
            return False
    encryption = value.get("encryption")
    if not isinstance(encryption, dict) or type(encryption.get("policyRequired")) is not bool:
        return False
    if encryption.get("plaintextFallback") is not False:
        return False
    if encryption.get("status") != ("passed" if encryption["policyRequired"] else "not-required"):
        return False
    recovery = value.get("recovery")
    if (not isinstance(recovery, dict) or recovery.get("initialOverlap") != value.get("overlap")
            or not dual_ios_recovery_passed(recovery, actors, scenarios["authorization"])):
        return False
    return value.get("cleanupComplete") is True


def native_qualification_passed(value: dict, sha: str, tree: str, spec: str) -> bool:
    if not isinstance(value, dict) or any(not isinstance(value.get(platform), dict)
                                          for platform in ("macos", "iosSimulator")):
        return False
    return (
        value.get("schemaVersion") == "weave.native-qualification/v2"
        and value.get("candidateCommit") == sha
        and value.get("sourceTree") == tree
        and value.get("specificationCommit") == spec
        and native_toolchain_recorded(value.get("toolchain"))
        and value.get("macos", {}).get("status") == "passed"
        and value.get("iosSimulator", {}).get("status") == "passed"
        and value.get("iosSimulator", {}).get("rustTarget") == "aarch64-apple-ios-sim"
        and dual_ios_collaboration_passed(value.get("iosSimulator", {}).get("collaboration"))
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
    # Select the latest relevant attempt before checking producer trust: never fall
    # back to an older successful run when a newer attempt is failed or untrusted.
    if run.get("head_sha") != sha or run.get("head_branch") != "dogfood":
        raise ValueError("Latest physical preparation must run from the exact protected Dogfood commit")
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
