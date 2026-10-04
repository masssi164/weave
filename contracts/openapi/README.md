# Server-owned OpenAPI artifacts

`weave-user-openapi.json` and `weave-admin-openapi.json` are generated from the
running Spring controller and transport definitions. They partition ordinary
member operations from administration and are the source inputs for their
respective generated HTTP clients. The combined `weave-openapi.json` remains a
temporary compatibility input while the existing model-only generators are
replaced; it is not a third API authority.

Run `./gradlew generateOpenApiContract` after changing a controller or transport
model. `./gradlew checkOpenApiContractFresh` re-exports all three documents from
the candidate source and fails on drift or overlapping User/Admin paths. The
normalizer sorts object keys only; it preserves ordered arrays, response
descriptions and security metadata.

These documents describe transport. Server authorization and independent
behavioral tests remain necessary for the User/Admin, organization and resource
boundaries.
