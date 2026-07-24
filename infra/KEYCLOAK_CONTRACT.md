# Keycloak and Identity Ops contract

The pinned Weave Specification Corpus and ADR 0017 are normative. This document is the
operator-facing implementation projection.

## Architectural boundary

Keycloak is the default self-hosted identity authority, not the Weave product API. OIDC,
OAuth 2.0, SAML, SCIM, and LDAP are the standards-facing seams. Weave domain use cases consume
provider-neutral identity ports, and provider adapters translate those ports to Keycloak or to
another IDM. No client may depend on Keycloak Admin REST shapes.

The fixed realm baseline is reconciled only by the `infra` module. Server startup, the Flutter
client, Admin Console, and MCP server do not import or mutate realm state. Dynamic Weaver
workload clients remain Agent Runtime Control scope and are not inferred from a human role.

The organization roles are `owner`, `admin`, `member`, and `guest`. Canonical organization
membership is represented with native Keycloak organization/group/role capabilities defined by
the pinned desired state. Provider-specific identifiers stay behind the adapter boundary.

## Exact runtime profiles

The only runtime/application profiles are:

- `dev`: local provider dependencies; Spring Boot runs on the host with H2.
- `test`: integrated application and providers with PostgreSQL; disposable E2E or a separately
  reviewed persistent test deployment.
- `prod`: release-capable integrated topology with protected, externally supplied coordinates and
  secrets.

`dev`, `dogfood`, and `main` may still name Git delivery lanes. Delivery lanes are not runtime
profiles and must not be passed to Compose, Spring, Identity Ops, backup, or teardown commands.

## Stock Keycloak

The runtime uses the approved official Keycloak OCI index by exact digest. It is pulled and
verified; it is not rebuilt, relabelled, or extended with a custom provider JAR or theme.
Stock-image evidence records the approved reference, resolved local image ID, and RepoDigest.
Weave source revision labels apply only to source-built backend, MCP, and Identity Ops images.

Authentication uses Authorization Code with PKCE S256 through the system browser. Password/direct
grants and embedded login are disabled. Human/public and workload clients remain separate.

## Rootless one-shot Identity Ops

`infra/weave-workspace/keycloak/identity_ops.py` is the only fixed-baseline reconciler. It runs as
a rootless, one-shot container and uses the matching official Keycloak `kcadm.sh` distribution.
It supports exactly `plan`, `apply`, and `verify`.

The lifecycle creates one bounded bootstrap service-account credential, starts stock Keycloak,
runs Identity Ops, verifies complete readback, and leaves no durable broad administrator. Evidence
is written only to `WEAVE_GENERATED_ROOT/identity-ops/identity-ops.json`; it is support-safe and
must contain no raw secret, access token, Admin REST body, member email, or provider response.

Ordinary reconciliation creates or updates managed resources and never deletes managed or
unmanaged resources. Destructive changes require a separate, reviewed tombstone/recovery contract
with current backup-and-restore evidence. The former privileged supervisor, sanitizer sidecar,
custom Keycloak event listener, and Docker-socket control plane are retired authorities.

## Reproducible module tasks

The `infra` module owns environment and Identity Ops tasks under
`infra/gradle/tasks/environment-profiles.gradle`, applied by `infra/build.gradle`.

```text
./gradlew :infra:tasks --group "weave infrastructure"

./gradlew :infra:composeDevConfig
./gradlew :infra:composeTestConfig
./gradlew :infra:composeProdConfig

./gradlew :infra:identityDevPlan
./gradlew :infra:identityDevApply
./gradlew :infra:identityDevVerify

./gradlew :infra:identityTestUsersFile
./gradlew :infra:identityOpsImageBuild
./gradlew :infra:keycloakStockImageResolve
```

The same `plan`/`apply`/`verify` task family exists for `test` and `prod`. Test and prod require a
private reviewed `WEAVE_ENV_FILE`; prod rejects a test-users file before any mutation. The
mode-0600 test-user fixture is test-only, idempotent, and never logs its generated secret.

## Session correctness

Realm reconciliation and user-session reconciliation are distinct:

- Identity Ops converges realm, clients, roles, groups, scopes, and fixed service accounts.
- `POST /api/v1/identity/session/reconcile` is a provider-neutral product use case. It checks the
  authenticated subject's current native organization entitlement and returns a typed result.
- If the response says access changed, the client performs exactly one standard refresh-token
  grant before workspace bootstrap. It never calls Keycloak Admin REST and never loops refreshes.

A custom Keycloak event listener is not a correctness dependency. A future event adapter may
reduce latency only after separate threat modelling and interoperability evidence.

## Release evidence

`WEAVE_CANDIDATE_COMMIT` binds lane, deployment, and human evidence. Source-built images bind to
the protected, tree-equivalent `WEAVE_IMAGE_SOURCE_COMMIT`. The stock Keycloak image instead binds
to its approved upstream OCI digest. `candidate-source-mapping.json` carries the closed four-image
set: `backend`, `mcp`, `identity-ops`, and `keycloak`.

An integrated run is acceptable only when:

1. the normalized `test` Compose model is stable;
2. Identity Ops apply and verify succeed;
3. a second explicit Identity Ops plan has `operationCount == 0`;
4. exact image and pinned-spec-corpus provenance is retained;
5. teardown touches only the exact owned isolated `test` namespace; and
6. support artifacts pass secret and identifier safety checks.

Availability smoke evidence does not replace authenticated E2E behavior or physical-iPhone proof.
