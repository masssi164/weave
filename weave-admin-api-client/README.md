# Generated JVM Admin API client

This module generates Java HTTP operations and transport models from the
server-owned [`weave-admin-openapi.json`](../contracts/openapi/weave-admin-openapi.json)
artifact. The generator is pinned to OpenAPI Generator CLI 7.17.0. Generated
sources stay under `build/` and are packaged by the normal Gradle library build.
JVM consumers depend on `project(':weave-admin-api-client')`.

Run `./gradlew :weave-admin-api-client:check` to compile the generated Admin
surface, compare two independent generations byte for byte, and verify that
every operation and schema in the artifact has generated Java code. Run
`./gradlew checkOpenApiContractFresh` to compare the server's current code-first
export with the checked-in User and Admin artifacts.

The product JVM E2E reads the Admin control plane through the generated client
with a separate Admin OIDC session. It independently asserts the organization
and support-safe projection, while retaining raw requests for deliberate
authorization failures and malformed-input probes.
