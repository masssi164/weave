#!/usr/bin/env python3
"""Contract tests for the intentionally small OpenAPI 3.1 type projections."""

from __future__ import annotations

import json
import unittest

import generate_admin_openapi_types as admin
import generate_client_openapi_models as client


class OpenApi31TypeProjectionTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
