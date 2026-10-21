# Weave Infrastructure

**Repeatable self-hosted Weave stack for operators.**

`infra/` owns the Docker Compose foundation for a self-hosted Weave deployment: identity, native collaboration services, backend API routing, local HTTPS, provider-readiness checks, backups, restore rehearsal, migration evidence, and support diagnostics. Executable OpenTofu/Terraform and its state have been retired.

This directory is the provider-stack implementation layer inside the Weave monorepo. The pinned
specification corpus owns the product and delivery contracts. Implementation guides are
[Product bootstrap](../docs/bootstrap-foundation-contract.md) and
[Keycloak realm lifecycle](../docs/architecture/keycloak-realm-lifecycle.md).

## What it provisions

- Caddy as HTTPS gateway.
- Keycloak as fixed platform identity authority.
- Environment-specific, secret-free Keycloak realm deployment artifacts rendered from one semantic candidate definition.
- `weave-backend` behind the canonical API route.
- `weave-mcp-server` as the workload-only Spring AI MCP projection.
- PostgreSQL databases and persisted Docker volumes.
- Native Weave Files, Calendar, and Chat defaults.
- Optional explicitly selected southbound provider profiles.
- Install, verify, migration, teardown, backup/restore, and support-bundle scripts.

## Identity lifecycle

Keycloak static IAM is deployment-owned. The semantic baseline comes from the corpus pinned by
`specs/weave-specs.lock.json`; `weave-workspace/keycloak` contains its projection support, stock
import defaults and versioned production migration definition. Each environment renders its own
public URLs, organization presentation, SMTP coordinates and public JWKS.

A generated `keycloak/import/weave-realm.json` is an environment deployment artifact, not a second
configuration source. Keep the canonical inputs and current generated artifact aligned.

- `dev`, `dogfood` and isolated `e2e`: stock startup import completely initializes identity, including
  the reviewed development role grants and native organization mapper. No post-import migration,
  temporary migration authority, backup prerequisite or production receipt is required.
- Existing realm: startup import skips it. New generated JSON does not update persisted issuer,
  audience or redirect/client URLs, so a green isolated test does not prove the target realm is ready.
- Stale resettable Dogfood state: use the existing explicit `dogfoodReset` lifecycle to recreate it
  from the current import. Do not maintain legacy URL aliases or a separate compatibility realm.
- Production: static IAM changes retain the restricted, versioned migration path with private
  backup, isolated restore rehearsal and semantic readback. Its executor is production-only.

Normal Server runtime never reconciles static realm structure.

## Local/dev path

For normal local development:

```bash
./gradlew :infra:composeDevDependenciesReady
./gradlew :server:serverDevBoot
```

The dev renderer uses the same pinned semantic baseline as other environments. Root `devUp`,
`devRun` and `devDown` are the supported convenience tasks for dependency and host-process lifecycle.
Production backup and migration requirements do not apply to these development tasks.

Use `./gradlew testApp` for disposable product qualification. The E2E path proves its run-unique
namespace is absent before creating resources and removes only its owned resources afterwards.

## Resettable Dogfood path

Root `dogfoodUp` builds and starts the exact clean checkout; `dogfoodDown` stops it while retaining
the session. Explicit `dogfoodReset` recreates the fixed `weave-dogfood` project and its PostgreSQL,
native Files and Mailpit session volumes. Reset discards their users and product data but preserves
reviewed secrets and the operator-owned CA, certificates and keys outside Docker volumes. The
existing closed legacy cleanup checks ownership before removing known unlabeled old Dogfood
resources; it does not remove host bind-mount directories or unrelated resources.

Reset and owner bootstrap share a session lock. Only the discarded session's generated
owner-bootstrap request/evidence records are cleared after its resource removal; ordinary up/down
and unrelated diagnostics are preserved.

Dogfood reset has no Fresh Start plan, backup/restore rehearsal, migration receipt, deletion manifest
or approval-token prerequisite. It is separate from ordinary startup and production data policy.
After reset, verify the live target's authentication, permitted identity operations and ordinary
restart, then refresh deployment and physical-preparation evidence. Unchanged source/specification
may reuse valid automated qualification; changed source/specification requires a new qualified
candidate. Actual physical VoiceOver and owner acceptance remain required before Main.

