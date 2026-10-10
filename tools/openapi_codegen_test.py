#!/usr/bin/env python3
"""Contract tests for the server-owned User and Admin OpenAPI consumers."""

from __future__ import annotations

import json
import unittest

import generate_admin_api_client as admin
import generate_client_user_api as client


class OpenApiGeneratedConsumerTest(unittest.TestCase):
    def test_consumers_use_separate_owned_artifacts(self) -> None:
        self.assertEqual("weave-user-openapi.json", client.CONTRACT.name)
        self.assertEqual("weave-admin-openapi.json", admin.CONTRACT.name)
        self.assertTrue((client.OUTPUT / "model/protocols.dart").is_file())
        self.assertTrue((client.OUTPUT / "model/workspace_capabilities_response.dart").is_file())
        self.assertFalse((client.OUTPUT / "model/admin_control_plane_response.dart").exists())
        self.assertTrue((admin.OUTPUT / "models/AdminControlPlaneResponse.ts").is_file())
        self.assertFalse((admin.ROOT / "admin-console/src/generated/openapi.ts").exists())

    def test_admin_nullable_openapi_31_union_is_projected_in_generated_client(self) -> None:
        generated = (admin.OUTPUT / "models/OrganizationMemberPageResponse.ts").read_text()
        self.assertIn("nextCursor?: string | null;", generated)

    def test_admin_free_form_object_is_projected_as_map(self) -> None:
        generated = (admin.OUTPUT / "models/AdminAuditEventResponse.ts").read_text()
        self.assertIn("payload?: { [key: string]: any; };", generated)

    def test_admin_console_uses_only_generated_client_transport_models(self) -> None:
        source = (admin.ROOT / "admin-console/src/api.ts").read_text()
        self.assertIn('from "./generated/admin-client"', source)
        self.assertNotIn('from "./generated/openapi"', source)

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
        admin_paths = json.loads(admin.CONTRACT.read_text())["paths"]
        self.assertIn("new GeneratedAdminControlPlaneApi", api_source)
        self.assertIn("new GeneratedPlatformApi", api_source)
        self.assertNotIn("fetchImpl(", api_source)
        self.assertNotIn("this.fetchImpl(", api_source)
        self.assertNotIn("interface ServerControlPlaneResponse", api_source)
        self.assertNotIn("interface ServerProviderCategory", api_source)
        self.assertIn("GeneratedProviderCategoryResponse", api_source)
        self.assertNotIn("/admin/agent-runtimes", api_source)
        self.assertNotIn("getAgentRuntime", app_source)
        self.assertNotIn("changeAgentRuntime", app_source)
        self.assertNotIn("agent-runtime-control-heading", app_source)
        self.assertFalse(any("/agent-runtimes/" in path for path in admin_paths))
        self.assertIn("/api/platform/config", json.loads(client.CONTRACT.read_text())["paths"])
        self.assertIn(
            "async config(",
            (admin.ROOT / "admin-console/src/generated/user-client/apis/PlatformApi.ts").read_text(),
        )


class AdminClientCoverageContractTest(unittest.TestCase):
    """The remaining pinned generator must cover the complete Admin artifact."""

    def test_generated_operations_cover_admin_contract(self) -> None:
        generated = admin.types_at(admin.OUTPUT)
        admin.check_operation_coverage(generated, admin.CONTRACT, "admin")

    def test_missing_operations_fail_closed(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "missing generated operations"):
            admin.check_operation_coverage({}, admin.CONTRACT, "admin")


if __name__ == "__main__":
    unittest.main()
