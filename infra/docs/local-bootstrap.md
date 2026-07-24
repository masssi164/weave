# Local bootstrap and app contract

This guide contains the local/dev provider-stack implementation path that used to make the top-level README intimidating: ports, TLS trust, generated env files, integration test inputs, and the native app/backend contract. It is not the canonical product bootstrap entrypoint; that boundary lives in `../../docs/bootstrap-foundation-contract.md` and deploys the Control Plane first, with infra selected only by profile. The canonical CLI bridge is `tools/weavectl bootstrap plan/apply`; when a local shell plan is explicitly executed with an approval ref, it dispatches this `install.sh` path instead of asking the operator to discover it manually.

## Hostnames

Default local `/etc/hosts` line:

```text
127.0.0.1 weave.test api.weave.test auth.weave.test files.weave.test matrix.weave.test
```

Run this from the repository root to print the current default line:

```bash
make dev-hosts
```

MAS is served behind the matrix hostname; no separate `mas.<tenant_domain>` entry is needed.

## Port modes

The three closed profile environments define their own non-overlapping port blocks:

- `dev`: `54080/54443` gateway, `58080/59000` Keycloak, `58025` Mailpit, and
  `58082/58008/58083/58084/58085` for MAS/Synapse/Nextcloud/backend/MCP;
- `test`: `44080/44443`, `48080/49000`, `48025`, and
  `48082/48008/48083/48084/48085`;
- `prod`: public `80/443`; service management ports remain loopback-bound and operator-reviewed.

The host Spring process in `dev` listens on `127.0.0.1:8080`; the Compose backend service is not
started in that profile. Isolated E2E uses the test topology with ten caller-reserved unique
ports and a namespace deterministically derived from the raw run ID.

For a non-destructive rerun:

```bash
./gradlew composeDevDown
./gradlew composeDevDependenciesReady
```

`down` preserves named volumes and SecretRefs. Destructive cleanup is available only to an exact
isolated-E2E namespace carrying matching ownership labels and candidate/run evidence; there is no
generic persistent reset command.

## TLS setup

The public local contract is HTTPS on these hostnames:

- `https://weave.test` as the Weave product gateway
- `https://weave.test/files` and `https://weave.test/calendar` as Weave product routes
- `https://api.weave.test/api` as the canonical backend API
- `https://auth.weave.test`
- `https://matrix.weave.test`
- `https://files.weave.test` as raw Nextcloud technical/admin/protocol fallback

Generated-CA flow:

1. Add the host entries shown above to `/etc/hosts`.
2. Run `./gradlew composeDevDependenciesReady`.
3. Trust `infra/weave-workspace/.generated/dev/tls/ca.pem` in the host operating system or browser trust store.
4. Reopen the browser after trusting the CA.

mkcert flow:

```bash
cd infra/weave-workspace
mkdir -p .generated/dev/tls
mkcert -install
mkcert \
  -cert-file .generated/dev/tls/cert.pem \
  -key-file .generated/dev/tls/key.pem \
  weave.test api.weave.test auth.weave.test files.weave.test matrix.weave.test
cp "$(mkcert -CAROOT)/rootCA.pem" .generated/dev/tls/ca.pem
cd ../..
./gradlew composeDevDependenciesReady
```

Caddy is declared once in `compose.yaml`; the dev overlay mounts the generated config and TLS roots
for the `weave-dev` project.

## Generated local env files

Rendering writes deterministic service configuration below `.generated/dev` and a public host-server
coordinate file at `.generated/dev/backend/host.env`. Credentials are separate mode-0600 files
below `.generated/dev/secrets`; TLS lives below `.generated/dev/tls`. The host coordinate file
selects `SPRING_PROFILES_ACTIVE=dev`, imports only generated provider coordinates, and deliberately
contains no `SPRING_DATASOURCE_URL`, so `application-dev.yml` remains the H2 authority.

