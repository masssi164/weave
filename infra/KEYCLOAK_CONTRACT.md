# Keycloak authority and environment realm contract

The pinned Weave Specification Corpus is normative. Keycloak is the identity and OAuth authority;
Weave Server owns only dynamic product identity lifecycle through its bounded Admin REST
anti-corruption layer. Static IAM structure is deployment state, never product request state.

## One semantic source, many derived environment renders

Weave does not maintain `dev-realm.json`, `dogfood-realm.json`, and `prod-realm.json` as independent
sources. Candidate Cut binds two environment-neutral identities:

- `semanticRealmSourceDigest`: the canonical realm baseline revision from the pinned Specification
  Corpus;
- `migrationDefinitionDigest`: the canonical digest of
  `infra/weave-workspace/keycloak/migration-definition.json`.

Each environment then derives its own generated realm from those identities plus a small,
allowlisted overlay and environment-owned public JWKS:

```text
semantic realm source
+ canonical migration definition
+ environment overlay
+ environment-owned public JWKS
        |
        v
.generated/<environment>/keycloak/import/weave-realm.json
```

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
- static service accounts and their minimum collection-gate role grants;
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

Agent Runtime Control owns per-Cell workload client registration, rotation, recovery/finalization,
revocation, and deletion through the restricted DCR boundary. MCP has neither product persistence
nor a Keycloak administrative credential. Human roles and workload entitlement remain orthogonal.

## Fresh import and explicit migration

A fresh Keycloak realm is established through Keycloak's supported startup import from the exact
environment-derived `weave-realm.json`. Existing realms are not reconciled by startup import.

Keycloak 26.7 processes client authorization before Organizations during import, so the exact
specific-organization FGAP projection requires one bounded post-import operation. This operation is
not a general reconciler. It uses an ephemeral bootstrap authority, performs the closed reviewed
FGAP mutation, requires semantic readback and an empty second plan, deletes the bootstrap authority,
and negatively verifies its absence before publishing a support-safe receipt.

Migration preconditions remain distinct:

- **Fresh Start dogfood:** exact approved Fresh Start plan/apply evidence proves the previous owned
  generation was retired; no legacy realm is transferred.
- **Persistent non-empty dogfood/prod:** private candidate-bound backup plus isolated restore
  rehearsal is mandatory before static IAM mutation.
- **Disposable E2E:** the same static migration may run without durable backup only after a
  machine-verifiable pre-creation proof establishes that the exact isolated run namespace and every
  run-owned resource were absent. An `e2e` profile name or an empty-database assertion is not proof.

Routine startup never performs realm-wide reconciliation and there is no permanent Identity Ops or
`kcadm` authority.

## Lifecycle

1. **Candidate Cut** binds `semanticRealmSourceDigest` and `migrationDefinitionDigest` independently
   of any environment.
2. **Environment configure** validates environment inputs, derives public JWKS, canonicalizes the
   overlay, renders `weave-realm.json`, and records environment render evidence.
3. **Fresh import** creates the static realm when the realm does not yet exist.
4. **Explicit static migration** applies only the reviewed migration definition and publishes
   readback/authority-retirement evidence.
5. **First owner bootstrap** creates one invitation through normal Server identity administration.
6. **Steady state** permits only dynamic Server/Agent Runtime operations; the realm render is not
   reapplied.
7. **Later static IAM change** changes the candidate semantic/migration identity and uses the
   explicit protected migration path for an existing persistent realm.

A software release whose semantic realm source and migration-definition identities are unchanged
requires no static Keycloak mutation merely because application code changed.
