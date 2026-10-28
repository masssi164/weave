#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export WORKSPACE_ROOT
python3 - <<'PY'
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class RuntimeStateImagePlatformTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="weave-runtime-image-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.log = self.root / "calls.jsonl"
        self.state = self.root / "state.json"
        docker = self.root / "docker"
        docker.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args = sys.argv[1:]
with Path(os.environ["WEAVE_TEST_DOCKER_LOG"]).open("a") as log:
    log.write(json.dumps(args) + "\\n")
state = Path(os.environ["WEAVE_TEST_DOCKER_STATE"])
if args[0] == "info":
    print(os.environ["WEAVE_TEST_NATIVE_PLATFORM"])
elif args[0] == "build":
    state.write_text(json.dumps({"platform": args[args.index("--platform") + 1],
        "definition": args[args.index("--build-arg") + 1].split("=", 1)[1]}))
elif args[:2] == ["image", "inspect"]:
    if not state.exists():
        sys.exit(1)
    data = json.loads(state.read_text())
    if "--format" not in args:
        sys.exit(0)
    fmt = args[args.index("--format") + 1]
    if fmt == "{{.Os}}/{{.Architecture}}":
        print(os.environ.get("WEAVE_TEST_IMAGE_PLATFORM", data["platform"]))
    elif fmt == "{{.Id}}":
        print("sha256:" + "a" * 64)
    elif "org.opencontainers.image.revision" in fmt:
        print(os.environ.get("WEAVE_TEST_SOURCE_REVISION", "9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a"))
    elif "mc-source-revision" in fmt:
        print("77f82e18b5401a65958f1619df6ebb994634bd88")
    elif "org.opencontainers.image.licenses" in fmt:
        print("AGPL-3.0-or-later")
    elif "build-definition-digest" in fmt:
        print(data["definition"])
    else:
        raise AssertionError(args)
else:
    raise AssertionError(args)
''')
        docker.chmod(0o755)
        self.environment = {**os.environ, "PATH": str(self.root) + os.pathsep + os.environ["PATH"],
            "WEAVE_TEST_DOCKER_LOG": str(self.log), "WEAVE_TEST_DOCKER_STATE": str(self.state),
            "WEAVE_TEST_NATIVE_PLATFORM": "linux/aarch64"}
        self.workspace = Path(os.environ["WORKSPACE_ROOT"])

    def run_builder(self, *arguments):
        return subprocess.run(["bash", str(self.workspace / "scripts/build_runtime_state_image.sh"), *arguments],
            env=self.environment, capture_output=True, text=True, check=False)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_default_build_is_native_arm64_and_uses_separate_tag(self):
        result = self.run_builder()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "sha256:" + "a" * 64)
        build = next(call for call in self.calls() if call[0] == "build")
        self.assertEqual(build[build.index("--platform") + 1], "linux/arm64")
        self.assertTrue(build[build.index("--tag") + 1].endswith("-arm64"))

    def test_explicit_amd64_requires_native_host_and_has_separate_tag(self):
        self.environment["WEAVE_TEST_NATIVE_PLATFORM"] = "linux/x86_64"
        result = self.run_builder("--platform", "linux/amd64")
        self.assertEqual(result.returncode, 0, result.stderr)
        build = next(call for call in self.calls() if call[0] == "build")
        self.assertEqual(build[build.index("--platform") + 1], "linux/amd64")
        self.assertTrue(build[build.index("--tag") + 1].endswith("-amd64"))

    def test_verified_platform_cache_avoids_rebuild(self):
        self.assertEqual(self.run_builder().returncode, 0)
        self.log.unlink()
        result = self.run_builder()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(call[0] == "build" for call in self.calls()))

    def test_wrong_cached_architecture_is_rejected_without_rebuild(self):
        self.assertEqual(self.run_builder().returncode, 0)
        self.log.unlink()
        self.environment["WEAVE_TEST_IMAGE_PLATFORM"] = "linux/amd64"
        result = self.run_builder()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("image platform mismatch", result.stderr)
        self.assertFalse(any(call[0] == "build" for call in self.calls()))

    def test_wrong_resulting_architecture_is_rejected(self):
        self.environment["WEAVE_TEST_IMAGE_PLATFORM"] = "linux/amd64"
        result = self.run_builder()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("image platform mismatch", result.stderr)

    def test_cached_source_identity_remains_verified(self):
        self.assertEqual(self.run_builder().returncode, 0)
        self.environment["WEAVE_TEST_SOURCE_REVISION"] = "b" * 40
        result = self.run_builder()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("source or licence label mismatch", result.stderr)

    def test_emulated_target_is_rejected_before_cache_or_build(self):
        self.environment["WEAVE_TEST_NATIVE_PLATFORM"] = "linux/x86_64"
        result = self.run_builder()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("native target platform is required", result.stderr)
        self.assertEqual([call[0] for call in self.calls()], ["info"])

    def test_invalid_or_missing_platform_is_rejected_before_docker(self):
        for arguments in (("--platform",), ("--platform", "linux/ppc64le"), ("--unknown",)):
            with self.subTest(arguments=arguments):
                result = self.run_builder(*arguments)
                self.assertEqual(result.returncode, 2)
                self.assertFalse(self.calls())


unittest.main()
PY
