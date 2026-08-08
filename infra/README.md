# Weave Infrastructure

**Repeatable self-hosted Weave stack for operators.**

`infra/` owns the Docker Compose foundation for a self-hosted Weave deployment: identity, chat, files/calendar storage, backend API routing, local HTTPS, provider-readiness checks, backups, restore rehearsal, and support diagnostics. Executable OpenTofu/Terraform and its state have been retired.

This directory is the provider-stack implementation layer inside the Weave monorepo. The canonical product bootstrap boundary is `../docs/bootstrap-foundation-contract.md`: bootstrap deploys the Control Plane first and activates this infra/provider-stack implementation only when the selected profile requires deploy-new self-hosted providers. The daily user experience lives in `../client`, product contracts and provider facades live in `../server`, and this layer makes the stack verifiable and recoverable without exposing secrets.

## What it provisions

- Caddy as the HTTPS gateway.
- Keycloak realm, clients, scopes, roles, and first-party Weave app contract.
- Matrix/Synapse with Matrix Authentication Service delegated auth.
- Nextcloud as the technical/admin/protocol backing service for files and calendar.
- `weave-backend` behind the canonical API route.
- `weave-mcp-server` as an internal OIDC-protected Spring AI stateful Streamable HTTP projection over backend-owned domain tools with standard form elicitation.
- PostgreSQL databases and persisted Docker volumes.
- Default Matrix workspace space/rooms for local/dev and smoke evidence.
- Optional provider-stack config for OpenProject, ONLYOFFICE/Collabora, DevOps candidates, and other guarded seams.
- Install, teardown, release verification, operator checks, backup/restore smoke, and support-bundle scripts.

## What it does not claim yet

- Multi-host high availability.
- Managed SaaS installer behavior.
- Automatic offsite backups.
- A complete Slack/Teams replacement.
- Production connector/provider writes.
- Completed Matrix E2EE product readiness.
- Matrix v1.19 plus pinned MatrixRTC Profile 0 is the Calls target; the obsolete member Calls API and proprietary LiveKit join-grant slice are removed. LiveKit remains a replaceable southbound SFU only.
- Agent/automation runtime.

Those are later product or operations tracks and must stay behind explicit contracts and evidence.

## Quick start: local/dev provider-stack implementation

This is the concrete local provider-stack implementation path, not the canonical product bootstrap entrypoint. Use it when the approved bootstrap profile selects local/self-hosted provider deployment.

Add local host entries before opening browser-facing URLs:

```text
127.0.0.1 weave.test api.weave.test auth.weave.test mail.weave.test files.weave.test matrix.weave.test
```

Prepare provider dependencies, then run Spring Boot separately with H2:

```bash
./gradlew :infra:composeDevDependenciesReady
./gradlew :server:serverDevBoot
```

`composeDevDependenciesReady` builds the rootless Identity Ops image from the pinned official Keycloak distribution, initializes named SecretRefs, renders the `dev` Compose model, reconciles PostgreSQL/Keycloak/Nextcloud, and starts only provider dependencies. `serverDevBoot` starts the host process with `application-dev.yml`: Flyway owns the schema, Hibernate validates it, Open EntityManager in View is disabled, and H2 runs in PostgreSQL compatibility mode. Use `./gradlew :server:serverDevHostSmoke` for a bounded boot/readiness/E2E smoke and `./gradlew :server:serverPostgresIntegrationTest` for the real-PostgreSQL persistence lane.

Nextcloud trusts only the exact Caddy address discovered on the active Docker network. `install.sh` pins `HTTP_X_FORWARDED_FOR`, keeps brute-force protection enabled, provisions calendars through local OCC, and then performs one bounded authenticated WebDAV check plus one CalDAV check. Backend readiness polling does not perform provider authentication, and a `429` stops without retrying.

The backend's direct host port is loopback-bound. Caddy preserves public `/api/health/*` but rejects `/actuator` and `/actuator/*`; protected deployment automation may collect cached Micrometer evidence only through the host-local port.

For TLS trust, port modes, smoke-test inputs, and native app contracts, see [Local bootstrap](docs/local-bootstrap.md).

## Single-host operator path

For a real single-host deployment, start here:

