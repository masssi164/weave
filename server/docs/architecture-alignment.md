# Weave Cross-Repo Architecture Alignment

## Recommended responsibility split

### `weave`

Own the native client experience and end-user sessions:

- public OIDC sign-in against Keycloak
- Matrix Native OAuth 2.0 login against the homeserver/MAS stack
- Nextcloud Login Flow v2 and app-password fallback
- user-facing feature flows and offline/mobile concerns

### `weave-backend`

Own server-side product APIs and orchestration:

- validate JWTs from Weave clients
- expose product-specific REST APIs
- run server-owned workflows and background jobs
- handle provisioning, automation, and cross-system tasks that should not live in Flutter

### `weave-infra`

Own the runnable environment and integration contract:

- hostnames, TLS, ingress, and service discovery
- Keycloak clients, audience/token-exchange contract, and secrets
- local stack bootstrapping for Keycloak, MAS, Synapse, and Nextcloud
- backend deployment/runtime wiring

## Gaps found in the current state

1. `weave` uses `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`, while older `weave-infra` revisions registered `weaveapp://login/callback` for the `weave-app` Keycloak client.
2. Older client defaults derived stale service routes instead of the final `files.<base-domain>` canonical Nextcloud URL and `api.<base-domain>/api` backend API base route.
3. Older local infrastructure exposed service-specific localhost routes instead of the final `https://weave.local` product gateway plus `https://api.weave.local/api`, `https://auth.weave.local`, `https://matrix.weave.local`, and `https://files.weave.local` service origins.
4. The original `weave-backend` spike assumed that user bearer tokens could be forwarded directly into Nextcloud and Matrix calls. That is the wrong default boundary for this stack.
5. The original backend spike did not compile in a clean Gradle/JDK environment because it used `WebClient` types without the needed reactive dependency and mixed mismatched OAuth client wiring.

## Why the backend boundary should stay narrow

- Matrix OAuth 2.0 makes the authentication service the source of truth for user accounts and access tokens, and gives less trust to clients by moving credential handling into the browser-managed flow.
- Nextcloud `user_oidc` supports bearer validation, but its own README explicitly notes that provisioning on bearer-token validation is not supported yet.
- Spring Security also cautions that `setDefaultOAuth2AuthorizedClient(true)` causes all outgoing `WebClient` requests to receive the current access token, which is not a good default for a backend that will eventually call multiple downstream systems with different trust models.

The safest first shape is therefore:

- Weave client authenticates users directly
- Weave backend validates the resulting JWT
- Server-side integrations are added only for workflows that are clearly backend-owned

## Good first backend-owned use cases

- workspace metadata / capability APIs
- server-side provisioning hooks for Nextcloud or Keycloak
- automation messages and bot-style Matrix events
- background jobs, scheduled sync, notifications, or audit logging
- future OpenAPI contract shared by mobile/web clients

## Research notes

- Matrix OAuth 2.0 API and native-client flow: https://spec.matrix.org/latest/client-server-api/index.html
- Matrix Authentication Service architecture: https://element-hq.github.io/matrix-authentication-service/setup/index.html
- Nextcloud `user_oidc` behavior and bearer validation limits: https://github.com/nextcloud/user_oidc
- Keycloak token exchange guidance: https://www.keycloak.org/securing-apps/token-exchange
- Spring Security servlet OAuth2 client caution for default authorized clients: https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html

## Issue drafts

Issue-ready drafts live under:

- [docs/issues/weave](issues/weave)
- [docs/issues/weave-infra](issues/weave-infra)
- [docs/issues/weave-backend](issues/weave-backend)
