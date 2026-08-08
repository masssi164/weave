# Keycloak authority and migration contract

The pinned Weave Specification Corpus is normative. This document describes the executable
infrastructure projection: Keycloak is the identity and OAuth authority, while Weave Server owns
dynamic product identity lifecycle through its bounded Admin REST anti-corruption layer.

## Ownership

- The generated, secret-free `keycloak/import/weave-realm.json` is the canonical static realm
  baseline. It is projected from the pinned desired state and public JWKS derived from each
  machine identity's owner-held private key.
- Weave Server owns human invitation, membership, role, session, and organization lifecycle.
- Agent Runtime Control owns dynamic workload client registration through the restricted DCR
  boundary.
- MCP has no Keycloak administrative credential or realm mutation authority.
- Admin Console uses browser OIDC and Weave Server `/admin/**`; it never calls Keycloak Admin REST.

There is no general-purpose Identity Ops reconciler, permanent `kcadm` dependency, or second
identity authority. Routine startup imports the baseline and verifies one exact migration receipt.

## Static import and File Vault

The renderer emits one realm import plus a manifest-bound migration bundle. Realm artifacts contain
no private JWK members or real shared-secret values. Dogfood sends invitation mail to its private
persistent Mailpit instance over implicit TLS and has no SMTP shared secret. Production SMTP
credentials arrive as a mode-restricted SecretRef through Keycloak File Vault; the realm contains
only the vault expression. First-party machine clients use `private_key_jwt`: Keycloak receives
public JWKS, and each private JWK remains with its owning process.

The only operator/application environments are:

- `dev`: required local infrastructure; Spring Boot applications run on the host with H2.
- `dogfood`: persistent production-shaped application topology with PostgreSQL and protected
  dogfood inputs.
- `prod`: release-capable topology with PostgreSQL and protected production inputs.
- `e2e`: disposable production-shaped verification with a run-unique namespace and PostgreSQL.

`dev`, `dogfood`, and `main` may still name Git delivery lanes. A workflow always selects an
operator environment explicitly; the Git ref never selects Compose or Spring behavior.

Dogfood uses its private persistent Mailpit instance over implicit TLS so the initial invitation,
required-action, and email-verification flow remains inspectable on the reviewed LAN. It carries no
SMTP credential. Production uses reviewed external implicit-TLS SMTP: the username is a non-secret
environment coordinate and the password is an operator-owned mode-`0600` SecretRef mounted only
into Keycloak File Vault as `weave_smtp-password`. Declarative production realm state carries only
`${vault.smtp-password}` and never a rendered credential value.

## Stock Keycloak

The runtime uses the approved official Keycloak OCI index by exact digest. It is pulled and
verified; it is not rebuilt, relabelled, or extended with a custom provider JAR or theme.
Stock-image evidence records the approved reference, resolved local image ID, and RepoDigest.
Weave source revision labels apply only to source-built backend and MCP images.

Authentication uses Authorization Code with PKCE S256 through the system browser. Password/direct
grants and embedded login are disabled. Human/public and workload clients remain separate.

## Bounded post-import migration

Keycloak 26.7 imports client authorization before organizations, so the specific-organization FGAP
permission requires one post-import operation. A fresh install runs:

```text
compose.sh <environment> keycloak-migration-apply
```

The operation is explicit and bounded:

1. render and digest-validate the exact baseline, migration manifest, and bundle;
2. start Keycloak once to import the default-deny baseline;
3. create a mode-0600 random bootstrap SecretRef;
4. stop Keycloak and invoke `kc.sh bootstrap-admin service` for the exact
   `weave-realm-migration-bootstrap` client;
5. restart Keycloak and invoke the Weave Server migration CLI with a secret-file coordinate,
   never a secret value in argv;
6. require semantic readback, an empty second plan, deletion of the bootstrap client, and negative
   readback proving absence;
7. atomically write a support-safe receipt and delete the temporary SecretRef.

The temporary bootstrap services belong to the inactive internal `identity-migration` profile and
are reachable only by explicit service targeting. Normal Keycloak, Server, MCP, and receipt-check
services do not mount the bootstrap SecretRef. The migration services receive no first-party
private JWK. Failed or stale receipts block application startup.

## Runtime gate

Dogfood, E2E, and production Server containers depend on the networkless, secretless
`keycloak-realm-migration-receipt-check` one-shot command. Dogfood/prod can satisfy it through the
qualified backup-gated migration; dev/E2E fail before application readiness because their
disposable migration contract is not yet qualified. The receipt binds:

- exact manifest, bundle, baseline-artifact, and semantic baseline digests;
- Keycloak `26.7.1` and the one qualified operation ID;
- a closed mutation-code set and mutation count;
- semantic readback and empty second plan;
- bootstrap authority deletion and negative readback;
- `supportSafe=true` and `containsSecretValues=false`.

Re-running the explicit migration after a valid receipt is a non-mutating success. Routine `up`
never performs identity reconciliation.

## Operator tasks

```text
./gradlew :infra:keycloakDogfoodMigrationApply
./gradlew :infra:keycloakProdMigrationApply
./gradlew :infra:keycloakRuntimeImageBuild
```

`install.sh dogfood|prod` invokes the migration once before normal startup. Dev/E2E remain
fail-closed until a separately reviewed disposable-environment migration contract exists. All destructive E2E
cleanup, provenance, backup/restore, rootless execution, capability-drop, and support-safety rules
remain unchanged.

Desired state never contains human users or passwords. Automated product proof creates owners and
members through Weave Server invitations, completes Keycloak required actions in a real browser,
and retains generated passwords only in process memory. Neither infrastructure nor Weave Server
marks email verified through an administrative API.

`POST /api/v1/identity/session/reconcile` remains the provider-neutral post-login use case. It
checks current organization entitlement and permits at most one standard refresh-token grant before
workspace bootstrap. Admin credential, required-action, impersonation, and general session routes
remain outside the Server operation allowlist.
