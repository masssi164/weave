# Runtime configuration

This document is the backend runtime reference for operators and local integration runs. The top-level README keeps the product boundary short; this file keeps the full environment-variable contract in one place.

## Required runtime variable

- `WEAVE_OIDC_ISSUER_URI`: public issuer URI for the Keycloak realm used by Weave. Canonical local/dev value: `https://auth.weave.local/realms/weave`.

## Optional platform and auth variables

- `WEAVE_OIDC_JWK_SET_URI`: internal JWKS URL for backend key discovery when it differs from the public issuer metadata route.
- `WEAVE_OIDC_REQUIRED_AUDIENCE`: audience required in access tokens, defaults to `weave-app`.
- `WEAVE_CLIENT_ID`: first-party Weave app client ID required in `azp` and/or `client_id`, defaults to `weave-app`.
- `WEAVE_PUBLIC_BASE_URL`: public product entrypoint, defaults to `https://weave.local`.
- `WEAVE_API_BASE_URL`: public backend API base URL, defaults to `https://api.weave.local/api`.
- `WEAVE_AUTH_BASE_URL`: public Keycloak base URL, defaults to `https://auth.weave.local`.
- `WEAVE_MATRIX_HOMESERVER_URL`: public Matrix homeserver URL, defaults to `https://matrix.weave.local`.
- `WEAVE_FILES_PRODUCT_URL`: public files product surface, defaults to `https://weave.local/files`.
- `WEAVE_CALENDAR_PRODUCT_URL`: public calendar product surface, defaults to `https://weave.local/calendar`.
- `WEAVE_NEXTCLOUD_BASE_URL`: canonical raw Nextcloud technical/admin/protocol URL, defaults to `https://files.weave.local`.
- `WEAVE_TARGET_MOBILE`: advertise mobile as a supported client target, defaults to `true`.
- `WEAVE_TARGET_DESKTOP`: advertise desktop as a supported client target, defaults to `true`.
- `WEAVE_TARGET_WEB`: advertise web as a supported client target, defaults to `false`.
- `PORT`: HTTP listen port, defaults to `8080`.

## Matrix E2EE and backend boundary variables

Matrix E2EE status is diagnostic-only until Matrix-native encrypted-room, device verification, recovery, lost-device, multi-device, and accessibility flows are validated. Backend diagnostics must not inspect or require encrypted message bodies.

- `WEAVE_MATRIX_FEDERATION_ENABLED`: report whether Matrix federation is enabled, defaults to `false` for the MVP private workspace contract.
- `WEAVE_MATRIX_E2EE_ENCRYPTED_ROOMS_VALIDATED`: encrypted default room gate, defaults to `false`.
- `WEAVE_MATRIX_E2EE_DEVICE_VERIFICATION_VALIDATED`: device verification gate, defaults to `false`.
- `WEAVE_MATRIX_E2EE_KEY_BACKUP_VALIDATED`: key backup/recovery gate, defaults to `false`.
- `WEAVE_MATRIX_E2EE_LOST_DEVICE_RECOVERY_VALIDATED`: lost-device recovery gate, defaults to `false`.
- `WEAVE_MATRIX_E2EE_MULTI_DEVICE_VALIDATED`: multi-device UX gate, defaults to `false`.
- `WEAVE_MATRIX_E2EE_ACCESSIBILITY_REVIEWED`: accessibility review gate for verification/recovery flows, defaults to `false`.
- `WEAVE_MATRIX_E2EE_STATUS_SOURCE`: support-safe source label for E2EE diagnostics, defaults to `backend_runtime_flags_only`.
- `WEAVE_MATRIX_AGENT_PARTICIPATION_POLICY`: policy label for bot/assistant participation in encrypted rooms, defaults to `blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented`.
- `WEAVE_MATRIX_CONNECTOR_WRITE_POLICY`: policy label for connector writes that target Matrix rooms, defaults to `fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented`.

`/api/platform/status` reports `matrix.e2eeEnabled = true` only when all E2EE validation flags above are true. It also reports `matrix.backendBoundary.serverReadableMessageContent = false` and lists only support-safe metadata classes that backend diagnostics may use (`room_id`, `event_id`, `sender_id`, timestamps, membership state, room encryption algorithm, and redacted state).

## Workspace capability variables

