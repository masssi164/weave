# Keycloak realm lifecycle

Status: implementation guide for the Keycloak lifecycle defined by the pinned corpus
`steering/devops-conformance.md`, ADR 0022, and human-testing acceptance. The pinned corpus is
normative; this page describes its current implementation.

## Objective

Weave treats Keycloak as a fixed platform identity authority while keeping static IAM configuration reproducible and environment-specific without maintaining multiple hand-edited realm files. The architecture separates candidate semantics, environment projection, static migration, dynamic human lifecycle, and Spring application configuration.

## Configuration authority boundaries

Weave has three separate configuration authorities:

1. **Spring application configuration** owns Backend and MCP application behaviour through versioned `application.yml` plus `application-dev.yml`, `application-e2e.yml`, `application-dogfood.yml`, and `application-prod.yml`. Stable environment defaults belong there. External environment variables or config trees override only genuinely deployment-specific values and secrets.
2. **Infrastructure rendering** owns deployment artifacts that are not Spring application configuration: Keycloak realm projection, Caddy routing, optional provider configuration, runtime policy, and support-safe deployment evidence. Infra must not synthesize Backend or MCP Spring property files.
3. **Keycloak semantic IAM** owns static realm semantics and explicit migrations. Server runtime does not reconcile static IAM.

`WEAVE_ENVIRONMENT` selects the matching Spring profile in Compose. Provider choice remains a property, not an additional Spring profile: `weave-native` is the default provider for the canonical domain and optional providers remain replaceable southbound adapters.

For OAuth/OIDC, the public issuer is deliberately separate from internal transport. Spring Security validates tokens against the public `issuer-uri`, while `jwk-set-uri` may use the private Keycloak service route. The same rule applies to MCP authorization-server identity versus internal token/JWKS transport.

## Source of truth

The semantic realm baseline is `contracts/examples/keycloak-desired-state.valid.json` in the
corpus selected by `specs/weave-specs.lock.json`. The versioned migration definition and stock
client-scope import defaults live under `infra/weave-workspace/keycloak`. The environment renderer
projects those reviewed inputs into the Keycloak import; it does not maintain a separate baseline.

Candidate-level semantic identity consists of exactly two values:

- `semanticRealmSourceDigest`: the normative semantic realm baseline revision from the pinned specification corpus;
- `migrationDefinitionDigest`: the canonical digest of `infra/weave-workspace/keycloak/migration-definition.json`.

Environment URLs, SMTP coordinates and public JWKS are recorded separately in the environment
render identity. A generated `weave-realm.json` or a live realm export is not a second source of
truth. Candidate identity alone does not prove the configuration of an existing running realm.

## Environment projection

Each environment renders its own deployment artifacts from the same candidate semantic identity.

Environment-specific inputs include:

- public Weave/API/Auth origins and redirect URIs;
- primary bootstrap-organization presentation metadata;
- SMTP coordinates and SecretRefs;
- public JWKS derived from environment-owned private JWKs;
- environment name and deployment coordinates.

The renderer emits support-safe generated artifacts including:

- `keycloak/semantic-realm-source.json`;
- `keycloak/migration-definition.json`;
- `keycloak/overlay.json`;
- `keycloak/import/weave-realm.json`;
- `keycloak/realm-render-evidence.json`;
- `render-manifest.json`;
- Caddy and optional-provider configuration when those providers are selected.

Generated files are deployment artifacts. They are not independently maintained configuration sources and they do not include generated Spring application property/env files.

## Renderer decomposition

The environment renderer is intentionally split by responsibility:

- `render_config.py` is orchestration only;
- `rendering/keycloak.py` owns Keycloak environment projection;
- `rendering/gateway.py` owns gateway routing and fail-closed public/internal boundaries;
- `rendering/providers.py` owns optional Matrix/MAS/Appservice configuration;
- `rendering/io.py` owns atomic support-safe generated-file and secret handling.

This keeps each unit reviewable and prevents infrastructure code from becoming a second application configuration system.

## Static baseline ownership

The static realm baseline exists before any normal product lifecycle operation. It owns:

