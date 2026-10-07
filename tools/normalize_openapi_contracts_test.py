#!/usr/bin/env python3
"""Contract checks for lossless, partitioned OpenAPI export normalization."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.normalize_openapi_contracts import normalize


class NormalizeOpenApiContractsTest(unittest.TestCase):
    def test_preserves_schema_security_headers_and_failure_responses(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/openapi").mkdir(parents=True)
            user = self.document("/api/files/content", "downloadContent")
            operation = user["paths"]["/api/files/content"]["get"]
            operation["security"] = [{"bearer-jwt": ["files:read", "workspace:member"]}]
            operation["parameters"] = [{
                "name": "If-Match", "in": "header", "required": True,
                "schema": {"type": "string", "minLength": 3},
            }]
            operation["responses"] = {
                "200": {
                    "description": "Binary content",
                    "headers": {"ETag": {"schema": {"type": "string"}}},
                    "content": {"application/octet-stream": {
                        "schema": {"type": "string", "format": "binary"},
                    }},
                },
                "412": {
                    "description": "Version precondition failed",
                    "content": {"application/json": {
                        "schema": {"$ref": "#/components/schemas/Error"},
                    }},
                },
            }
            user["components"] = {
                "securitySchemes": {"bearer-jwt": {
                    "type": "http", "scheme": "bearer", "bearerFormat": "JWT",
                }},
                "schemas": {
                    "Error": {
                        "type": "object", "required": ["code", "details"],
                        "properties": {
                            "code": {"type": "string", "enum": ["stale", "missing"]},
                            "details": {"type": ["string", "null"]},
                        },
                    },
                    "OrderedCoordinates": {
                        "type": "array",
                        "prefixItems": [{"type": "number"}, {"type": "string"}],
                        "items": False,
                        "examples": [[2, "north"], [1, "south"]],
                    },
                },
            }
            self.write_raw(root, "weave-openapi", user)
            self.write_raw(root, "weave-user-openapi", user)
            self.write_raw(root, "weave-admin-openapi", self.document("/api/admin/files", "adminFiles"))

            normalize(root)

            target = root / "contracts/openapi/weave-user-openapi.json"
            first_export = target.read_bytes()
            self.assertEqual(json.loads(first_export), user)

            # Input object insertion order must not affect the generated artifact.
            self.write_raw(root, "weave-user-openapi", dict(reversed(list(user.items()))))
            normalize(root)
            self.assertEqual(target.read_bytes(), first_export)

    def test_preserves_ordered_examples_and_response_meaning(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/openapi").mkdir(parents=True)
            user = self.document("/api/files", "listFiles")
            user["paths"]["/api/files"]["get"]["responses"]["200"]["description"] = "Visible files"
            user["paths"]["/api/files"]["get"]["responses"]["200"]["content"] = {
                "application/json": {"example": {"items": ["zeta", "alpha"]}}
            }
            self.write_raw(root, "weave-openapi", user)
            self.write_raw(root, "weave-user-openapi", user)
            self.write_raw(root, "weave-admin-openapi", self.document("/api/admin/files", "adminFiles"))

            normalize(root)

            result = json.loads((root / "contracts/openapi/weave-user-openapi.json").read_text())
            response = result["paths"]["/api/files"]["get"]["responses"]["200"]
            self.assertEqual(response["description"], "Visible files")
            self.assertEqual(response["content"]["application/json"]["example"]["items"],
                             ["zeta", "alpha"])

    def test_rejects_user_admin_path_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/openapi").mkdir(parents=True)
            document = self.document("/api/admin/files", "adminFiles")
            for name in ("weave-openapi", "weave-user-openapi", "weave-admin-openapi"):
                self.write_raw(root, name, document)
            with self.assertRaisesRegex(ValueError, "paths overlap"):
                normalize(root)

    def test_rejects_response_header_without_a_transport_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/openapi").mkdir(parents=True)
            user = self.document("/api/files", "listFiles")
            user["paths"]["/api/files"]["get"]["responses"]["200"]["headers"] = {
                "ETag": {"description": "Strong version"}
            }
            self.write_raw(root, "weave-openapi", user)
            self.write_raw(root, "weave-user-openapi", user)
            self.write_raw(root, "weave-admin-openapi", self.document("/api/admin/files", "adminFiles"))
            with self.assertRaisesRegex(ValueError, "Response header without schema/content"):
                normalize(root)

    @staticmethod
    def document(path: str, operation_id: str) -> dict:
        return {"openapi": "3.1.0", "paths": {
            path: {"get": {"operationId": operation_id,
                           "responses": {"200": {"description": "Successful response"}}}}
        }}

    @staticmethod
    def write_raw(root: Path, name: str, document: dict) -> None:
        (root / "build/openapi" / f"{name}.raw.json").write_text(json.dumps(document))


if __name__ == "__main__":
    unittest.main()
