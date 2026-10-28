#!/usr/bin/env python3
"""Read-only, exact-candidate registry verification on a disposable hosted runner."""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener, parse_http_list
import zipfile

REPOSITORY = "masssi164/weave"
BRANCH = "refs/heads/diagnostic/verify-first-ghcr-publication-20261028"
SPECIFICATION = "cf249847c7f48ccc51507c6d06f5576e7a332f06"
MEDIA_TYPES = ", ".join(("application/vnd.oci.image.manifest.v1+json",
                         "application/vnd.oci.image.index.v1+json",
                         "application/vnd.docker.distribution.manifest.v2+json",
                         "application/vnd.docker.distribution.manifest.list.v2+json"))
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")


class VerificationFailure(Exception):
    pass


def require(condition, reason):
    if not condition:
        raise VerificationFailure(reason)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request_json(url, headers=None):
    """Never print provider bodies, credentials, headers or tokens."""
    opener = build_opener(NoRedirect(), ProxyHandler({}))
    try:
        response = opener.open(Request(url, headers=headers or {}), timeout=30)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read(2 * 1024 * 1024 + 1)
        status, challenge = response.code, response.headers.get("WWW-Authenticate", "")
    require(len(raw) <= 2 * 1024 * 1024, "response-size-rejected")
    return status, challenge, raw, json.loads(raw)


def denied(status, value):
    errors = value.get("errors") if isinstance(value, dict) else None
    return status in {401, 403} and isinstance(errors, list) and bool(errors) and all(
        isinstance(error, dict) and error.get("code") in {"UNAUTHORIZED", "DENIED"}
        for error in errors)


def anonymous_access(package, digest):
    """Complete anonymous Bearer authentication, independently of Docker credentials."""
    require(re.fullmatch(r"weave-private-ci-[a-z-]+", package) and DIGEST.fullmatch(digest),
            "registry-target-rejected")
    repository = "masssi164/" + package
    manifest = "https://ghcr.io/v2/" + repository + "/manifests/" + digest
    headers = {"Accept": MEDIA_TYPES}
    status, challenge, raw, value = request_json(manifest, headers)
    if status != 200:
        require(status == 401, "anonymous-manifest-response-unproved")
        scheme, separator, parameters = challenge.partition(" ")
        require(separator and scheme.lower() == "bearer", "anonymous-challenge-unproved")
        fields = {}
        for parameter in parse_http_list(parameters):
            key, equals, quoted = parameter.partition("=")
            key, quoted = key.strip(), quoted.strip()
            require(equals and key not in fields and len(quoted) >= 2
                    and quoted.startswith('"') and quoted.endswith('"'), "anonymous-challenge-unproved")
            fields[key] = quoted[1:-1]
        require(fields == {"realm": "https://ghcr.io/token", "service": "ghcr.io",
                           "scope": "repository:" + repository + ":pull"}, "anonymous-challenge-unproved")
        query = urlencode({"service": "ghcr.io", "scope": fields["scope"]})
        status, _, _, value = request_json("https://ghcr.io/token?" + query)
        if denied(status, value):
            return "denied", status
        require(status == 200 and isinstance(value, dict), "anonymous-token-response-unproved")
        token = value.get("token") or value.get("access_token")
        require(isinstance(token, str) and 0 < len(token) <= 65536
                and not any(char in token for char in "\r\n\0"), "anonymous-token-invalid")
        status, _, raw, value = request_json(manifest, {**headers, "Authorization": "Bearer " + token})
    if status == 200:
        require("sha256:" + hashlib.sha256(raw).hexdigest() == digest,
                "anonymous-manifest-digest-mismatch")
        return "allowed", status
    require(denied(status, value), "anonymous-access-unproved")
    return "denied", status


