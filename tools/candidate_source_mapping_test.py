#!/usr/bin/env python3
"""Unit evidence for protected dev source-to-lane candidate mapping."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import candidate_source_mapping as module  # noqa: E402


class CandidateSourceMappingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.repository = Path(self.temporary.name) / "repository"
        self.repository.mkdir()
        self.git("init", "--quiet", "--initial-branch", "dogfood")
        self.git("config", "user.name", "Candidate Mapping Test")
        self.git("config", "user.email", "candidate-mapping@invalid")
        (self.repository / "contract.txt").write_text("base\n", encoding="utf-8")
        self.git("add", "contract.txt")
        self.git("commit", "--quiet", "-m", "base")
        self.git("branch", "dev")
        self.git("switch", "--quiet", "dev")
        (self.repository / "contract.txt").write_text("selected dev source\n", encoding="utf-8")
        self.git("commit", "--quiet", "-am", "source candidate")
        self.source = self.git("rev-parse", "HEAD")
        self.git(
            "update-ref",
            "refs/remotes/origin/dev",
            self.source,
        )
        self.git("switch", "--quiet", "-c", "promotion", "dogfood")
        self.git("merge", "--quiet", "--no-ff", "dev", "-m", "promote dev")
        self.lane = self.git("rev-parse", "HEAD")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def git(self, *arguments: str) -> str:
        return subprocess.run(
            ["git", "-C", str(self.repository), *arguments],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout.strip()

    def test_resolves_one_protected_source_and_complete_image_mapping(self) -> None:
        images = {
            name: "sha256:" + str(index + 1) * 64
            for index, name in enumerate(module.IMAGE_NAMES)
        }
        result = module.resolve(
            self.repository,
            self.lane,
            "refs/remotes/origin/dev",
            images,
        )
        self.assertEqual(result["laneCandidateCommit"], self.lane)
        self.assertEqual(result["sourceCandidateCommit"], self.source)
        self.assertEqual(result["sourceTree"], result["laneTree"])
        self.assertEqual(result["images"], dict(sorted(images.items())))
        self.assertTrue(result["supportSafe"])
        self.assertFalse(result["containsSecretValues"])

    def test_rejects_lane_content_not_present_in_selected_dev_source(self) -> None:
        (self.repository / "lane-only.txt").write_text("not in dev\n", encoding="utf-8")
        self.git("add", "lane-only.txt")
        self.git("commit", "--quiet", "-m", "lane drift")
        drifted = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(
            module.MappingError,
            "lane tree differs",
        ):
            module.resolve(
                self.repository,
                drifted,
                "refs/remotes/origin/dev",
            )

    def test_requires_all_and_only_closed_image_bindings(self) -> None:
        with self.assertRaisesRegex(module.MappingError, "all four"):
            module.parse_images(["backend=sha256:" + "1" * 64])
        with self.assertRaisesRegex(module.MappingError, "closed"):
            module.parse_images(["provider=sha256:" + "1" * 64])
        parsed = module.parse_images(
            [
                f"{name}=sha256:{str(index + 1) * 64}"
                for index, name in enumerate(module.IMAGE_NAMES)
            ]
        )
        self.assertEqual(set(parsed), set(module.IMAGE_NAMES))

    def test_rejects_any_source_authority_other_than_fetched_origin_dev(self) -> None:
        with self.assertRaisesRegex(module.MappingError, "malformed"):
            module.resolve(
                self.repository,
                self.lane,
                "refs/remotes/origin/unreviewed",
            )

    def test_cli_writes_support_safe_evidence_and_runner_environment(self) -> None:
        output = Path(self.temporary.name) / "mapping.json"
        github_env = Path(self.temporary.name) / "github.env"
        github_env.touch()
        exit_code = module.main(
            [
                "--repository",
                str(self.repository),
                "--lane-candidate",
                self.lane,
                "--output",
                str(output),
                "--github-env",
                str(github_env),
            ]
        )
        self.assertEqual(exit_code, 0)
        evidence = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(evidence["sourceCandidateCommit"], self.source)
        self.assertEqual(
            github_env.read_text(encoding="utf-8"),
            f"WEAVE_IMAGE_SOURCE_COMMIT={self.source}\n",
        )

    def test_evidence_and_runner_environment_reject_symlink_targets(self) -> None:
        target = Path(self.temporary.name) / "target"
        target.touch()
        symlink = Path(self.temporary.name) / "symlink"
        symlink.symlink_to(target)
        with self.assertRaisesRegex(module.MappingError, "symlink"):
            module.write_json(symlink, {"supportSafe": True})
        with self.assertRaisesRegex(module.MappingError, "regular runner file"):
            module.append_github_env(
                symlink,
                {"sourceCandidateCommit": self.source, "images": {}},
            )

    def test_expected_mapping_must_match_authority_and_exports_exact_images(self) -> None:
        images = {
            name: "sha256:" + str(index + 1) * 64
            for index, name in enumerate(module.IMAGE_NAMES)
        }
        expected = module.resolve(
            self.repository,
            self.lane,
            "refs/remotes/origin/dev",
            images,
        )
        path = Path(self.temporary.name) / "expected.json"
        path.write_text(json.dumps(expected), encoding="utf-8")
        loaded = module.load_expected_mapping(path)
        module.assert_expected_authority(loaded, expected)
        self.assertEqual(module.expected_images(loaded), dict(sorted(images.items())))

        github_env = Path(self.temporary.name) / "complete.env"
        github_env.touch()
        module.append_github_env(github_env, expected)
        exported = dict(
            line.split("=", 1)
            for line in github_env.read_text(encoding="utf-8").splitlines()
        )
        self.assertEqual(exported["WEAVE_IMAGE_SOURCE_COMMIT"], self.source)
        for name, image_id in images.items():
            self.assertEqual(exported[module.IMAGE_ENVIRONMENT[name]], image_id)

        drifted = dict(expected)
        drifted["laneTree"] = "f" * 40
        with self.assertRaisesRegex(module.MappingError, "current source/lane"):
            module.assert_expected_authority(drifted, expected)

    def test_local_image_verification_rejects_changed_identity_or_revision(self) -> None:
        images = {
            name: "sha256:" + str(index + 1) * 64
            for index, name in enumerate(module.IMAGE_NAMES)
        }

        def inspect(arguments: list[str], **_: object) -> subprocess.CompletedProcess[str]:
            image_id = arguments[3]
            name = next(name for name, value in images.items() if value == image_id)
            labels = {"org.opencontainers.image.revision": self.source}
            if name == "identity-ops":
                labels["com.massimotter.weave.component"] = "keycloak-identity-ops"
            if name == "keycloak":
                labels.update(
                    {
                        "com.massimotter.weave.module": module.KEYCLOAK_MODULE,
                        "com.massimotter.weave.provider-id": module.KEYCLOAK_PROVIDER,
                        "com.massimotter.weave.keycloak-patch-sha256": "a" * 64,
                        "com.massimotter.weave.keycloak-patched-services-sha256": "b" * 64,
                    }
                )
            return subprocess.CompletedProcess(
                arguments,
                0,
                json.dumps(
                    {
                        "Id": image_id,
                        "Config": {"Labels": labels},
                        "RepoDigests": [],
                    }
                ),
                "",
            )

        with mock.patch.object(module.subprocess, "run", side_effect=inspect):
            module.assert_local_images(images, self.source)

        def stale_revision(
            arguments: list[str],
            **kwargs: object,
        ) -> subprocess.CompletedProcess[str]:
            result = inspect(arguments, **kwargs)
            payload = json.loads(result.stdout)
            payload["Config"]["Labels"]["org.opencontainers.image.revision"] = "f" * 40
            return subprocess.CompletedProcess(arguments, 0, json.dumps(payload), "")

        with mock.patch.object(module.subprocess, "run", side_effect=stale_revision):
            with self.assertRaisesRegex(module.MappingError, "changed"):
                module.assert_local_images(images, self.source)


if __name__ == "__main__":
    unittest.main()
