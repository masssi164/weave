# Keycloak authority and environment realm contract

The pinned Weave Specification Corpus is normative. Keycloak is the identity and OAuth authority;
Weave Server owns only dynamic product identity lifecycle through its bounded Admin REST
anti-corruption layer. Static IAM structure is deployment state, never product request state.

## One semantic source, many derived environment renders

The renderer reads `contracts/examples/keycloak-desired-state.valid.json` from the corpus pinned by
`specs/weave-specs.lock.json`, the versioned migration definition and the stock import defaults.
Weave does not maintain independent `dev-realm.json`, `dogfood-realm.json` and `prod-realm.json`
sources. Render provenance separates two environment-neutral definition identities:

- `semanticRealmSourceDigest`: the canonical realm baseline revision from the pinned Specification
  Corpus;
- `migrationDefinitionDigest`: the canonical digest of
  `infra/weave-workspace/keycloak/migration-definition.json`.

Each environment derives `.generated/<environment>/keycloak/import/weave-realm.json` from the
reviewed semantic source, import projection, environment overlay and environment-owned public JWKS.
The migration definition also supplies the native organization mapper used by development imports;
its presence in render provenance does not require executing a production migration.

The generated `weave-realm.json` is a deployment artifact, not configuration truth. E2E, dogfood,
and production may legitimately have different URLs, SMTP coordinates, organization presentation,
and public JWKS. Repeating the same inputs for one environment must reproduce the same render.

The renderer writes support-safe evidence under the environment generated root:

```text
keycloak/semantic-realm-source.json
keycloak/migration-definition.json
keycloak/overlay.json
keycloak/import/weave-realm.json
keycloak/realm-render-evidence.json
render-manifest.json
```

`realmIdentity` separates four digests:

- `semanticRealmSourceDigest` - candidate-bound static IAM meaning;
- `migrationDefinitionDigest` - candidate-bound transformation intent;
- `overlayDigest` - environment-specific public configuration;
- `renderedRealmDigest` - concrete environment RealmRepresentation.

No environment-specific raw Keycloak export can become a second source of truth.

## Static realm ownership

The semantic baseline owns structure that must exist before any human or workload lifecycle event:

- realm security switches and Organizations/FGAP enablement;
- first-party clients and their authentication methods;
- realm/client roles, client scopes, protocol mappers, required actions, and client policies;
- the current single-primary deployment organization semantic slot;
- `/owners`, `/admins`, `/members`, `/guests`, `/capabilities`, and
  `/capabilities/weaver` organization groups;
- static service accounts and environment-appropriate role grants;
- static FGAP intent and the workload-registration policy.

The current primary organization is a deployment-owned bootstrap organization for the
single-primary-organization contract. Its existence is static; its display name, alias,
description, and redirect URI are environment render inputs. This does not establish a general
Server API for creating Keycloak organizations.

## Environment overlay ownership

The environment overlay may resolve only public/deployment coordinates:

- public Weave/API/Auth origins and derived redirect URIs;
- SMTP host, port, sender metadata, and non-secret username;
- current primary-organization presentation;
- immutable Keycloak runtime image identity;
- references to environment-owned machine identities.

Private first-party JWKs remain solely with their owning process. The renderer derives public JWKS
inside that environment and only those public keys enter the realm render. Keycloak File Vault is
used only for symmetric shared secrets Keycloak genuinely consumes, such as authenticated
production SMTP. The realm contains `${vault.smtp-password}`, never the password value.

For `dev`, `dogfood` and isolated `e2e`, the reviewed projection includes stock client scopes and
the native organization mapper in the import, and adds `manage-organizations` and `manage-users`
to `weave-identity-admin`. These development grants are forbidden in `prod`. They do not expand
the permanent Server operation allowlist below.

## Dynamic Server ownership

Weave Server never creates or repairs realm structure. Its permanent identity-admin credential is
allowlisted only for human lifecycle operations:

- create/list/resend/revoke organization invitations;
- resolve/read organization members;
- set exact membership in the existing product-role groups;
- toggle `/capabilities/weaver` entitlement;
- suspend/resume the existing human identity;
- revoke member sessions;
- offboard by revoking sessions, removing organization membership, and disabling the user.

