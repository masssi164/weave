#!/usr/bin/env python3
"""Exercise the bounded Space lifecycle without Docker or live credentials."""
from __future__ import annotations

import contextlib
import io
import json
import os
import signal
import sys
import tempfile
import time
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import compose_runtime as runtime
import default_space_bootstrap as bootstrap
import bootstrap_https
from compose_env import ContractError

CANONICAL_BACKEND = runtime._canonical_backend


class DefaultSpaceBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name).resolve()
        self.context = types.SimpleNamespace(
            environment="dogfood", isolated_namespace=None, generated_root=root / "generated",
            root=root,
            secret_root=root / "secrets", repository_root=root, tls_root=root / "tls",
            env={"WEAVE_RUNTIME_UID": str(os.getuid()), "WEAVE_RUNTIME_GID": str(os.getgid()),
                 "WEAVE_BACKEND_IMAGE": "sha256:" + "a" * 64,
                 "WEAVE_IMAGE_SOURCE_COMMIT": "b" * 40, "WEAVE_SPEC_DIGEST": "sha256:" + "c" * 64},
        )
        self.context.secret_root.mkdir(mode=0o700)
        self.evidence = root / "output/evidence.json"
        self.image = "sha256:" + "a" * 64
        self.container = {"Image": self.image, "Config": {"Env": [], "Labels": {
            "org.opencontainers.image.revision": "b" * 40,
            "com.massimotter.weave.spec-digest": "sha256:" + "c" * 64}}, "Mounts": []}
        self.result = {"schemaVersion": "weave.default-space-bootstrap/v1", "status": "passed",
                       "spaceRef": "workspace-default", "outcome": "CREATED",
                       **{flag: True for flag in bootstrap.RESPONSE_FLAGS}, "containsSecretValues": False}
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        def mock(name, **kwargs):
            return self.stack.enter_context(patch(name, **kwargs))
        self.identity = mock("default_space_bootstrap._identity", return_value={
            "candidateCommit": "b" * 40, "sourceTree": "d" * 40, "specificationCommit": "e" * 40})
        self.prepare = mock("compose_runtime.prepare")
        self.readiness = mock("compose_runtime.verify_dogfood_identity")
        self.service = mock("compose_runtime._service_container", return_value=self.container)
        self.restore = mock("compose_runtime._canonical_backend", return_value=self.container)
        self.disabled = mock("default_space_bootstrap._disabled", return_value=True)
        self.request = mock("default_space_bootstrap._request", return_value=self.result)
        self.inspect = mock("default_space_bootstrap.subprocess.run", return_value=types.SimpleNamespace(stdout=self.image))
        self.override_contents = []
        def observe(context, override, *arguments):
            payload = json.loads(override.read_text())
            self.override_contents.append(payload)
            backend = payload["services"]["backend"]
            token = Path(backend["volumes"][0]["source"]) / "token"
            self.assertEqual(token.stat().st_mode & 0o777, 0o600)
            self.assertEqual(token.parent.stat().st_mode & 0o777, 0o700)
            self.assertNotIn(token.read_text().strip(), json.dumps(payload))
            self.assertNotIn(token.read_text().strip(), " ".join(arguments))
            self.assertTrue(backend["volumes"][0]["read_only"])
            self.assertIn("--no-deps", arguments)
        self.override = mock("compose_runtime._bootstrap_override_command", side_effect=observe)

    def run_operation(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            bootstrap.execute(self.context, ["--evidence-file", str(self.evidence)])
        return output.getvalue()

    def assert_cleanup(self):
        self.restore.assert_called_once()
        self.assertEqual(list(self.context.secret_root.iterdir()), [])
        self.assertEqual(list((self.context.generated_root / "operations").glob("*.compose.json")), [])

    def test_success_publishes_only_post_cleanup_bound_evidence(self):
        output = self.run_operation()
        payload = json.loads(self.evidence.read_text())
        self.assertEqual(payload["schemaVersion"], "weave.default-space-preparation/v1")
        self.assertEqual(payload["candidateCommit"], "b" * 40)
        for flag in (*bootstrap.RESPONSE_FLAGS, "bootstrapAuthorityAbsent", "bootstrapMountAbsent", "tokenAbsent", "canonicalImageUnchanged"):
            self.assertIs(payload[flag], True)
        self.assertEqual(self.evidence.stat().st_mode & 0o777, 0o600)
        token = self.request.call_args.args[1]
        self.assertNotIn(token, self.evidence.read_text() + output)
        self.assertEqual(self.request.call_args.args[0], self.context)
        self.assert_cleanup()

    def test_reused_artifact_preserves_producer_identity_but_binds_current_runtime(self):
        artifact = types.ModuleType("artifact_consumers")
        artifact.ArtifactError = type("ArtifactError", (ValueError,), {})
        artifact.verify_runtime_container = Mock(return_value={"sourceCommit": "f" * 40})
        self.context.env.update(WEAVE_RESOURCE_PREFIX="weave-dogfood-owned", WEAVE_COMPOSE_PROJECT="weave-dogfood-owned")
        self.container["Config"]["Labels"].update({"org.opencontainers.image.revision": "f" * 40,
            "com.massimotter.weave.candidate-commit": "b" * 40, "com.massimotter.weave.namespace": "weave-dogfood-owned",
            "com.docker.compose.project": "weave-dogfood-owned", "com.docker.compose.service": "backend"})
        with patch.dict(sys.modules, {"artifact_consumers": artifact}), patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": "/owned/artifacts"}):
            self.run_operation()
        artifact.verify_runtime_container.assert_called_once_with(self.container, "b" * 40)
        self.assertEqual(self.context.env["WEAVE_BACKEND_IMAGE"], self.image)
        self.assert_cleanup()

    def test_wrong_artifact_blocks_before_any_bootstrap_authority_is_created(self):
        artifact = types.ModuleType("artifact_consumers")
        artifact.ArtifactError = type("ArtifactError", (ValueError,), {})
        artifact.verify_runtime_container = Mock(side_effect=artifact.ArtifactError("wrong-artifact"))
        with patch.dict(sys.modules, {"artifact_consumers": artifact}), patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": "/owned/artifacts"}):
            with self.assertRaisesRegex(ContractError, "artifact differs from the candidate composition"):
                self.run_operation()
        self.override.assert_not_called()
        self.request.assert_not_called()
        self.assertEqual(list(self.context.secret_root.iterdir()), [])
        self.assertFalse(self.evidence.exists())

    def test_existing_space_is_verified_without_new_identity_or_membership_inputs(self):
        self.result["outcome"] = "VERIFIED"
        self.run_operation()
        self.assertEqual(json.loads(self.evidence.read_text())["outcome"], "VERIFIED")
        self.assert_cleanup()

    def test_conflict_restores_authority_and_removes_stale_evidence(self):
        self.evidence.parent.mkdir()
        self.evidence.write_text('{"status":"passed"}')
        self.request.side_effect = ContractError("Space setup prerequisite failed (HTTP 409)")
        with self.assertRaisesRegex(ContractError, "409"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assert_cleanup()

    def test_exception_during_override_still_restores_canonical_backend(self):
        self.override.side_effect = RuntimeError("private provider response")
        with self.assertRaisesRegex(ContractError, "canonical authority was restored"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assert_cleanup()

    def test_graceful_termination_restores_authority_and_previous_signal_handler(self):
        previous = signal.getsignal(signal.SIGTERM)
        self.request.side_effect = lambda *args: signal.raise_signal(signal.SIGTERM)
        with self.assertRaisesRegex(ContractError, "interrupted"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous)
        self.assert_cleanup()

    def test_termination_during_cleanup_cannot_interrupt_authority_removal(self):
        previous = signal.getsignal(signal.SIGTERM)
        def restore(context, **kwargs):
            signal.raise_signal(signal.SIGTERM)
            return self.container
        self.restore.side_effect = restore
        self.run_operation()
        self.assertTrue(self.evidence.is_file())
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous)
        self.assert_cleanup()

    def test_interrupted_bootstrap_process_is_reaped_before_canonical_restoration(self):
        process = types.SimpleNamespace(pid=12345, returncode=0,
                                       communicate=Mock(side_effect=[ContractError("interrupted"), (None, None)]))
        with runtime.bootstrap_termination_cleanup(), patch.object(runtime, "compose_environment", return_value={}), \
                patch.object(runtime.subprocess, "Popen", return_value=process), patch.object(runtime.os, "killpg") as terminate:
            with self.assertRaisesRegex(ContractError, "interrupted"):
                runtime._bootstrap_process(self.context, ["docker", "compose", "fixture"])
        terminate.assert_called_once_with(12345, signal.SIGTERM)
        self.assertEqual(process.communicate.call_count, 2)

    def test_blocked_real_restoration_process_is_killed_and_private_authority_removed(self):
        pid_file = self.context.root / "blocked-restoration.pid"
        code = ("import os,signal,time; "
                "signal.signal(signal.SIGTERM,signal.SIG_IGN); "
                f"open({str(pid_file)!r},'w').write(str(os.getpid())); "
                "time.sleep(60)")
        self.context.compose_base_command = [sys.executable, "-c", code]
        self.restore.side_effect = CANONICAL_BACKEND
        started = time.monotonic()
        with patch.object(runtime, "BOOTSTRAP_RESTORATION_BUDGET_SECONDS", 1), \
                patch.object(runtime, "compose_environment", return_value=dict(os.environ)):
            with self.assertRaisesRegex(ContractError, "exceptional authority"):
                self.run_operation()
        self.assertLess(time.monotonic() - started, 3)
        self.assertTrue(pid_file.is_file(), "The real blocked subprocess must have started")
        pid = int(pid_file.read_text())
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            pass
        else:
            os.kill(pid, signal.SIGKILL)
            self.fail("The blocked restoration child was not reaped")
        self.assertFalse(self.evidence.exists())
        self.disabled.assert_not_called()
        self.assert_cleanup()

    def test_canonical_compose_and_inspections_share_one_absolute_deadline(self):
        self.context.compose_base_command = ["docker", "compose", "--project-name", "owned"]
        self.context.env.update(WEAVE_COMPOSE_PROJECT="owned", WEAVE_RESOURCE_PREFIX="owned")
        container = {**self.container, "Config": {"Env": [], "Labels": {
            "com.docker.compose.project": "owned", "com.docker.compose.service": "backend",
            "com.massimotter.weave.namespace": "owned"}}}
        results = [types.SimpleNamespace(stdout=""), types.SimpleNamespace(stdout="f" * 64),
                   types.SimpleNamespace(stdout=json.dumps([container]))]
        deadline = time.monotonic() + 220
        with patch.object(runtime, "_bootstrap_process", side_effect=results) as process:
            self.assertEqual(CANONICAL_BACKEND(self.context, deadline=deadline), container)
        self.assertEqual([call.kwargs["deadline"] for call in process.call_args_list], [deadline] * 3)
        self.assertEqual([call.kwargs["timeout_seconds"] for call in process.call_args_list], [190, 10, 10])

    def test_complete_endpoint_probe_has_absolute_deadline_and_restores_alarm(self):
        previous = signal.getsignal(signal.SIGALRM)
        started = time.monotonic()
        with self.assertRaisesRegex(ContractError, "absence probe exceeded"):
            with runtime.bootstrap_probe_deadline(time.monotonic() + 0.05):
                time.sleep(1)
        self.assertLess(time.monotonic() - started, 0.5)
        self.assertEqual(signal.getsignal(signal.SIGALRM), previous)
        self.assertEqual(signal.getitimer(signal.ITIMER_REAL), (0.0, 0.0))

    def test_cleanup_endpoint_or_image_failure_never_produces_evidence(self):
        self.disabled.return_value = False
        with self.assertRaisesRegex(ContractError, "exceptional authority"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assert_cleanup()

    def test_image_change_during_restore_fails(self):
        self.restore.return_value = {**self.container, "Image": "sha256:" + "f" * 64}
        with self.assertRaisesRegex(ContractError, "exceptional authority"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assert_cleanup()

    def test_retained_bootstrap_mount_prevents_success(self):
        self.restore.return_value = {**self.container, "Mounts": [{"Destination": bootstrap.MOUNT}]}
        with self.assertRaisesRegex(ContractError, "exceptional authority"):
            self.run_operation()
        self.assertFalse(self.evidence.exists())
        self.assert_cleanup()

    def test_forbidden_environments_and_unisolated_e2e_do_not_mutate(self):
        for environment in ("dev", "prod", "e2e"):
            self.context.environment = environment
            with self.assertRaisesRegex(ContractError, "owned isolated"):
                self.run_operation()
        self.prepare.assert_not_called()
        self.override.assert_not_called()

    def test_isolated_e2e_requires_existing_owned_resources(self):
        self.context.environment, self.context.isolated_namespace = "e2e", "weave-e2e-owned"
        with patch.object(runtime, "resource_inventory", return_value={("network", "owned")}), patch.object(runtime, "inspect_resource", return_value=(True, False)):
            with self.assertRaisesRegex(ContractError, "existing owned"):
                self.run_operation()
        self.override.assert_not_called()
        self.restore.assert_not_called()
        self.prepare.assert_not_called()

    def test_isolated_e2e_uses_same_bounded_lifecycle(self):
        self.context.environment, self.context.isolated_namespace = "e2e", "weave-e2e-owned"
        with patch.object(runtime, "resource_inventory", return_value={("network", "owned")}), patch.object(runtime, "inspect_resource", return_value=(True, True)):
            self.run_operation()
        configuration = json.loads(self.override_contents[0]["services"]["backend"]["environment"]["SPRING_APPLICATION_JSON"])
        self.assertEqual(configuration["weave"]["spaces"]["bootstrap"]["environment"], "e2e")
        self.assert_cleanup()

    def test_shared_session_lock_blocks_competing_reset_or_owner_operation(self):
        with runtime._dogfood_session_lock(self.context):
            with self.assertRaisesRegex(ContractError, "another dogfood"):
                self.run_operation()
        self.prepare.assert_not_called()
        self.override.assert_not_called()

    def test_identity_readiness_failure_blocks_before_temporary_authority(self):
        self.readiness.side_effect = ContractError("identity readiness is unavailable")
        with self.assertRaisesRegex(ContractError, "identity readiness"):
            self.run_operation()
        self.override.assert_not_called()
        self.restore.assert_not_called()
        self.assertEqual(list(self.context.secret_root.iterdir()), [])

    def test_bootstrap_boundary_detects_space_mount_nested_or_flat_json_and_malformed(self):
        for environment in (
            'SPRING_APPLICATION_JSON={"weave":{"spaces":{"bootstrap":{"enabled":true}}}}',
            'SPRING_APPLICATION_JSON={"weave.spaces.bootstrap.enabled":true}',
            'SPRING_APPLICATION_JSON=invalid',
            'WEAVE_SPACES_BOOTSTRAP_ENABLED=true',
        ):
            self.assertTrue(runtime._bootstrap_boundary_present({"Config": {"Env": [environment]}}))
        self.assertTrue(runtime._bootstrap_boundary_present({"Mounts": [{"Destination": bootstrap.MOUNT}]}))
        self.assertFalse(runtime._bootstrap_boundary_present({"Config": {"Env": ['SPRING_APPLICATION_JSON={"normal":true}']}}))

    def test_request_uses_only_header_and_empty_body_and_whitelists_result(self):
        self.stack.close()
        response = types.SimpleNamespace(status=200, read=lambda maximum: json.dumps({**self.result, "ownerIdentity": "private"}).encode())
        response_context = contextlib.nullcontext(response)
        with patch.object(bootstrap, "_open", return_value=response_context) as opened:
            self.context.env["WEAVE_API_ORIGIN"] = "https://weave.test"
            result = bootstrap._request(self.context, "private-token")
        request = opened.call_args.args[1]
        self.assertEqual(request.data, b"{}")
        self.assertEqual(request.get_header(bootstrap.HEADER.capitalize()), "private-token")
        self.assertNotIn("private-token", request.full_url)
        self.assertNotIn("ownerIdentity", result)

    def test_request_rejects_partial_or_invalid_owner_access_without_private_diagnostics(self):
        self.stack.close()
        self.context.env["WEAVE_API_ORIGIN"] = "https://weave.test"
        for changes in ({"ownerAccessVerified": False}, {"filesReady": False}, {"calendarReady": False},
                        {"supportSafe": False}, {"containsSecretValues": 0}, {"outcome": {}},
                        {"spaceRef": "other-space"}):
            payload = {**self.result, **changes, "ownerIdentity": "never-in-diagnostics"}
            response = types.SimpleNamespace(status=200, read=lambda maximum: json.dumps(payload).encode())
            with patch.object(bootstrap, "_open", return_value=contextlib.nullcontext(response)):
                with self.assertRaisesRegex(ContractError, "did not verify") as error:
                    bootstrap._request(self.context, "private-token")
            self.assertNotIn("never-in-diagnostics", str(error.exception))

    def test_request_does_not_follow_redirect_or_publish_error_body(self):
        self.stack.close()
        self.assertIsNone(bootstrap_https.NoRedirect().redirect_request(None, None, 307, "redirect", {}, "https://other.test"))
        self.context.env["WEAVE_API_ORIGIN"] = "https://weave.test"
        error = bootstrap.urllib.error.HTTPError("https://weave.test", 409, "private-owner", {}, io.BytesIO(b"private-owner"))
        with patch.object(bootstrap, "_open", side_effect=error):
            with self.assertRaisesRegex(ContractError, "HTTP 409") as observed:
                bootstrap._request(self.context, "private-token")
        self.assertNotIn("private-owner", str(observed.exception))

    def test_identity_rejects_dirty_or_mismatched_source_before_mutation(self):
        self.stack.close()
        specs = self.context.repository_root / "specs"
        specs.mkdir()
        (specs / "weave-specs.lock.json").write_text(json.dumps({"specCorpus": {"gitCommit": "e" * 40}}))
        self.context.env.update(WEAVE_CANDIDATE_COMMIT="b" * 40, WEAVE_SPEC_COMMIT="e" * 40)
        with patch.object(bootstrap, "_git", side_effect=["b" * 40, "d" * 40, " M modified.py"]):
            with self.assertRaisesRegex(ContractError, "clean exact"):
                bootstrap._identity(self.context)

    def test_identity_accepts_reused_producer_only_in_current_verified_composition(self):
        self.stack.close()
        specs = self.context.repository_root / "specs"
        specs.mkdir()
        (specs / "weave-specs.lock.json").write_text(json.dumps({"specCorpus": {"gitCommit": "e" * 40}}))
        self.context.env.update(WEAVE_CANDIDATE_COMMIT="b" * 40, WEAVE_SPEC_COMMIT="e" * 40,
                                WEAVE_IMAGE_SOURCE_COMMIT="f" * 40)
        artifact = types.ModuleType("artifact_consumers")
        artifact.ArtifactError = type("ArtifactError", (ValueError,), {})
        artifact.candidate_composition = Mock(return_value={"components": {"runtime-linux-amd64": {"sourceCommit": "f" * 40}}})
        with patch.dict(sys.modules, {"artifact_consumers": artifact}), \
                patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": "/owned/artifacts"}), \
                patch.object(bootstrap, "_git", side_effect=["b" * 40, "d" * 40, ""]) as git:
            self.assertEqual(bootstrap._identity(self.context),
                             {"candidateCommit": "b" * 40, "sourceTree": "d" * 40, "specificationCommit": "e" * 40})
        self.assertEqual(git.call_count, 3)
        artifact.candidate_composition.assert_called_once_with("b" * 40)
        artifact.candidate_composition.return_value["components"]["runtime-linux-amd64"]["sourceCommit"] = "a" * 40
        with patch.dict(sys.modules, {"artifact_consumers": artifact}), \
                patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": "/owned/artifacts"}), \
                patch.object(bootstrap, "_git", side_effect=["b" * 40, "d" * 40, ""]), \
                self.assertRaisesRegex(ContractError, "source differs from the candidate composition"):
            bootstrap._identity(self.context)
        with patch.object(bootstrap, "_git", side_effect=["b" * 40, "d" * 40, "", "f" * 40]):
            with self.assertRaisesRegex(ContractError, "source differs"):
                bootstrap._identity(self.context)


if __name__ == "__main__":
    unittest.main(verbosity=2)
