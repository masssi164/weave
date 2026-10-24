#!/usr/bin/env python3
"""Content-addressed hosted build artifacts; this module never proves live acceptance."""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import re
import shutil
import stat
import subprocess
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONFIG = "tools/build_artifacts.inputs.json"
DEFAULT_COMPONENTS = ("runtime-linux-amd64", "ios-simulator", "ios-device")
SCENARIO = "fixtures/member-client-baseline/v1.json"
SHA = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
TRUSTED_REFS = {"refs/heads/dev", "refs/heads/dogfood", "refs/heads/main"}
TRUSTED_WORKFLOWS = {
    ".github/workflows/build-artifacts.yml",
    ".github/workflows/live-stack-e2e.yml",
    ".github/workflows/macos-manual.yml",
}
MAX_ARCHIVE = 6 * 1024**3
MAX_EXPANDED = 16 * 1024**3


class ArtifactError(RuntimeError):
    """A bounded artifact error without URLs, credentials or raw remote bodies."""


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def digest_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def digest_file(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return "sha256:" + result.hexdigest()


def git(root: Path, *arguments: str) -> bytes:
    return subprocess.check_output(["git", *arguments], cwd=root, stderr=subprocess.DEVNULL)


def candidate_identity(root: Path, candidate: str = "HEAD") -> dict:
    commit = git(root, "rev-parse", candidate + "^{commit}").decode().strip()
    tree = git(root, "rev-parse", commit + "^{tree}").decode().strip()
    lock = json.loads(git(root, "show", commit + ":specs/weave-specs.lock.json"))
    return {"candidateCommit": commit, "sourceTree": tree,
            "specificationCommit": lock["specCorpus"]["gitCommit"]}


def scenario_digest(root: Path, candidate: str = "HEAD") -> str:
    # Match member_test_scenario.scenario_sha256: formatting is not scenario drift.
    return digest_bytes(canonical(json.loads(git(root, "show", candidate + ":" + SCENARIO))))


def configuration(root: Path, candidate: str = "HEAD") -> dict:
    config = json.loads(git(root, "show", candidate + ":" + CONFIG))
    if config.get("schemaVersion") != "weave.build-inputs/v1":
        raise ArtifactError("unsupported-build-input-schema")
    return config


def component_configuration(root: Path, component: str, candidate: str = "HEAD") -> dict:
    try:
        return configuration(root, candidate)["components"][component]
    except KeyError:
        raise ArtifactError("unknown-component") from None


def input_digest(root: Path, component: str, candidate: str = "HEAD") -> str:
    """Hash tracked dependency contents, paths and executable bits, never the whole commit.

    Corpus/profile revisions remain composition/test inputs unless they affect generated
    production code. Generated HTTP contracts and all compiler/lock/recipe inputs are
    explicit dependencies. Deletion and rename alter the full input set as well.
    """
    config = configuration(root, candidate)
    definition = component_configuration(root, component, candidate)
    patterns = config["sharedInputs"] + definition["inputs"]
    entries = []
    for record in git(root, "ls-tree", "-rz", "--full-tree", candidate).split(b"\0"):
        if not record:
            continue
        header, name = record.split(b"\t", 1)
        path = name.decode("utf-8")
        if any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns):
            mode, kind, blob = header.decode().split()
            if kind != "blob" or mode not in {"100644", "100755", "120000"}:
                raise ArtifactError("unsupported-build-input-kind")
            entries.append([path, mode, blob])
    if not entries:
        raise ArtifactError("empty-build-inputs")
    return digest_bytes(canonical({"schema": config["schemaVersion"], "component": component,
                                   "definition": definition, "files": sorted(entries)}))


def artifact_name(component: str, input_hash: str) -> str:
    if not DIGEST.fullmatch(input_hash):
        raise ArtifactError("invalid-input-digest")
    return "weave-build-" + component + "-" + input_hash.removeprefix("sha256:")