Never attach the generated, secret, or TLS roots to support issues. `support-bundle.sh dev` has a
strict allowlist and excludes them.

## Disposable three-identity E2E

`isolated-e2e-identities.sh prepare --run-id <unique-run>` creates a private credential env, a
startup env, and a support-safe hashed manifest. The Compose boundary independently derives the
same namespace as `weave-e2e-<sha256(run-id)[:16]>`, requires all ten unique bounded host ports,
and rejects persistent dogfood membership inputs or a caller-selected namespace.

After stack readiness, run `provision` with `WEAVE_E2E_STACK_SCOPE=isolated`; it creates only marker-owned Keycloak users/groups and verifies the isolated backend actually loaded all three ReBAC facts. Run `cleanup` after the two collaboration passes. Keycloak cleanup is idempotent, and provider/context data is removed by destruction of that isolated stack namespace. The helper refuses persistent dogfood scope.

Before the collaboration passes, run `isolated-e2e-authorization-probes.sh --run-id <same-run>` with the integration variables printed by `prepare`. The helper proves a missing Calendar capability returns `403`, a genuinely expired Keycloak token returns `401` through Chat, Files, and Calendar, and Matrix logout immediately revokes the presented Chat token. It is isolated-only, verifies exact user and backend namespace markers, restores the Calendar group plus temporary realm/client settings with an exit trap, and writes only subject hashes, booleans, and status codes to `WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH`.

The domain-local failure-containment fixture is also isolated-only. Run `isolated-e2e-calendar-outage.sh begin` before checking that Calendar alone becomes unavailable, then always run `isolated-e2e-calendar-outage.sh restore`. `begin` deletes only the backend actor's disposable `weave-workspace` calendar and waits for cached Calendar status `0` while cached Files remains `2`; `restore` recreates that exact calendar and waits for cached status `2`. A failed `begin` trap recreates the calendar automatically. The documented recovery command is safe to repeat:

```bash
WEAVE_E2E_STACK_SCOPE=isolated \
  bash infra/weave-workspace/isolated-e2e-calendar-outage.sh restore
```

## Integration tests

Use the dedicated Gradle lanes:

```bash
./gradlew serverDevH2Test
./gradlew serverDevHostSmoke
./gradlew serverPostgresIntegrationTest
```

`serverDevH2Test` proves Flyway and Hibernate mappings on fresh H2 PostgreSQL mode.
`serverDevHostSmoke` starts the host server against live Compose provider dependencies and checks
readiness. `serverPostgresIntegrationTest` uses disposable PostgreSQL/Testcontainers for migration,
repository, transaction, and concurrency claims. The production boot JAR gate separately proves
that the H2 driver is absent.

Full identity/authz E2E never enables a shared static user or password grant. The isolated workflow
creates run-owned author/collaborator/outsider identities, uses Authorization Code + PKCE or its
explicit test automation boundary, and destroys only that namespace.

## Native app contract

The default Keycloak client contract for the Weave mobile app is:

- Keycloak display name: `weave-app`
- OIDC client ID: `weave-app`
- sign-in redirect URI: `com.massimotter.weave:/oauthredirect`
- post-logout redirect URI: `com.massimotter.weave:/logout`
- default API scope: `weave:workspace`
- Resource Owner Password Grant: disabled with no environment override

The backend resource server contract is:

- issuer URI: `https://auth.weave.test/realms/weave`
- JWKS URI: `http://weave-keycloak:8080/realms/weave/protocol/openid-connect/certs`
- required audience: `weave-app`
- expected client ID / authorized party: `weave-app`
- public readiness endpoint: `https://api.weave.test/api/health/ready`
- host-dev direct readiness endpoint: `http://127.0.0.1:8080/api/health/ready`
- test/prod container readiness endpoint: the loopback-bound `WEAVE_BACKEND_HOST_PORT`

See [../KEYCLOAK_CONTRACT.md](../KEYCLOAK_CONTRACT.md) for the full realm, client, scope, claim, and audience contract.