- realm security settings;
- Organizations and Fine-Grained Admin Permissions enablement;
- first-party clients and service-account boundaries;
- client scopes and protocol mappers;
- required actions and authentication policy;
- realm/client roles;
- primary bootstrap organization definition;
- organization role groups `/owners`, `/admins`, `/members`, `/guests`;
- `/capabilities/weaver` entitlement group;
- workload-only client policy and authorization boundaries;
- secret-free SMTP structure and SecretRefs.

The normal Server runtime must not create, repair, or reconcile these objects.

## Dynamic Server ownership

After static IAM is qualified, Server owns only bounded human lifecycle state:

- owner/member invitations and resend/revoke lifecycle;
- normal Keycloak browser registration/required-action activation;
- organization membership;
- one product-role group membership per human;
- optional Weaver entitlement membership;
- suspend/resume;
- session revocation;
- offboarding with retained-owner protection.

Server may consume the same Keycloak Admin REST anti-corruption layer used by migration code, but ordinary serving requests have no authority to mutate static realm topology.

## Workload ownership

Where the optional workload lifecycle is enabled, Agent Runtime Control owns workload-cell
lifecycle and per-cell workload identity. It is not a prerequisite for the native Dogfood baseline.
MCP remains workload-only and receives no realm-administration authority. Dynamic workload clients
are not human users and must not share credentials or grants with human lifecycle administration.

## Secret boundary

Private key material remains with the runtime that owns it. Rendering may derive the corresponding public JWK/JWKS and place only public values into the RealmRepresentation.

Spring applications consume mounted secrets through file references or Spring `configtree:` imports where appropriate. Secrets are not copied into generated public environment files.

Generated realm and support-safe evidence must reject:

- private JWK members;
- passwords;
- bearer or refresh tokens;
- cookies;
- client-secret values;
- SecretRef payload values;
- raw provider credentials or diagnostic payloads.

## Development identity initialization

For `dev`, `dogfood`, and isolated `e2e`, stock Keycloak startup import is the complete static
identity initialization. The renderer includes the required stock client scopes and native
organization membership mapper in the import. The development projection adds only
`manage-organizations` and `manage-users` to the private-key `weave-identity-admin` service
account. The Server's existing closed human-lifecycle operation policy remains enforced.
These development grants are forbidden in `prod`.

There is no post-import FGAP migration, temporary migration authority, backup prerequisite or
production migration receipt in these development lanes. `testApp` creates its own absent,
run-unique Compose namespace, proves real invitation/activation, PKCE and product behavior, then
removes only that run's resources. It does not execute `e2e keycloak-migration-apply`.

Keycloak imports a realm only when it does not already exist. Ordinary startup skips an existing
realm, so rerendering `keycloak/import/weave-realm.json` does not update its stored issuer,
audiences, redirects, client URLs or role grants. A successful render is not live reconciliation.

## Resettable Dogfood

The supported operator entry points are root Gradle tasks:

- `dogfoodUp` builds and starts the clean, exact checkout while preserving the session volumes;
- `dogfoodDown` stops the stack and preserves the session volumes and external TLS;
- `dogfoodReset` recreates the fixed `weave-dogfood` stack with empty PostgreSQL, native Files and
  Mailpit session volumes, importing the current generated realm.

The explicit reset discards Dogfood users and product data in those session volumes. It preserves
the reviewed secret files and operator-owned CA, certificates and keys outside Docker volumes.
Its preflight validates configuration, secret inputs and stable TLS before removal. The existing
closed cleanup may also remove the known unlabeled OpenTofu-era Dogfood resources only after its
name, network, mount and ownership checks pass; bind-mounted host directories are not targets.

Reset and first-owner bootstrap share a session lock. After session resources have been removed,
reset clears only the generated `owner-bootstrap/request-anchor.json` and
`owner-bootstrap/evidence.json` records belonging to that discarded session. Unrelated diagnostics
remain available, and ordinary up/down preserves the bootstrap records.

