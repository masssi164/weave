# Keycloak Contract

This is the local identity contract for the Weave self-hosted development stack.

## Realm

- Realm name: `weave`
- Default public issuer URI: `https://auth.weave.test/realms/weave`
- OpenTofu module source: `weave-workspace/02-keycloak-setup/modules/tenant-identity`

The issuer URI follows the infrastructure inputs:

- `tenant_slug`: realm name, default `weave`
- `auth_subdomain`: default `auth`
- `tenant_domain`: default `weave.test`
- `public_scheme`: default `https`
- `proxy_host_port`: default `443`

## Integration Test User

The Keycloak setup stage can create a local integration test user. It is disabled by default and must not be enabled in production.

Enable it with `TF_VAR_create_test_user=true` when running `weave-workspace/install.sh`, or by setting `create_test_user=true` for the `02-keycloak-setup` OpenTofu stage. `TF_VAR_*` remains the OpenTofu/Terraform-compatible variable environment prefix.

- Username: `test`
- Email and login identifier: `test@weave.test`
- First name: `Test`
- Last name: `User`
- Password: `<generated — see bootstrap.env>`
- Email verified: true
- Temporary password: false

For integration tests, use:

```bash
export WEAVE_TEST_USERNAME=test@weave.test
export WEAVE_TEST_PASSWORD='<generated — see bootstrap.env>'
```

`install.sh` also writes non-secret Flutter integration settings when the test user is enabled:

```bash
export WEAVE_API_BASE_URL=https://api.weave.test/api
export WEAVE_BASE_URL=https://api.weave.test/api
export WEAVE_OIDC_ISSUER_URL=https://auth.weave.test/realms/weave
export WEAVE_OIDC_CLIENT_ID=weave-app
```

## Clients

### Weave Mobile App

- Keycloak display name: `weave-app`
- OIDC client ID: `weave-app`
- Access type: public
- OAuth flow: authorization code
- PKCE: required, `S256`
- Sign-in redirect URI: `com.massimotter.weave:/oauthredirect`
- Post-logout redirect URI: `com.massimotter.weave:/logout`
- Default API scope: `weave:workspace`
- Optional long-lived session scope: `offline_access`
- Resource Owner Password Grant: disabled by default, enabled only when `create_test_user=true`

The Flutter app requests `openid profile email offline_access weave:workspace` for mobile sign-in. `weave:workspace` is also assigned as a default app scope so backend-bound access tokens include it when command-line smoke tests request only `openid profile email`. Long-lived mobile sessions are intentional for normal members: the dogfood realm grants the built-in Keycloak `offline_access` role to `owner`, `admin`, `operator`, and `member` product groups, while `guest` stays excluded until a separate guest-session policy exists.

Dogfood/local realm email is captured by Mailpit only:

- SMTP endpoint: `weave-mailpit:1025` on the Docker network.
- Operator inbox: `http://127.0.0.1:8025`.
- No external delivery is configured for dogfood/local mail.

### Weave Backend

- Keycloak client ID: `weave-backend`
- Access type: bearer-only
- Expected token audience: `weave-backend`
- Direct member API token `azp` or `client_id`: `weave-app`
- Backend environment:
  - `WEAVE_OIDC_ISSUER_URI=https://auth.weave.test/realms/weave`
  - `WEAVE_OIDC_JWK_SET_URI=http://weave-keycloak:8080/realms/weave/protocol/openid-connect/certs`
  - `WEAVE_OIDC_REQUIRED_AUDIENCE=weave-backend`
  - `WEAVE_CLIENT_ID=weave-app`
- Public API URL: `https://api.weave.test/api`
- Direct readiness URL: `http://127.0.0.1:8084/api/health/ready`

### Weave MCP Server

- Keycloak client ID: `weave-mcp-server`
- Access type: confidential, with a service account and no service-account roles
- Browser/direct-access grants: disabled
- Full scope: disabled
- Standard token exchange: enabled
- Refresh tokens for client credentials and token exchange: disabled
- Workload access-token lifespan: 60 seconds
- Inbound human/member tokens are forbidden. The MCP edge is currently dark and rejects every caller.
- ARC will provision one confidential service-account client per enabled Weaver cell using the `weaver-cell-{cellId}` convention.
- Future admission binds the authenticated workload client and subject to the server-owned cell, organization, immutable human owner, and RuntimeProfile v2. Generic or unbound service accounts remain forbidden.
- The fixed `weave-mcp-server` client is platform baseline state, not a Weaver cell identity and not a compatibility caller.