- `WEAVE_WORKSPACE_SHELL_ACCESS_ENABLED`: enable the authenticated shell contract, defaults to `true`.
- `WEAVE_WORKSPACE_CHAT_ENABLED`: enable chat in the workspace snapshot, defaults to `true`.
- `WEAVE_WORKSPACE_CHAT_READINESS`: optional explicit chat readiness override (`ready`, `degraded`, `blocked`, `unavailable`).
- `WEAVE_WORKSPACE_FILES_ENABLED`: enable files in the workspace snapshot, defaults to `true`.
- `WEAVE_WORKSPACE_FILES_READINESS`: optional explicit files readiness override (`ready`, `degraded`, `blocked`, `unavailable`).
- `WEAVE_WORKSPACE_CALENDAR_ENABLED`: enable the calendar capability, defaults to `false`.
- `WEAVE_WORKSPACE_CALENDAR_READINESS`: optional explicit calendar readiness override (`ready`, `degraded`, `blocked`, `unavailable`).
- `WEAVE_WORKSPACE_BOARDS_ENABLED`: enable the boards capability, defaults to `false`.
- `WEAVE_WORKSPACE_BOARDS_READINESS`: optional explicit boards readiness override (`ready`, `degraded`, `blocked`, `unavailable`).

Capability readiness is intentionally conservative:

- `shellAccess` is `unavailable` when disabled, otherwise `ready` only when JWT validation can be enforced with issuer, audience, and client contract.
- `chat` follows `WEAVE_WORKSPACE_CHAT_READINESS` when set; otherwise it is `ready` when `WEAVE_MATRIX_HOMESERVER_URL` is configured, `degraded` without that route, and `blocked` if shell access is blocked.
- `files` follows `WEAVE_WORKSPACE_FILES_READINESS` when set; otherwise it is `ready` when `WEAVE_NEXTCLOUD_BASE_URL` is configured, `degraded` without that route, and `blocked` if shell access is blocked.
- `calendar` and `boards` are stable contract slots. They are `unavailable` when disabled and may advertise rollout state through explicit readiness overrides.

## Boards/OpenProject runtime gates

Boards remains a Weave product facade. OpenProject is the first provider-backed workspace-sync engine, not the visible product UX. Runtime defaults are fail-closed and local-workspace unless explicitly configured by infra/operator env.

- `WEAVE_BOARDS_RUNTIME_ENABLED`: enables authenticated Boards workspace routes, defaults to `false`; legacy `WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED` is a compatibility fallback only.
- `WEAVE_BOARDS_PROVIDER`: backend provider, defaults to `local-workspace`; legacy `WEAVE_BOARDS_PREVIEW_PROVIDER` is a compatibility fallback only.
- `WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED`: OpenProject provider runtime gate, defaults to `false`.
- `WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED`: OpenProject workspace-sync gate, defaults to `false`.
- `WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED`: Context/Space authorization gate for provider references, defaults to `false`; must be `true` before OpenProject workspace-sync is reachable.
- `WEAVE_BOARDS_OPENPROJECT_AUDIT_CONSENT_ENABLED`: audit/consent promotion gate for future provider writes, defaults to `false`.
- `WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED`: provider-write gate, defaults to `false` and must stay false until provider write audit/consent promotion is proved.
- `WEAVE_BOARDS_OPENPROJECT_AUTH_MODE`: provider auth mode, defaults to `disabled`; OpenProject workspace-sync requires `service-token`.
- `WEAVE_BOARDS_OPENPROJECT_BASE_URL`: backend-only OpenProject base URL. Blank keeps workspace-sync fail-closed.
- `WEAVE_BOARDS_OPENPROJECT_API_TOKEN`: backend-held OpenProject service token. Blank keeps workspace-sync fail-closed; never expose this to Flutter, platform config, support logs, or support bundles.

OpenProject workspace-sync requires provider `openproject`, runtime enabled, read-sync enabled, Context/Space authorization enabled, `service-token` auth, a base URL, and a backend-held API token. Local workspace user writes are in v0.1 scope when authenticated, authorized, explicit, and audited; OpenProject provider writes still fail closed unless the future write, audit/consent, and Context/Space gates are all promoted. The OpenProject webhook signature verifier is available for the future ingress seam, but no runtime webhook route is published here; webhook handling remains normalization-only and does not enable agent/team writes or live audit publication.

## Meetings/LiveKit provider contract

LiveKit is the active meetings/video-call provider key in the provider registry. The backend exposes only support-safe readiness for meetings and keeps actual room/session tokens behind a backend-owned facade. Matrix is not advertised as the generic meetings provider.

