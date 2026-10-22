#!/usr/bin/env python3
"""Offline negatives for the protected main promotion evidence gate."""

import os
import copy
import io
import json
import zipfile
from unittest.mock import patch
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.verify_main_promotion_evidence import (
    artifact_json,
    attempt_artifact,
    native_qualification_passed,
    preparation_passed,
    owner_workspace_passed,
    qualified_run,
    prepared_run,
    deployed_run,
    matches_promotion_pr,
    owner_human_pass,
    parse_human_comment,
    required_jobs_passed,
    resolve_merge_group,
)

SHA = "a" * 40
OTHER = "b" * 40
TOOLCHAIN = {
    "frameworkVersion": "3.41.6", "channel": "stable", "frameworkRevision": "d" * 40,
    "engineRevision": "e" * 40, "dartSdkVersion": "3.11.4", "xcodeVersion": "26.5",
    "xcodeBuild": "17F42", "iosSimulatorSdkVersion": "26.5", "rustToolchain": "1.95.0",
    "rustCompilerVersion": "rustc 1.95.0 (1234567 2026-01-01)",
}

def owner_workspace_proof():
    return {
        "schemaVersion": "weave.default-space-preparation/v1", "candidateCommit": SHA,
        "sourceTree": OTHER, "specificationCommit": "c" * 40,
        "status": "passed", "spaceRef": "workspace-default", "outcome": "CREATED",
        **{key: True for key in ("ownerAccessVerified", "filesReady", "calendarReady",
                                "bootstrapAuthorityAbsent", "bootstrapMountAbsent",
                                "tokenAbsent", "canonicalImageUnchanged", "supportSafe")},
        "containsSecretValues": False,
    }


def dual_ios_proof():
    start, end = "2026-10-12T10:00:00Z", "2026-10-12T10:05:00Z"
    proof = {
        "status": "passed", "appBuildSha256": "f" * 64,
        "actors": [
            {"role": role, "simulatorId": uid, "processId": pid,
             "identityDigest": identity * 64, "matrixDeviceDigest": device * 64,
             "storageDigest": storage * 64, "startedAt": start, "endedAt": end}
            for role, uid, pid, identity, device, storage in (
                ("A", "00000000-0000-0000-0000-000000000001", 101, "1", "3", "5"),
                ("B", "00000000-0000-0000-0000-000000000002", 102, "2", "4", "6"))
        ],
        "overlap": {"startedAt": "2026-10-12T10:01:00Z", "endedAt": "2026-10-12T10:04:00Z"},
        "scenarios": {name: {"status": "passed", "evidence": "flutter-ui", "actors": ["A", "B"]}
                      for name in ("authentication", "chat", "calendar", "files", "authorization", "recovery", "accessibility")},
        "encryption": {"policyRequired": False, "status": "not-required", "plaintextFallback": False},
        "cleanupComplete": True,
    }
    proof["scenarios"]["authorization"].update({
        "deniedIdentityDigest": "7" * 64,
        "resourceDigest": "8" * 64,
        "denials": {"chat": "passed", "files": "passed", "calendar": "passed"},
    })
    proof["recovery"] = {
        "actors": [{
            "role": actor["role"], "initialProcessId": actor["processId"],
            "restoredProcessId": actor["processId"] + 100,
            "initialStartedAt": actor["startedAt"], "initialEndedAt": actor["endedAt"],
            "restoredStartedAt": "2026-10-12T10:06:00Z", "restoredEndedAt": "2026-10-12T10:10:00Z",
            "previousProcessAbsent": True, "expiryDenied": True,
            "refreshObserved": True, "logoutIndependent": True,
            **{"restored" + key[0].upper() + key[1:]: actor[key]
               for key in ("identityDigest", "matrixDeviceDigest", "storageDigest")},
        } for actor in proof["actors"]],
        "initialOverlap": dict(proof["overlap"]),
        "network": {
            "interruptedActor": "A", "affectedConnections": 1,
            "actorAForwardedBytes": 100, "actorBForwardedBytesDuringInterruption": 50,
            **{key: True for key in ("userApiFailureObserved", "matrixSendFailureObserved",
                                     "peerUiContinuityObserved", "restoreObserved",
                                     "matrixRetryObserved", "peerReceptionObserved")},
        },
        "currentGrantRevocation": {
            "status": "passed", "evidence": "server-denial-and-flutter-readback",
            "resourceDigest": "8" * 64, "deniedIdentityDigest": "2" * 64,
            "denied": True, "regrantRestored": True, "uiReadback": True,
        },
    }
    return proof


