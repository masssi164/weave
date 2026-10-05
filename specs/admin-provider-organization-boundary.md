# Admin provider status and deployment organization admission

Status: locally validated conformance fix for #1472; integration and generated consumers pending.
This is implementation evidence, not new product truth.

## Authority and scope

The pinned corpus `3b27ad2e667e462d9f63ff9c58fc02469e167f9b`,
`steering/release-2026-10-product-consolidation.md` and
`platform/identity-security/spec.md` require separated User/Admin sessions and one
configured Keycloak organization per deployment. Token claims cannot select another
organization. This fix preserves existing data and does not implement provider migration.

## Admission contract

- `weave.security.primary-organization.keycloak-id` and `.keycloak-alias` are the
  explicit, operator-owned native organization coordinate. Their environment
  projections are `WEAVE_PRIMARY_ORGANIZATION_ID` and `WEAVE_PRIMARY_ORGANIZATION_ALIAS`.
  Both default to blank; missing configuration denies human product access.
- The configured coordinate maps to the existing canonical
  `weave.context.authorization.default-tenant-id`. No public request selects this
  mapping. The native `organization` claim must contain exactly that alias and ID.
  Any configured tenant or fallback tenant claim must equal the canonical tenant;
  malformed, blank or conflicting claims are denied rather than ignored.
- User and Admin security chains apply this same admission rule in addition to
  their existing audience/client, scope and selected-role checks. An admin role
  on a User session never admits an Admin request. Workloads do not become humans.
- Invitation reconciliation remains accessible to an authenticated bootstrap
  session without organization roles or an organization claim. Present native
  organization/tenant claims must still match configuration. Existing verified
  pending-intent and current membership checks remain required before access is
  applied. Configure invitation ID/alias consistently with the primary coordinate.
  The unauthenticated empty-realm owner bootstrap retains its separate credential.

## Provider status contract

- `GET /api/admin/providers/status` retains operation ID `status`; the former
  `/api/providers/status` has no compatibility alias. The Admin artifact owns it.
- Current-org owner/admin admission and `admin_control_plane.readiness_read` remain
  required. The shared organization gate also fences the existing legacy Admin
  overview, selection and readiness routes to the configured deployment.
- Category-only legacy selections remain configuration evidence for this single
  deployment. They are not proof of an active organization runtime binding.
- Actual Files binding status is projected from
  `ProviderBindingRepository.current(canonicalOrganization, "files")`; adapter
  readiness is checked through the existing Files resolver. No private configuration
  reference, SecretRef, provider object ID or raw provider failure is returned in
  that projection. No binding is changed by a read.

## Validation and integration

Exercise current-org owner/admin success; User-session admin and MCP rejection;
wrong ID/alias, missing/multiple native organizations, inconsistent tenant claims
and unconfigured admission denial; bootstrap reconciliation; Admin-only OpenAPI
partitioning; and actual Files binding absence/configuration without cross-org reads.
Use existing decoder, authorization, controller and metadata export tests. Root
integration regenerates artifacts and consumers after this source change.

Infrastructure must supply the primary coordinate to Server consistently with the
Keycloak baseline/invitation target and preserve the canonical tenant used by Files
bindings. Flutter's legacy provider-stack diagnostics currently use a User bearer;
integration must retire that call or use the dedicated Admin surface. Historical
closure evidence is retained. This change alone does not close #1472 or #1470.

### Local verification evidence

- `./gradlew :server:test`: 1,039 tests, zero failures/errors/skips, including
  the existing PostgreSQL repository tests. The run used the active Docker context
  and the verified local `WEAVE_MINIO_TEST_IMAGE=weave-runtime-state:ci-s3` fixture.
- Final targeted `:server:test` rerun for `OpenApiDocumentationTest`,
  `FirstPartyIdentityContractTest`, `AdminProviderRegistryServiceTest`, and
  `ProviderRegistryControllerTest` passed after the final status schema annotations.
- `./gradlew specCorpusConformance docsStructureCheck` passed against the exact
  pinned corpus using `WEAVE_SPEC_CORPUS_ROOT` to avoid a newer sibling checkout.
- Valid controller fixtures now configure their asserted canonical deployment
  tenant; no authorization, provider redaction or architectural assertion was relaxed.
  The existing exclusive tenant-claim owner assertion remains unchanged.
- Checked-in OpenAPI and consumer artifacts were deliberately left for the
  integration regeneration. No production or persistent deployment was changed.
