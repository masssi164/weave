# Generated JVM User API client

This module compiles a Java HTTP client and transport models from the server-owned
[`weave-user-openapi.json`](../contracts/openapi/weave-user-openapi.json). The
generator is pinned to OpenAPI Generator CLI 7.17.0 in `build.gradle`; generated
sources stay under `build/` and are packaged by the normal Gradle `java-library`
build. Consumers depend on `project(':weave-user-api-client')`.

Run `./gradlew :weave-user-api-client:check` to compile the full generated User
surface, compare two independent generations byte for byte, and check that
every operation and schema in the artifact has generated Java code. Run
`./gradlew checkOpenApiContractFresh` to compare the server's current code-first
export with the checked-in OpenAPI artifacts. The narrow post-processing script
in `tools/patch_openapi_java_enum_query.py` corrects a Java template type error
for `BoardProviderCapabilities` enum arrays. The equally narrow
`tools/patch_openapi_java_binary_body.py` sends Files `application/octet-stream`
request bodies as file bytes instead of JSON-serializing a `File`. Both fail
closed if the pinned generator output changes. A transport test checks the
actual POST and PUT bytes on a local HTTP endpoint.

The product JVM E2E uses the generated `IdentityApi.me` operation during the
Chat outage journey. The test still independently checks the returned issuer,
subject, organization, and immutable identity key against OIDC claims. Raw
malformed/security probes remain on the bounded test HTTP client.

This is a partial migration. The User artifact now has a bounded, owner-scoped
Files list/inspect/upload/update/download slice. MCP still calls the legacy
Weave Files WebDAV facade for `SEARCH` and `GET`, and the remaining Files
operations and resource grants are not complete. Product E2E still has
handwritten normal User calls for profile
readiness, Chat readiness, identity reconciliation, public platform config,
profile read/update, and workspace Home. Its Admin, WebDAV/CalDAV, Matrix,
OAuth, MCP protocol, and deliberately malformed/security traffic remains
separate according to those protocol contracts and test roles.
