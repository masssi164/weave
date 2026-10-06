#!/usr/bin/env python3
"""Contract tests for the intentionally small OpenAPI 3.1 type projections."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import generate_admin_openapi_types as admin
import generate_client_openapi_models as client


class OpenApi31TypeProjectionTest(unittest.TestCase):
    def test_consumer_projections_use_only_their_owned_artifacts(self) -> None:
        self.assertEqual("weave-user-openapi.json", client.OPENAPI.name)
        self.assertEqual("weave-admin-openapi.json", admin.OPENAPI.name)
        user_models = client.render()
        self.assertNotIn("class AdminControlPlaneResponse", user_models)
        self.assertNotIn("class ProviderRegistryResponse", user_models)
        self.assertIn("class WorkspaceCapabilitiesResponse", user_models)

    def test_required_nullable_string_remains_required_and_nullable_in_dart(self) -> None:
        schema = {"type": ["null", "string"]}

        self.assertEqual("String?", client.type_for(schema, required=True))
        self.assertEqual(
            'json["secret"] as String?',
            client.read_expr(client.type_for(schema, required=True), "secret"),
        )
        emitted = "\n".join(
            client.emit_class(
                "Credential",
                {
                    "type": "object",
                    "required": ["secret"],
                    "properties": {"secret": schema},
                },
            )
        )
        self.assertIn("const Credential({required this.secret});", emitted)
        self.assertIn("final String? secret;", emitted)

    def test_nullable_openapi_31_union_is_projected_in_typescript(self) -> None:
        self.assertEqual(
            "string | null",
            admin.type_for({"type": ["null", "string"]}),
        )

    def test_free_form_object_is_not_mistaken_for_nested_map(self) -> None:
        schema = {"type": "object", "additionalProperties": {}}

        self.assertEqual("Map<String, Object?>?", client.type_for(schema))
        self.assertEqual("Record<string, unknown>", admin.type_for(schema))

    def test_schema_reference_keeps_requiredness_in_dart(self) -> None:
        schema = {"$ref": "#/components/schemas/ProviderStatus"}

        self.assertEqual("ProviderStatus", client.type_for(schema, required=True))
        self.assertEqual("ProviderStatus?", client.type_for(schema))

    def test_checked_in_contract_is_openapi_31_with_unique_operation_ids(self) -> None:
        document = json.loads(client.OPENAPI.read_text())
        self.assertEqual("3.1.0", document.get("openapi"))
        observed: dict[str, str] = {}
        for path, path_item in document.get("paths", {}).items():
            for method, operation in path_item.items():
                if method not in {"get", "put", "post", "delete", "patch", "head", "options", "trace"}:
                    continue
                operation_id = operation.get("operationId")
                self.assertIsInstance(
                    operation_id,
                    str,
                    f"{method.upper()} {path} has no operationId",
                )
                self.assertNotIn(
                    operation_id,
                    observed,
                    f"{operation_id} is shared by {observed.get(operation_id)} and {method.upper()} {path}",
                )
                observed[operation_id] = f"{method.upper()} {path}"

    def test_checked_in_contract_contains_no_openapi_30_nullable_keyword(self) -> None:
        document = json.loads(client.OPENAPI.read_text())

        def assert_no_nullable(value: object, location: str = "$") -> None:
            if isinstance(value, dict):
                self.assertNotIn("nullable", value, location)
                for key, child in value.items():
                    assert_no_nullable(child, f"{location}.{key}")
            elif isinstance(value, list):
                for index, child in enumerate(value):
                    assert_no_nullable(child, f"{location}[{index}]")

        assert_no_nullable(document)

    def test_user_manifest_requires_separate_matrix_oauth_discovery_fields(self) -> None:
        schemas = json.loads(client.OPENAPI.read_text())["components"]["schemas"]
        manifest = schemas["PlatformConfigResponse"]
        self.assertTrue(
            {"oidc", "protocols", "userApiBaseUrl"}.issubset(manifest["required"])
        )
        self.assertEqual(
            {"matrixClientServerBaseUrl", "matrixOAuthIssuer", "matrixOAuthClientId"},
            set(schemas["Protocols"]["required"]),
        )
        self.assertEqual(
            "uri", schemas["Protocols"]["properties"]["matrixOAuthIssuer"]["format"]
        )


class FreshnessFailureContractTest(unittest.TestCase):
    """Exercise the real generator CLI without changing repository outputs."""

    def probe(self, language: str, *, invalid_source: bool = False,
              formatter_exit: int = 0, stale: bool = False) -> subprocess.CompletedProcess:
        generator = admin if language == "admin" else client
        with tempfile.TemporaryDirectory(prefix="weave-codegen-probe-") as temporary:
            root = Path(temporary)
            script = root / "tools" / Path(generator.__file__).name
            script.parent.mkdir()
            shutil.copyfile(generator.__file__, script)
            source = root / generator.OPENAPI.relative_to(generator.ROOT)
            source.parent.mkdir(parents=True)
            shutil.copyfile(generator.OPENAPI, source)
            output = root / generator.OUT.relative_to(generator.ROOT)
            output.parent.mkdir(parents=True)
            output.write_text(generator.render())
            if stale:
                output.write_text("stale checked-in output")
            before = output.read_bytes()
            if invalid_source:
                source.write_text("invalid JSON")
            binaries = root / "bin"
            binaries.mkdir()
            formatter = binaries / "dart"
            formatter.write_text(f"#!/bin/sh\nexit {formatter_exit}\n")
            formatter.chmod(0o755)
            result = subprocess.run(
                [sys.executable, str(script), "--check"], cwd=root,
                env={**os.environ, "PATH": str(binaries) + os.pathsep + os.environ["PATH"]},
                text=True, capture_output=True, check=False)
            self.assertEqual(before, output.read_bytes(), "check must not modify checked-in output")
            return result

    def test_equal_generated_output_succeeds_without_mutation(self) -> None:
        for language in ("admin", "client"):
            with self.subTest(language=language):
                result = self.probe(language)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_generator_failure_cannot_pass_with_unchanged_output(self) -> None:
        for language in ("admin", "client"):
            with self.subTest(language=language):
                result = self.probe(language, invalid_source=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("JSONDecodeError", result.stderr)

    def test_formatter_failure_cannot_pass_with_unchanged_output(self) -> None:
        result = self.probe("client", formatter_exit=42)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exit status 42", result.stderr)

    def test_stale_output_fails_without_overwriting_it(self) -> None:
        for language in ("admin", "client"):
            with self.subTest(language=language):
                result = self.probe(language, stale=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("are stale", result.stderr)


if __name__ == "__main__":
    unittest.main()
