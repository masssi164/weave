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

The product JVM E2E uses the generated Admin client for the one-shot owner
bootstrap (with its scoped bootstrap token), then uses a separate Admin OIDC
session for invitations, member listing, Weaver entitlement updates, workspace
diagnostics, the control plane, and provider selections. It independently
asserts organization identity, member versions and capabilities, and support-safe
provider projections. Raw requests remain for deliberate authorization failures,
blocked Files activation, and malformed-input probes. Historical private Agent
Runtime proof traffic is outside the current Admin OpenAPI artifact.