def safe_relative(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if (not value or value != path.as_posix() or path.is_absolute()
            or any(part in {"..", ".", ""} for part in value.split("/"))
            or "\\" in value or "\0" in value or any(ord(ch) < 32 for ch in value)):
        raise ArtifactError("unsafe-archive-path")
    return path


def tree_digest(root: Path, *, exclude: frozenset[str] = frozenset()) -> str:
    rows = []
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root).as_posix()
        if relative in exclude:
            continue
        safe_relative(relative)
        mode = path.lstat().st_mode
        if stat.S_ISLNK(mode):
            target = os.readlink(path)
            resolved = posixpath.normpath(posixpath.join(posixpath.dirname(relative), target))
            if target.startswith("/") or resolved == ".." or resolved.startswith("../"):
                raise ArtifactError("unsafe-payload-symlink")
            rows.append([relative, "link", target])
        elif stat.S_ISDIR(mode):
            rows.append([relative, "directory", stat.S_IMODE(mode)])
        elif stat.S_ISREG(mode):
            rows.append([relative, "file", stat.S_IMODE(mode), digest_file(path)])
        else:
            raise ArtifactError("unsupported-payload-file")
    if not rows:
        raise ArtifactError("empty-payload")
    return digest_bytes(canonical(rows))


def package_payload(payload: Path, archive: Path) -> str:
    # Preserve executable intent while removing host umask/group-write differences.
    for path in payload.rglob("*"):
        if not path.is_symlink():
            path.chmod(0o755 if path.is_dir() or path.stat().st_mode & 0o111 else 0o644)
    result = tree_digest(payload)
    with tarfile.open(archive, "w:gz", format=tarfile.PAX_FORMAT) as target:
        for path in sorted(payload.rglob("*")):
            # Do not emit filesystem-dependent hard links; app symlinks stay intact.
            target.inodes.clear()
            info = target.gettarinfo(str(path), arcname=path.relative_to(payload).as_posix())
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            if info.isfile():
                with path.open("rb") as stream:
                    target.addfile(info, stream)
            else:
                target.addfile(info)
    return result


def extract_payload(archive: Path, destination: Path) -> None:
    if destination.exists() or destination.is_symlink():
        raise ArtifactError("payload-destination-already-exists")
    with tarfile.open(archive, "r:gz") as source:
        members = source.getmembers()
        if len(members) > 100000 or sum(item.size for item in members) > MAX_EXPANDED:
            raise ArtifactError("payload-size-limit")
        indexed = {}
        for item in members:
            path = safe_relative(item.name)
            if item.name in indexed or not (item.isfile() or item.isdir() or item.issym()):
                raise ArtifactError("unsupported-or-duplicate-archive-member")
            if item.mode & 0o7000:
                raise ArtifactError("privileged-archive-mode")
            if item.issym():
                resolved = posixpath.normpath(posixpath.join(str(path.parent), item.linkname))
                if (item.linkname.startswith("/") or "\\" in item.linkname or resolved == ".."
                        or resolved.startswith("../") or "\0" in item.linkname):
                    raise ArtifactError("unsafe-archive-symlink")
            indexed[item.name] = item
        for item in members:
            if any(str(parent) in indexed and not indexed[str(parent)].isdir()
                   for parent in PurePosixPath(item.name).parents if str(parent) != "."):
                raise ArtifactError("archive-write-through-link")
        destination.mkdir(mode=0o700, parents=True)
        # All paths/types/link targets and parents were checked before the first write.
        source.extractall(destination, members=members, filter="data")
        # Python's data filter deliberately leaves directory modes to the host
        # umask. Restore validated archive modes so private Mac operators (077)
        # verify the same executable/directory tree as hosted builders (022).
        for item in members:
            if item.isdir():
                (destination / item.name).chmod(item.mode & 0o777)


def validate_manifest(root: Path, manifest: dict, component: str, candidate: str = "HEAD") -> dict:
    definition = component_configuration(root, component, candidate)
    expected = {"schemaVersion": "weave.build-artifact/v1", "component": component,
                "inputDigest": input_digest(root, component, candidate), "target": definition["target"],
                "buildMode": definition["buildMode"], "toolchain": definition["toolchain"]}
    if any(manifest.get(key) != value for key, value in expected.items()):
        raise ArtifactError("artifact-input-or-target-mismatch")
    if (not SHA.fullmatch(manifest.get("sourceCommit", "")) or not SHA.fullmatch(manifest.get("sourceTree", ""))
            or not isinstance(manifest.get("buildNumber"), str) or not manifest["buildNumber"].isdigit()):
        raise ArtifactError("invalid-build-identity")
    payload = manifest.get("payload", {})
    if (payload.get("filename") != "payload.tar.gz" or not DIGEST.fullmatch(payload.get("sha256", ""))
            or not DIGEST.fullmatch(payload.get("treeDigest", "")) or type(payload.get("bytes")) is not int
            or not 0 < payload["bytes"] <= MAX_ARCHIVE):
        raise ArtifactError("invalid-payload-identity")
    return manifest