- `WEAVE_LIVEKIT_ENABLED`: exposes the LiveKit meetings provider contract, defaults to `true` while failing closed when not configured.
- `WEAVE_LIVEKIT_URL`: LiveKit server URL. Blank keeps direct credential mode and token-endpoint mode unconfigured.
- `WEAVE_LIVEKIT_API_KEY`: backend-held LiveKit API key for future token minting. Blank keeps direct credential mode unconfigured. Never expose this value to Flutter, platform config, support logs, or support bundles.
- `WEAVE_LIVEKIT_API_SECRET`: backend-held LiveKit API secret for future token minting. Blank keeps direct credential mode unconfigured. Never expose this value to Flutter, platform config, support logs, or support bundles.
- `WEAVE_LIVEKIT_TOKEN_ENDPOINT`: optional backend/internal token endpoint alternative when token minting is delegated. Blank keeps token-endpoint mode unconfigured.

Provider readiness is `configured` only when LiveKit is enabled and either `WEAVE_LIVEKIT_URL` + `WEAVE_LIVEKIT_API_KEY` + `WEAVE_LIVEKIT_API_SECRET`, or `WEAVE_LIVEKIT_URL` + `WEAVE_LIVEKIT_TOKEN_ENDPOINT`, are present. `/api/providers/status` reports booleans such as `livekitUrlConfigured`, `apiKeyConfigured`, `apiSecretConfigured`, and `tokenEndpointConfigured`; it must not return raw keys, secrets, endpoint credentials, room tokens, credential-bearing join URLs, or raw LiveKit errors.

## Files facade and Nextcloud WebDAV adapter

The app never sends raw Nextcloud credentials to this backend. Files operations use a backend-owned actor for WebDAV calls.

- `WEAVE_NEXTCLOUD_FILES_ACTOR_MODEL`: backend-to-Nextcloud token model, currently only `backend-service-account`; other values fail closed until implemented.
- `WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME`: backend-owned Nextcloud actor username for WebDAV calls. Blank keeps the facade unavailable.
- `WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN`: backend-owned Nextcloud app password/token for WebDAV calls. Blank keeps the facade unavailable.
- `WEAVE_NEXTCLOUD_FILES_APP_PASSWORD`: compatibility alias used when `WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN` is blank.
- `WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH`: Nextcloud WebDAV files root path, defaults to `/remote.php/dav/files`.

If the actor model, username, or token is missing, files endpoints fail closed with `nextcloud-adapter-not-configured`. Implemented WebDAV operations are folder listing with quota when returned by Nextcloud, folder creation, upload, download, and delete. Move/share remain unsupported until product policy and endpoint contracts are specified.

## Calendar facade and CalDAV adapter

Calendar product operations stay on `/api`; this backend is the only component that talks to Nextcloud CalDAV.

- `WEAVE_CALDAV_BASE_URL`: Nextcloud origin used by the backend CalDAV adapter, defaults to `WEAVE_NEXTCLOUD_BASE_URL` or `https://files.weave.local`.
- `WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE`: CalDAV calendar collection path for the backend-owned workspace calendar, defaults to `/remote.php/dav/calendars/${WEAVE_CALDAV_BACKEND_USERNAME:-weave-backend}/personal/`. Optional scope placeholders `{scopeId}`, `{scopeType}`, `{team}`, and `{channel}` can be used by operators who provision explicit workspace/team/channel collections.
- `WEAVE_CALDAV_AUTH_MODE`: backend actor credential mode (`BASIC` or `BEARER`), defaults to `BASIC`.
- `WEAVE_CALDAV_BACKEND_USERNAME`: backend actor username for Basic auth; required with `BASIC`.
- `WEAVE_CALDAV_BACKEND_TOKEN`: backend actor app password/token or bearer token; required for the CalDAV adapter to call Nextcloud.
- `WEAVE_CALDAV_REQUEST_TIMEOUT_SECONDS`: CalDAV request timeout, defaults to `10`.

The active Calendar facade stores Weave workspace/team/channel scopes in backend-actor CalDAV collections. With the default path template, workspace events use the configured collection, while team and channel scopes derive sibling backend-owned collections such as `weave-team-engineering` and `weave-channel-engineering-general` to avoid cross-scope event leakage. `WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE` values containing `{user}` are treated as private-personal calendar targets and fail closed with `nextcloud-adapter-not-configured` until a reviewed provisioning/sharing/delegated-token model is specified. Facade responses include `scope` and `contextId` metadata so clients do not present this as a private per-user calendar.