def package_visibility(token, packages):
    headers = {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
               "X-GitHub-Api-Version": "2022-11-28"}
    result = {}
    for page in range(1, 11):
        query = urlencode({"package_type": "container", "per_page": 100, "page": page})
        status, _, _, values = request_json("https://api.github.com/users/masssi164/packages?" + query, headers)
        require(status == 200 and isinstance(values, list), "package-metadata-unavailable")
        for item in values:
            if not isinstance(item, dict) or item.get("name") not in packages:
                continue
            require(item["name"] not in result and item.get("package_type") == "container"
                    and item.get("visibility") in {"private", "public"}
                    and item.get("owner", {}).get("login") == "masssi164", "package-metadata-rejected")
            result[item["name"]] = item["visibility"]
        if set(result) == packages:
            return result
        if len(values) < 100:
            break
    raise VerificationFailure("package-metadata-incomplete")


def verify(audit):
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY
            and os.environ.get("GITHUB_REF") == BRANCH
            and os.environ.get("GITHUB_EVENT_NAME") == "push"
            and os.environ.get("VERIFICATION_RUNNER_ENVIRONMENT") == "github-hosted"
            and platform.system() == "Linux" and platform.machine() in {"aarch64", "arm64"},
            "execution-context-rejected")
    candidate = os.environ["VERIFIED_CANDIDATE"]
    tree = os.environ["VERIFIED_SOURCE_TREE"]
    producer = int(os.environ["VERIFIED_PRODUCER_RUN"])
    require(SHA.fullmatch(candidate) and SHA.fullmatch(tree) and producer > 0, "candidate-input-invalid")
    root = Path(os.environ["GITHUB_WORKSPACE"]) / "candidate"
    sys.path.insert(0, str(root / "tools"))
    from build_artifacts import GitHub, candidate_identity, consume, digest_file, git, validate_composition
    from artifact_consumers import require_release_runtime, runtime_definition
    from artifact_runtime_storage import default_ledger
    from runtime_registry_auth import runner_temp
    from runtime_image_registry import NAMES

    audit.update(candidateCommit=candidate, sourceTree=tree, producerRunId=producer, producerRunAttempt=1)
    audit["stage"] = "candidate-identity"
    require(candidate_identity(root, candidate) == {"candidateCommit": candidate, "sourceTree": tree,
                "specificationCommit": SPECIFICATION}
            and git(root, "rev-parse", "HEAD").decode().strip() == candidate
            and not git(root, "diff", "--name-only", "HEAD", "--").strip(), "candidate-identity-rejected")
    require(set(NAMES) == {"backend", "mcp", "keycloak", "runtimeState"}
            and all(name.startswith("ghcr.io/masssi164/weave-private-ci-") for name in NAMES.values()),
            "registry-mapping-rejected")
    packages = {key: name.removeprefix("ghcr.io/masssi164/") for key, name in NAMES.items()}
    token = os.environ.get("GITHUB_TOKEN", "")
    require(bool(token) and not any(char in token for char in "\r\n\0"), "job-token-unavailable")
    github = GitHub(REPOSITORY, token)
    audit["stage"] = "producer"
    deadline = time.monotonic() + 1200
    while True:
        run = github.json(github.prefix + f"/actions/runs/{producer}")
        require(run.get("id") == producer and run.get("run_attempt") == 1
                and run.get("head_sha") == candidate and run.get("head_branch") == "dev"
                and run.get("repository", {}).get("full_name") == REPOSITORY
                and run.get("head_repository", {}).get("full_name") == REPOSITORY
                and run.get("event") == "push" and run.get("path") == ".github/workflows/build-artifacts.yml",
                "producer-identity-rejected")
        if run.get("status") == "completed":
            require(run.get("conclusion") == "success", "producer-failed")
            break
        require(time.monotonic() < deadline, "producer-timeout")
        time.sleep(30)

    with tempfile.TemporaryDirectory(prefix="weave-registry-proof-", dir=runner_temp()) as temporary:
        directory = Path(temporary)
        audit["stage"] = "composition"
        name = f"candidate-artifacts-{candidate}-{producer}-attempt-1"
        artifacts = list(github.pages(github.prefix + f"/actions/runs/{producer}/artifacts", "artifacts"))
        matching = [item for item in artifacts if item.get("name") == name]
        require(len(matching) == 1, "composition-artifact-unavailable")
        artifact = matching[0]
        require(artifact.get("expired") is False and type(artifact.get("id")) is int
                and artifact["id"] > 0 and 0 < artifact.get("size_in_bytes", 0) <= 8 * 1024**2
                and artifact.get("workflow_run", {}).get("id") == producer
                and artifact.get("workflow_run", {}).get("head_sha") == candidate
                and isinstance(artifact.get("digest"), str) and DIGEST.fullmatch(artifact["digest"]),
                "composition-artifact-rejected")
        archive = directory / "composition.zip"
        github.download(artifact["id"], archive)
        require(archive.stat().st_size <= 8 * 1024**2 and digest_file(archive) == artifact["digest"],
                "composition-digest-rejected")
        with zipfile.ZipFile(archive) as bundle:
            entries = bundle.infolist()
            require(len(entries) == 1 and entries[0].filename == "candidate-artifacts.json"
                    and not entries[0].is_dir() and entries[0].file_size <= 1024**2
                    and not entries[0].flag_bits & 1, "composition-archive-rejected")
            composition = json.loads(bundle.read(entries[0]))
        validate_composition(root, composition, candidate)
        require_release_runtime(composition, candidate)
        entry = composition["components"].get("runtime-linux-arm64", {})
        require(entry.get("producerRunId") == producer and entry.get("producerRunAttempt") == 1
                and entry.get("sourceCommit") == candidate and entry.get("sourceTree") == tree,
                "runtime-producer-rejected")
        composition_file = directory / "candidate-artifacts.json"
        composition_file.write_text(json.dumps(composition))
        destination = directory / "components"
        os.environ["WEAVE_CANDIDATE_COMPOSITION"] = str(composition_file)
        os.environ["WEAVE_BUILD_ARTIFACT_ROOT"] = str(destination)
        audit["stage"] = "component-consumption"
        consume(root, github, composition, destination, candidate, components=["runtime-linux-arm64"])
        manifest, receipt, _ = runtime_definition(candidate)
        ledger = default_ledger()
        require(not ledger.exists() and not ledger.is_symlink(), "fresh-consumer-required")
        audit["stage"] = "authenticated-digest-pull"
        environment = {key: value for key, value in os.environ.items()
                       if key not in {"GITHUB_TOKEN", "GH_TOKEN", "GHCR_TOKEN"}}
        code = "import sys; sys.path.insert(0, sys.argv[1]); from artifact_consumers import load_runtime_images; load_runtime_images(sys.argv[2])"
        result = subprocess.run([sys.executable, "-c", code, str(root / "tools"), candidate],
                                cwd=root, env=environment, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=900)
        require(result.returncode == 0, "authenticated-digest-pull-failed")
        audit.update(authenticatedDigestPull="passed", compositionDigest=composition["compositionDigest"],
                     platform=manifest["target"], runtime=manifest["runtime"])
        audit["stage"] = "observed-package-access"
        for component, package in packages.items():
            reference = receipt["images"][component]["reference"]
            prefix = NAMES[component] + "@"
            require(reference.startswith(prefix), "registry-reference-rejected")
            access, status = anonymous_access(package, reference[len(prefix):])
            audit["images"][component] = {"reference": reference, "anonymousAccess": access,
                                          "anonymousHttpStatus": status, "visibility": "unproved"}
        visibility = package_visibility(token, set(packages.values()))
        for component, package in packages.items():
            image = audit["images"][component]
            image["visibility"] = visibility[package]
            require(image["anonymousAccess"] == ("allowed" if visibility[package] == "public" else "denied"),
                    "visibility-access-mismatch")
        audit.update(status="passed", stage="complete", packageVisibility="observed-per-image")


def main():
    audit = {"schemaVersion": "weave.registry-access-verification/v2", "status": "blocked",
             "stage": "preflight", "policy": "owner-approved-public-candidate-fallback",
             "specificationCommit": SPECIFICATION, "authenticatedDigestPull": "unproved",
             "images": {}, "settingsChanged": False, "liveQualification": "not-executed",
             "supportSafe": True, "containsSecretValues": False}
    try:
        verify(audit)
    except VerificationFailure as error:
        audit["reason"] = str(error)
    except Exception:
        audit["reason"] = "verification-command-or-response-failed"
    print(json.dumps(audit, sort_keys=True), flush=True)
    return 0 if audit["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
