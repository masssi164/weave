#!/usr/bin/env python3
"""Contract tests for the server-owned User and Admin OpenAPI consumers."""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import generate_admin_openapi_types as admin
import generate_client_user_api as client


class OpenApi31TypeProjectionTest(unittest.TestCase):
    def test_consumers_use_separate_owned_artifacts(self) -> None:
        self.assertEqual("weave-user-openapi.json", client.CONTRACT.name)
        self.assertEqual("weave-admin-openapi.json", admin.OPENAPI.name)
        self.assertTrue((client.OUTPUT / "model/protocols.dart").is_file())
        self.assertTrue((client.OUTPUT / "model/workspace_capabilities_response.dart").is_file())
        self.assertFalse((client.OUTPUT / "model/admin_control_plane_response.dart").exists())
        self.assertIn("GeneratedAdminControlPlaneResponse", admin.render())

    def test_admin_nullable_openapi_31_union_is_projected_in_typescript(self) -> None:
        self.assertEqual("string | null", admin.type_for({"type": ["null", "string"]}))

    def test_admin_free_form_object_is_not_mistaken_for_nested_map(self) -> None:
        self.assertEqual(
            "Record<string, unknown>",
            admin.type_for({"type": "object", "additionalProperties": {}}),
        )

    def test_admin_schema_reference_keeps_its_generated_name(self) -> None:
        self.assertEqual(
            "GeneratedProviderStatus",
            admin.type_for({"$ref": "#/components/schemas/ProviderStatus"}),
        )

    def test_checked_in_user_contract_has_unique_operation_ids(self) -> None:
        document = json.loads(client.CONTRACT.read_text())
        self.assertEqual("3.1.0", document.get("openapi"))
        observed: dict[str, str] = {}
        for path, path_item in document.get("paths", {}).items():
            for method, operation in path_item.items():
                if method not in {"get", "put", "post", "delete", "patch", "head", "options", "trace"}:
                    continue
                operation_id = operation.get("operationId")
                self.assertIsInstance(operation_id, str, f"{method.upper()} {path} has no operationId")
                self.assertNotIn(
                    operation_id,
                    observed,
                    f"{operation_id} is shared by {observed.get(operation_id)} and {method.upper()} {path}",
                )
                observed[operation_id] = f"{method.upper()} {path}"

    def test_checked_in_contract_contains_no_openapi_30_nullable_keyword(self) -> None:
        document = json.loads(client.CONTRACT.read_text())

        def assert_no_nullable(value: object, location: str = "$") -> None:
            if isinstance(value, dict):
                self.assertNotIn("nullable", value, location)
                for key, child in value.items():
                    assert_no_nullable(child, f"{location}.{key}")
            elif isinstance(value, list):
                for index, child in enumerate(value):
                    assert_no_nullable(child, f"{location}[{index}]")

        assert_no_nullable(document)

    def test_user_manifest_advertises_only_the_credential_free_matrix_endpoint(self) -> None:
        schemas = json.loads(client.CONTRACT.read_text())["components"]["schemas"]
        manifest = schemas["PlatformConfigResponse"]
        self.assertTrue({"oidc", "protocols", "userApiBaseUrl"}.issubset(manifest["required"]))
        self.assertEqual(
            {"matrixClientServerBaseUrl"},
            set(schemas["Protocols"]["required"]),
        )
        self.assertEqual({"matrixClientServerBaseUrl"}, set(schemas["Protocols"]["properties"]))
        self.assertEqual("uri", schemas["Protocols"]["properties"]["matrixClientServerBaseUrl"]["format"])
        generated = (client.OUTPUT / "model/protocols.dart").read_text()
        self.assertIn("required this.matrixClientServerBaseUrl", generated)
        self.assertNotIn("matrixOAuthIssuer", generated)
        self.assertNotIn("matrixOAuthClientId", generated)

    def test_current_admin_console_has_no_parallel_arc_http_transport(self) -> None:
        api_source = (admin.ROOT / "admin-console/src/api.ts").read_text()
        app_source = (admin.ROOT / "admin-console/src/App.tsx").read_text()
        admin_paths = json.loads(admin.OPENAPI.read_text())["paths"]
        self.assertIn("new GeneratedAdminControlPlaneApi", api_source)
        self.assertNotIn("this.fetchImpl(", api_source)
        self.assertNotIn("/admin/agent-runtimes", api_source)
        self.assertNotIn("getAgentRuntime", app_source)
        self.assertNotIn("changeAgentRuntime", app_source)
        self.assertNotIn("agent-runtime-control-heading", app_source)
        self.assertFalse(any("/agent-runtimes/" in path for path in admin_paths))


class AdminFreshnessFailureContractTest(unittest.TestCase):
    """Exercise the Admin generator CLI without changing repository outputs."""

    def probe(self, *, invalid_source: bool = False, stale: bool = False) -> subprocess.CompletedProcess:
        with tempfile.TemporaryDirectory(prefix="weave-admin-codegen-probe-") as temporary:
            root = Path(temporary)
            script = root / "tools" / Path(admin.__file__).name
            script.parent.mkdir()
            shutil.copyfile(admin.__file__, script)
            source = root / admin.OPENAPI.relative_to(admin.ROOT)
            source.parent.mkdir(parents=True)
            shutil.copyfile(admin.OPENAPI, source)
            output = root / admin.OUT.relative_to(admin.ROOT)
            output.parent.mkdir(parents=True)
            output.write_text(admin.render())
            if stale:
                output.write_text("stale checked-in output")
            before = output.read_bytes()
            if invalid_source:
                source.write_text("invalid JSON")
            result = subprocess.run(
                [sys.executable, str(script), "--check"], cwd=root,
                text=True, capture_output=True, check=False,
            )
            self.assertEqual(before, output.read_bytes(), "check must not modify checked-in output")
            return result

    def test_equal_generated_output_succeeds_without_mutation(self) -> None:
        result = self.probe()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_generator_failure_cannot_pass_with_unchanged_output(self) -> None:
        result = self.probe(invalid_source=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("JSONDecodeError", result.stderr)

    def test_stale_output_fails_without_overwriting_it(self) -> None:
        result = self.probe(stale=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("are stale", result.stderr)


if __name__ == "__main__":
    unittest.main()
