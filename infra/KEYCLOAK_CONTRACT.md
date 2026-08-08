# Keycloak and Identity Ops contract

The pinned Weave Specification Corpus and ADR 0017 are normative. This document is the
operator-facing implementation projection of the
`weave.keycloak-desired-state/v2` contract.

## Architectural boundary

Keycloak is Weave's fixed identity-management backbone and identity authority, not the Weave
product API. Human authentication, local users, organizations, groups, roles, invitations,
required actions, credentials, sessions, workload clients, and upstream identity integration are
all administered through Keycloak. OIDC and OAuth 2.0 are the client-facing seams; SAML,
OIDC brokering, and LDAP/AD federation are Keycloak-managed upstream seams. Identity is not a
runtime-selectable southbound provider category and does not enter the provider patch panel. No
client may depend on Keycloak Admin REST shapes.

The fixed realm baseline is reconciled only by the `infra` module. Server startup, the Flutter
client, Admin Console, and MCP server do not import or mutate realm state. Dynamic Weaver
workload clients remain Agent Runtime Control scope and are not inferred from a human role.

The organization roles are `owner`, `admin`, `member`, and `guest`. Their flat Keycloak
projections are `/owners`, `/admins`, `/members`, and `/guests`. The independent Weaver
capability `agent-runtime.entitled` projects to `/capabilities/weaver`; that group path is an IDM
implementation detail and never a Flutter, MCP, public API, or domain contract. Human role and
Weaver capability remain orthogonal.

## Exact runtime profiles

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
./gradlew :infra:composeDogfoodConfig
./gradlew :infra:composeE2eConfig
./gradlew :infra:composeProdConfig

./gradlew :infra:identityDevPlan
./gradlew :infra:identityDevApply
./gradlew :infra:identityDevVerify

./gradlew :infra:identityOpsImageBuild
./gradlew :infra:keycloakRuntimeImageBuild
```

The same `plan`/`apply`/`verify` task family exists for `dogfood`, `prod`, and `e2e`. Those
environments require a private reviewed `WEAVE_ENV_FILE`; E2E additionally requires an isolated
run identifier. Desired State never contains human users or passwords.
Automated product proof creates the owner/member through Weave invitations, completes Keycloak
required actions in a real browser, and retains generated passwords only in process memory.
The realm keeps native email verification enabled. Invitation registration therefore follows
the organization action link and Keycloak's subsequent one-time `VERIFY_EMAIL` action link in
the same browser session before credential setup. Neither Identity Ops nor Weave Server marks
email as verified through an administrative API.

Identity Ops verifies the positive delegated-administration boundary after convergence: the
bounded service account must be able to read the exact primary organization and its own
service-account user. Keycloak 26.7 cannot combine the required all-Users lifecycle permission
with a realm-wide negative password-reset permission, so Weave makes no false provider-level
deny claim. The administrative client remains Guarded behind a closed backend operation
allowlist and an internal network boundary. Credential, required-action, impersonation,
session-creation, and general session routes are absent; only member-bound logout after current
organization membership verification is allowed for revocation/offboarding. A failed positive
probe blocks apply/readiness without retaining the token or provider response.

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

1. the normalized `e2e` Compose model is stable and isolated;
2. Identity Ops apply and verify succeed;
3. a second explicit Identity Ops plan has `operationCount == 0`;
4. exact image and pinned-spec-corpus provenance is retained;
5. teardown touches only the exact owned isolated `e2e` namespace; and
6. support artifacts pass secret and identifier safety checks.

Availability smoke evidence does not replace authenticated E2E behavior or physical-iPhone proof.
