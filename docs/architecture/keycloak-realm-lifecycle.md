# Keycloak realm lifecycle

Status: canonical implementation architecture for static IAM ownership, environment rendering, migration qualification, and runtime lifecycle.

## Objective

Weave treats Keycloak as a fixed platform identity authority while keeping static IAM configuration reproducible and environment-specific without maintaining multiple hand-edited realm files. The architecture separates candidate semantics, environment projection, static migration, and dynamic human lifecycle.

## Source of truth

The authoritative static IAM source lives under `infra/weave-workspace/keycloak`.

Candidate-level semantic identity consists of exactly two values:

- `semanticRealmSourceDigest`: the normative semantic realm baseline revision from the pinned specification corpus;
- `migrationDefinitionDigest`: the canonical digest of `infra/weave-workspace/keycloak/migration-definition.json`.

No rendered `realm.json`, environment URL, SMTP endpoint, public JWKS value, candidate run id, or private secret contributes to candidate semantic identity.

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
- `render-manifest.json`.

Generated files are deployment artifacts. They are not independently maintained configuration sources.

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

Agent Runtime Control owns workload-cell lifecycle and per-cell workload identity. MCP remains workload-only and receives no realm-administration authority. Dynamic workload clients are not human users and must not share credentials or grants with human lifecycle administration.

## Secret boundary

Private key material remains with the runtime that owns it. Rendering may derive the corresponding public JWK/JWKS and place only public values into the RealmRepresentation.

Generated realm and support-safe evidence must reject:

- private JWK members;
- passwords;
- bearer or refresh tokens;
- cookies;
- client-secret values;
- SecretRef payload values;
- raw provider credentials or diagnostic payloads.

## Fresh realm import

For a proven-empty realm, Keycloak startup import establishes the static baseline. Startup import is not a reconciliation mechanism and is not used to update an existing realm.

After import, the bounded FGAP migration applies only state that the supported Keycloak import order cannot express safely. The migration must be explicit and idempotent.

## Migration qualification

### Proven-empty Fresh Start

A persistent Fresh Start is authorized by the exact approved Fresh-Start plan and apply evidence proving the retired generation was removed and the target generation is newly created.

The new empty realm does not require a fabricated backup. Recovery evidence belongs to the retired generation before destructive cutover, not to the newly created empty realm.

### Disposable E2E

Disposable E2E may use the same static migration without durable backup only when machine-verifiable preflight proves the exact run-owned Docker namespace and resources do not yet exist. Profile name alone is insufficient.

### Existing persistent realm

A genuinely existing non-empty dogfood or production realm changes only through a versioned migration bound to:

- current semantic baseline identity;
- target semantic baseline identity;
- migration-definition digest;
- environment render evidence;
- private backup evidence;
- successful isolated restore rehearsal.

Ambiguous or stale state fails closed before mutation.

## Migration completion

Every qualified migration path must prove:

1. exact candidate semantic identity;
2. exact environment render identity;
3. allowlisted mutation only;
4. semantic post-apply readback;
5. an empty second plan;
6. deletion of temporary bootstrap authority;
7. negative readback proving bootstrap authority is gone;
8. support-safe receipt evidence.

Final `realmEvidence` binds:

- `semanticRealmSourceDigest`;
- `migrationDefinitionDigest`;
- `overlayDigest`;
- `renderedRealmDigest`;
- `semanticReadbackDigest`;
- `candidateRealmDefinitionMatched=true`;
- `environmentRealmRenderStable=true`;
- `semanticReadbackVerified=true`.

## Cross-environment comparison

E2E, dogfood, and production are expected to render different concrete realm bytes because URLs, public JWKS, SMTP coordinates, and organization metadata may differ.

Cross-environment comparison therefore requires only identical candidate semantic definition digests. Concrete `overlayDigest`, `renderedRealmDigest`, and `semanticReadbackDigest` are environment-specific.

Within one environment and candidate, repeated configure/install must converge to the same semantic/render identity.

## Release and readiness evidence

Candidate Manifest v4 binds only semantic realm identity and the three candidate runtime images: Server, MCP Server, and Keycloak Runtime.

Human-testing readiness schema v5 consumes finalized environment `realmEvidence`. Provider-health evidence must match the dogfood deployment realm evidence exactly. Automated E2E evidence may carry a different environment render while sharing the same candidate semantic identity.

No v3 candidate reader, rendered-realm candidate authority, dual write, compatibility conversion, or second static IAM authority is retained.

## Operator rule

If an IAM change can be known before a concrete human or workload exists, it belongs to the static semantic realm source or an explicit version migration. If it depends on a concrete human or workload lifecycle event, it belongs to Server or Agent Runtime Control.
