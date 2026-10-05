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

The MCP Files projection and product JVM E2E depend on this same generated User
client. E2E uses generated Identity, Files, Calendar, Workspace Home and profile
operations, while independently asserting identity, access denial, private
activity, idempotent Files references, binary content and restart behavior.
Raw malformed/security probes remain on the bounded test HTTP client.

Migration of normal-purpose Weave HTTP traffic is still in progress. The
remaining product E2E bootstrap, readiness, identity reconciliation and Admin
calls must move to their matching generated User or Admin operations. Matrix,
OIDC/OAuth, MCP protocol, internal E2E proof and deliberate negative probes
remain outside the Weave OpenAPI client because they follow their own protocol
or test boundaries. Files operations and resource grants beyond the current
generated slice require further server contract work.
