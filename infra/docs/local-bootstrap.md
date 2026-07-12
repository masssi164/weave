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

There are two supported local port modes:

- canonical single-stack ports: `80`, `443`, `8080`, `8082`, `8008`, `8083`, `8084`
- shared-host isolation block: `44080`, `44443`, `48080`, `48082`, `48008`, `48083`, `48084`

Use canonical ports only when Weave owns the machine's standard local ports. On a shared Docker host or self-hosted runner, use the isolated block. `install.sh` defaults to the isolated block, and `.env.example` shows both modes explicitly.

If you need a clean non-destructive rerun on a shared host:

```bash
cd weave-workspace
WEAVE_RUNNER_HYGIENE=true ./install.sh
# or
bash ./teardown.sh
```

A destructive reset requires explicit opt-in and the tenant/workspace slug. For the default local tenant, read [operator-runbook.md#5-backup-expectations](operator-runbook.md#5-backup-expectations) first, then run only if data loss is intended:

```bash
cd weave-workspace
WEAVE_REMOVE_VOLUMES=true \
WEAVE_CONFIRM_DESTRUCTIVE_RESET=weave \
bash ./teardown.sh
```

Before deleting volumes, the helper lists the affected data domains: Keycloak identity/session data, backend/Postgres data, Matrix/Synapse database and media, Nextcloud database/files/calendar data, Caddy/TLS state, and exact Docker volumes. Generated `.generated/` secrets/config are not removed by the helper; back them up or delete them manually only when intended.

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
2. Run `cd weave-workspace && ./install.sh`.
3. Trust `weave-workspace/01-infrastructure/.generated/caddy/certs/weave-local-ca.pem` in the host operating system or browser trust store.
4. Reopen the browser after trusting the CA.

mkcert flow:

```bash
cd weave-workspace
mkdir -p 01-infrastructure/.generated/caddy/certs
mkcert -install
mkcert \
  -cert-file 01-infrastructure/.generated/caddy/certs/weave.test.pem \
  -key-file 01-infrastructure/.generated/caddy/certs/weave.test-key.pem \
  weave.test api.weave.test auth.weave.test files.weave.test matrix.weave.test
cp "$(mkcert -CAROOT)/rootCA.pem" 01-infrastructure/.generated/caddy/certs/weave-local-ca.pem
./install.sh
```

Caddy is managed by the OpenTofu infrastructure stage. `weave-workspace/docker-compose.yml` mirrors the same Caddy service and mounts the generated Caddyfile plus cert directory for proxy-only iteration against an existing `weave_network`.

## Generated local env files

`install.sh` writes two generated env files:

- `weave-workspace/.generated/bootstrap.env`: private local bootstrap values and secrets. Use only for local backend/server-side runs that need those secrets.
- `weave-workspace/.generated/app-config.env`: no-secrets app/runtime summary. It includes product gateway, backend API, auth issuer, the API-origin Matrix facade, Weave product files/calendar routes, and clearly labeled technical provider URLs for operator/admin use only.

Local dogfood also copies `bootstrap.env` to `${XDG_STATE_HOME:-$HOME/.local/state}/weave/dogfood/bootstrap.env` with mode `0600`. This copy is credential continuity for a fresh checkout, not a support artifact. Set `WEAVE_LOCAL_CREDENTIAL_STATE_FILE=none` for disposable E2E and for non-local deployments whose private release env is the credential authority.

Do not attach `bootstrap.env` to support issues or logs.

## Disposable three-identity E2E

`isolated-e2e-identities.sh prepare --run-id <unique-run>` creates a private credential env, a startup env, and a support-safe hashed manifest. Source the startup env before installing a fully isolated stack. It enables the documented live-E2E ReBAC seed path with `preferred_username`: author and collaborator share `workspace-default`, while outsider belongs only to a run-scoped outside context. The OpenTofu guard rejects these inputs unless the namespace and Docker network are run-scoped, the static `test` user is disabled, and persistent dogfood membership inputs are empty.

After stack readiness, run `provision` with `WEAVE_E2E_STACK_SCOPE=isolated`; it creates only marker-owned Keycloak users/groups and verifies the isolated backend actually loaded all three ReBAC facts. Run `cleanup` after the two collaboration passes. Keycloak cleanup is idempotent, and provider/context data is removed by destruction of that isolated stack namespace. The helper refuses persistent dogfood scope.

Before the collaboration passes, run `isolated-e2e-authorization-probes.sh --run-id <same-run>` with the integration variables printed by `prepare`. The helper proves a missing Calendar capability returns `403`, a genuinely expired Keycloak token returns `401` through Chat, Files, and Calendar, and Matrix logout immediately revokes the presented Chat token. It is isolated-only, verifies exact user and backend namespace markers, restores the Calendar group plus temporary realm/client settings with an exit trap, and writes only subject hashes, booleans, and status codes to `WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH`.

The domain-local failure-containment fixture is also isolated-only. Run `isolated-e2e-calendar-outage.sh begin` before checking that Calendar alone becomes unavailable, then always run `isolated-e2e-calendar-outage.sh restore`. `begin` deletes only the backend actor's disposable `personal` calendar and waits for cached Calendar status `0` while cached Files remains `2`; `restore` recreates that exact calendar and waits for cached status `2`. A failed `begin` trap recreates the calendar automatically. The documented recovery command is safe to repeat:

```bash
WEAVE_E2E_STACK_SCOPE=isolated \
  bash infra/weave-workspace/isolated-e2e-calendar-outage.sh restore
```

## Integration tests

Integration tests should call the backend through the Caddy proxy URL, not the direct backend container port. For the default local stack:

```bash
export WEAVE_API_BASE_URL=https://api.weave.test/api
export WEAVE_BASE_URL=https://api.weave.test/api
export WEAVE_OIDC_ISSUER_URL=https://auth.weave.test/realms/weave
export WEAVE_OIDC_CLIENT_ID=weave-app
export WEAVE_TEST_USERNAME=test@weave.test
export WEAVE_TEST_PASSWORD='<generated — see install.sh output or bootstrap.env>'
```

`WEAVE_API_BASE_URL` (mirrored as legacy-compatible `WEAVE_BASE_URL`) must match the canonical Caddy API route under `api.<tenant_domain>/api`. `WEAVE_OIDC_ISSUER_URL` must match the public Keycloak issuer used in access tokens. When `TF_VAR_create_test_user=true`, `install.sh` also writes these `WEAVE_*` values to `weave-workspace/.generated/bootstrap.env`.

The test user is disabled by default. Enable it only for local integration testing and smoke validation:

```bash
cd weave-workspace
TF_VAR_create_test_user=true ./install.sh
./smoke-test.sh
```

Or from the repository root:

```bash
TF_VAR_create_test_user=true bash weave-workspace/install.sh
make smoke
```

## Native app contract

The default Keycloak client contract for the Weave mobile app is:

- Keycloak display name: `weave-app`
- OIDC client ID: `weave-app`
- sign-in redirect URI: `com.massimotter.weave:/oauthredirect`
- post-logout redirect URI: `com.massimotter.weave:/logout`
- default API scope: `weave:workspace`
- Resource Owner Password Grant: disabled by default, enabled only when `TF_VAR_create_test_user=true`

The backend resource server contract is:

- issuer URI: `https://auth.weave.test/realms/weave`
- JWKS URI: `http://weave-keycloak:8080/realms/weave/protocol/openid-connect/certs`
- required audience: `weave-app`
- expected client ID / authorized party: `weave-app`
- public readiness endpoint: `https://api.weave.test/api/health/ready`
- direct readiness endpoint: `http://127.0.0.1:8084/api/health/ready`

See [../KEYCLOAK_CONTRACT.md](../KEYCLOAK_CONTRACT.md) for the full realm, client, scope, claim, and audience contract.