When required actor credentials are missing or an unsafe private-personal template is configured, calendar operations fail closed with `nextcloud-adapter-not-configured`. Recurrence creation, editing, and expansion are deferred: the current DTO has no RRULE contract, and the adapter does not expose raw recurrence fields. Recurring events returned by CalDAV may appear as their source VEVENT only until a later product/API spec defines full recurrence UX.

## Profile and onboarding variables

- `WEAVE_PROFILE_STORAGE_PATH`: durable JSON file path for mutable `PATCH /api/profile` overrides, defaults to `./data/profile-overrides.json`.
- `WEAVE_ONBOARDING_MATRIX_PROVISIONING_STATE`: optional first-run Matrix provisioning override (`not_configured`, `pending`, `ready`, `degraded`, `failed`); blank derives status from chat capability.
- `WEAVE_ONBOARDING_NEXTCLOUD_PROVISIONING_STATE`: optional first-run Nextcloud provisioning override (`not_configured`, `pending`, `ready`, `degraded`, `failed`); blank derives status from files/calendar capability and Nextcloud route configuration.

Profile facade endpoints are protected by the same first-party bearer-token contract as `/api/me`. `PATCH /api/profile` accepts partial updates for `displayName`, `avatar`, `locale`, `timezone`, `accessibilityPreferences`, and `profileVisibility`. Set `WEAVE_PROFILE_STORAGE_PATH` to mounted durable storage for containerized runs.

Onboarding status returns identity, roles, groups, invite status, profile completeness, and module provisioning states. Downstream states must remain frontend-safe and must not expose stack traces, tokens, secrets, or raw service errors.

## Interop gateway, Slack on-ramp, guests, and migration previews

Interop, guest access, and migration/import execution remain disabled by default. The backend exposes contract/readiness surfaces so feature-gated work can be validated without making Slack, Teams, guest portal, connector SDK, or migration runtime behavior a core dependency.

- `WEAVE_INTEROP_ENABLED`: master interop gateway flag, defaults to `false`.
- `WEAVE_INTEROP_SUPPORT_BUNDLE_REDACTION_MODE`: support bundle redaction mode label, defaults to `support-safe-redacted`.
- `WEAVE_INTEROP_SLACK_ENABLED`: Slack provider flag, defaults to `false`.
- `WEAVE_INTEROP_SLACK_CLIENT_ID`: Slack app client id metadata. This is not secret material.
- `WEAVE_INTEROP_SLACK_CLIENT_SECRET_REF`: reference to Slack OAuth client secret in an operator-owned secret store. Raw client secrets must not be supplied through API payloads or support bundles.
- `WEAVE_INTEROP_SLACK_SIGNING_SECRET_REF`: reference to Slack request signing secret. Signed inbound events fail closed when this reference cannot be resolved by backend secret brokering.
- `WEAVE_INTEROP_SLACK_TOKEN_REF`: reference to Slack bot token. Raw bot/access/refresh tokens must not be supplied through API payloads or support bundles.
- `WEAVE_INTEROP_SLACK_WORKSPACE_ID`: one Slack workspace id for the proof-of-on-ramp.
- `WEAVE_INTEROP_SLACK_CHANNEL_ID`: one Slack channel id mapped by the proof-of-on-ramp.
- `WEAVE_INTEROP_SLACK_ROOM_ID`: one Weave/Matrix room reference mapped by the proof-of-on-ramp.
- `WEAVE_INTEROP_TEAMS_ENABLED`: Teams provider flag, defaults to `false`; Teams remains gated behind Slack hardening.
- `WEAVE_GUEST_ENABLED`: guest invitation flag, defaults to `false` and denies access while preserving distinct guest identity semantics.
- `WEAVE_MIGRATION_ENABLED`: migration dry-run flag, defaults to `false`; dry-run reports are replay-safe and do not import source data.
- `WEAVE_CONNECTORS_PUBLIC_SDK_ENABLED`: connector SDK flag, defaults to `false`; manifest validation rejects inline secret material.

The Slack on-ramp is intentionally one-channel and sandbox-first. Inbound Slack text events require Slack HMAC request signature verification before mapping. Bot-message subtype events are rejected before mapping to prevent loops. Outbound Weave-to-Slack messages currently return a deterministic, idempotent `sandbox-not-delivered` response and do not call Slack production APIs. Status responses expose degraded/rate-limited/signature/loop-prevention reasons without exposing secret refs or raw provider tokens.