def verify_component(root: Path, directory: Path, candidate: str = "HEAD", component: str | None = None) -> dict:
    """Verify a previously authenticated, private extracted component against this candidate.

    Download/producer authentication is done by resolve/consume. This local check also
    re-hashes the payload tree, so a modified local bundle cannot reuse its old receipt.
    """
    if (directory.is_symlink() or not directory.is_dir() or (directory / "manifest.json").is_symlink()
            or (directory / "payload").is_symlink()):
        raise ArtifactError("unsafe-component-directory")
    manifest = json.loads((directory / "manifest.json").read_text())
    component = component or manifest.get("component")
    validate_manifest(root, manifest, component, candidate)
    if tree_digest(directory / "payload") != manifest["payload"]["treeDigest"]:
        raise ArtifactError("extracted-payload-digest-mismatch")
    return manifest


def publish_manifest(root: Path, component: str, payload: Path, output: Path) -> dict:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise ArtifactError("hosted-producer-required")
    ref = os.environ.get("GITHUB_REF", "")
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    workflow_ref = os.environ.get("GITHUB_WORKFLOW_REF", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    workflow = workflow_ref.removeprefix(repository + "/").split("@", 1)[0]
    if ref not in TRUSTED_REFS or event not in {"push", "workflow_dispatch"} or workflow not in TRUSTED_WORKFLOWS:
        raise ArtifactError("protected-producer-context-required")
    identity = candidate_identity(root)
    if identity["candidateCommit"] != os.environ.get("GITHUB_SHA"):
        raise ArtifactError("producer-source-mismatch")
    if git(root, "diff", "--name-only", "HEAD").strip():
        raise ArtifactError("producer-tracked-source-modified")
    definition = component_configuration(root, component)
    output.mkdir(parents=True, exist_ok=True)
    archive = output / "payload.tar.gz"
    tree = package_payload(payload, archive)
    manifest = {"schemaVersion": "weave.build-artifact/v1", "component": component,
                "inputDigest": input_digest(root, component), "sourceCommit": identity["candidateCommit"],
                "sourceTree": identity["sourceTree"], "target": definition["target"],
                "buildMode": definition["buildMode"], "toolchain": definition["toolchain"],
                "buildNumber": os.environ["GITHUB_RUN_NUMBER"],
                "producer": {"repository": repository, "runId": int(os.environ["GITHUB_RUN_ID"]),
                             "runAttempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
                             "workflowPath": workflow, "jobName": "Build component (" + component + ")",
                             "ref": ref, "event": event},
                "payload": {"filename": archive.name, "sha256": digest_file(archive),
                            "bytes": archive.stat().st_size, "treeDigest": tree}}
    validate_manifest(root, manifest, component)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GitHub:
    def __init__(self, repository: str, token: str):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not token:
            raise ArtifactError("github-repository-and-token-required")
        self.repository, self.token = repository, token
        self.prefix = "/repos/" + repository

    def request(self, path: str) -> urllib.request.Request:
        return urllib.request.Request("https://api.github.com" + path, headers={
            "Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28"})

    def json(self, path: str) -> dict:
        try:
            with urllib.request.urlopen(self.request(path), timeout=60) as response:
                return json.load(response)
        except (urllib.error.URLError, ValueError):
            raise ArtifactError("github-metadata-unavailable") from None

    def pages(self, path: str, key: str):
        separator = "&" if "?" in path else "?"
        for page in range(1, 101):
            values = self.json(path + separator + "per_page=100&page=" + str(page)).get(key)
            if not isinstance(values, list):
                raise ArtifactError("github-pagination-invalid")
            yield from values
            if len(values) < 100:
                return
        raise ArtifactError("github-pagination-limit")

    def download(self, artifact_id: int, destination: Path) -> None:
        # Do not forward the GitHub token to the signed storage URL.
        opener = urllib.request.build_opener(NoRedirect)
        try:
            try:
                response = opener.open(self.request(self.prefix + f"/actions/artifacts/{artifact_id}/zip"), timeout=60)
            except urllib.error.HTTPError as error:
                if error.code != 302:
                    raise
                location = error.headers.get("Location", "")
                parsed = urllib.parse.urlsplit(location)
                if (parsed.scheme != "https" or parsed.username or parsed.password
                        or not (parsed.hostname or "").endswith((".blob.core.windows.net", ".githubusercontent.com", ".github.com"))):
                    raise ArtifactError("untrusted-artifact-download-host")
                response = urllib.request.urlopen(location, timeout=60)
            total = 0
            with response, destination.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > MAX_ARCHIVE:
                        raise ArtifactError("artifact-download-size-limit")
                    output.write(chunk)
        except urllib.error.URLError:
            raise ArtifactError("artifact-download-unavailable") from None

    def trusted_producer(self, artifact: dict, manifest: dict, runner: str) -> None:
        producer = manifest.get("producer", {})
        workflow_run = artifact.get("workflow_run", {})
        if (producer.get("repository") != self.repository or type(producer.get("runId")) is not int
                or type(producer.get("runAttempt")) is not int or producer["runAttempt"] < 1
                or producer["runId"] != workflow_run.get("id")
                or workflow_run.get("head_sha") != manifest["sourceCommit"]
                or producer.get("ref") not in TRUSTED_REFS or producer.get("event") not in {"push", "workflow_dispatch"}
                or producer.get("workflowPath") not in TRUSTED_WORKFLOWS):
            raise ArtifactError("artifact-producer-untrusted")
        run = self.json(self.prefix + "/actions/runs/" + str(producer["runId"]))
        if (run.get("repository", {}).get("full_name") != self.repository
                or run.get("head_repository", {}).get("full_name") != self.repository
                or run.get("head_sha") != manifest["sourceCommit"] or run.get("event") != producer["event"]
                or "refs/heads/" + run.get("head_branch", "") != producer["ref"]
                or run.get("path", "").split("@", 1)[0] != producer["workflowPath"]):
            raise ArtifactError("artifact-producer-run-mismatch")
        jobs = list(self.pages(self.prefix + f"/actions/runs/{producer['runId']}/attempts/{producer['runAttempt']}/jobs", "jobs"))
        expected = "Build component (" + manifest["component"] + ")"
        matching = [job for job in jobs if job.get("name", "").split(" / ")[-1] == expected]
        if (producer.get("jobName") != expected or len(matching) != 1 or matching[0].get("conclusion") != "success"
                or "self-hosted" in matching[0].get("labels", []) or runner not in matching[0].get("labels", [])):
            raise ArtifactError("artifact-build-job-not-successful-hosted-job")


def read_download(root: Path, github: GitHub, artifact: dict, component: str,
                  destination: Path | None = None, candidate: str = "HEAD") -> dict:
    definition = component_configuration(root, component, candidate)
    if (artifact.get("expired") is not False or type(artifact.get("id")) is not int
            or artifact.get("name") != artifact_name(component, input_digest(root, component, candidate))
            or not 0 < artifact.get("size_in_bytes", 0) <= MAX_ARCHIVE):
        raise ArtifactError("artifact-metadata-invalid")
    with tempfile.TemporaryDirectory(prefix="weave-build-download-") as name:
        temporary = Path(name)
        archive = temporary / "artifact.zip"
        github.download(artifact["id"], archive)
        if not DIGEST.fullmatch(artifact.get("digest", "")) or digest_file(archive) != artifact["digest"]:
            raise ArtifactError("github-artifact-digest-mismatch")
        with zipfile.ZipFile(archive) as source:
            entries = source.infolist()
            if (sorted(entry.filename for entry in entries) != ["manifest.json", "payload.tar.gz"]
                    or any(entry.flag_bits & 1 or stat.S_ISLNK(entry.external_attr >> 16) for entry in entries)
                    or sum(entry.file_size for entry in entries) > MAX_ARCHIVE):
                raise ArtifactError("unexpected-github-artifact-files")
            if source.getinfo("manifest.json").file_size > 65536:
                raise ArtifactError("manifest-size-limit")
            manifest = validate_manifest(root, json.loads(source.read("manifest.json")), component, candidate)
            github.trusted_producer(artifact, manifest, definition["runner"])
            with source.open("payload.tar.gz") as payload, (temporary / "payload.tar.gz").open("wb") as output:
                shutil.copyfileobj(payload, output, 1024 * 1024)
        payload = temporary / "payload.tar.gz"
        if payload.stat().st_size != manifest["payload"]["bytes"] or digest_file(payload) != manifest["payload"]["sha256"]:
            raise ArtifactError("artifact-payload-digest-mismatch")
        if destination is not None:
            if destination.exists() or destination.is_symlink():
                # Reuse is allowed only for the exact already-verified local payload.
                existing = verify_component(root, destination, candidate, component)
                if existing != manifest:
                    raise ArtifactError("component-destination-conflict")
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                with tempfile.TemporaryDirectory(prefix=".weave-component-", dir=destination.parent) as staging:
                    directory = Path(staging) / "component"
                    directory.mkdir(mode=0o700)
                    extract_payload(payload, directory / "payload")
                    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
                    verify_component(root, directory, candidate, component)
                    directory.rename(destination)
        return manifest


def find_artifact(root: Path, github: GitHub, component: str, candidate: str = "HEAD",
                  destination: Path | None = None) -> tuple[dict, dict] | None:
    name = artifact_name(component, input_digest(root, component, candidate))
    candidates = github.pages(github.prefix + "/actions/artifacts?name=" + urllib.parse.quote(name), "artifacts")
    for artifact in candidates:
        if artifact.get("name") != name or artifact.get("expired") is not False:
            continue
        try:
            return artifact, read_download(root, github, artifact, component, destination, candidate)
        except (ArtifactError, ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, tarfile.TarError):
            # Missing/expired/untrusted/corrupt cache entries never become acceptance.
            # A trusted fresh build is the fallback, not a permissive cache restore.
            continue
    return None


def composition_entry(artifact: dict, manifest: dict) -> dict:
    return {"artifactId": artifact["id"], "artifactName": artifact["name"],
            "producerRunId": manifest["producer"]["runId"], "producerRunAttempt": manifest["producer"]["runAttempt"],
            **{key: manifest[key] for key in ("inputDigest", "sourceCommit", "sourceTree", "target", "buildMode", "buildNumber")},
            "payloadSha256": manifest["payload"]["sha256"]}


def composition_digest(composition: dict) -> str:
    return digest_bytes(canonical({key: value for key, value in composition.items() if key != "compositionDigest"}))


def validate_composition(root: Path, composition: dict, candidate: str = "HEAD") -> dict:
    identity = candidate_identity(root, candidate)
    if (composition.get("schemaVersion") != "weave.candidate-artifacts/v1"
            or composition.get("compositionDigest") != composition_digest(composition)
            or any(composition.get(key) != value for key, value in identity.items())
            or not isinstance(composition.get("components"), dict) or not composition["components"]
            or (composition.get("scenarioDigest") is not None and not DIGEST.fullmatch(composition["scenarioDigest"]))):
        raise ArtifactError("candidate-composition-mismatch")
    if composition.get("scenarioDigest") is not None and composition["scenarioDigest"] != scenario_digest(root, candidate):
        raise ArtifactError("candidate-scenario-mismatch")
    for component, entry in composition["components"].items():
        definition = component_configuration(root, component, candidate)
        input_hash = input_digest(root, component, candidate)
        if (not isinstance(entry, dict) or type(entry.get("artifactId")) is not int or entry["artifactId"] < 1
                or type(entry.get("producerRunId")) is not int or entry["producerRunId"] < 1
                or type(entry.get("producerRunAttempt")) is not int or entry["producerRunAttempt"] < 1
                or entry.get("inputDigest") != input_hash
                or entry.get("artifactName") != artifact_name(component, input_hash)
                or entry.get("target") != definition["target"] or entry.get("buildMode") != definition["buildMode"]
                or not SHA.fullmatch(entry.get("sourceCommit", "")) or not SHA.fullmatch(entry.get("sourceTree", ""))
                or not isinstance(entry.get("buildNumber"), str) or not entry["buildNumber"].isdigit()
                or not DIGEST.fullmatch(entry.get("payloadSha256", ""))):
            raise ArtifactError("composition-component-metadata-mismatch")
    return composition


def resolve(root: Path, github: GitHub, components: list[str], destination: Path | None,
            candidate: str = "HEAD", scenario: Path | None = None) -> dict:
    if scenario is not None and (scenario.resolve() != (root / SCENARIO).resolve()
            or digest_bytes(canonical(json.loads(scenario.read_text()))) != scenario_digest(root, candidate)):
        raise ArtifactError("candidate-scenario-source-mismatch")
    result = {"schemaVersion": "weave.candidate-artifacts/v1", **candidate_identity(root, candidate),
              "scenarioDigest": scenario_digest(root, candidate) if scenario else None, "components": {}}
    for component in components:
        found = find_artifact(root, github, component, candidate,
                              destination / component if destination else None)
        if found is None:
            raise ArtifactError("required-component-unavailable-" + component)
        result["components"][component] = composition_entry(*found)
    result["compositionDigest"] = composition_digest(result)
    validate_composition(root, result, candidate)
    return result


def consume(root: Path, github: GitHub, composition: dict, destination: Path, candidate: str = "HEAD",
            components: list[str] | None = None) -> None:
    validate_composition(root, composition, candidate)
    selected = components if components is not None else list(composition["components"])
    if not selected or len(set(selected)) != len(selected) or any(item not in composition["components"] for item in selected):
        raise ArtifactError("composition-required-component-missing")
    for component in selected:
        entry = composition["components"][component]
        artifact = github.json(github.prefix + "/actions/artifacts/" + str(entry["artifactId"]))
        manifest = read_download(root, github, artifact, component, destination / component, candidate)
        if composition_entry(artifact, manifest) != entry:
            raise ArtifactError("composition-component-mismatch")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("plan", "resolve", "consume", "verify", "inputs"))
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--candidate", default="HEAD")
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", "masssi164/weave"))
    parser.add_argument("--components")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--directory", type=Path)
    parser.add_argument("--composition", type=Path)
    parser.add_argument("--scenario", type=Path)
    args = parser.parse_args()
    components = args.components.split(",") if args.components else list(DEFAULT_COMPONENTS)
    for component in components:
        component_configuration(args.root, component, args.candidate)
    if args.operation == "inputs":
        result = {component: input_digest(args.root, component, args.candidate) for component in components}
    elif args.operation == "verify":
        if not args.directory:
            parser.error("--directory is required")
        result = {component: verify_component(args.root, args.directory / component, args.candidate, component)
                  for component in components}
    else:
        github = GitHub(args.repository, os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN", ""))
        if args.operation == "plan":
            matrix = []
            reused = {}
            for component in components:
                found = find_artifact(args.root, github, component, args.candidate)
                if found:
                    reused[component] = composition_entry(*found)
                else:
                    definition = component_configuration(args.root, component, args.candidate)
                    matrix.append({"component": component, "runner": definition["runner"],
                                   "name": artifact_name(component, input_digest(args.root, component, args.candidate))})
            result = {"matrix": {"include": matrix}, "needsBuild": bool(matrix), "reused": reused}
            if os.environ.get("GITHUB_OUTPUT"):
                with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                    output.write("matrix=" + json.dumps(result["matrix"], separators=(",", ":")) + "\n")
                    output.write("needs-build=" + str(bool(matrix)).lower() + "\n")
        elif args.operation == "resolve":
            result = resolve(args.root, github, components, args.directory, args.candidate, args.scenario)
        else:
            if not args.composition or not args.directory:
                parser.error("--composition and --directory are required")
            result = json.loads(args.composition.read_text())
            consume(args.root, github, result, args.directory, args.candidate,
                    components if args.components else None)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n")
    else:
        print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ArtifactError, ValueError, OSError, subprocess.CalledProcessError) as error:
        # urllib errors are normalized before this boundary; never print signed URLs.
        print("WEAVE_BUILD_ARTIFACT_ERROR " + (str(error) if isinstance(error, ArtifactError) else type(error).__name__))
        raise SystemExit(2) from None