- [Single-host operator guide](docs/single-host-operator-guide.md): target shape, public contract, required inputs, TLS/image/persistence expectations, and verify flow.
- [prod.env.example](weave-workspace/environments/prod.env.example): release-capable operator environment template; copy it outside the checkout and pin every image digest.
- [Operator runbook](docs/operator-runbook.md): install/upgrade, rotation, backup, restore, destructive reset, and triage guidance.
- [CalDAV/CardDAV external clients](docs/calendar-caldav-external-clients.md): DAV discovery, safe external-client credential path, and blocked private calendar/addressbook/profile flows.
- [Connector runtime guardrails](docs/connector-runtime-guardrails.md): disabled-by-default connector runtime, callback, secret, and support-bundle boundaries.
- [Matrix/Synapse southbound Chat Application Service](docs/matrix-synapse-chat-appservice.md): private provider credential, namespace, callback, backup/restore, and isolated proof boundaries.
- [Weaver runtime lifecycle](docs/weaver-runtime-lifecycle.md): Agent Runtime Control cell lifecycle, signed RuntimeProfile v2 input, zero durable cell-byte boundary, external encrypted state, per-cell Keycloak workload identity, and deletion evidence.
- [Weave MCP workload contract](docs/weave-mcp-tool-contract.md): Spring AI transport, workload-only OIDC admission, protected-resource discovery, token exchange, current ARC context, and fail-closed empty catalogs.
- [Identity environment parity](docs/identity-environment-parity.md): one stock-Keycloak/Identity-Ops model across test and production, plus the iPhone Mailpit verification boundary.

After installation, verify public and host-local state:

```bash
bash weave-workspace/release-verify.sh
bash weave-workspace/operator-check.sh
```

## Public contract

Default local names resolve to loopback; non-local installs derive the same pattern from `<tenant_domain>`:

- `https://<tenant_domain>`: Weave product gateway, including `/files` and `/calendar` product routes.
- `https://api.<tenant_domain>/api`: canonical backend API origin.
- `https://auth.<tenant_domain>`: Keycloak.
- `https://mail.<tenant_domain>`: private-CIDR test deployment Mailpit inbox only; absent in production.
- `https://matrix.<tenant_domain>`: Matrix/Synapse/MAS behind the matrix hostname.
- `https://files.<tenant_domain>`: raw Nextcloud technical/admin/protocol fallback.

Product clients should prefer Weave routes and backend APIs where they exist. Raw Nextcloud remains a technical/admin/protocol fallback, not the customer-facing files/calendar UX.

## Provider-stack posture

Optional providers are fail-closed by default:

- OpenProject is an optional provider-backed Boards validation path behind the backend workspace facade, not a visible product UX or direct client dependency.
- ONLYOFFICE/Collabora are optional office candidates behind backend-owned capabilities and launch checks.
- The optional product DevOps facade represents GitLab only and stays disabled unless an explicit runtime contract configures it. Repository delivery and dogfood promotion are GitHub-only.
- Missing provider credentials must produce support-safe unavailable/not-configured readiness, not insecure fallback behavior.
- Support bundles redact tokens, cookies, app passwords, signing keys, provider URLs, raw provider errors, and generated secrets; raw service/provider logs are excluded because generic redaction cannot prove removal of actor/content identifiers.
- Nextcloud Contacts/CardDAV and Forms seams are visible to the backend but stay disabled/fail-closed until the backend contracts are merged and live-validated.
- Private personal calendar/addressbook access is blocked unless a later contract adds explicit sharing, provisioning, or delegated-token behavior.

## Repo compass

- `README.md`: operator overview and entry points.
- `AGENTS.md`: repository navigation notes for maintainers.
- `Makefile`: local helper targets such as `make dev-hosts` and `make smoke`.
- `../.github/workflows/ci.yml`: Compose/JPA/contract validation and exact-candidate integration gates.
- `KEYCLOAK_CONTRACT.md`: realm, client, scope, claim, and audience contract.
- `docs/local-bootstrap.md`: local port modes, TLS trust, integration test inputs, and native app contract.
- `docs/single-host-operator-guide.md`: single-host deployment target.
- `docs/operator-runbook.md`: operations, backup/restore, rotation, and triage.
- `docs/matrix-default-workspace.md`: default Matrix space/room provisioning.
- `docs/matrix-e2ee-posture.md`: current honest E2EE posture.
- `docs/calendar-caldav-external-clients.md`: CalDAV/CardDAV discovery, revocable client credentials, and fail-closed profile boundaries.
- `weave-workspace/compose.sh`: the closed `dev|dogfood|prod|e2e` lifecycle and one-shot Keycloak Identity Ops interface.
- `weave-workspace/install.sh`: idempotent environment preparation and apply wrapper.
- `weave-workspace/teardown.sh`: destructive cleanup for an exact isolated-E2E namespace only; persistent environments have no destructive teardown path.
- `weave-workspace/release-verify.sh`: public endpoint verification for non-local single-host installs.
- `weave-workspace/operator-check.sh`: host-local container and health checks.
- `../gradle/tasks/test-app.sh`: the single run-scoped invitation, Keycloak activation, PKCE, WebDAV, ARC, MCP, revocation, and cleanup proof.
- `weave-workspace/isolated-e2e-calendar-outage.sh`: isolated-only Calendar outage/recovery fixture that deletes only the backend actor's disposable `weave-workspace` calendar and proves cached domain-local degradation while Files stays available.
- `weave-workspace/persistent-dogfood-observation.sh`: read-only before/after hashes and counts for non-destructive persistent dogfood deployment evidence.
- `weave-workspace/nextcloud-auth-security-audit.sh`: support-safe classification of recent invalid-authentication/throttle sources without counter reset or raw addresses.
- `weave-workspace/backup.sh`, `adoption-rehearsal.sh`, `restore-private-backup.sh`, and `support-bundle.sh`: private consistency backup, isolated adoption proof, integrity-only guarded restore preflight, and support-safe diagnostics.
- `weave-workspace/weave-mcp-tool-contract.json`: support-safe canonical domain contract and active Spring AI MCP runtime evidence.
- `weave-workspace/compose.yaml` plus the explicit `compose.dev.yaml`, `compose.dogfood.yaml`,
  `compose.prod.yaml`, and `compose.e2e.yaml` overlays: the one supported process graph and its
  four operator environments.
