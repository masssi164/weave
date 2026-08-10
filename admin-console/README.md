# Weave Organization/Admin Console

Separate React + MUI admin surface for organization owners, admins, and operators.

## Contract

- Deploys as an immutable Vite production bundle inside the Weave Server `bootJar`: `Weave Server + Admin Console` remains the reproducible Control Plane deployment target without a second production process.
- Vite is development-only; `npm run dev` remains the unchanged host-development entrypoint and is not the bootstrap/Admin Console deployment target.
- Talks only to Weave backend admin APIs (`/api/admin/...`).
- Uses browser OIDC Authorization Code + PKCE S256 through the public `weave-admin-console` client. Browser code may use the issuer authorization/token/session endpoints, but never Keycloak Admin REST.
- Shows organization overview, effective policy explanation, provider category readiness, replacement dry-run results, provider detail/readiness actions, deny-by-default whitelist policy, and redacted audit events.
- Renders owner/admin, operator, and member boundaries distinctly: owners/admins configure, operators inspect support-safe readiness, and members see only usable/disabled/degraded/policy-blocked capability states.
- Never calls raw providers directly and never renders raw provider secrets.
- Shows support-safe bootstrap evidence refs and readiness summaries; it must not become a raw CI log viewer or deploy the Flutter/member client.
- New user-visible Admin Console copy is kept in `src/copy.ts` so localization entries stay reviewable even before additional locales are enabled.
- The Weave member client remains provider-agnostic and is not the admin portal.

## Local commands

```bash
cd admin-console
npm ci
npm run ci
npm run generate:openapi
npm run check:openapi
```

OpenAPI consumer types are generated from the server-owned artifact at `contracts/openapi/weave-openapi.json`. Use the root commands `./gradlew generateOpenApiContract generateAdminOpenApiTypes` after server contract changes and `./gradlew checkOpenApiContractFresh checkAdminOpenApiTypesFresh` to fail on stale generated artifacts.

## Dependency update policy

`package.json` uses exact dependency versions that match the reviewed `package-lock.json`; do not use `latest` ranges. To refresh Admin Console dependencies, intentionally edit the versions or run a targeted npm update, then refresh the lockfile with `npm install --package-lock-only` and validate with `npm ci` plus `npm run ci` before opening a PR.

Configure with Vite env vars when needed:

```bash
VITE_WEAVE_API_BASE_URL=https://api.weave.test:44443/api
VITE_WEAVE_OIDC_ISSUER_URL=https://auth.weave.test:44443/realms/weave
VITE_WEAVE_ADMIN_OIDC_CLIENT_ID=weave-admin-console
```

The packaged bundle is served only below `/admin-console/`. It first reads the Server's public `/api/platform/config` to obtain the runtime issuer and uses same-origin `/api` calls; the PKCE callback is `/admin-console/`. The Vite environment values remain explicit host-development fallbacks. Production packaging is performed by `./gradlew :server:bootJar`; that task runs the exact locked npm install/build, rejects inline scripts, secrets, and `/admin/realms/**` markers, and embeds `dist/` under the Server's Admin Console static resource root.
