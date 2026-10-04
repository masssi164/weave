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
for `BoardProviderCapabilities` enum arrays; it fails closed if that output
changes.

The product JVM E2E uses the generated `IdentityApi.me` operation during the
Chat outage journey. The test still independently checks the returned issuer,
subject, organization, and immutable identity key against OIDC claims. Raw
malformed/security probes remain on the bounded test HTTP client.

This is a partial migration. MCP currently calls the Weave Files WebDAV facade
for `SEARCH` and `GET`. The User artifact exposes Files setup/readiness routes,
but no Files content/search routes. Therefore the MCP Files tools cannot yet
switch to a generated User HTTP operation without a server-owned contract
addition. Product E2E still has handwritten normal User calls for profile
readiness, Chat readiness, identity reconciliation, public platform config,
profile read/update, and workspace Home. Its Admin, WebDAV/CalDAV, Matrix,
OAuth, MCP protocol, and deliberately malformed/security traffic remains
separate according to those protocol contracts and test roles.
