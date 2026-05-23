# Connector runtime guardrails

Connector runtime work is fail-closed by default. The infra layer may expose safe backend metadata for guarded product UI, but it must not expose provider webhooks, OAuth callbacks, provider tokens, refresh tokens, or static demo secrets unless a later reviewed connector issue explicitly enables them.

## Default runtime posture

- `WEAVE_INTEROP_ENABLED=false`
- `WEAVE_INTEROP_SLACK_ENABLED=false`
- `WEAVE_INTEROP_TEAMS_ENABLED=false`
- `WEAVE_CONNECTORS_PUBLIC_SDK_ENABLED=false`
- `connector_provider_callbacks_exposed=false`
- `WEAVE_BOARDS_RUNTIME_ENABLED=false` unless a live feature-proof run explicitly opts in
- `WEAVE_BOARDS_PROVIDER=local-workspace`
- `WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=false`
- `WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=false`
- `WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=false`
- `WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=disabled`

With these defaults, backend interop/connector status can describe disabled or unavailable capabilities, while provider callback routes such as Slack OAuth and event ingestion are blocked at Caddy with `404` before reaching the backend.
Boards may advertise the product capability as ready for dogfood workspace UX, but provider-backed runtime remains fail-closed until the explicit Boards workspace/runtime flag is enabled and backend Context/Space authorization allows the request. OpenProject is the first real provider-backed workspace-sync path, but remains optional and backend-held-token only.

## Secret handling boundary

- Connector secrets are operator-owned and revocable; do not commit demo OAuth secrets, webhook signing secrets, bot tokens, access tokens, or refresh tokens.
- Boards provider secrets follow the same rule: keep API tokens, refresh tokens, and provider credentials server-side and out of app config, support bundles, and frontend artifacts.
- For OpenProject workspace-sync, `TF_VAR_boards_openproject_api_token` is a backend-only secret; app config may expose only support-safe readiness/source metadata such as provider name, runtime gate state, and base URL when needed for operator diagnostics.
- Future secret-manager wiring must pass secret references to the backend, not raw provider secrets to Flutter.
- Support bundles must redact token, secret, password, credential, authorization, cookie, and private-key patterns before an archive is considered shareable.
- Connector diagnostics should report safe states such as `disabled`, `unavailable`, `degraded`, `action-required`, and `configured-reference`; they must not include raw secret values.

## Future enablement checklist

Before exposing a provider callback route or enabling provider runtime behavior, the enabling PR must show:

1. the backend connector/interop issue that owns the provider contract;
2. Caddy/Terraform config that exposes only the required callback paths;
3. revocation guidance for every provider credential involved;
4. support-bundle redaction coverage for new env names, logs, and diagnostics;
5. smoke/static checks proving core behavior is unchanged when connector config is absent.
