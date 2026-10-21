#!/usr/bin/env python3
"""Exercise reset and first-owner session boundaries without a Docker daemon."""

from __future__ import annotations

import fcntl
import hashlib
import json
import sys
import tempfile
import unittest
from contextlib import ExitStack, contextmanager
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import compose_runtime as runtime  # noqa: E402
from compose_env import ContractError  # noqa: E402


class DogfoodResetSessionContractTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.generated = self.root / "generated"
        self.session = self.generated / "owner-bootstrap"
        self.session.mkdir(parents=True, mode=0o700)
        self.anchor = self.session / "request-anchor.json"
        self.evidence = self.session / "evidence.json"
        self.anchor.write_text(json.dumps({
            "schemaVersion": "weave-owner-bootstrap-request-anchor-v1",
            "emailSha256": hashlib.sha256(b"old@example.test").hexdigest(),
            "idempotencyKeySha256": hashlib.sha256(b"old-key").hexdigest(),
            "supportSafe": True,
        }), encoding="utf-8")
        self.evidence.write_text('{"mailMessageMatched": true}', encoding="utf-8")
        self.anchor.chmod(0o600)
        self.evidence.chmod(0o600)
        self.context = SimpleNamespace(
            environment="dogfood", profile="dogfood", isolated_namespace=None,
            generated_root=self.generated, active_profiles=(),
            env={
                "WEAVE_COMPOSE_PROJECT": "weave-dogfood",
                "WEAVE_DB_DATA_VOLUME": "weave_dogfood_db_data",
                "WEAVE_NATIVE_FILES_DATA_VOLUME": "weave_dogfood_native_files_data",
                "WEAVE_MAILPIT_DATA_VOLUME": "weave_dogfood_mailpit_data",
                "WEAVE_DOCKER_NETWORK": "weave_dogfood_network",
            },
        )

    @contextmanager
    def isolated_runtime(self):
        with ExitStack() as stack:
            mocks = {
                name: stack.enter_context(patch.object(runtime, name))
                for name in (
                    "script", "prepare", "compose", "cleanup_retired_dogfood",
                    "_remove_exact_dogfood_resource", "execute",
                )
            }
            yield mocks

    @contextmanager
    def held_session_lock(self):
        operations = self.generated / "operations"
        operations.mkdir(mode=0o700, exist_ok=True)
        lock_path = operations / "owner-bootstrap.lock"
        with lock_path.open("a+") as lock:
            lock_path.chmod(0o600)
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            try:
                yield
            finally:
                fcntl.flock(lock, fcntl.LOCK_UN)

    def request(self) -> Path:
        request = self.root / "new-owner.json"
        request.write_text(json.dumps({
            "displayName": "New Owner", "email": "new@example.test",
            "idempotencyKey": "new-key",
        }), encoding="utf-8")
        request.chmod(0o600)
        return request

    def test_reset_retires_old_owner_only_after_session_resources_are_removed(self) -> None:
        preserved = [
            self.root / "tls" / "ca.pem",
            self.root / "secrets" / "machine-key",
            self.session / "diagnostics.json",
        ]
        for path in preserved:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("preserve", encoding="utf-8")
        removed = []

        def remove_resource(kind, name):
            self.assertTrue(self.anchor.exists())
            self.assertTrue(self.evidence.exists())
            removed.append((kind, name))

        def start_empty_stack(context, command, extra):
            self.assertIs(context, self.context)
            self.assertEqual((command, extra), ("up", []))
            self.assertFalse(self.anchor.exists())
            self.assertFalse(self.evidence.exists())

        with self.isolated_runtime() as mocks:
            mocks["_remove_exact_dogfood_resource"].side_effect = remove_resource
            mocks["execute"].side_effect = start_empty_stack
            runtime.reset_dogfood(self.context)
            mocks["compose"].assert_called_once_with(
                self.context, "down", "--volumes", "--remove-orphans")
            mocks["execute"].assert_called_once()
        self.assertEqual(removed, [
            ("volume", self.context.env[key])
            for key in ("WEAVE_DB_DATA_VOLUME", "WEAVE_NATIVE_FILES_DATA_VOLUME",
                        "WEAVE_MAILPIT_DATA_VOLUME")
        ] + [("network", self.context.env["WEAVE_DOCKER_NETWORK"])])
        for path in preserved:
            self.assertEqual(path.read_text(encoding="utf-8"), "preserve")

    def test_reset_allows_a_new_first_owner_for_the_new_empty_session(self) -> None:
        with self.isolated_runtime():
            runtime.reset_dogfood(self.context)
        canonical = {"Image": "sha256:" + "1" * 64, "Config": {"Env": []}, "Mounts": []}
        with (
            patch.object(runtime, "prepare"),
            patch.object(runtime, "_owner_bootstrap_smtp_preflight"),
            patch.object(runtime, "_await_healthy"),
            patch.object(runtime, "verify_dogfood_identity"),
            patch.object(runtime, "_service_container", return_value=canonical),
            patch.object(runtime, "_canonical_backend", return_value=canonical),
            patch.object(runtime, "_bootstrap_disabled", return_value=True),
            patch.object(runtime, "_mailpit_recipient_summaries", side_effect=
                         ContractError("stopped before external invitation")) as mailpit,
        ):
            with self.assertRaisesRegex(ContractError, "stopped before external invitation"):
                runtime.owner_bootstrap(self.context, ["--request-file", str(self.request())])
            mailpit.assert_called_once()
        anchor = json.loads(self.anchor.read_text(encoding="utf-8"))
        self.assertEqual(anchor["emailSha256"], hashlib.sha256(b"new@example.test").hexdigest())

    def test_failed_volume_removal_retains_the_existing_owner_session(self) -> None:
        with self.isolated_runtime() as mocks:
            mocks["_remove_exact_dogfood_resource"].side_effect = ContractError("volume is busy")
            with self.assertRaisesRegex(ContractError, "volume is busy"):
                runtime.reset_dogfood(self.context)
            mocks["execute"].assert_not_called()
        self.assertTrue(self.anchor.exists())
        self.assertTrue(self.evidence.exists())

    def test_ordinary_down_preserves_owner_session(self) -> None:
        before = (self.anchor.read_bytes(), self.evidence.read_bytes())
        with patch.object(runtime, "compose") as compose:
            runtime.execute(self.context, "down", ["--remove-orphans"])
            compose.assert_called_once_with(self.context, "down", "--remove-orphans")
        self.assertEqual((self.anchor.read_bytes(), self.evidence.read_bytes()), before)

    def test_ordinary_up_preserves_owner_session_and_checks_live_identity(self) -> None:
        before = (self.anchor.read_bytes(), self.evidence.read_bytes())
        with (
            patch.object(runtime, "script"),
            patch.object(runtime, "prepare"),
            patch.object(runtime, "compose"),
            patch.object(runtime, "runtime_root_services", return_value=("caddy", "mcp")),
            patch.object(runtime, "verify_dogfood_identity") as readiness,
        ):
            runtime.execute(self.context, "up", [])
            readiness.assert_called_once_with(self.context)
        self.assertEqual((self.anchor.read_bytes(), self.evidence.read_bytes()), before)

    def test_linked_or_nonregular_session_files_fail_before_resource_deletion(self) -> None:
        for filename in ("request-anchor.json", "evidence.json"):
            for kind in ("symlink", "directory"):
                with self.subTest(filename=filename, kind=kind):
                    path = self.session / filename
                    original = path.read_bytes()
                    path.unlink()
                    target = self.root / "unrelated-private-file"
                    target.write_text("untouched", encoding="utf-8")
                    if kind == "symlink":
                        path.symlink_to(target)
                    else:
                        path.mkdir()
                    with self.isolated_runtime() as mocks:
                        with self.assertRaises(ContractError):
                            runtime.reset_dogfood(self.context)
                        mocks["compose"].assert_not_called()
                        mocks["_remove_exact_dogfood_resource"].assert_not_called()
                    self.assertEqual(target.read_text(encoding="utf-8"), "untouched")
                    if path.is_symlink():
                        path.unlink()
                    else:
                        path.rmdir()
                    path.write_bytes(original)
                    path.chmod(0o600)

    def test_concurrent_owner_bootstrap_blocks_reset_before_mutation(self) -> None:
        with self.held_session_lock(), self.isolated_runtime() as mocks:
            with self.assertRaisesRegex(ContractError, "operation is active"):
                runtime.reset_dogfood(self.context)
            mocks["compose"].assert_not_called()
            mocks["_remove_exact_dogfood_resource"].assert_not_called()
            mocks["execute"].assert_not_called()
        self.assertTrue(self.anchor.exists())

    def test_rejected_concurrent_bootstrap_never_recreates_backend(self) -> None:
        with (
            self.held_session_lock(),
            patch.object(runtime, "prepare"),
            patch.object(runtime, "_owner_bootstrap_smtp_preflight"),
            patch.object(runtime, "_canonical_backend") as restore,
            patch.object(runtime, "_bootstrap_disabled", return_value=True),
            patch.object(runtime, "compose") as compose,
        ):
            with self.assertRaisesRegex(ContractError, "operation is active"):
                runtime.owner_bootstrap(self.context, ["--request-file", str(self.request())])
            restore.assert_not_called()
            compose.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