- `weave-workspace/keycloak/`: rootless one-shot Desired-State Identity Ops without human-user fixtures.

## Validation

Repository-safe validation used by CI:

```bash
./gradlew infraStatic
./gradlew :infra:tasks --group "weave infrastructure"
./gradlew :infra:composeDevConfig
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env ./gradlew :infra:composeDogfoodConfig
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./gradlew :infra:composeProdConfig
WEAVE_E2E_STACK_SCOPE=isolated WEAVE_E2E_RUN_ID=<unique-run-id> \
  WEAVE_ENV_FILE=/absolute/path/to/reviewed-e2e.env ./gradlew :infra:composeE2eConfig
./gradlew :server:serverDevH2Test :server:serverPostgresIntegrationTest
```

The environment value is explicit and independent of the Git branch. `dogfood` is persistent;
`e2e` is always disposable and run-unique. The old `test` selector and Gradle task aliases remain
only so pre-existing CI can address the transitional application topology while the server-owned
Matrix/Nextcloud/Identity Ops removal lands. New operator automation must not use them.

Compose itself remains the lifecycle engine. After the safety wrapper has created the protected
SecretRefs, rendered configuration, and ownership-labeled external resources, read-only lifecycle
commands may use the same native Compose model directly, for example:

```bash
cd infra/weave-workspace
docker compose \
  --env-file environments/common.env \
  --env-file /absolute/path/to/reviewed-dogfood.env \
  --file compose.yaml \
  --file compose.dogfood.yaml \
  --project-name weave-dogfood \
  --profile test ps
```

The final `--profile test` is a documented internal compatibility detail, not an environment
choice. Use `compose.sh <environment> up/down` for mutation because that narrow wrapper still owns
SecretRef permissions, provenance labels, resource adoption checks, identity reconciliation, and
exact isolated cleanup. It does not select an environment from a branch.

Local host-server validation when Docker is available:

```bash
./gradlew :server:serverDevHostSmoke
```

GitHub Actions runs deterministic repository checks on pushes and pull requests. Docker-backed full-stack smoke is manual-only and asks the dispatcher to confirm power/storage budget before it starts.

## Operator safety

- `./weave-workspace/compose.sh <environment> down` stops an environment without deleting its named volumes or SecretRefs.
- Destructive cleanup exists only for an exact isolated-E2E project and requires its run-bound ownership evidence. Persistent dogfood/prod volumes are restored or rolled back through reviewed operator procedures, never a generic teardown flag.
- Create an operator-owned backup before destructive maintenance:

```sh
WEAVE_CANDIDATE_COMMIT=<exact-sha> \
WEAVE_BACKUP_ROOT=/var/backups/weave \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
bash weave-workspace/backup.sh dogfood
```

- Validate the private v2 consistency set without applying it:

```sh
WEAVE_RESTORE_PREFLIGHT_ONLY=true \
bash weave-workspace/restore-private-backup.sh /var/backups/weave/<weave-test-timestamp-sha>
```

Direct restore apply remains `Guarded` until the reviewed Compose/control-store restore workflow has destructive rehearsal evidence. `adoption-rehearsal.sh test` is the one-time former-runtime adoption proof; it backs up the running stack and verifies database plus volume restoration in an isolated namespace before any existing persistent resource receives Compose ownership labels.

- Create a redacted diagnostics bundle before sharing logs manually:

```sh
bash weave-workspace/support-bundle.sh
```

Support bundles are not backups. Keep `BackupManifest.json`, database dumps, volume archives, and `private-config-secrets.tgz` private; share only explicitly support-safe receipts and reports.
