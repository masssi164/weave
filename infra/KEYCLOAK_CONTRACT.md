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

`weave-app` is never assigned either Agent Runtime Control machine scope. A human token cannot request or present an MCP access token.

Dogfood/local realm email is captured by Mailpit only:

- SMTP endpoint: `weave-mailpit:1025` on the Docker network.
- Operator inbox: `http://127.0.0.1:8025`.
- No external delivery is configured for dogfood/local mail.

### Weave Backend

- Keycloak display name: `weave-backend`
- Resource client ID and required audience: `${api_public_url}/api`
- Access type: bearer-only
- Direct member API token `azp` or `client_id`: `weave-app`
- Backend environment:
  - `WEAVE_OIDC_ISSUER_URI=https://auth.weave.test/realms/weave`
  - `WEAVE_OIDC_JWK_SET_URI=http://weave-keycloak:8080/realms/weave/protocol/openid-connect/certs`
  - `WEAVE_OIDC_REQUIRED_AUDIENCE=https://api.weave.test/api`
  - `WEAVE_CLIENT_ID=weave-app`
- Public API URL: `https://api.weave.test/api`
- Direct readiness URL: `http://127.0.0.1:${TF_VAR_backend_host_port}/api/health/ready`

### Weave MCP Server

- Keycloak client ID: `weave-mcp-server`
- Access type: confidential, with a service account and no realm-management or product roles
- Browser/direct-access grants: disabled
- Full scope: disabled
- Standard token exchange: enabled
- Refresh tokens for client credentials and token exchange: disabled
- Workload access-token lifespan: 60 seconds
- Inbound human/member tokens are forbidden. The MCP edge admits only a currently bound cell workload after the Agent Runtime Control checks described below.
- ARC provisions one confidential service-account client per enabled Weaver cell using the `weaver-cell-{cellId}` convention and `private_key_jwt` in the self-hosted adapter.
- Admission requires `client_id == azp`, `sub` equal to the immutable service-account subject recorded by ARC, the sole realm role `weaver-runtime`, no client roles, the exact MCP/resource-plus-requester audience set, `mcp:tools`, only allowed domain scopes, and a current server-owned cell/profile/entitlement binding. Generic or unbound service accounts remain forbidden.
- The fixed `weave-mcp-server` client is platform baseline state, not a Weaver cell identity and not a compatibility caller.

`TF_VAR_weave_mcp_client_secret` is an operator-owned credential for the MCP edge's server-side exchange only. It is never issued to a cell and is mounted from a permission-restricted file rather than exposed to human clients. Both its client ID and secret are form-encoded before HTTP Basic construction as required by RFC 6749 section 2.3.1. ARC owns dynamic per-cell key creation, rotation, revocation, cleanup, and restore reconciliation.

Keycloak Standard Token Exchange V2 is active for audience-restricted downstream workload tokens; the experimental delegation feature stays disabled. The exchanged token preserves the cell service-account `sub`, sets `azp=weave-mcp-server`, has the exact backend audience and reduced domain scopes, and has no refresh or ID token. Current Keycloak does not implement RFC 8707 `resource` for this exchange, so end-to-end RFC 8707 authorization-server conformance remains **Guarded**. The MCP edge does publish RFC 9728-style protected-resource metadata and its discoverable bearer challenge. See the [Keycloak token exchange documentation](https://www.keycloak.org/securing-apps/token-exchange).

### Agent Runtime administration

- `weave-agent-runtime-admin` is a confidential service account used only by ARC's Keycloak workload-client adapter. Its realm-management roles are the minimum set needed to create/read/update/delete owned `weaver-cell-*` clients, service-account users, credentials, and the `weaver-runtime` mapping. The adapter rejects targets outside that namespace.
- `weave-identity-admin` remains a separate confidential service account for organization/member lifecycle and authoritative user/group reads. ARC entitlement reads use a separately qualified provider backed by this credential; workload client lifecycle never receives it.
- Keycloak generates both administrative client secrets. After the Keycloak stage, `install.sh` atomically reconciles the sensitive outputs into their mounted SecretRefs and reapplies the runtime stage before backend readiness; the clients authenticate with HTTP Basic and never use a form-post fallback.
- Both credentials are mounted through separate SecretRefs. Neither is available to a cell, MCP edge, member client, or product-domain service.

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

### `agent-runtime.profile.read`

- Machine-only optional scope assigned to managed `weaver-cell-*` clients by ARC
- Adds the exact `https://api.weave.test/api/v1/agent-runtime` resource audience
- Requested alone for workload-only retrieval of the cell's current signed RuntimeProfile
- Never assigned to `weave-app` or the fixed MCP edge client

### `mcp:tools`

- Machine-only optional scope assigned to managed `weaver-cell-*` clients by ARC
- Adds the exact `https://api.weave.test/mcp` resource audience
- Requested alone when the cell opens the MCP transport
- Never assigned to `weave-app`; it is insufficient without the exact workload identity, current cell binding, profile, entitlement, and domain authorization

### `weaver-runtime.workload`

- Fixed non-requestable scope attached by ARC to every managed cell client
- Carries only the `weaver-runtime` realm-role mapping
- Is not emitted as user-requestable product authority

### `calendar.read`

- Machine-only optional domain scope in the current proof slice
- Assigned to a cell only when its current RuntimeProfile permits Calendar reads
- Downscoped through the MCP edge; never grants direct member/admin API access

### `weave-mcp-backend.exchange`

- Internal non-requestable default scope attached only to `weave-mcp-server`
- Supplies the backend audience that Keycloak Standard Token Exchange V2 can filter with its `audience` request parameter
- `include_in_token_scope` is false, so this internal name does not appear in the exchanged token's `scope` claim

### `weaver-runtime` realm role

- Assigned only to the service account of an ARC-managed cell client
- Must be that account's sole effective realm workload role
- Is not a member/product role and never establishes a human identity or domain permission

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

A direct cell MCP access token must instead include an RFC 9068 `typ=at+jwt` header, the exact
MCP resource and requester audiences, `client_id == azp == weaver-cell-{cellId}`, the immutable
service-account `sub`, the sole `weaver-runtime` realm role, no client roles, `mcp:tools`, and
only the domain scopes granted to that cell. The exchanged backend token preserves `sub`, uses
`azp=weave-mcp-server`, carries exactly the backend audience and reduced domain scopes, and is
accepted only on the private MCP context security chain.

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
- `weave_agent_runtime_admin_client_id`
- `agent_runtime_admin_scope_name`
- `weaver_runtime_workload_scope_name`
- `agent_runtime_resource`
- `agent_runtime_profile_read_scope_name`
- `mcp_tools_scope_name`
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