Dogfood startup and direct owner bootstrap fail early when trusted-CA public discovery or
identity-admin private-key authentication disagrees with the reviewed issuer/client boundary.
The preflight neither repairs IAM nor resets data. A stale-origin failure points to the explicit
reset path; successful preflight alone does not establish complete realm equivalence, invitation
success, physical-device trust or HIL readiness.

## Single-host operator path

For a real single-host deployment, start with:

- [Single-host operator guide](docs/single-host-operator-guide.md)
- [Operator runbook](docs/operator-runbook.md)
- [Native platform Compose](docs/native-platform-compose.md)
- [Identity environment parity](docs/identity-environment-parity.md)
- [Keycloak authority contract](KEYCLOAK_CONTRACT.md)
- [Canonical realm lifecycle](../docs/architecture/keycloak-realm-lifecycle.md)

After installation:

```bash
bash weave-workspace/release-verify.sh
bash weave-workspace/operator-check.sh
```

## Public contract

Default local names resolve to loopback; non-local installs derive the same pattern from `<tenant_domain>`:

- `https://<tenant_domain>`: Weave product gateway.
- `https://api.<tenant_domain>/api`: canonical backend API origin.
- `https://auth.<tenant_domain>`: Keycloak.
- `https://mail.<tenant_domain>`: dogfood/dev Mailpit where configured.

Raw external provider routes are optional southbound/admin surfaces and are not the normal member-facing product contract.

## Provider posture

Weave-native is the default for canonical collaboration domains. External providers are explicit adapters and must stay fail-closed when unconfigured. Missing provider credentials produce support-safe unavailable/not-configured readiness rather than insecure fallback behavior.

Support bundles redact tokens, cookies, passwords, signing keys, private JWK material, provider URLs where unsafe, raw provider errors, and generated secrets.

## Repo compass

- `KEYCLOAK_CONTRACT.md`: Keycloak authority and migration boundary.
- `weave-workspace/keycloak/`: semantic realm projection, migration definition, renderer, and verification helpers.
- `weave-workspace/compose.sh`: closed environment lifecycle and bounded migration interface.
- `weave-workspace/install.sh`: idempotent environment preparation/apply wrapper.
- `weave-workspace/teardown.sh`: exact isolated-E2E cleanup only.
- `weave-workspace/backup.sh`: private consistency backup for persistent recovery paths.
- `weave-workspace/support-bundle.sh`: support-safe diagnostics, not backup material.
- `../gradle/tasks/test-app.sh`: disposable invitation, browser PKCE, collaboration, curated MCP,
  authorization, restart and owned-resource cleanup proof.

## Validation

Repository-safe validation:

```bash
./gradlew infraStatic
./gradlew :infra:composeDevConfig
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env ./gradlew :infra:composeDogfoodConfig
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./gradlew :infra:composeProdConfig
WEAVE_E2E_STACK_SCOPE=isolated WEAVE_E2E_RUN_ID=<unique-run-id> \
  WEAVE_ENV_FILE=/absolute/path/to/reviewed-e2e.env ./gradlew :infra:composeE2eConfig
```

The environment is explicit and independent of Git branch. Dogfood preserves resettable session
data between ordinary starts; production retains its separate persistent policy. E2E is disposable
and run-unique. Environment files provide reviewed non-secret coordinates; SecretRefs and private
keys remain outside generated support-safe artifacts.

Compose remains the lifecycle engine. The environment wrapper validates SecretRef permissions,
provenance and resource ownership; production additionally enforces static migration completion.
It does not perform general identity reconciliation or select an environment from a branch.

## Operator safety

- `compose.sh <environment> down` stops without deleting persistent volumes or SecretRefs.
- Destructive development cleanup is bounded to exact isolated E2E resources or the explicit
  fixed-project `dogfoodReset` lifecycle. Ordinary `down` never removes session data.
- Production static IAM upgrades require their private backup, restore and migration evidence.
- Generated realm provenance does not prove live target readiness or replace authentication checks.
- Support bundles are not backups; keep database dumps, private config, and recovery archives private.