While the edge is dark, `TF_VAR_weave_mcp_client_secret` remains operator-owned Keycloak baseline input but is not injected into the MCP container. ARC owns dynamic per-cell secret creation, rotation, revocation, cleanup, and restore reconciliation.

Keycloak's supported Standard Token Exchange V2 is the target for audience-restricted downstream workload tokens; the experimental delegation feature stays disabled. Current Keycloak does not implement RFC 8707 `resource` for this flow, so end-to-end RFC 8707 and MCP Authorization Server Metadata conformance remain **Guarded**, not claimed complete. See the [Keycloak token exchange documentation](https://www.keycloak.org/securing-apps/token-exchange).

### Matrix Authentication Service

- Client ID: `matrix-mas`
- Access type: confidential
- Redirect URI: `https://matrix.weave.test/upstream/callback/01JQ7N9R4QK6W3M5X8Y2ZC1DHF`
- Web origins: `+`

### Nextcloud

- Client ID: `nextcloud`
- Access type: confidential
- Redirect URI: `https://files.weave.test/*`
- Post-logout redirect URI: `https://files.weave.test/*`
- Backchannel logout URL: `https://files.weave.test/index.php/apps/user_oidc/backchannel-logout/keycloak`
- Token claims include `groups` for Nextcloud group provisioning.

## Client Scopes

### `weave:workspace`

- Type: OpenID client scope
- `include_in_token_scope`: true
- Assigned to `weave-app` as a default scope
- Purpose: API access scope for Weave workspace operations

The scope carries an audience mapper:

- Mapper name: `weave-backend-audience`
- Mapper type: OIDC audience protocol mapper
- Included client audience: `weave-backend`
- Added to access token: true
- Added to ID token: false

### `weave:mcp`

- Optional scope of `weave-app`; a runtime must request it explicitly
- Adds `weave-mcp-server` to the access-token audience
- Required by the `/mcp` resource server

### `weave:mcp-backend`

- Optional scope assigned only to the confidential `weave-mcp-server` client
- Requested only during standard token exchange
- Adds `weave-backend` to the exchanged access-token audience and carries the tenant identity claim
- Does not grant general workspace API access

## Token Claims

A mobile access token for `weave-app` must include:

- `iss`: `https://auth.weave.test/realms/weave`
- `azp`: `weave-app`
- `client_id`: `weave-app` when present
- `aud`: includes `weave-backend`
- `scope`: includes `openid`, requested profile scopes, and `weave:workspace`
- refresh token: present for mobile app sign-in when the user belongs to an offline-session-entitled product group and the app requested `offline_access`

The backend accepts the token only when:

- the issuer matches `WEAVE_OIDC_ISSUER_URI`
- the `aud` claim includes `WEAVE_OIDC_REQUIRED_AUDIENCE`
- the authorized party or client ID matches `WEAVE_CLIENT_ID`

## OpenTofu outputs

The Keycloak setup stage exports these OpenTofu outputs:

- `keycloak_realm_name`
- `keycloak_issuer_url`
- `weave_app_client_id`
- `weave_app_redirect_uris`
- `weave_app_post_logout_redirect_uris`
- `weave_app_default_scopes`
- `weave_app_optional_scopes`
- `weave_workspace_scope_name`
- `weave_backend_client_id`
- `weave_backend_audience`
- `weave_mcp_client_id`
- `weave_mcp_audience`
- `weave_mcp_backend_scope_name`
- `nextcloud_client_id`
- `nextcloud_client_secret`
- `test_user_username`
- `test_user_password`

The infrastructure stage exports these OpenTofu outputs:

- `weave_backend_oidc_issuer_uri`
- `weave_backend_oidc_jwk_set_uri`
- `weave_backend_required_audience`
- `weave_backend_client_id`
- `public_urls.api` with the backend available at `/api`
- `weave_api_base_url`
- `service_names.backend`
