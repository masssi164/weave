#!/usr/bin/env python3
"""Build the provenance-bound downstream Keycloak 26.7 runtime exactly once."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import xml.etree.ElementTree as element_tree
from datetime import datetime, timezone
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
UPSTREAM_COMMIT = "6c73e3027811d9c7b22683edd825e839272e9547"
ARCHIVE_SHA256 = "32267c4f45db91874c46a097415c336d137ee184d25c3481a513905a92669186"
STOCK_SERVICES_SHA256 = "052169f7907a21f4e26679bca5c7365627db91b071a7a2fcaeee00230e6b1419"
STOCK_KEYCLOAK_REFERENCE = (
    "quay.io/keycloak/keycloak@"
    "sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13"
)
ARCHIVE_URL = f"https://github.com/keycloak/keycloak/archive/{UPSTREAM_COMMIT}.tar.gz"
SPEC_COMMIT = "d44ca90a1010616c9430fe0b45cdf0876d507774"
PATCH_RELATIVE = Path(
    "infra/weave-workspace/keycloak-runtime/patches/"
    "weave-workload-registration.patch"
)
PATCHED_PATHS = (
    "services/src/main/java/org/keycloak/services/clientpolicy/executor/"
    "WeaveWorkloadClientRegistrationExecutor.java",
    "services/src/main/java/org/keycloak/services/clientpolicy/executor/"
    "WeaveWorkloadClientRegistrationExecutorFactory.java",
    "services/src/main/java/org/keycloak/services/clientregistration/oidc/"
    "OIDCClientRegistrationProvider.java",
    "services/src/main/resources/META-INF/services/"
    "org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory",
    "services/src/test/java/org/keycloak/services/clientpolicy/executor/"
    "WeaveWorkloadClientRegistrationExecutorTest.java",
)
DOWNSTREAM_TEST_CLASS = (
    "org.keycloak.services.clientpolicy.executor."
    "WeaveWorkloadClientRegistrationExecutorTest"
)
DOCKERFILE_RELATIVE = Path("infra/weave-workspace/keycloak/Dockerfile.runtime")
SERVICES_JAR = Path("services/target/keycloak-services-26.7.0.jar")
STOCK_SERVICES_PATH = (
    "/opt/keycloak/lib/lib/main/org.keycloak.keycloak-services-26.7.0.jar"
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def run(*command: str, cwd: Path | None = None, capture: bool = False) -> str:
    completed = subprocess.run(
        list(command),
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
    )
    return completed.stdout.strip() if capture else ""


def resolve_candidate(repository: Path, supplied: str | None) -> str:
    head = run(
        "git", "-C", str(repository), "rev-parse", "HEAD", capture=True
    )
    candidate = supplied or head
    if not COMMIT.fullmatch(candidate):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR candidate commit must be an exact lowercase SHA"
        )
    if candidate != head:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR candidate commit differs from the local source HEAD"
        )
    if run(
        "git",
        "-C",
        str(repository),
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
        capture=True,
    ):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR candidate build requires a clean source tree"
        )
    return candidate


def download_archive(target: Path) -> None:
    with urllib.request.urlopen(ARCHIVE_URL, timeout=60) as response:
        if response.status != 200:
            raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR upstream archive unavailable")
        with target.open("wb") as stream:
            shutil.copyfileobj(response, stream)
    if sha256(target) != ARCHIVE_SHA256:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR upstream commit archive digest mismatch"
        )


def extract_archive(archive: Path, target: Path) -> Path:
    with tarfile.open(archive, "r:gz") as bundle:
        members = bundle.getmembers()
        if any(
            member.name.startswith("/")
            or ".." in Path(member.name).parts
            for member in members
        ):
            raise SystemExit(
                "WEAVE_KEYCLOAK_BUILD_ERROR upstream archive has unsafe members"
            )
        bundle.extractall(target, members=members, filter="data")
    source = target / f"keycloak-{UPSTREAM_COMMIT}"
    if not source.is_dir():
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR upstream source root is unavailable"
        )
    return source


def verify_stock_services() -> None:
    run("docker", "pull", STOCK_KEYCLOAK_REFERENCE)
    observed = run(
        "docker",
        "run",
        "--rm",
        "--entrypoint",
        "/usr/bin/sha256sum",
        STOCK_KEYCLOAK_REFERENCE,
        STOCK_SERVICES_PATH,
        capture=True,
    ).split()[0]
    if observed != STOCK_SERVICES_SHA256:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR stock services JAR digest mismatch"
        )


def patch_paths(patch: Path) -> tuple[str, ...]:
    paths: list[str] = []
    for line in patch.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"diff --git a/(\S+) b/(\S+)", line)
        if match:
            if match.group(1) != match.group(2):
                raise SystemExit(
                    "WEAVE_KEYCLOAK_BUILD_ERROR patch contains a path-changing delta"
                )
            paths.append(match.group(1))
    observed = tuple(paths)
    if observed != PATCHED_PATHS:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR patch changed-path allowlist mismatch"
        )
    return observed


def downstream_test_count(source: Path) -> int:
    report = (
        source
        / "services/target/surefire-reports"
        / f"TEST-{DOWNSTREAM_TEST_CLASS}.xml"
    )
    if not report.is_file():
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR downstream policy test report is absent"
        )
    root = element_tree.parse(report).getroot()
    tests = int(root.attrib.get("tests", "0"))
    failures = int(root.attrib.get("failures", "0"))
    errors = int(root.attrib.get("errors", "0"))
    skipped = int(root.attrib.get("skipped", "0"))
    if tests < 4 or failures != 0 or errors != 0 or skipped != 0:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR downstream policy tests did not pass completely"
        )
    return tests


def build_services(
    repository: Path, temporary: Path
) -> tuple[Path, str, str, tuple[str, ...], int]:
    patch = repository / PATCH_RELATIVE
    if not patch.is_file():
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR canonical patch is unavailable")
    patch_digest = sha256(patch)
    changed_paths = patch_paths(patch)
    archive = temporary / "keycloak.tar.gz"
    download_archive(archive)
    source = extract_archive(archive, temporary)
    run("git", "apply", "--check", str(patch), cwd=source)
    run("git", "apply", str(patch), cwd=source)
    build_environment = os.environ.copy()
    build_environment["SOURCE_DATE_EPOCH"] = "946684800"
    completed = subprocess.run(
        [
            str(source / "mvnw"),
            "-pl",
            "services",
            "-am",
            "-DskipTestsuite",
            "-DskipExamples",
            f"-Dtest={DOWNSTREAM_TEST_CLASS}",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "-Dproject.build.outputTimestamp=2000-01-01T00:00:00Z",
            "package",
        ],
        cwd=source,
        env=build_environment,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if completed.returncode != 0:
        diagnostic = [
            line.replace(str(source), "<keycloak-source>")
            for line in completed.stdout.splitlines()
            if "[ERROR]" in line
        ][-12:]
        for line in diagnostic:
            print(line, file=sys.stderr)
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR patched Keycloak services compilation failed"
        )
    test_count = downstream_test_count(source)
    services = source / SERVICES_JAR
    if not services.is_file():
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR patched services JAR is absent")
    patched_digest = sha256(services)
    if patched_digest == STOCK_SERVICES_SHA256:
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR downstream services JAR is unchanged")
    listing = run("jar", "tf", str(services), capture=True)
    required_entries = (
        "org/keycloak/services/clientpolicy/executor/"
        "WeaveWorkloadClientRegistrationExecutor.class",
        "org/keycloak/services/clientpolicy/executor/"
        "WeaveWorkloadClientRegistrationExecutorFactory.class",
    )
    if any(entry not in listing.splitlines() for entry in required_entries):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR built-in policy classes are absent"
        )
    return services, patch_digest, patched_digest, changed_paths, test_count


def build_image(
    repository: Path,
    candidate: str,
    services: Path,
    patch_digest: str,
    patched_digest: str,
    temporary: Path,
) -> tuple[str, str]:
    context = temporary / "image"
    context.mkdir(mode=0o700)
    shutil.copyfile(services, context / services.name)
    created = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace(
        "+00:00", "Z"
    )
    tag = f"weave-keycloak-runtime:{candidate[:12]}"
    build_arguments = {
        "WEAVE_KEYCLOAK_BASE": STOCK_KEYCLOAK_REFERENCE,
        "WEAVE_IMAGE_CREATED": created,
        "WEAVE_IMAGE_REVISION": candidate,
        "WEAVE_IMAGE_VERSION": f"26.7.0-weave.{candidate[:12]}",
        "WEAVE_SPEC_COMMIT": SPEC_COMMIT,
        "WEAVE_SPEC_DIGEST": (
            "sha256:"
            + hashlib.sha256(
                (repository / "specs/weave-specs.lock.json").read_bytes()
            ).hexdigest()
        ),
        "WEAVE_KEYCLOAK_UPSTREAM_COMMIT": UPSTREAM_COMMIT,
        "WEAVE_KEYCLOAK_ARCHIVE_SHA256": ARCHIVE_SHA256,
        "WEAVE_KEYCLOAK_STOCK_SERVICES_SHA256": STOCK_SERVICES_SHA256,
        "WEAVE_KEYCLOAK_PATCH_SHA256": patch_digest,
        "WEAVE_PATCHED_SERVICES_SHA256": patched_digest,
    }
    command = [
        "docker",
        "build",
        "--pull=false",
        "--tag",
        tag,
        "--file",
        str(repository / DOCKERFILE_RELATIVE),
    ]
    for name, value in build_arguments.items():
        command.extend(["--build-arg", f"{name}={value}"])
    command.append(str(context))
    run(*command)
    image_id = run(
        "docker", "image", "inspect", tag, "--format", "{{.Id}}", capture=True
    )
    if not IMAGE_ID.fullmatch(image_id):
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR candidate image ID is invalid")
    return tag, image_id


def prepare_build_context(
    context: Path,
    services: Path,
    dockerfile: Path,
) -> None:
    if context.exists():
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR prepared build context already exists"
        )
    context.mkdir(parents=True, mode=0o700)
    shutil.copyfile(services, context / services.name)
    os.chmod(context / services.name, 0o600)
    shutil.copyfile(dockerfile, context / "Dockerfile.runtime")
    os.chmod(context / "Dockerfile.runtime", 0o600)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate-commit")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--prepare-context", type=Path)
    args = parser.parse_args()
    repository = args.root.resolve()
    candidate = resolve_candidate(repository, args.candidate_commit)
    verify_stock_services()
    with tempfile.TemporaryDirectory(prefix="weave-keycloak-build-") as directory:
        temporary = Path(directory)
        services, patch_digest, patched_digest, changed_paths, test_count = build_services(
            repository, temporary
        )
        if args.prepare_context:
            prepare_build_context(
                args.prepare_context.resolve(),
                services,
                repository / DOCKERFILE_RELATIVE,
            )
            tag = None
            image_id = None
        else:
            tag, image_id = build_image(
                repository,
                candidate,
                services,
                patch_digest,
                patched_digest,
                temporary,
            )
    evidence = {
        "schemaVersion": "weave.downstream-keycloak-image.v1",
        "evidenceForCandidateCommit": candidate,
        "specificationCommit": SPEC_COMMIT,
        "keycloakVersion": "26.7.0",
        "upstreamCommit": UPSTREAM_COMMIT,
        "upstreamArchiveSha256": ARCHIVE_SHA256,
        "stockReference": STOCK_KEYCLOAK_REFERENCE,
        "stockServicesJarSha256": STOCK_SERVICES_SHA256,
        "patchSha256": patch_digest,
        "patchedPaths": list(changed_paths),
        "patchedServicesJarSha256": patched_digest,
        "downstreamPolicyTestClass": DOWNSTREAM_TEST_CLASS,
        "downstreamPolicyTestCount": test_count,
        "imageTag": tag,
        "imageId": image_id,
        "preparedContext": args.prepare_context is not None,
        "providerId": "weave-workload-client-registration-enforcer",
        "providerPackaging": "built-in-keycloak-services",
        "containsSecretValues": False,
        "supportSafe": True,
    }
    if args.output:
        atomic_write(args.output.resolve(), evidence)
    print(args.prepare_context.resolve() if args.prepare_context else image_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
