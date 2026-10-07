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
- Compose reads the native alias from the same `WEAVE_ORGANIZATION_ALIAS` overlay
  used by realm rendering and invitation configuration; environment-specific aliases
  must not be replaced with a hardcoded `weave` value. Host `dev` defaults to `weave-dev`.
- Keycloak creates its built-in `organization` scope before importing custom scopes.
  The realm artifact must not redeclare it: Keycloak 26.7.1 rejects that duplicate.
  Its stock membership mapper omits the native ID. The explicit manifest-bound
  post-import migration therefore includes a separate
  `organization-membership-id-post-import` operation alongside the existing FGAP
  operation. It sets the supported `addOrganizationId=true`, JSON multivalued
  projection and disables organization attributes/domains. No custom claim or
  second membership mapper substitutes for the native representation.
- The one-shot executor first qualifies the existing temporary bootstrap authority,
  selects exactly one native `organization` OIDC scope and exactly one built-in
  membership mapper, and checks their identities before mutation. It preserves the
  mapper's unrelated configuration and all other scope mappers, applies one bounded
  Admin REST PUT, verifies exact readback and an empty second plan, and then retires
  the authority with the existing negative check. Missing, duplicate or mismatched
  scope/mapper identity and rejected or changed readback block completion.
- The migration definition and digest-bound bundle declare both operations.
  Receipt version 2 requires both completed operation IDs; a receipt for the former
  FGAP-only contract cannot satisfy readiness. Existing backup/disposable-run proof,
  artifact digests, secret isolation, role checks and authority retirement remain
  mandatory. Steady-state clients gain no mapper-management authority. Existing
  persistent realms require their explicit reviewed migration and fresh tokens;
  routine startup never silently mutates them.
  The supported option is defined by the pinned
  [Keycloak 26.7.1 organization mapper](https://github.com/keycloak/keycloak/blob/26.7.1/services/src/main/java/org/keycloak/organization/protocol/mappers/oidc/OrganizationMembershipMapper.java).
- The configured coordinate maps to the existing canonical
  `weave.context.authorization.default-tenant-id`. No public request selects this
  mapping. The native `organization` claim must contain exactly that alias and ID.
  Any configured tenant or fallback tenant claim must equal the canonical tenant;
  malformed, blank or conflicting claims are denied rather than ignored.
- User and Admin security chains apply this same admission rule in addition to
  their existing audience/client, scope and selected-role checks. An admin role
  on a User session never admits an Admin request. Workloads do not become humans.
  The separate human Agent Runtime Admin chain also requires this admission and
  exactly one selected product role, owner or admin, while retaining its own
  `agent-runtime.admin` scope. The workload and MCP chains remain separate.
  The legacy mixed Files DAV decoder applies the same admission after successful
  human token decoding. A denied human context cannot fall through to workload
  decoding; exchanged workloads still require their exact existing decoder and
  device credentials retain their separate path. This is a compatibility security
  guard, not new northbound DAV release acceptance.
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
- The Admin Console sends provider-selection requests through the generated Admin
  operation and its generated request model. This metadata endpoint accepts no
  cutover evidence or consequence-confirmation fields. Its dry-run response is not
  a verified adoption or replacement proof; the UI must keep activation blocked,
  including when a test fixture or stale local state presents a purported evidence
  reference. #1498 owns those operations. Recording a category selection cannot
  be presented as an active binding change.
- The historical `WEAVE_SPEC_0010_PROVIDER_CHANGE` scenario stays mapped as
  offline specification evidence for #1498. Its current Admin UI test fragment
  asserts only the fail-closed selection boundary; it does not prove migration,
  cutover, reconciliation or rollback for #1470.
- Actual Files binding status is projected from
  `ProviderBindingRepository.current(canonicalOrganization, "files")`; adapter
  readiness is checked through the existing Files resolver. No private configuration
  reference, SecretRef, provider object ID or raw provider failure is returned in
  that projection. No binding is changed by a read.

## Validation and integration

### Workspace diagnostics and member Home (#1472)

- `GET /api/admin/workspace/capability-policy` and
  `GET /api/admin/workspace/release-readiness` retain operation IDs
  `capabilityPolicy` and `releaseReadiness`. They require the dedicated Admin
  audience/client, current configured organization, selected owner/admin role,
  workspace scope and existing `admin_control_plane.readiness_read` capability.
  The former `/api/workspace/` diagnostic routes have no compatibility aliases.
- The Admin readiness response describes the existing configuration/cached
  capability projection. It must not claim live provider verification or release
  eligibility, or assume that Files uses Nextcloud. Operator remediation remains
  available here; current binding diagnostics remain at `/api/admin/providers/status`.
- Member Home uses the requesting member's capability projection and independently
  authorized recent activity. It does not call operator readiness or return provider
  setup, environment variables, adapter names or operator instructions, including
  when the member's User session carries an owner/admin role. A limited capability
  does not block the otherwise authorized Home shell. Member remediation is limited
  to retrying the affected capability or contacting the organization administrator.
- Home response version 3 represents an unmeasured section `itemCount` as nullable
  integer, never a synthetic zero or one derived from capability availability.
  Existing section keys and navigation references remain stable. Shared activity
  records remain filtered by current Context/Space authorization. Completed User
  Files writes remain visible only to the actor while those Files are owner-only;
  Context/Space VIEW alone does not grant visibility into another member's File.
  Activity records do not imply a
  measured count for unrelated sections.
- Focused HTTP evidence must cover Admin owner/admin success, User-session admin,
  member, workload, anonymous and foreign/malformed organization denial; absence of
  former aliases; User/Admin OpenAPI partition and stable operation IDs; and member
  Home readiness, unknown counts and absence of operator/provider diagnostics.
  Decoder tests retain signed-token audience/client validation. Integration regenerates
  consumers and updates their nullable-count/version handling before merge.

Local evidence for this bounded correction:

- `:server:test --tests '*WorkspaceControllerTest' --tests '*WorkspaceHomeServiceTest'
  --tests '*WorkspaceReleaseReadinessServiceTest' --tests '*FirstPartyIdentityContractTest'
  --tests '*JwtDecoderConfigTest' --tests '*OpenApiDocumentationTest'` passed 99 tests
  with zero failures, errors or skips (87 selected Jupiter tests plus 12 existing
  Cucumber scenarios discovered by the standard task).
- `specCorpusConformance` passed against pinned corpus `3b27ad2`; `docsStructureCheck`
  and `git diff --check` passed. HTTP tests exercise the real security chains and
  existing validator-backed decoder fixtures; separate decoder tests use signed JWTs.
- OpenAPI metadata checks confirm the two unchanged operation IDs in Admin only and
  the Home count schema's `integer | null` type with minimum zero. Member HTTP tests
  assert five explicit null counts, version 3, no operator setup strings and current
  Context/Space activity filtering. A denied Decisions capability stays denied even
  when Chat and Files are ready; a limited capability does not block an authorized shell.
- These focused checks do not replace generated-consumer, integrated browser/Admin
  or post-merge verification. Integration also updates the former readiness path in
  `server/docs/release-operations.md` and the Home consumer's version/count handling.

Exercise current-org owner/admin success; User-session admin and MCP rejection;
wrong ID/alias, missing/multiple native organizations, inconsistent tenant claims
and unconfigured admission denial; bootstrap reconciliation; Admin-only OpenAPI
partitioning; and actual Files binding absence/configuration without cross-org reads.
Use existing decoder, authorization, controller and metadata export tests. Root
integration regenerates artifacts and consumers after this source change.

The existing isolated `testApp` journey additionally reads provider status through the
generated JVM Admin client after real browser/PKCE owner sign-in. It independently
asserts the configured canonical tenant and the fixture's active native Files binding,
revision and configured adapter state. A User-session request to the Admin status route
must return 401. The existing fixture creates its own provider binding and subsequently
verifies real Files bytes; a configured status alone is not live provider proof. Existing
`testApp` ownership, sanitized evidence and exact namespace cleanup remain unchanged.
The first real candidate correctly failed during reconciliation because its realm
omitted the native organization ID and the server alias differed from the environment
overlay. A subsequent attempt to redeclare the built-in scope failed with Keycloak's
unique scope-name constraint. The corrected import omits that scope and the declared
post-import operation updates its existing mapper. Renderer and Compose regression
checks assert the complete coordinate; server missing/wrong-ID negatives remain unchanged.

The product journey independently checks the isolated realm's exact native organization
alias and ID in human and Admin tokens, including the fresh authorization-code/PKCE
session obtained after reconciliation. Failure diagnostics distinguish absent, malformed,
empty, multiple, wrong-alias and wrong-ID claims using fixed reason codes only; they never
emit token contents, claim values or private provider payloads. These assertions do not
relax Server admission or treat an empty organization object as an absent claim.

The isolated `testApp` lifecycle must explicitly run `e2e keycloak-migration-apply`
after its exact empty-namespace proof and before application startup or browser login.
That existing one-shot operation qualifies the disposable environment, applies both
declared operations, verifies readback/idempotency and retires its temporary authority.
E2E application startup requires the completed receipt, as production startup already
does. An import-only run leaves Keycloak's stock native-ID omission in place and cannot
qualify the product. The previous structural test forbidding this explicit operation was
obsolete after the manifest-bound native mapper migration; it is replaced by lifecycle
ordering and fail-closed receipt assertions. Normal Server requests remain unable to
reconcile static IAM, and persistent environments still require their reviewed migration.

This is an explicit E2E conformance delta from the pinned identity specification's
ADR-0022 development overlay exception: isolated E2E now uses the canonical
`query-organizations`/`query-users` identity-administration grants and the qualified
organization-scoped FGAP operation. Its former `manage-organizations`/`manage-users`
overlay existed to avoid a second migration phase; that premise no longer holds when
the native mapper operation is mandatory. The executor must continue rejecting broad
steady-state grants. This narrows E2E authority and exercises the production boundary;
the resettable dev/dogfood exception is not changed by this fix.

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
- The separate Agent Runtime human Admin chain has HTTP regressions for current
  owner/admin success and wrong/missing/multiple native organizations, conflicting
  tenant claims, ambiguous roles and missing runtime scope. Denied requests never
  invoke the runtime service. Existing signed JWT decoder tests remain green.
- Legacy Files DAV decoder regressions reject foreign/missing/malformed human
  organizations and conflicting tenants without invoking the workload decoder.
  Current members, validated exchanged workloads and the existing device credential
  and DAV controller tests remain green; workload validation was not relaxed.
- Checked-in OpenAPI and consumer artifacts were deliberately left for the
  integration regeneration. No production or persistent deployment was changed.

### Native mapper conformance evidence

- A disposable stock Keycloak 26.7.1 realm successfully imported without a duplicate
  built-in scope. Admin REST readback confirmed one native organization membership
  mapper and the stock ID omission. Updating only that mapper returned HTTP 204;
  exact configuration readback and an empty second plan passed. The exact container
  and private temporary files were removed; the probe created no volumes and touched
  no persistent realm. This proves the supported mapper mechanism, not the full
  product login journey.
- Migration regressions reject missing, duplicate or wrong scope/mapper identities,
  refused writes, ignored writes and changes to unrelated configuration/mappers.
  Existing bootstrap role, FGAP, backup proof and authority deletion negatives remain.
  Manifest and receipt readers reject a changed mapper target/configuration and
  completion evidence that covers only FGAP.
- The complete disposable product flow must still pass on the integrated candidate
  before this fix is described as end-to-end login evidence.
- Final local verification for the mapper correction: all 1,076 Server tests passed
  with zero failures, errors or skips; `:server:bootJar` was rebuilt. `infraStatic`,
  `specCorpusConformance` and `docsStructureCheck` passed against the pinned corpus.
  The existing product-flow structural guard now checks its actual separate Admin
  session and Files dry-run/409 activation fence plus current binding metadata,
  replacing obsolete assertions for a User-session setup and applied Files selection.

### Generated integrated diagnostic and Home proof

The isolated product journey calls both workspace diagnostics through the generated
JVM Admin client with its separately obtained Admin session. Independent assertions
require the admitted capability policy and the documented configuration-check set;
reading that snapshot does not count as live provider readiness. Deliberate negative
HTTP probes reject the User bearer at both Admin diagnostic routes.

The member journey reads Home through the generated JVM User client and asserts
version 3, the accepted section set, explicit unknown counts and absence of operator
setup instructions. The same read after restart must retain those semantics. Failure
evidence reports only fixed diagnostic stages and HTTP status, never response bodies
or session tokens. Transport fixtures and compilation remain separate from a green
actual Compose run.
