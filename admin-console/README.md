# Weave Organization/Admin Console

Separate React + MUI admin surface for organization owners, admins, and operators.

## Contract

- Deploys with Weave Server as the Control Plane: `Weave Server + Admin Console` is the reproducible bootstrap deployment target.
- Vite is development-only; `npm exec vite` is not the bootstrap/Admin Console deployment target.
- Talks only to Weave backend admin APIs (`/api/admin/...`).
- Uses OIDC/Keycloak as the default self-hosted identity broker contract through `weave-admin-console`.
- Shows organization overview, effective policy explanation, provider category readiness, replacement dry-run results, provider detail/readiness actions, deny-by-default whitelist policy, and redacted audit events.
- Renders owner/admin, operator, and member boundaries distinctly: owners/admins configure, operators inspect support-safe readiness, and members see only usable/disabled/degraded/policy-blocked capability states.
- Never calls raw providers directly and never renders raw provider secrets.
- Shows support-safe bootstrap evidence refs and readiness summaries; it must not become a raw CI log viewer or deploy the Flutter/member client.
- New user-visible Admin Console copy is kept in `src/copy.ts` so localization entries stay reviewable even before additional locales are enabled.
- The Weave member client remains provider-agnostic and is not the admin portal.

## Local commands

```bash
cd admin-console
npm install
npm run ci
```

## Dependency update policy

`package.json` uses exact dependency versions that match the reviewed `package-lock.json`; do not use `latest` ranges. To refresh Admin Console dependencies, intentionally edit the versions or run a targeted npm update, then refresh the lockfile with `npm install --package-lock-only` and validate with `npm ci` plus `npm run ci` before opening a PR.

Configure with Vite env vars when needed:

```bash
VITE_WEAVE_API_BASE_URL=https://api.weave.local:44443/api
VITE_WEAVE_OIDC_ISSUER_URL=https://auth.weave.local:44443/realms/weave
VITE_WEAVE_ADMIN_OIDC_CLIENT_ID=weave-admin-console
```
