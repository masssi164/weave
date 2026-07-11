# Local/Dev Admin User Activation Helper

The single-host operator path needs a support-safe way for an operator to invite and activate a Weave user without editing Keycloak internals by hand or distributing initial passwords. The helper is intentionally local/dev oriented and maps directly to the current backend product-profile contract:

- MVP realm roles: `owner`, `admin`, `member`, `guest`
- default role-mapped group claims: `workspace-owners`, `workspace-admins`, `workspace-members`, `workspace-guests`
- backend verification path: `/api/me` or the app Profile surface

## Prerequisites

Run the local stack install first so `weave-workspace/.generated/bootstrap.env` contains the Keycloak admin URL and credentials:

```bash
cd weave-workspace
./install.sh
```

The helper loads `.generated/bootstrap.env` automatically. If you are running it from a different shell, ensure these values are available:

- `TF_VAR_keycloak_admin_username`
- `TF_VAR_keycloak_admin_password`
- `TF_VAR_public_scheme`
- `TF_VAR_tenant_domain`
- optional `TF_VAR_auth_subdomain`, `TF_VAR_proxy_host_port`, `TF_VAR_caddy_tls_ca_file`, `WEAVE_TLS_CA_FILE`

## Dry-run the activation invite plan

```bash
cd weave-workspace
./activate-user.sh \
  --dry-run \
  --username alice \
  --email alice@example.test \
  --display-name 'Alice Example' \
  --role member \
  --invite-ref activation-alice-home \
  --evidence-file build/dogfood/activation-alice-home.json
```

The dry run prints the realm, username, email, display name, role, role-mapped default group, non-secret invite reference, required action list, and action lifetime. It does not contact Keycloak. When `--evidence-file` is provided, the file stores only support-safe hashes and invite metadata; it does not store the activation URL, password, token, or raw provider payload.

Guests are mapped to `workspace-guests`, not member/admin groups. Override `--workspace-group` only for an intentional local/dev policy test.

## Create an activation invite

```bash
cd weave-workspace
./activate-user.sh \
  --username alice \
  --email alice@example.test \
  --display-name 'Alice Example' \
  --role member \
  --invite-ref activation-alice-home \
  --activation-lifespan 900 \
  --evidence-file build/dogfood/activation-alice-home.json
```

The helper does not create, accept, or print an initial password. Password-based flags are rejected. The QR/deeplink remains a bootstrap handoff only and may carry the non-secret invite reference, organization/workspace context, route mode, and platform-config URL. Account activation happens in the system browser through the identity provider's one-time required-action link.

For dogfood, Keycloak sends the required-action email to Mailpit. Treat the action URL in the Mailpit message as a secret, one-time, expiring activation artifact. Do not paste it into this repository, the field manual, QR codes, logs, screenshots, support bundles, app preferences, or issue/PR comments.

Successful live creation prints `WEAVE_ACTIVATION_INVITE_CREATED` with only the invite reference, required action names, TTL, and `supportSafe=true`. Dry runs print `WEAVE_ACTIVATION_INVITE_DRY_RUN`.

## What the helper changes

The helper uses the Keycloak admin API to:

1. ensure the selected MVP realm role exists;
2. ensure the workspace group exists;
3. create or update the user;
4. mark the account with required first-login actions such as `VERIFY_EMAIL` and `UPDATE_PASSWORD`;
5. assign the role and group;
6. send a short-lived Keycloak required-action email for the system-browser activation path.

It does not create separate Matrix or Nextcloud accounts. Those modules remain behind Weave/Keycloak SSO and the existing provisioning contracts.

## Verify activation

After sign-in, verify the user through the app profile/status screen or backend facade:

```bash
curl -sS "$WEAVE_API_BASE_URL/me" \
  -H "Authorization: Bearer <user access token>" | jq .
```

Expected evidence:

- `roles` includes the selected MVP role;
- `groups` includes the role-mapped default group (`workspace-guests` for guest, `workspace-members` for member, etc.) unless a different `--workspace-group` was used;
- profile display name/email match the activated user.
- `build/dogfood/activation-*.json`, when written, has `qrOrDeeplinkCarriesSecret=false`, `appStoresActivationSecret=false`, `supportSafe=true`, hashed direct identity fields, and the required-action activation metadata.

## Release boundary

This is an operator helper, not the final product admin UI/API. The single-host operator path may use it for local/dev owner/admin activation evidence. A later product admin flow should replace this script for non-technical workspace administrators.
