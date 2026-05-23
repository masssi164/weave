# Historical issue drafts

These files are preserved as historical implementation planning notes. They are not the current public product contract.

Before copying or reopening anything from this directory, compare it against:

- [`../runtime-configuration.md`](../runtime-configuration.md)
- [`../architecture-alignment.md`](../architecture-alignment.md)
- [`../boards-preview-contract.md`](../boards-preview-contract.md)
- [`../../README.md`](../../README.md)

Current rules override older issue text:

- Flutter uses backend-owned product APIs for provider readiness, files/calendar facades, office launch, DevOps readiness, and other provider-stack surfaces.
- Direct Flutter-to-provider calls are not the default product contract.
- Backend actor credentials, provider tokens, raw provider URLs, and downstream error bodies must not be exposed to clients or support bundles.
- Optional providers fail closed unless explicit runtime gates are configured.

## Archived drafts

### `weave`

- [Align native OIDC registration and derived endpoints](weave/align-native-oidc-and-derived-endpoints.md)
- [Introduce a backend API boundary in the Flutter client](weave/introduce-backend-api-boundary.md)

### `weave-infra`

- [Enable local TLS and align public hostnames](weave-infra/enable-local-tls-and-align-public-hostnames.md)
- [Deploy weave-backend and finalize the Keycloak contract](weave-infra/deploy-weave-backend-and-finalize-keycloak-contract.md)

### `weave-backend`

- [Establish the backend API and OpenAPI contract](weave-backend/establish-backend-api-and-openapi-contract.md)
- [Implement server-owned integrations only](weave-backend/implement-server-owned-integrations-only.md)
