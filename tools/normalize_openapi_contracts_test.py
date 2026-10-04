#!/usr/bin/env python3
"""Contract checks for lossless, partitioned OpenAPI export normalization."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.normalize_openapi_contracts import normalize


class NormalizeOpenApiContractsTest(unittest.TestCase):
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
