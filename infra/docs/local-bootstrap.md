# Local bootstrap and app contract

This guide contains the local/dev infrastructure path that used to make the top-level README intimidating: ports, TLS trust, generated env files, integration test inputs, and the native app/backend contract. It is not the canonical product bootstrap entrypoint; that boundary lives in `../../docs/bootstrap-foundation-contract.md` and deploys the Control Plane first, with infra selected only by an explicit environment. The canonical CLI bridge is `tools/weavectl bootstrap plan/apply`; when a local shell plan is explicitly executed with an approval ref, it dispatches this `install.sh` path instead of asking the operator to discover it manually.

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

## Environment and port modes

Environment selection is explicit: `dev`, `dogfood`, `prod`, or `e2e`. A branch name never selects
or changes the environment. The checked-in/default port blocks are:

- `dev`: `58080/59000` Keycloak. Normal `compose.sh dev up` starts PostgreSQL and Keycloak only;
  Server, MCP, and Admin Console run on the host. Other checked-in dev ports are reserved for
  transitional/provider-specific diagnostics and are not started by the normal lifecycle;
- `dogfood`: `44080/44443`, `48080/49000`, `48025`, and
  `48082/48008/48083/48084/48085`;
- `prod`: public `80/443`; service management ports remain loopback-bound and operator-reviewed.
- `e2e`: dynamically assigned host ports and a namespace derived from the explicit run ID.

The host Spring process in `dev` listens on `127.0.0.1:8080`; the Compose backend service is not
started in that environment. E2E currently reuses the transitional dogfood application topology,
but never its project, volumes, network, generated files, SecretRefs, or ports.

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

Caddy is declared once in `compose.yaml`, but is not part of normal dev startup. The generated dev
TLS material remains available for an explicitly selected gateway/provider diagnostic; normal
host Server and Admin Console development uses their host-local entry points.

## Generated local env files

Rendering writes deterministic service configuration below `.generated/dev` and a public host-server
coordinate file at `.generated/dev/backend/host.env`. Credentials are separate mode-0600 files
below `.generated/dev/secrets`; TLS lives below `.generated/dev/tls`. The host coordinate file
selects `SPRING_PROFILES_ACTIVE=dev`, imports only generated provider coordinates, and deliberately
contains no `SPRING_DATASOURCE_URL`, so `application-dev.yml` remains the H2 authority.

Never attach the generated, secret, or TLS roots to support issues. `support-bundle.sh dev` has a
strict allowlist and excludes them.

## Disposable Fresh product E2E

`./gradlew testApp` creates one private, run-scoped Compose `e2e` context. Owner
and member identities are created only through Weave invitations, Mailpit
activation links, Keycloak required actions, and Authorization Code with PKCE.
The proof then exercises WebDAV, governed Agent Runtime Control,
`private_key_jwt`, OAuth token exchange, and MCP `files.search`.

Passwords, activation links, bearer tokens, and private keys stay in the
bounded test process or their mounted SecretRefs. The only retained artifacts
are allowlisted support-safe product-flow and teardown evidence. The cleanup
accepts only the deterministic `weave-e2e-<sha256(run-id)[:16]>` namespace,
verifies ownership labels, removes its external volumes and network, and
removes its exact generated SecretRef tree.

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
- dogfood/e2e/prod container readiness endpoint: the loopback-bound `WEAVE_BACKEND_HOST_PORT`

See [../KEYCLOAK_CONTRACT.md](../KEYCLOAK_CONTRACT.md) for the full realm, client, scope, claim, and audience contract.
