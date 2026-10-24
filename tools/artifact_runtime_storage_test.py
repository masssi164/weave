#!/usr/bin/env python3
"""Ownership and retention checks for the Mac's hosted runtime image ledger."""
from __future__ import annotations

import fcntl
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import artifact_runtime_storage as storage


def digest(number: int) -> str:
    return "sha256:" + f"{number:064x}"


def fixture(number: int) -> tuple[dict, dict]:
    manifest = {"schemaVersion": "weave.build-artifact/v1", "component": "runtime-linux-amd64",
                "sourceCommit": f"{number:040x}", "inputDigest": digest(number + 1000),
                "payload": {"sha256": digest(number + 2000)}}
    images = dict(zip(("backend", "mcp", "keycloak", "runtimeState"),
                      (digest(number * 10 + index) for index in range(4))))
    return manifest, images


class Docker:
    def __init__(self):
        self.images = {}
        self.containers = {}
        self.removed = []
        self.commands = []
        self.fail_removal = set()

    def add(self, images: dict):
        for image in images.values():
            self.images.setdefault(image, {"Id": image, "RepoTags": [], "RepoDigests": []})

    def __call__(self, arguments: list[str]) -> str:
        self.commands.append(arguments)
        if arguments[:2] == ["container", "ls"]:
            return "\n".join(self.containers)
        if arguments[:2] == ["container", "inspect"]:
            return json.dumps([{"Id": name, "Image": self.containers[name]} for name in arguments[2:]])
        if arguments[:2] == ["image", "ls"]:
            return "\n".join(self.images)
        if arguments[:2] == ["image", "inspect"]:
            return json.dumps([self.images[arguments[2]]])
        if arguments[:2] == ["image", "rm"]:
            if arguments[-1] in self.fail_removal:
                raise storage.RuntimeStorageError("runtime-image-docker-command-failed")
            target = arguments[-1]
            self.removed.append(target)
            if target in self.images:
                del self.images[target]
            else:
                image = next(image for image, value in self.images.items() if target in value["RepoDigests"])
                value = self.images[image]
                value["RepoDigests"].remove(target)
                if not value["RepoTags"] and not value["RepoDigests"]:
                    del self.images[image]
            return ""
        raise AssertionError(arguments)


class RuntimeStorageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.ledger = self.root / "private/ledger.json"
        self.docker = Docker()

    def record(self, number: int, images: dict | None = None):
        manifest, original = fixture(number)
        images = images if images is not None else original
        self.docker.add(images)
        return storage.record_verified_runtime(manifest, images, ledger=self.ledger, docker=self.docker)

    def read(self):
        return json.loads(self.ledger.read_text())

    def record_registry(self, number: int, images: dict | None = None):
        manifest, original = fixture(number)
        images = images if images is not None else original
        references = {component: storage.NAMES[component] + "@" + digest(number * 100 + index)
                      for index, component in enumerate(images)}
        self.docker.add(images)
        for component, image in images.items():
            self.docker.images[image]["RepoDigests"].append(references[component])
        result = storage.record_verified_runtime(manifest, images, registry_references=references,
                                                 ledger=self.ledger, docker=self.docker)
        return result, references

    def test_retains_current_and_one_previous_set_only(self):
        self.record(1)
        self.record(2)
        result = self.record(3)
        self.assertEqual(set(self.docker.removed), set(fixture(1)[1].values()))
        self.assertEqual(result["removedImageCount"], 4)
        self.assertEqual(result["retainedSetCount"], 2)
        self.assertEqual(result["pendingImageCount"], 0)
        self.assertEqual([item["sourceCommit"] for item in self.read()["retainedSets"]],
                         [fixture(3)[0]["sourceCommit"], fixture(2)[0]["sourceCommit"]])
        self.assertEqual(self.ledger.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.ledger.parent.stat().st_mode & 0o777, 0o700)
        self.assertTrue(all(command[:3] == ["image", "rm", "--no-prune"] and len(command) == 4
                            for command in self.docker.commands if "rm" in command))

    def test_repeated_consumption_does_not_evict_the_previous_distinct_set(self):
        self.record(1)
        self.record(2)
        self.record(2)
        self.assertEqual(len(self.read()["retainedSets"]), 2)
        self.assertFalse(self.docker.removed)
        self.record(1)
        self.assertEqual(self.read()["retainedSets"][0]["sourceCommit"], fixture(1)[0]["sourceCommit"])

    def test_shared_base_images_remain_when_older_component_is_retired(self):
        self.record(1)
        self.record(2)
        third = fixture(3)[1]
        third["keycloak"] = fixture(1)[1]["keycloak"]
        third["runtimeState"] = fixture(1)[1]["runtimeState"]
        self.record(3, third)
        self.assertEqual(set(self.docker.removed), {fixture(1)[1]["backend"], fixture(1)[1]["mcp"]})

    def test_every_container_reference_survives_including_stopped_containers(self):
        self.record(1)
        self.record(2)
        first = fixture(1)[1]
        self.docker.containers = {"a" * 64: first["backend"], "b" * 64: first["mcp"]}
        result = self.record(3)
        self.assertEqual(result["inUseImageCount"], 2)
        self.assertEqual(set(self.read()["pendingImageIds"]), {first["backend"], first["mcp"]})
        self.assertIn(["container", "ls", "--all", "--quiet", "--no-trunc"], self.docker.commands)
        self.docker.containers.clear()
        result = storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)
        self.assertEqual(result["removedImageCount"], 2)
        self.assertEqual(self.read()["pendingImageIds"], [])

    def test_operator_tags_and_registry_digests_are_not_taken_over(self):
        self.record(1)
        first = fixture(1)[1]
        self.docker.images[first["backend"]]["RepoTags"] = ["operator-owned:latest"]
        self.docker.images[first["mcp"]]["RepoDigests"] = ["registry/weave@" + digest(42)]
        self.record(2)
        result = self.record(3)
        self.assertEqual(result["taggedImageCount"], 2)
        self.assertNotIn(first["backend"], self.docker.removed)
        self.assertNotIn(first["mcp"], self.docker.removed)
        self.assertFalse(any(command[:2] == ["image", "tag"] for command in self.docker.commands))

    def test_registry_pulled_images_are_bounded_and_owned_digests_are_removed(self):
        _, first_references = self.record_registry(1)
        self.record_registry(2)
        result, _ = self.record_registry(3)
        self.assertEqual(set(self.docker.removed), set(first_references.values()))
        self.assertEqual(result["removedImageCount"], 4)
        self.assertEqual(result["pendingImageCount"], 0)
        self.assertEqual(len(self.docker.images), 8)
        self.assertTrue(set(self.read()["ownedRegistryReferences"]).isdisjoint(fixture(1)[1].values()))
        self.assertTrue(all(command[:3] == ["image", "rm", "--no-prune"] and len(command) == 4
                            for command in self.docker.commands if "rm" in command))

    def test_owned_registry_images_still_preserve_operator_and_in_use_references(self):
        _, first_references = self.record_registry(1)
        first = fixture(1)[1]
        self.docker.images[first["backend"]]["RepoTags"] = ["operator-owned:stable"]
        self.docker.images[first["mcp"]]["RepoDigests"].append("other-registry/example@" + digest(9000))
        self.docker.containers = {"a" * 64: first["keycloak"]}
        self.record_registry(2)
        result, _ = self.record_registry(3)
        self.assertEqual(result["removedImageCount"], 1)
        self.assertEqual(result["taggedImageCount"], 2)
        self.assertEqual(result["inUseImageCount"], 1)
        self.assertEqual(self.docker.removed, [first_references["runtimeState"]])
        self.assertEqual(set(self.read()["pendingImageIds"]), {first["backend"], first["mcp"], first["keycloak"]})
        self.docker.containers.clear()
        self.docker.images[first["backend"]]["RepoTags"] = []
        self.docker.images[first["mcp"]]["RepoDigests"] = [first_references["mcp"]]
        result = storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)
        self.assertEqual(result["removedImageCount"], 3)
        self.assertEqual(result["pendingImageCount"], 0)

    def test_all_verified_aliases_of_the_same_retired_image_are_removed(self):
        shared = fixture(1)[1]
        _, first_references = self.record_registry(1)
        _, second_references = self.record_registry(2, shared)
        self.record_registry(3)
        result, _ = self.record_registry(4)
        self.assertEqual(result["removedImageCount"], 4)
        self.assertEqual(set(self.docker.removed), set(first_references.values()) | set(second_references.values()))
        self.assertEqual(result["pendingImageCount"], 0)

    def test_legacy_ledger_migration_never_claims_existing_registry_references(self):
        self.record(1)
        first = fixture(1)[1]
        # Even a familiar package name is not proof of consumer ownership.
        self.docker.images[first["backend"]]["RepoDigests"] = [storage.NAMES["backend"] + "@" + digest(9021)]
        legacy = self.read()
        legacy["schemaVersion"] = storage.LEGACY_SCHEMA
        del legacy["ownedRegistryReferences"]
        self.ledger.write_text(json.dumps(legacy))
        self.record_registry(2)
        result, _ = self.record_registry(3)
        self.assertEqual(self.read()["schemaVersion"], storage.SCHEMA)
        self.assertEqual(result["taggedImageCount"], 1)
        self.assertNotIn(first["backend"], self.docker.removed)
        self.assertNotIn(first["backend"], self.read()["ownedRegistryReferences"])

    def test_failed_registry_removal_retains_ownership_for_safe_retry(self):
        _, references = self.record_registry(1)
        failed = references["backend"]
        self.docker.fail_removal.add(failed)
        self.record_registry(2)
        result, _ = self.record_registry(3)
        self.assertEqual(result["deferredImageCount"], 1)
        self.assertEqual(self.read()["pendingImageIds"], [fixture(1)[1]["backend"]])
        self.assertEqual(self.read()["ownedRegistryReferences"][fixture(1)[1]["backend"]], [failed])
        self.docker.fail_removal.clear()
        self.assertEqual(storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)["removedImageCount"], 1)

    def test_unverified_registry_reference_is_rejected_before_recording_authority(self):
        manifest, images = fixture(1)
        invalid = {component: storage.NAMES[component] + "@" + digest(101) for component in images}
        for component, reference in (("backend", "ghcr.io/another/repository@" + digest(1)),
                                     ("backend", storage.NAMES["backend"] + ":latest")):
            with self.subTest(reference=reference):
                references = {**invalid, component: reference}
                with self.assertRaisesRegex(storage.RuntimeStorageError, "registry-ownership-invalid"):
                    storage.record_verified_runtime(manifest, images, registry_references=references,
                                                    ledger=self.ledger, docker=self.docker)
        self.assertFalse(self.ledger.exists())
        self.assertFalse(self.docker.commands)

    def test_unrecorded_untagged_images_are_never_pruned(self):
        unrelated = digest(9999)
        self.docker.add({"unrelated": unrelated})
        self.record(1)
        self.record(2)
        self.record(3)
        self.assertIn(unrelated, self.docker.images)
        self.assertNotIn(unrelated, self.docker.removed)

    def test_failed_removal_stays_recorded_and_retries_without_force(self):
        self.record(1)
        self.record(2)
        image = fixture(1)[1]["backend"]
        self.docker.fail_removal.add(image)
        result = self.record(3)
        self.assertEqual(result["deferredImageCount"], 1)
        self.assertEqual(self.read()["pendingImageIds"], [image])
        self.docker.fail_removal.clear()
        result = storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)
        self.assertEqual(result["removedImageCount"], 1)
        self.assertFalse(any("--force" in command or "prune" in command for command in self.docker.commands))

    def test_already_absent_retired_images_leave_the_ledger(self):
        self.record(1)
        self.record(2)
        missing = fixture(1)[1]["backend"]
        del self.docker.images[missing]
        result = self.record(3)
        self.assertEqual(result["removedImageCount"], 3)
        self.assertEqual(result["pendingImageCount"], 0)

    def test_ledger_corruption_blocks_before_any_docker_mutation(self):
        self.record(1)
        self.ledger.write_text('{"schemaVersion":"other"}')
        self.docker.commands.clear()
        with self.assertRaises(storage.RuntimeStorageError):
            self.record(2)
        self.assertFalse(self.docker.commands)

    def test_permissive_file_or_directory_is_not_cleanup_authority(self):
        self.record(1)
        for path in (self.ledger, self.ledger.parent):
            original = path.stat().st_mode & 0o777
            path.chmod(0o755 if path.is_dir() else 0o644)
            with self.assertRaises(storage.RuntimeStorageError):
                storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)
            path.chmod(original)

    def test_symlinks_in_ledger_lock_or_parent_are_rejected(self):
        self.record(1)
        for path in (self.ledger, self.ledger.with_suffix(".json.lock"), self.ledger.parent):
            target = path.with_name(path.name + "-target")
            path.rename(target)
            path.symlink_to(target, target_is_directory=target.is_dir())
            with self.assertRaises(storage.RuntimeStorageError):
                storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)
            path.unlink()
            target.rename(path)

    def test_hardlinked_ledger_is_rejected(self):
        self.record(1)
        os.link(self.ledger, self.root / "second-ledger")
        with self.assertRaises(storage.RuntimeStorageError):
            storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)

    def test_owner_uid_is_part_of_the_ledger_contract(self):
        self.record(1)
        value = self.read()
        value["ownerUid"] += 1
        self.ledger.write_text(json.dumps(value))
        with self.assertRaises(storage.RuntimeStorageError):
            storage.prune_runtime_storage(ledger=self.ledger, docker=self.docker)

    def test_existing_lock_serializes_consumers_with_a_bounded_wait(self):
        self.record(1)
        original = self.ledger.read_bytes()
        with self.ledger.with_suffix(".json.lock").open("r+") as held:
            fcntl.flock(held, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with patch.object(storage.time, "monotonic", side_effect=[0, 31]):
                with self.assertRaisesRegex(storage.RuntimeStorageError, "lock-timeout"):
                    self.record(2)
        self.assertEqual(self.ledger.read_bytes(), original)

    def test_rejects_incomplete_or_wrong_component_records(self):
        manifest, images = fixture(1)
        manifest["component"] = "ios-device"
        with self.assertRaises(storage.RuntimeStorageError):
            storage.record_verified_runtime(manifest, images, ledger=self.ledger, docker=self.docker)
        manifest["component"] = "runtime-linux-amd64"
        del images["keycloak"]
        with self.assertRaises(storage.RuntimeStorageError):
            storage.record_verified_runtime(manifest, images, ledger=self.ledger, docker=self.docker)

    def test_broken_container_inventory_keeps_all_images_and_new_ownership(self):
        self.record(1)
        self.record(2)
        real_docker = self.docker
        def broken(arguments):
            if arguments[:2] == ["container", "ls"]:
                return "not-an-immutable-container-id"
            return real_docker(arguments)
        manifest, images = fixture(3)
        self.docker.add(images)
        with self.assertRaises(storage.RuntimeStorageError):
            storage.record_verified_runtime(manifest, images, ledger=self.ledger, docker=broken)
        self.assertEqual(set(self.read()["pendingImageIds"]), set(fixture(1)[1].values()))
        self.assertEqual(self.read()["retainedSets"][0]["sourceCommit"], manifest["sourceCommit"])
        self.assertFalse(self.docker.removed)


if __name__ == "__main__":
    unittest.main()