These are resettable development sessions, not a promise to migrate an obsolete Dogfood identity
or URL scheme. Use the existing reset when discarding stale session state; do not add legacy URL
aliases, parallel realm files or a production-migration compatibility path. Ordinary startup does
not reset data, and an import-file change never authorizes deleting a realm on its own. The bounded
Dogfood reset has no Fresh Start plan, backup/restore rehearsal, deletion manifest or approval-token
prerequisite. Production data is outside that boundary.

## Production migration

Production retains the restricted identity-administration model and its explicit, versioned FGAP
migration. `keycloak-migration-apply` is production-only and requires verified private backup and
isolated restore evidence. It must not be repurposed to repair a development realm.

A production migration binds the reviewed semantic definition, migration definition, environment
render and candidate. It permits only the declared changes and requires semantic provider readback,
an empty second plan, deletion of temporary bootstrap authority, negative readback proving that
authority is gone, and a support-safe receipt. Missing, stale or ambiguous evidence fails closed.
These production requirements do not become Dev, Dogfood, E2E or HIL prerequisites.

## Cross-environment comparison

E2E, dogfood, and production are expected to render different concrete realm bytes because URLs, public JWKS, SMTP coordinates, and organization metadata may differ.

Cross-environment comparison uses the same candidate semantic definition. Concrete `overlayDigest`
and `renderedRealmDigest` values are environment-specific. Production migration additionally has
environment-specific semantic readback; development import provenance makes no such claim.

Within one environment and candidate, repeated rendering of unchanged inputs must produce the same
semantic/render identity. This determinism does not prove that an existing realm consumed the import.

## Release and readiness evidence

Where human-testing readiness evidence is emitted, schema version 6 records `realmEvidence` with
`initializationMode=realm-import`, the two semantic-definition digests, `overlayDigest`,
`renderedRealmDigest`, `candidateRealmDefinitionMatched=true`, `environmentRealmRenderStable=true`
and `containsSecrets=false`. These are matching render-provenance facts, not semantic provider
readback or a claim that authentication works. Pre-runtime render evidence keeps
`semanticReadbackDigest=null` and `semanticReadbackVerified=false`.

Green isolated Compose/native qualification proves the selected source in its disposable test
environment. Target Dogfood readiness additionally needs current live evidence for its reviewed
HTTPS origins, actual authentication and permitted identity operations, ordinary restart, and
deployment identity. A stored realm with obsolete URLs blocks readiness even when source and
generated import are correct. Do not relax issuer/audience validation to accept it.

Dogfood startup and direct first-owner bootstrap check the live identity boundary before reporting
success or creating an invitation. The check uses trusted-CA HTTPS discovery, matches the issuer and
authorization/token/userinfo/JWKS/logout endpoints to the reviewed auth origin, and exercises the
existing identity-admin `private_key_jwt` authentication with that issuer as assertion audience.
Returned token issuer/client identity must match. Failure reports the boundary without exposing
credentials; it does not patch static IAM or reset data. This check proves the observed OIDC and
client-authentication boundary, not complete realm equivalence, invitation success, physical-device
CA trust or human acceptance.

An explicit reset of the same source and specification does not itself change the qualified
candidate. Refresh the affected live deployment and physical-preparation evidence after reset;
externally stateful evidence is not reusable merely because repository paths are unchanged.
Changed candidate source or specification requires new qualification through the normal delivery
lanes. Equivalent full integration work is not repeated solely for an unchanged Main promotion.

HIL starts only after mandatory exact-candidate qualification, ready Dogfood deployment and an
in-place installation of that build on the physical iPhone. VoiceOver, session behavior and explicit
owner acceptance remain physical/human results. Candidate Cut, Fresh Start and a versioned readiness
document are not prerequisites for beginning this development test loop.

## Operator rule

If a Backend or MCP value is known for an environment before deployment starts, it belongs in the matching Spring profile. If it is genuinely deployment-specific, it may be supplied externally. If it is an IAM topology decision, it belongs to the static semantic realm source or an explicit version migration. If it depends on a concrete human or workload lifecycle event, it belongs to Server or Agent Runtime Control.
