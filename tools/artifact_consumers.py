#!/usr/bin/env python3
"""Consume verified hosted packages without compiling on the local runtime runner."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import shlex
import shutil
import subprocess

from build_artifacts import ArtifactError, candidate_identity, verify_component
from artifact_runtime_storage import RuntimeStorageError, record_verified_runtime

ROOT = Path(__file__).resolve().parents[1]
IMAGE = re.compile(r"sha256:[0-9a-f]{64}")


def component(name: str, candidate: str = "HEAD") -> tuple[dict, Path]:
    selected = os.environ.get("WEAVE_BUILD_ARTIFACT_ROOT", "")
    directory = Path(selected)
    if not selected or not directory.is_absolute() or any(path.is_symlink() for path in (directory, *directory.parents)):
        raise ArtifactError("verified-hosted-artifact-root-required")
    directory = directory / name
    manifest = verify_component(ROOT, directory, candidate, name)
    composition = candidate_composition(candidate)
    expected = composition["components"].get(name)
    producer = manifest.get("producer", {})
    actual = {key: manifest[key] for key in ("sourceCommit", "sourceTree", "inputDigest", "target", "buildMode", "buildNumber")}
    actual.update(payloadSha256=manifest["payload"]["sha256"], producerRunId=producer.get("runId"),
                  producerRunAttempt=producer.get("runAttempt"))
    if not isinstance(expected, dict) or any(expected.get(key) != value for key, value in actual.items()):
        raise ArtifactError("consumed-component-differs-from-qualified-composition")
    return manifest, directory / "payload"


def component_evidence(manifest: dict) -> dict:
    return {key: manifest[key] for key in ("component", "sourceCommit", "sourceTree", "inputDigest", "target", "buildMode", "buildNumber")} | {
        "payloadSha256": manifest["payload"]["sha256"]}


def native_bundle(target: str = "ios-simulator", variant: str = "ordinary", candidate: str = "HEAD") -> tuple[dict, Path, Path]:
    if target not in {"ios-simulator", "ios-device"} or variant not in {"ordinary", "collaboration"}:
        raise ArtifactError("unsupported-native-artifact")
    if variant == "collaboration" and target != "ios-simulator":
        raise ArtifactError("collaboration-target-mismatch")
    manifest, payload = component(target, candidate)
    app, products = payload / variant / "Runner.app", payload / "driver/Products"
    if not app.is_dir() or not products.is_dir() or len(list(products.glob("*.xctestrun"))) != 1:
        raise ArtifactError("native-artifact-products-missing")
    info = plistlib.loads((app / "Info.plist").read_bytes())
    if info.get("CFBundleIdentifier") != "com.massimotter.weave" or str(info.get("CFBundleVersion")) != manifest["buildNumber"]:
        raise ArtifactError("native-artifact-build-identity-mismatch")
    return manifest, app, products


def copy_driver_products(products: Path, destination: Path) -> Path:
    """Keep per-actor XCTest inputs private while the immutable build remains shared."""
    if destination.exists() or destination.is_symlink():
        raise ArtifactError("driver-destination-already-exists")
    shutil.copytree(products, destination, symlinks=True)
    plans = list(destination.glob("*.xctestrun"))
    if len(plans) != 1:
        raise ArtifactError("native-driver-plan-ambiguous")
    # Xcode emits __TESTROOT__ references for portable build-for-testing products.
    # A producer checkout reference would be unusable and must not be guessed.
    plan = plistlib.loads(plans[0].read_bytes())
    def inspect(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in {"TestBundlePath", "TestHostPath", "UITargetAppPath"} and isinstance(item, str):
                    if item.startswith("/") and not item.startswith(str(destination) + "/"):
                        raise ArtifactError("nonportable-native-driver-plan")
                inspect(item)
        elif isinstance(value, list):
            for item in value:
                inspect(item)
    inspect(plan)
    return destination


def runtime_definition(candidate: str = "HEAD") -> tuple[dict, dict, Path]:
    manifest, payload = component("runtime-linux-amd64", candidate)
    value = json.loads((payload / "images.json").read_text())
    images = value.get("images", {})
    if (value.get("schemaVersion") != "weave.runtime-images/v1"
            or value.get("sourceCommit") != manifest["sourceCommit"]
            or value.get("platform") != "linux/amd64"
            or not IMAGE.fullmatch(value.get("specDigest", ""))
            or set(images) != {"backend", "mcp", "keycloak", "runtimeState"}
            or any(not isinstance(image, str) or not IMAGE.fullmatch(image) for image in images.values())):
        raise ArtifactError("runtime-artifact-image-mapping-invalid")
    return manifest, value, payload


def verify_runtime_container(container: dict, candidate: str = "HEAD") -> dict:
    manifest, definition, _ = runtime_definition(candidate)
    image_id = definition["images"]["backend"]
    if container.get("Image") != image_id:
        raise ArtifactError("running-server-artifact-mismatch")
    # Compose correctly labels the runtime with the current candidate/specification.
    # Image provenance belongs to the immutable image, not those runtime labels.
    images = json.loads(subprocess.check_output(["docker", "image", "inspect", image_id], cwd=ROOT))
    if len(images) != 1 or images[0].get("Id") != image_id:
        raise ArtifactError("running-server-artifact-mismatch")
    labels = images[0].get("Config", {}).get("Labels", {}) or {}
    if (labels.get("org.opencontainers.image.revision") != manifest["sourceCommit"]
            or labels.get("com.massimotter.weave.spec-digest") != definition["specDigest"]
            or labels.get("com.massimotter.weave.module") != "server"):
        raise ArtifactError("running-server-artifact-mismatch")
    return component_evidence(manifest)


def candidate_composition(candidate: str = "HEAD") -> dict:
    from build_artifacts import validate_composition
    selected = os.environ.get("WEAVE_CANDIDATE_COMPOSITION", "")
    path = Path(selected)
    if (not selected or not path.is_absolute() or not path.is_file()
            or any(parent.is_symlink() for parent in (path, *path.parents))):
        raise ArtifactError("qualified-candidate-composition-required")
    return validate_composition(ROOT, json.loads(path.read_text()), candidate)


def fetch_composition(candidate: str, run_id: int, run_attempt: int, output: Path, *, qualified: bool = False) -> None:
    from build_artifacts import validate_composition
    from verify_main_promotion_evidence import api, artifact_json, download_artifact, paged, qualification_evidence
    repository = os.environ.get("GITHUB_REPOSITORY", "masssi164/weave")
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN", "")
    if not token or min(run_id, run_attempt) < 1:
        raise ArtifactError("authenticated-qualification-run-required")
    identity = candidate_identity(ROOT, candidate)
    run = api(f"/repos/{repository}/actions/runs/{run_id}", token)
    if (run.get("head_sha") != identity["candidateCommit"] or run.get("head_branch") != "dogfood"
            or run.get("event") != "push" or run.get("path") != ".github/workflows/live-stack-e2e.yml"
            or type(run.get("run_attempt")) is not int or run["run_attempt"] < run_attempt):
        raise ArtifactError("composition-qualification-producer-mismatch")
    if qualified:
        observed_attempt, value = qualification_evidence(repository, identity['candidateCommit'],
            identity['sourceTree'], identity['specificationCommit'], run, token)
        if os.environ.get("GITHUB_ENV"):
            with Path(os.environ["GITHUB_ENV"]).open("a") as environment:
                environment.write(f"WEAVE_QUALIFICATION_RUN_ATTEMPT={observed_attempt}\n")
    else:
        # Retrying a failed live job does not repeat a successful hosted build.
        # Choose the latest composition for this exact run/candidate, then verify
        # every candidate input again. An expired latest record never falls back.
        prefix = f"candidate-artifacts-{identity['candidateCommit']}-{run_id}-attempt-"
        records = paged(f"/repos/{repository}/actions/runs/{run_id}/artifacts", token, "artifacts")
        names = [(int(item['name'][len(prefix):]), item['name']) for item in records
                 if isinstance(item.get('name'), str) and item['name'].startswith(prefix)
                 and re.fullmatch(r'[1-9][0-9]*', item['name'][len(prefix):])
                 and int(item['name'][len(prefix):]) <= run_attempt]
        if not names:
            raise ArtifactError("candidate-composition-artifact-unavailable")
        _, name = max(names)
        value = artifact_json(download_artifact(repository, run_id, name, token), "candidate-artifacts.json")
    validate_composition(ROOT, value, candidate)
    if any(path.is_symlink() for path in (output, *output.parents)):
        raise ArtifactError("unsafe-composition-output")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(value, indent=2) + "\n")


def load_runtime_images(candidate: str = "HEAD") -> dict[str, str]:
    manifest, definition, payload = runtime_definition(candidate)
    images = definition["images"]
    def installed(image: str) -> bool:
        return subprocess.run(["docker", "image", "inspect", image], cwd=ROOT,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    if not all(installed(image) for image in images.values()):
        subprocess.run(["docker", "load", "--input", str(payload / "images.tar")], cwd=ROOT,
                       check=True, stdout=subprocess.DEVNULL)
    for key, image in images.items():
        values = json.loads(subprocess.check_output(["docker", "image", "inspect", image], cwd=ROOT))
        if len(values) != 1 or values[0].get("Id") != image or values[0].get("Os") != "linux" or values[0].get("Architecture") != "amd64":
            raise ArtifactError("loaded-runtime-image-mismatch")
        if key in {"backend", "mcp"}:
            labels = values[0].get("Config", {}).get("Labels", {}) or {}
            if (labels.get("org.opencontainers.image.revision") != manifest["sourceCommit"]
                    or labels.get("com.massimotter.weave.spec-digest") != definition["specDigest"]):
                raise ArtifactError("loaded-runtime-provenance-mismatch")
    try:
        record_verified_runtime(manifest, images)
    except RuntimeStorageError as failure:
        raise ArtifactError(str(failure)) from None
    except (OSError, ValueError):
        raise ArtifactError("runtime-image-retention-unavailable") from None
    return {"WEAVE_TEST_APP_SERVER_IMAGE": images["backend"], "WEAVE_TEST_APP_MCP_IMAGE": images["mcp"],
            "WEAVE_TEST_APP_KEYCLOAK_IMAGE": images["keycloak"], "WEAVE_RUNTIME_STATE_IMAGE": images["runtimeState"],
            "WEAVE_BACKEND_IMAGE": images["backend"], "WEAVE_MCP_IMAGE": images["mcp"],
            "WEAVE_KEYCLOAK_IMAGE": images["keycloak"], "WEAVE_IMAGE_SOURCE_COMMIT": manifest["sourceCommit"],
            "WEAVE_ARTIFACT_SPEC_DIGEST": definition["specDigest"],
            "WEAVE_TEST_APP_DRIVER_CLASSPATH": str(payload / "test-driver/lib/*")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("runtime-env", "native", "verify", "fetch-composition"))
    parser.add_argument("--candidate", default="HEAD")
    parser.add_argument("--target", default="ios-simulator")
    parser.add_argument("--variant", default="ordinary")
    parser.add_argument("--run-id", type=int)
    parser.add_argument("--run-attempt", type=int)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--qualified", action="store_true")
    args = parser.parse_args()
    if args.operation == "fetch-composition":
        if args.run_id is None or args.run_attempt is None or args.output is None:
            parser.error("fetch-composition requires run, attempt and output")
        fetch_composition(args.candidate, args.run_id, args.run_attempt, args.output, qualified=args.qualified)
    elif args.operation == "runtime-env":
        for key, value in load_runtime_images(args.candidate).items():
            print("export " + key + "=" + shlex.quote(value))
    elif args.operation == "native":
        manifest, app, products = native_bundle(args.target, args.variant, args.candidate)
        print(json.dumps({"app": str(app), "products": str(products), **component_evidence(manifest)}))
    else:
        identity = candidate_identity(ROOT, args.candidate)
        result = {"schemaVersion": "weave.consumed-artifacts/v1", **identity, "components": {}}
        for name in ("runtime-linux-amd64", "ios-simulator", "ios-device"):
            manifest, _ = component(name, args.candidate)
            result["components"][name] = component_evidence(manifest)
        print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ArtifactError as failure:
        raise SystemExit("WEAVE_ARTIFACT_CONSUMPTION_ERROR " + str(failure)) from None