HUMAN = "\n".join(
    [
        "Weave human test v1",
        f"dogfood: {SHA}",
        "sign-in: passed",
        "chat: passed",
        "files: passed",
        "calendar: passed",
        "accessibility: passed",
        "revoke-regrant: passed",
        "identity-continuity: passed",
        "notes: No private content included.",
    ]
)


class MainPromotionEvidenceTest(unittest.TestCase):
    def test_physical_preparation_rejects_a_superseded_qualified_ancestor(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/ios-dogfood.yml').read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            checkout = root / 'checkout'
            checkout.mkdir()
            def git(*args: str) -> str:
                return subprocess.check_output(['git', '-C', str(checkout), *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            subprocess.run(['git', 'init', '--bare', '--quiet', str(root / 'origin.git')], check=True)
            git('init', '--quiet')
            git('config', 'user.name', 'Disposable preparation test')
            git('config', 'user.email', 'gate@example.invalid')
            git('-c', 'commit.gpgsign=false', 'commit', '--allow-empty', '-m', 'Qualified candidate')
            stale = git('rev-parse', 'HEAD')
            git('-c', 'commit.gpgsign=false', 'commit', '--allow-empty', '-m', 'New Dogfood candidate')
            current = git('rev-parse', 'HEAD')
            git('remote', 'add', 'origin', str(root / 'origin.git'))
            git('push', '--quiet', 'origin', 'HEAD:refs/heads/dogfood')
            for name, boundary in (
                ('Verify current protected dogfood candidate', None),
                ('Start dogfood twice and prove TLS identity is stable', './gradlew'),
                ('Build, sign, install in place, and launch on physical iPhone', 'tools/dogfood_iphone_entry.sh'),
            ):
                step = workflow.split(f'      - name: {name}\n', 1)[1].split('      - name:', 1)[0]
                script = '\n'.join(line[10:] for line in step.split('        run: |\n', 1)[1].splitlines())
                if boundary:
                    script = script.split(boundary, 1)[0]
                for candidate, expected in ((stale, 1), (current, 0)):
                    git('checkout', '--quiet', '--detach', candidate)
                    result = subprocess.run(['bash', '-c', script], cwd=checkout,
                                            env={**os.environ, 'DOGFOOD_COMMIT': candidate},
                                            capture_output=True, text=True)
                    self.assertEqual(result.returncode, expected, (name, result.stderr))

    def test_native_qualification_requires_both_platforms_exact_identity_and_cleanup(self) -> None:
        valid = {
            "schemaVersion": "weave.native-qualification/v2", "candidateCommit": SHA,
            "sourceTree": OTHER, "specificationCommit": "c" * 40,
            "macos": {"status": "passed"}, "toolchain": TOOLCHAIN,
            "iosSimulator": {"status": "passed", "rustTarget": "aarch64-apple-ios-sim", "collaboration": dual_ios_proof()},
            "cleanupComplete": True, "supportSafe": True, "containsSecretValues": False,
        }
        self.assertTrue(native_qualification_passed(valid, SHA, OTHER, "c" * 40))
        for key in valid:
            broken = copy.deepcopy(valid)
            del broken[key]
            self.assertFalse(native_qualification_passed(broken, SHA, OTHER, "c" * 40), key)
        for key, value in (("candidateCommit", OTHER), ("sourceTree", SHA),
                           ("specificationCommit", SHA), ("cleanupComplete", False)):
            self.assertFalse(native_qualification_passed({**valid, key: value}, SHA, OTHER, "c" * 40))
        for outcome in ("failed", "not-available", "skipped"):
            self.assertFalse(native_qualification_passed({**valid, "iosSimulator": {
                "status": outcome, "rustTarget": "aarch64-apple-ios-sim"}}, SHA, OTHER, "c" * 40))
        for platform in ("macos", "iosSimulator"):
            for malformed in (None, [], "passed"):
                self.assertFalse(native_qualification_passed({**valid, platform: malformed}, SHA, OTHER, "c" * 40))

    def test_dual_ios_rejects_single_sequential_reused_and_api_only_evidence(self) -> None:
        from tools.verify_main_promotion_evidence import dual_ios_collaboration_passed
        self.assertTrue(dual_ios_collaboration_passed(dual_ios_proof()))
        bad = []
        single = dual_ios_proof(); single["actors"] = single["actors"][:1]; bad.append(single)
        for key in ("simulatorId", "processId", "identityDigest", "matrixDeviceDigest", "storageDigest"):
            reused = dual_ios_proof(); reused["actors"][1][key] = reused["actors"][0][key]; bad.append(reused)
        sequential = dual_ios_proof(); sequential["actors"][1]["startedAt"] = "2026-10-12T10:04:30Z"; bad.append(sequential)
        for name in ("authentication", "chat", "calendar", "files", "authorization", "recovery", "accessibility"):
            missing = dual_ios_proof(); del missing["scenarios"][name]; bad.append(missing)
            failed = dual_ios_proof(); failed["scenarios"][name]["status"] = "not-run"; bad.append(failed)
            api = dual_ios_proof(); api["scenarios"][name]["evidence"] = "api-only"; bad.append(api)
        for role in ([], {}, None):
            malformed = dual_ios_proof(); malformed["actors"][0]["role"] = role; bad.append(malformed)
        for name in ("authentication", "chat", "calendar", "files", "authorization", "recovery", "accessibility"):
            unbound = dual_ios_proof(); unbound["scenarios"][name].pop("actors"); bad.append(unbound)
        for key in ("deniedIdentityDigest", "denials"):
            missing_denial = dual_ios_proof(); missing_denial["scenarios"]["authorization"].pop(key); bad.append(missing_denial)
        same_identity = dual_ios_proof(); same_identity["scenarios"]["authorization"]["deniedIdentityDigest"] = "1" * 64; bad.append(same_identity)
        unencrypted = dual_ios_proof(); unencrypted["encryption"]["policyRequired"] = True; bad.append(unencrypted)
        downgrade = dual_ios_proof(); downgrade["encryption"]["plaintextFallback"] = True; bad.append(downgrade)
        dirty = dual_ios_proof(); dirty["cleanupComplete"] = False; bad.append(dirty)
        for index, proof in enumerate(bad):
            self.assertFalse(dual_ios_collaboration_passed(proof), index)

    def test_historical_single_simulator_schema_cannot_qualify_new_release(self) -> None:
        value = {"schemaVersion": "weave.native-qualification/v1", "candidateCommit": SHA,
                 "sourceTree": OTHER, "specificationCommit": "c" * 40, "toolchain": TOOLCHAIN,
                 "macos": {"status": "passed"}, "iosSimulator": {"status": "passed", "rustTarget": "aarch64-apple-ios-sim"},
                 "cleanupComplete": True, "supportSafe": True, "containsSecretValues": False}
        self.assertFalse(native_qualification_passed(value, SHA, OTHER, "c" * 40))

    def test_dual_recovery_requires_real_restart_transport_and_current_resource_proof(self) -> None:
        from tools.verify_main_promotion_evidence import dual_ios_collaboration_passed
        valid = dual_ios_proof()
        self.assertTrue(dual_ios_collaboration_passed(valid))
        mutations = [
            (("recovery",), None),
            (("recovery", "actors"), []),
            (("recovery", "actors", 0, "role"), []),
            (("recovery", "actors", 0, "initialProcessId"), True),
            (("recovery", "actors", 0, "restoredProcessId"), 101),
            (("recovery", "actors", 1, "restoredProcessId"), 201),
            (("recovery", "actors", 0, "restoredStartedAt"), "2026-10-12T10:04:00Z"),
            (("recovery", "actors", 0, "restoredEndedAt"), "2026-10-12T10:10:00"),
            (("recovery", "initialOverlap", "endedAt"), "2026-10-12T10:06:00Z"),
            (("recovery", "network", "interruptedActor"), "B"),
            (("recovery", "currentGrantRevocation", "resourceDigest"), "9" * 64),
            (("recovery", "currentGrantRevocation", "deniedIdentityDigest"), "1" * 64),
            (("recovery", "currentGrantRevocation", "evidence"), "api-only"),
        ]
        for key in ("restoredIdentityDigest", "restoredMatrixDeviceDigest", "restoredStorageDigest"):
            mutations.append((("recovery", "actors", 0, key), "9" * 64))
        for key in ("previousProcessAbsent", "expiryDenied", "refreshObserved", "logoutIndependent"):
            mutations.append((("recovery", "actors", 0, key), False))
        for key in ("affectedConnections", "actorAForwardedBytes", "actorBForwardedBytesDuringInterruption"):
            for invalid in (0, True, "100"):
                mutations.append((("recovery", "network", key), invalid))
        for key in ("userApiFailureObserved", "matrixSendFailureObserved", "peerUiContinuityObserved",
                    "restoreObserved", "matrixRetryObserved", "peerReceptionObserved"):
            mutations.append((("recovery", "network", key), False))
        for key in ("denied", "regrantRestored", "uiReadback"):
            mutations.append((("recovery", "currentGrantRevocation", key), False))
        for path, replacement in mutations:
            with self.subTest(path=path, replacement=replacement):
                malformed = copy.deepcopy(valid)
                target = malformed
                for key in path[:-1]:
                    target = target[key]
                target[path[-1]] = replacement
                self.assertFalse(dual_ios_collaboration_passed(malformed))

    def test_native_toolchain_missing_malformed_or_inconsistent_fails_closed(self) -> None:
        from tools.verify_main_promotion_evidence import native_toolchain_recorded
        self.assertTrue(native_toolchain_recorded(TOOLCHAIN))
        for key in TOOLCHAIN:
            self.assertFalse(native_toolchain_recorded({k: v for k, v in TOOLCHAIN.items() if k != key}), key)
            self.assertFalse(native_toolchain_recorded({**TOOLCHAIN, key: True}), key)
        for invalid in (None, [], {**TOOLCHAIN, "engineRevision": "unknown"},
                        {**TOOLCHAIN, "rustCompilerVersion": "rustc 1.94.0 (1234567 2026-01-01)"}):
            self.assertFalse(native_toolchain_recorded(invalid))

    def test_owner_workspace_requires_current_access_and_removed_temporary_authority(self) -> None:
        valid = owner_workspace_proof()
        self.assertTrue(owner_workspace_passed(valid, SHA, OTHER, "c" * 40))
        self.assertTrue(owner_workspace_passed({**valid, "outcome": "VERIFIED"}, SHA, OTHER, "c" * 40))
        for key in valid:
            broken = {name: value for name, value in valid.items() if name != key}
            self.assertFalse(owner_workspace_passed(broken, SHA, OTHER, "c" * 40), key)
            if type(valid[key]) is bool:
                self.assertFalse(owner_workspace_passed({**valid, key: not valid[key]}, SHA, OTHER, "c" * 40), key)
                self.assertFalse(owner_workspace_passed({**valid, key: int(valid[key])}, SHA, OTHER, "c" * 40), key)
        for broken in (None, [], {**valid, "candidateCommit": OTHER},
                       {**valid, "sourceTree": SHA}, {**valid, "specificationCommit": SHA},
                       {**valid, "outcome": "REGRANTED"}, {**valid, "spaceRef": "another-space"}):
            self.assertFalse(owner_workspace_passed(broken, SHA, OTHER, "c" * 40))

    def test_preparation_requires_current_qualification_and_exact_installed_identity(self) -> None:
        valid = {"schemaVersion": "weave.hil-preparation/v2", "candidateCommit": SHA,
                 "sourceTree": OTHER, "specificationCommit": "c" * 40,
                 "qualificationRunId": 123, "qualificationRunAttempt": 1, "status": "passed", "install": "update_in_place",
                 "toolchain": {**TOOLCHAIN, "iosDeviceSdkVersion": "26.5"},
                 "ownerWorkspace": owner_workspace_proof(),
                 "supportSafe": True, "containsSecretValues": False}
        self.assertTrue(preparation_passed(valid, SHA, OTHER, "c" * 40, 123, 1))
        self.assertFalse(preparation_passed(valid, SHA, OTHER, "c" * 40, 124, 1))
        for key in valid:
            broken = {name: value for name, value in valid.items() if name != key}
            self.assertFalse(preparation_passed(broken, SHA, OTHER, "c" * 40, 123, 1), key)
        self.assertFalse(preparation_passed({**valid, "status": "failed"}, SHA, OTHER, "c" * 40, 123, 1))
        for toolchain in (None, {}, TOOLCHAIN,
                          {**TOOLCHAIN, "iosDeviceSdkVersion": "26.4"},
                          {**TOOLCHAIN, "iosDeviceSdkVersion": True}):
            self.assertFalse(preparation_passed({**valid, "toolchain": toolchain}, SHA, OTHER, "c" * 40, 123, 1))

    def test_latest_preparation_requires_exact_protected_producer_before_artifact_trust(self) -> None:
        qualification = {"id": 123, "run_attempt": 1, "updated_at": "2026-10-07T11:00:00Z"}
        trusted = {"id": 456, "run_attempt": 1, "display_title": f"Prepare Human Test {SHA}",
                   "event": "workflow_dispatch", "path": ".github/workflows/ios-dogfood.yml",
                   "head_sha": SHA, "head_branch": "dogfood", "status": "completed",
                   "conclusion": "success", "created_at": "2026-10-07T12:00:00Z",
                   "run_started_at": "2026-10-07T12:00:00Z"}
        with patch("tools.verify_main_promotion_evidence.paged", return_value=[trusted]), \
                patch("tools.verify_main_promotion_evidence.download_artifact", return_value=b"fixture"), \
                patch("tools.verify_main_promotion_evidence.artifact_json", return_value={}), \
                patch("tools.verify_main_promotion_evidence.preparation_passed", return_value=True):
            self.assertEqual(prepared_run("masssi164/weave", SHA, OTHER, "c" * 40,
                                          qualification, "fixture-token"), trusted)
        for field, value in (("head_sha", OTHER), ("head_sha", None),
                             ("head_branch", "dev"), ("head_branch", "hotfix/claim"),
                             ("head_branch", None), ("conclusion", "failure"),
                             ("status", "in_progress")):
            latest = {**trusted, "id": 457, "created_at": "2026-10-07T13:00:00Z", field: value}
            if value is None:
                del latest[field]
            with self.subTest(field=field, value=value), \
                    patch("tools.verify_main_promotion_evidence.paged", return_value=[trusted, latest]), \
                    patch("tools.verify_main_promotion_evidence.download_artifact", return_value=b"fixture") as download, \
                    patch("tools.verify_main_promotion_evidence.artifact_json", return_value={}), \
                    patch("tools.verify_main_promotion_evidence.preparation_passed", return_value=True):
                with self.assertRaises(ValueError):
                    prepared_run("masssi164/weave", SHA, OTHER, "c" * 40, qualification, "fixture-token")
                download.assert_not_called()

    def test_hotfix_name_cannot_skip_dogfood_tree_or_human_evidence(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/main-promotion-gate.yml").read_text()
        for name in ("Verify exact qualified dogfood tree", "Verify exact dogfood and separate human result"):
            step = workflow.split(f"      - name: {name}\n", 1)[1].split("      - name:", 1)[0]
            self.assertNotIn("        if:", step, name)
        step = workflow.split("      - name: Verify exact qualified dogfood tree\n", 1)[1].split("      - name:", 1)[0]
        script = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args: str) -> str:
                return subprocess.check_output(["git", "-C", directory, *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            git("init", "--quiet")
            git("config", "user.name", "Disposable hotfix gate test")
            git("config", "user.email", "gate@example.invalid")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Qualified dogfood")
            qualified = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/dogfood", qualified)
            git("checkout", "-b", "hotfix/unqualified", "--quiet")
            Path(directory, "unqualified-change").write_text("source has changed")
            git("add", "unqualified-change")
            git("-c", "commit.gpgsign=false", "commit", "-m", "Unqualified hotfix")
            result = subprocess.run(["bash", "-c", script], cwd=directory,
                                    env={**os.environ, "CANDIDATE": git("rev-parse", "HEAD")},
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0, result.stdout)
            self.assertIn("exact protected Dogfood tree", result.stderr)

    def test_preparation_cannot_reuse_an_earlier_qualification_attempt(self) -> None:
        qualification = {"id": 123, "run_attempt": 2, "updated_at": "2026-10-07T13:00:00Z"}
        run = {"id": 456, "run_attempt": 1, "head_sha": SHA, "head_branch": "dogfood", "display_title": f"Prepare Human Test {SHA}",
               "event": "workflow_dispatch", "path": ".github/workflows/ios-dogfood.yml",
               "status": "completed", "conclusion": "success", "created_at": "2026-10-07T12:00:00Z",
               "run_started_at": "2026-10-07T12:00:00Z"}
        with patch("tools.verify_main_promotion_evidence.paged", return_value=[run]):
            with self.assertRaisesRegex(ValueError, "start after"):
                prepared_run("masssi164/weave", SHA, OTHER, "c" * 40, qualification, "fixture-token")
        valid = {"schemaVersion": "weave.hil-preparation/v2", "candidateCommit": SHA,
                 "sourceTree": OTHER, "specificationCommit": "c" * 40,
                 "qualificationRunId": 123, "qualificationRunAttempt": 1,
                 "status": "passed", "install": "update_in_place",
                 "toolchain": {**TOOLCHAIN, "iosDeviceSdkVersion": "26.5"},
                 "ownerWorkspace": owner_workspace_proof(),
                 "supportSafe": True, "containsSecretValues": False}
        self.assertTrue(preparation_passed(valid, SHA, OTHER, "c" * 40, 123, 1))
        self.assertFalse(preparation_passed(valid, SHA, OTHER, "c" * 40, 123, 2))

    def test_rerun_selects_only_its_attempt_artifact(self) -> None:
        self.assertEqual(attempt_artifact("full-compose-e2e", SHA, {"run_attempt": 2}),
                         f"full-compose-e2e-{SHA}-attempt-2")
        for attempt in (None, 0, True, "2"):
            with self.assertRaises(ValueError):
                attempt_artifact("full-compose-e2e", SHA, {"run_attempt": attempt})

    def test_artifact_rejects_ambiguous_or_missing_native_proof(self) -> None:
        def archive(names: list[str]) -> bytes:
            result = io.BytesIO()
            with zipfile.ZipFile(result, "w") as bundle:
                for name in names:
                    bundle.writestr(name, json.dumps({"status": "passed"}))
            return result.getvalue()
        self.assertEqual(artifact_json(archive(["run/native-qualification.json"]),
                                       "native-qualification.json"), {"status": "passed"})
        for names in ([], ["one/native-qualification.json", "two/native-qualification.json"]):
            with self.assertRaises(ValueError):
                artifact_json(archive(names), "native-qualification.json")

    def test_qualification_validates_native_cleanup_and_deployment_artifacts(self) -> None:
        tree, spec = OTHER, "c" * 40
        run = {"id": 123, "run_attempt": 1, "head_sha": SHA, "head_branch": "dogfood", "event": "push",
               "created_at": "2026-10-07T11:00:00Z", "status": "completed", "conclusion": "success",
               "path": ".github/workflows/live-stack-e2e.yml"}
        jobs = [{"name": name, "conclusion": "success"} for name in
                ("Full Compose E2E", "Deploy dogfood with Compose")]
        native = {"schemaVersion": "weave.native-qualification/v2", "candidateCommit": SHA,
                  "sourceTree": tree, "specificationCommit": spec, "macos": {"status": "passed"}, "toolchain": TOOLCHAIN,
                  "iosSimulator": {"status": "passed", "rustTarget": "aarch64-apple-ios-sim", "collaboration": dual_ios_proof()},
                  "cleanupComplete": True, "supportSafe": True, "containsSecretValues": False}
        teardown = {"candidateCommit": SHA, "dryRun": False, "ownershipLabelsVerified": True,
                    "remainingOwnedResources": 0, "remainingContainerCount": 0,
                    "remainingNetworkCount": 0, "remainingVolumeCount": 0}
        deployment = {"schemaVersion": "weave.dogfood-deployment/v1", "candidateCommit": SHA,
                      "sourceTree": tree, "specificationCommit": spec, "qualificationRunId": 123,
                      "status": "passed", "supportSafe": True, "containsSecretValues": False}
        def bundle(values: dict) -> bytes:
            result = io.BytesIO()
            with zipfile.ZipFile(result, "w") as archive:
                for name, value in values.items():
                    archive.writestr("run/" + name, json.dumps(value))
            return result.getvalue()
        def verify(native_value, teardown_value, deployment_value):
            with patch("tools.verify_main_promotion_evidence.api", return_value={"workflow_runs": [run]}), \
                 patch("tools.verify_main_promotion_evidence.paged", return_value=jobs), \
                 patch("tools.verify_main_promotion_evidence.download_artifact", side_effect=[
                     bundle({"native-qualification.json": native_value, "teardown-evidence.json": teardown_value}),
                     bundle({"deployment-result.json": deployment_value})]):
                return qualified_run("masssi164/weave", SHA, tree, spec, "fixture-token")
        self.assertEqual(verify(native, teardown, deployment), run)
        for native_value, teardown_value, deployment_value in (
            ({**native, "iosSimulator": {"status": "not-run"}}, teardown, deployment),
            (native, {**teardown, "candidateCommit": OTHER}, deployment),
            (native, {**teardown, "remainingOwnedResources": 1}, deployment),
            (native, {**teardown, "remainingOwnedResources": False}, deployment),
            (native, teardown, {**deployment, "specificationCommit": SHA}),
            (native, teardown, {**deployment, "qualificationRunId": 122}),
        ):
            with self.assertRaises(ValueError):
                verify(native_value, teardown_value, deployment_value)

    def test_unchanged_dogfood_can_promote_after_dev_advances(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/main-promotion-gate.yml").read_text()
        step = workflow.split("      - name: Verify exact qualified dogfood tree\n", 1)[1].split("      - name:", 1)[0]
        script = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args: str) -> str:
                return subprocess.check_output(["git", "-C", directory, *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            git("init", "--quiet")
            git("config", "user.name", "Disposable gate test")
            git("config", "user.email", "gate@example.invalid")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Qualified dogfood")
            candidate = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/dogfood", candidate)
            Path(directory, "new-dev-work").write_text("later work")
            git("add", "new-dev-work")
            git("-c", "commit.gpgsign=false", "commit", "-m", "Dev advances")
            git("update-ref", "refs/remotes/origin/dev", git("rev-parse", "HEAD"))
            for sha, expected in ((candidate, 0), (git("rev-parse", "HEAD"), 1)):
                result = subprocess.run(["bash", "-c", script], cwd=directory,
                                        env={**os.environ, "CANDIDATE": sha}, capture_output=True, text=True)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_queue_commit_cannot_supply_missing_hotfix_head_ancestry(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/main-promotion-gate.yml").read_text()
        step = workflow.split("      - name: Verify hotfix ancestry\n", 1)[1].split("      - name:", 1)[0]
        script = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args: str) -> str:
                return subprocess.check_output(["git", "-C", directory, *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            git("init", "--quiet")
            git("config", "user.name", "Disposable gate test")
            git("config", "user.email", "gate@example.invalid")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Old hotfix base")
            stale_head = git("rev-parse", "HEAD")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Current main")
            current_main = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/main", current_main)
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Synthetic queue candidate")
            candidate = git("rev-parse", "HEAD")
            for head, expected in ((stale_head, 1), (current_main, 0)):
                result = subprocess.run(["bash", "-c", script], cwd=directory,
                                        env={**os.environ, "CANDIDATE": candidate, "EVIDENCE_SHA": head},
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_merge_group_binds_synthetic_head_to_current_same_repo_pr(self) -> None:
        repository = "masssi164/weave"
        pr = {
            "number": 42, "state": "OPEN", "baseRefName": "main",
            "baseRepository": {"nameWithOwner": repository},
            "headRepository": {"nameWithOwner": repository},
            "headRefOid": SHA, "headRefName": "dogfood",
        }
        entry = {"headCommit": {"oid": OTHER}, "pullRequest": pr}
        self.assertEqual(resolve_merge_group([entry], OTHER, repository), pr)
        invalid = [[], [entry, entry], [{**entry, "headCommit": {"oid": SHA}}]]
        for field, value in (
            ("state", "CLOSED"), ("baseRefName", "dev"),
            ("headRepository", {"nameWithOwner": "other/weave"}),
            ("baseRepository", {"nameWithOwner": "other/weave"}),
            ("headRefOid", "invalid"), ("number", 0),
        ):
            invalid.append([{**entry, "pullRequest": {**pr, field: value}}])
        for entries in invalid:
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                resolve_merge_group(entries, OTHER, repository)

    def test_edited_failure_overrides_later_created_pass(self) -> None:
        passed = {
            "id": 2, "user": {"login": "masssi164"}, "author_association": "OWNER",
            "created_at": "2026-10-07T12:00:00Z", "updated_at": "2026-10-07T12:00:00Z",
            "body": HUMAN,
        }
        edited = {
            **passed, "id": 1, "created_at": "2026-10-07T10:00:00Z",
            "updated_at": "2026-10-07T13:00:00Z",
        }
        for body in (HUMAN.replace("chat: passed", "chat: failed"),
                     HUMAN.replace("files: passed\n", ""), HUMAN.replace(SHA, OTHER)):
            self.assertFalse(owner_human_pass([passed, {**edited, "body": body}],
                                             "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertTrue(owner_human_pass([edited], "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([edited], "masssi164", SHA, "2026-10-07T14:00:00Z"))

    def test_human_evidence_belongs_to_exact_open_main_promotion(self) -> None:
        repository = "masssi164/weave"
        pr = {
            "state": "open",
            "base": {"ref": "main", "repo": {"full_name": repository}},
            "head": {"sha": SHA, "repo": {"full_name": repository}},
        }
        self.assertTrue(matches_promotion_pr(pr, repository, SHA))
        self.assertFalse(matches_promotion_pr(pr, repository, OTHER))
        self.assertFalse(matches_promotion_pr({**pr, "state": "closed"}, repository, SHA))
        self.assertFalse(matches_promotion_pr({**pr, "base": {**pr["base"], "ref": "dev"}}, repository, SHA))
        self.assertFalse(matches_promotion_pr({**pr, "head": {**pr["head"],
            "repo": {"full_name": "other/weave"}}}, repository, SHA))

    def test_human_result_needs_exact_commit_and_every_pass(self) -> None:
        self.assertTrue(parse_human_comment(HUMAN, SHA))
        self.assertFalse(parse_human_comment(HUMAN, OTHER))
        self.assertFalse(parse_human_comment(HUMAN.replace("chat: passed", "chat: failed"), SHA))
        self.assertFalse(parse_human_comment(HUMAN.replace("files: passed\n", ""), SHA))
        self.assertFalse(parse_human_comment(HUMAN.replace("chat: passed", "chat: passed\nchat: passed"), SHA))

    def test_only_owner_comment_after_deployment_can_attest(self) -> None:
        comment = {
            "user": {"login": "masssi164"},
            "author_association": "OWNER",
            "created_at": "2026-10-07T12:00:00Z",
            "body": HUMAN,
        }
        self.assertTrue(owner_human_pass([comment], "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([comment], "masssi164", SHA, "2026-10-07T13:00:00Z"))
        self.assertFalse(owner_human_pass([comment], "another-owner", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([{**comment, "author_association": "COLLABORATOR"}],
                                          "masssi164", SHA, "2026-10-07T11:00:00Z"))
        failed_later = {**comment, "id": 2, "created_at": "2026-10-07T12:01:00Z",
                        "body": HUMAN.replace("chat: passed", "chat: failed")}
        self.assertFalse(owner_human_pass([comment, failed_later], "masssi164", SHA,
                                          "2026-10-07T11:00:00Z"))
        wrong_sha_later = {**failed_later, "body": HUMAN.replace(SHA, OTHER)}
        self.assertFalse(owner_human_pass([comment, wrong_sha_later], "masssi164", SHA,
                                          "2026-10-07T11:00:00Z"))

    def test_latest_exact_push_and_both_jobs_must_pass(self) -> None:
        run = {
            "id": 1,
            "head_sha": SHA,
            "head_branch": "dogfood",
            "event": "push",
            "created_at": "2026-10-07T11:00:00Z",
            "status": "completed",
            "conclusion": "success",
        }
        self.assertEqual(deployed_run([run], SHA), run)
        self.assertIsNone(deployed_run([run], OTHER))
        self.assertIsNone(deployed_run([run, {**run, "id": 2,
            "created_at": "2026-10-07T12:00:00Z", "conclusion": "failure"}], SHA))
        self.assertFalse(required_jobs_passed([{"name": "Full Compose E2E", "conclusion": "success"}]))
        self.assertTrue(required_jobs_passed([
            {"name": "Full Compose E2E", "conclusion": "success"},
            {"name": "Deploy dogfood with Compose", "conclusion": "success"},
        ]))

    def test_duplicate_job_cannot_shadow_a_required_failure_or_skip(self) -> None:
        jobs = [
            {"name": "Full Compose E2E", "conclusion": "success"},
            {"name": "Deploy dogfood with Compose", "conclusion": "success"},
        ]
        for conclusion in ("failure", "skipped", "success"):
            with self.subTest(conclusion=conclusion):
                self.assertFalse(required_jobs_passed([
                    *jobs,
                    {"name": "Full Compose E2E", "conclusion": conclusion},
                ]))


if __name__ == "__main__":
    unittest.main()