Static realm/client/scope/mapper/role/group/organization/authentication-flow mutation is rejected by
the permanent Server operation policy. Server startup does not reconcile Keycloak.

The first owner follows the same rule. A protected temporary Server bootstrap route may create one
stock Keycloak Organizations invitation after proving there are no human users. It does not create
an organization, group, role, realm, or static client.

## Dynamic workload ownership

Where the optional workload lifecycle is enabled, Agent Runtime Control owns per-Cell workload
client registration, rotation, recovery/finalization, revocation and deletion through the restricted
DCR boundary. It does not become a native Dogfood prerequisite. MCP has neither product persistence
nor a Keycloak administrative credential. Human roles and workload entitlement remain orthogonal.

## Development import and reset

Stock Keycloak startup import is the complete identity initialization for `dev`, `dogfood` and
isolated `e2e`. No post-import FGAP migration, temporary migration credential, backup prerequisite
or production receipt is required in those lanes. E2E still proves ownership of its absent,
run-unique namespace before creating resources and cleans that namespace after execution.

Startup import skips an existing realm. A current `weave-realm.json` therefore does not establish
that persisted issuer URLs, audiences, redirects, client URLs or role grants are current. Ordinary
startup never reconciles static IAM and never deletes a realm to reapply an import.

Root `dogfoodUp` and `dogfoodDown` preserve the three Dogfood session volumes. An explicit
`dogfoodReset` validates the exact checkout, reviewed environment, secret inputs and stable TLS,
then recreates the fixed `weave-dogfood` stack with empty PostgreSQL, native Files and Mailpit
volumes. This discards their users and product data, and imports the current canonical projection.
Reviewed secret files and operator-owned TLS outside the volumes remain intact. The existing
closed cleanup also handles the known unlabeled OpenTofu-era Dogfood stack after ownership checks;
it does not remove bind-mounted host directories or unrelated resources.

Reset is serialized with first-owner bootstrap. After discarding the session resources, it removes
only the two generated owner-bootstrap request/evidence records for that session. Ordinary startup
and shutdown preserve them, and unrelated diagnostics remain available.

The resettable Dogfood contract requires no Fresh Start plan, migration receipt, backup/restore
rehearsal, deletion manifest or approval token. Obsolete development state is replaced through
that existing reset, not preserved with legacy URL aliases, separate realm sources or compatibility
migrations. This development boundary does not authorize production data deletion.

## Production migration

Production retains its restricted authority and explicit versioned FGAP migration. The existing
`keycloak-migration-apply` executor accepts only `prod` with verified private backup and isolated
restore evidence. It binds the candidate, semantic definition, migration definition and environment
render, performs only the reviewed mutations, requires semantic readback and an empty second plan,
deletes temporary bootstrap authority and negatively verifies its absence before issuing the receipt.
Ambiguous or stale evidence fails closed. It is not a Dogfood repair command.

Normal serving still has no static IAM reconciliation or permanent Identity Ops authority. An
application-only update does not require a production IAM mutation merely because code changed.

## Readiness and evidence

Human-testing readiness schema version 6 records matching, stable `realm-import` provenance. Its
definition/render digests are not live provider readback or successful authentication. Pre-runtime
render evidence explicitly has `semanticReadbackDigest=null` and `semanticReadbackVerified=false`.

Isolated automated qualification and readiness of the deployed Dogfood realm are separate results.
Before HIL, the actual target must prove current authentication, allowed identity operations,
ordinary restart and correct HTTPS configuration; the exact qualified build must be installed in
place on the physical iPhone. Automated accessibility is not physical VoiceOver acceptance.

Dogfood startup and direct owner bootstrap now check trusted-CA public HTTPS discovery against the
reviewed issuer and endpoint URLs, then authenticate the existing private-key identity-admin client
with the canonical issuer audience and require matching returned token identity. This fail-closed
preflight does not mutate static IAM or reset data. It establishes the OIDC/client-auth boundary;
it does not prove full realm equivalence, successful invitation or physical iPhone trust.

Resetting the same candidate requires fresh affected live deployment and physical-preparation
evidence. It does not require repeating equivalent full integration solely because session data was
discarded. Changed source or specification requires new qualification; externally stateful evidence
is not cached based on unchanged source paths. Candidate Cut and a versioned readiness document are
not prerequisites for the development human-test loop.
