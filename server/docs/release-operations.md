# Backend API operations guide

This backend is intentionally small, but operators still need a clear runtime contract.

## Runtime inputs

Required:

- `WEAVE_OIDC_ISSUER_URI`: public issuer URI for the Keycloak realm used by Weave; canonical local/dev value is `https://auth.weave.local/realms/weave`

Optional:

- `WEAVE_OIDC_JWK_SET_URI`: internal JWKS URL when the backend cannot use the issuer metadata endpoint directly
- `WEAVE_OIDC_REQUIRED_AUDIENCE`: required audience in Weave access tokens, defaults to `weave-app`
- `WEAVE_CLIENT_ID`: required first-party client identifier in `azp` and/or `client_id`, defaults to `weave-app`
- `WEAVE_PUBLIC_BASE_URL`: public product entrypoint, defaults to `https://weave.local`
- `WEAVE_API_BASE_URL`: public backend API base URL, defaults to `https://api.weave.local/api`
- `WEAVE_AUTH_BASE_URL`: public Keycloak base URL, defaults to `https://auth.weave.local`
- `WEAVE_MATRIX_HOMESERVER_URL`: public Matrix homeserver URL, defaults to `https://matrix.weave.local`
- `WEAVE_FILES_PRODUCT_URL`: public files product surface, defaults to `https://weave.local/files`
- `WEAVE_CALENDAR_PRODUCT_URL`: public calendar product surface, defaults to `https://weave.local/calendar`
- `WEAVE_NEXTCLOUD_BASE_URL`: canonical Nextcloud URL, defaults to `https://files.weave.local`
- `WEAVE_ONBOARDING_MATRIX_PROVISIONING_STATE`: optional first-run Matrix provisioning override (`not_configured`, `pending`, `ready`, `degraded`, `failed`); blank derives status from the chat capability
- `WEAVE_ONBOARDING_NEXTCLOUD_PROVISIONING_STATE`: optional first-run Nextcloud provisioning override (`not_configured`, `pending`, `ready`, `degraded`, `failed`); blank derives status from files/calendar capability and Nextcloud route configuration
- `WEAVE_PROFILE_STORAGE_PATH`: durable JSON file path for mutable profile overrides accepted by `PATCH /api/profile`, defaults to `./data/profile-overrides.json`
- `PORT`: HTTP listen port, defaults to `8080`

For the complete environment-variable reference, including Files/Calendar adapter credentials and capability toggles, see [runtime-configuration.md](runtime-configuration.md).

## Local/dev public contract

The backend's canonical local/dev API base is `https://api.weave.local/api`. The product shell remains `https://weave.local`.

The canonical local/dev Keycloak issuer is `https://auth.weave.local/realms/weave`. Keep any private JWKS route in `WEAVE_OIDC_JWK_SET_URI`; do not replace token issuer validation with an internal service URL.

## Protected API behavior

Protected `/api/**` routes require a bearer token that satisfies the first-party Weave contract:

- `iss` matches `WEAVE_OIDC_ISSUER_URI`
- `aud` includes `WEAVE_OIDC_REQUIRED_AUDIENCE`
- `azp` and/or `client_id` matches `WEAVE_CLIENT_ID`
- `scope` includes `weave:workspace`

`/api/health/live`, `/api/health/ready`, `/api/platform/config`, and `/api/platform/status` are public diagnostics/bootstrap endpoints.

Protected endpoints return a stable JSON error envelope on auth failures and include the same request id in `X-Request-Id`:

```json
{
  "code": "unauthorized",
  "message": "Bearer authentication is required and must satisfy the first-party Weave token contract.",
  "details": {
    "status": 401,
    "path": "/api/workspace/capabilities",
    "error": "Unauthorized"
  },
  "requestId": "01HV..."
}
```

Use `401` for missing or invalid tokens. Use `403` when the token is authenticated but does not include `weave:workspace`.

`GET /api/profile`, `PATCH /api/profile`, and `GET /api/profile/sync-status` are the authenticated product profile facade. `PATCH /api/profile` supports partial updates to `displayName`, `avatar`, `locale`, `timezone`, `accessibilityPreferences`, and `profileVisibility`; validation errors use the same stable JSON error envelope. Mutable profile overrides are persisted in the configured `WEAVE_PROFILE_STORAGE_PATH` file and survive service/repository recreation when that path is on durable storage. Matrix/Nextcloud profile sync still reports `not_configured` until module propagation is implemented.

`/api/onboarding/status` is the authenticated first-run user snapshot. It returns identity, role/group routing data, invite status, profile completeness, and frontend-safe provisioning states for identity, profile, Matrix, and Nextcloud. Matrix/Nextcloud states are `not_configured`, `pending`, `ready`, `degraded`, or `failed`; response messages must remain support-safe and must not expose downstream stack traces, secrets, tokens, or raw infrastructure errors.

`/api/workspace/release-readiness` is the backend-owned operator snapshot for the core workspace. It rolls auth, Matrix chat, and Nextcloud files into one response and lists the exact remaining setup actions when the workspace is still degraded or blocked.

The older `/api/v1/workspace/capabilities` and `/api/v1/workspace/release-readiness` paths remain compatibility aliases while clients migrate to the canonical non-versioned workspace routes.

## Minimum operator checks

- `GET /api/health/ready` should return `200 OK`
- `GET /v3/api-docs` should return the published OpenAPI document
- `GET /api/platform/config` should return public product URLs, `matrixHomeserverUrl`, canonical `nextcloudBaseUrl`, and module flags
- `GET /api/platform/status` should return module status for smoke and diagnostics
- `GET /api/me` with a valid first-party token should return caller claims
- `GET /api/profile` and `PATCH /api/profile` with a valid first-party token should return the product profile facade and updated mutable profile fields
- `GET /api/profile/sync-status` with a valid first-party token should return frontend-safe Matrix/Nextcloud profile sync state
- `GET /api/onboarding/status` with a valid first-party token should return first-run invite, role/group, profile, Matrix, and Nextcloud provisioning status
- `GET /api/workspace/capabilities` with a valid first-party token should return the client-facing capability snapshot
- `GET /api/workspace/release-readiness` with a valid first-party token should return operator-facing workspace setup status and remaining actions

## Logging and audit baseline

For the core operator path, keep at least:

- reverse proxy access logs with request path, status, latency, and request id
- application logs from stdout/stderr collected by the deployment target
- retained auth failure logs long enough to diagnose issuer, audience, or scope drift

Do not log raw bearer tokens.

## Deployment expectations

- Run behind TLS termination
- Keep the backend on an internal/private port when a reverse proxy is present
- Keep issuer and JWKS URLs aligned with the public auth contract exposed to the app
- Treat issuer, JWKS, and client/audience values as release contract, not per-developer convenience settings
