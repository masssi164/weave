# Weave Organization/Admin Console

Separate React + MUI admin surface for organization owners, admins, and operators.

## Contract

- Talks only to Weave backend admin APIs (`/api/admin/...`).
- Uses OIDC/Keycloak as the default self-hosted identity broker contract through `weave-admin-console`.
- Shows organization overview, provider category readiness, provider detail/readiness actions, deny-by-default whitelist policy, and redacted audit events.
- Never calls raw providers directly and never renders raw provider secrets.
- The Weave member client remains provider-agnostic and is not the admin portal.

## Local commands

```bash
cd admin-console
npm install
npm run ci
```

Configure with Vite env vars when needed:

```bash
VITE_WEAVE_API_BASE_URL=https://api.weave.local:44443/api
VITE_WEAVE_OIDC_ISSUER_URL=https://auth.weave.local:44443/realms/weave
VITE_WEAVE_ADMIN_OIDC_CLIENT_ID=weave-admin-console
```
