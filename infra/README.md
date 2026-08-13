# Weave Infrastructure

**Repeatable self-hosted Weave stack for operators.**

`infra/` owns the Docker Compose foundation for a self-hosted Weave deployment: identity, native collaboration services, backend API routing, local HTTPS, provider-readiness checks, backups, restore rehearsal, migration evidence, and support diagnostics. Executable OpenTofu/Terraform and its state have been retired.

This directory is the provider-stack implementation layer inside the Weave monorepo. The canonical product bootstrap boundary is `../docs/bootstrap-foundation-contract.md`; the canonical Keycloak ownership and migration model is `../docs/architecture/keycloak-realm-lifecycle.md`.

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

Keycloak static IAM is deployment-owned. `infra/weave-workspace/keycloak` contains the canonical semantic source and versioned migration definition. Each environment renders its own public URLs, organization presentation metadata, SMTP coordinates, and public JWKS.

A generated `realm.json` is an environment deployment artifact, not source control authority.

- Proven-empty Fresh Start: startup import creates the realm; the bounded FGAP operation is authorized by Fresh-Start plan/apply evidence.
- Disposable E2E: the same operation is allowed only after exact run-owned namespace absence is proven before resource creation.
- Existing non-empty dogfood/prod realm: static IAM changes require explicit versioned migration, private backup, and isolated restore rehearsal.

Normal Server runtime never reconciles static realm structure.

## Local/dev path

For normal local development:

```bash
./gradlew :infra:composeDevDependenciesReady
./gradlew :server:serverDevBoot
```

The dev renderer produces the environment-specific realm from the same semantic source used by every other environment. Dev does not inherit the persistent dogfood/prod backup contract; persistent-realm recovery gates apply only when an existing non-empty persistent realm is being mutated.

Use `./gradlew testApp` for the disposable Fresh product proof. The E2E path must prove its exact namespace is absent before it creates resources and may not infer Fresh eligibility from the profile name alone.

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
- `../gradle/tasks/test-app.sh`: disposable invitation/activation/PKCE/ARC/MCP/product proof.

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

The environment is explicit and independent of Git branch. `dogfood` and `prod` are persistent; `e2e` is disposable and run-unique. Environment files provide reviewed non-secret coordinates; SecretRefs and private keys remain outside generated support-safe artifacts.

Compose remains the lifecycle engine. `compose.sh <environment> up/down` owns SecretRef permissions, provenance labels, resource ownership checks, static migration completion checks, and exact isolated cleanup. It does not perform general identity reconciliation or select an environment from a branch.

## Operator safety

- `compose.sh <environment> down` stops without deleting persistent volumes or SecretRefs.
- Destructive cleanup exists only for exact isolated E2E, or through an explicitly approved Fresh Start operation.
- Existing persistent realm upgrades require private backup and restore rehearsal before static IAM mutation.
- Support bundles are not backups; keep database dumps, private config, and recovery archives private.
