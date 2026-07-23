# Dogfood auth onboarding plan

Status: active implementation gate for the next physical iPhone dogfood attempt.

## Product decision

Mobile members need long-lived sessions in normal use. Organizations still need a security boundary, so long-lived sessions are an explicit identity entitlement rather than a client-side workaround. Keycloak is the identity authority. For the dogfood realm, approved `weave-app` client roles receive the built-in `offline_access` role; `guest` is excluded until guest-session policy is defined. `operator` is not a member product role.

The Weave mobile app may request `offline_access`. If the identity provider denies it, the app must show localized product copy and a support code, not a raw provider error.

## Dogfood mail catcher

Use Mailpit for dogfood-only mail capture. It belongs in `infra/weave-workspace` as a local/dogfood service with:

- SMTP endpoint for stack services: `weave-mailpit:1025`.
- Human dogfood inbox: `https://mail.weave.test:44443` for the current LAN dogfood port profile, through Caddy only for explicitly configured private-LAN CIDRs. Runner-local checks may use the loopback API as an internal implementation detail.
- SMTP remains private on the Docker network and is never routed through Caddy or exposed to the LAN.
- No production mail path, no public Internet exposure, and no real external delivery.
- Support-bundle redaction for message bodies unless a future evidence task explicitly stores sanitized fixtures.
- A bounded SQLite database on `weave_mailpit_data`, retaining the latest 500 messages across ordinary container replacement and excluding that database from support bundles.

Destructive live-stack E2E is not allowed to share the persistent dogfood runner or its Compose project, named volumes, desired-state reconciliation authority, or human data. It requires a run-unique disposable project and cleanup boundary; otherwise the workflow fails closed instead of removing the human dogfood identity or inbox.

This replaces ambiguous "mail catcher/mailkit-style" wording with one concrete local stack component.

## Persistent human dogfood member

The iPhone tester uses one persistent Keycloak organization member with the `member` role. This identity is distinct from disposable automation principals and is not part of the fixed Keycloak desired-state baseline. Protected dogfood automation may create it once, report its support-safe state, or resend activation while it is pending. Once active, deployment only verifies its immutable subject, organization membership, role, and expected capability groups; it never re-invites, recreates, or rewrites the account.

The tester does not need Admin Console access. The supported remote path is the protected GitHub dogfood-member workflow for initial ensure/status/pending activation resend, Safari at `https://mail.weave.test:44443`, and normal OIDC sign-in in Weave. Active-account password or passkey recovery stays in Keycloak.

## Activation invite lifecycle

Dogfood member activation must not depend on publishing a password or long-lived token. An owner or admin creates a Keycloak Organization invitation through the Weave Admin API. Keycloak sends the short-lived activation email directly to Mailpit and owns the activation token, expiry, resend, deletion, registration, credentials, organization membership, organization groups, and `weave-app` client roles. Weave stores only temporary role/group provisioning intent when those assignments cannot be attached to the pending Keycloak invitation.

After browser activation, the email completion link, QR code, app/universal link, and manually entered server URI all resolve the same secret-free organization access contract. They must not contain passwords, bearer tokens, refresh tokens, Keycloak action-token URLs, raw provider payloads, SecretRefs, or credential URLs. Treat the action URL inside Mailpit as a secret identity-provider artifact: open it only in the system browser activation path and never paste it into docs, logs, app preferences, QR payloads, GitHub comments, or support bundles.

Support-safe activation evidence may record hashed username/email, the invite reference, role/group, required action names, TTL, and whether Mailpit captured the message. It must not record the action URL or token value.

## Onboarding state machine

The member app should model these states explicitly:

- `handoff_received`: a join/deep link was opened and stored.
- `platform_config_loaded`: the organization access discovery contract was fetched from the product origin.
- `ready_for_sso`: issuer/client/redirect configuration is complete.
- `sso_in_progress`: AppAuth/browser sign-in is active.
- `authenticated`: access token and refresh/offline token were saved.
- `workspace_bootstrap_loading`: authenticated backend/profile/capability bootstrap is running.
- `workspace_ready`: member lands in the authenticated Weave workspace.
- `recoverable_error`: localized retryable issue such as network, TLS trust, invite refresh needed, or offline-session entitlement missing.
- `terminal_setup_error`: support-safe state requiring operator action.

The Organisation access screen must always expose a functional **Sign in** action once discovery succeeds. The ready/prepared screen must not reappear after successful credentials unless the session was not saved or the authenticated bootstrap explicitly failed with localized recovery guidance.

## Acceptance test matrix

- Delivery gate: implement against `dev`, run the onboarding E2E gate in the iOS Simulator from the current `dev` state, and only then promote/install the dogfood candidate for physical iPhone testing.
- Trust-preserving app-state reset or first install, handoff link, successful SSO, app lands in workspace/home and persists a refresh/offline token.
- Admin-created Keycloak Organization invitation uses a short TTL, Mailpit capture, support-safe evidence, and no initial password output.
- Email completion link, QR code, and manual server URI converge on the same Organisation access screen and explicit Sign in action.
- Force-quit/reopen with saved session, no login prompt, backend profile/capability bootstrap succeeds.
- Expired access token with valid refresh/offline token, refresh succeeds without interactive login.
- Missing `offline_access` role, app shows localized offline-session entitlement guidance in English and German.
- User cancels SSO, app shows localized cancelled state and stays ready for retry.
- Wrong/stale handoff origin, app shows localized invite refresh guidance without raw provider URLs.
- Trust-preserving app-state reset, open same current handoff, manual login succeeds and records fresh handoff evidence without deleting the Developer App trust anchor.
- Manual sign-in from saved configuration without handoff, successful workspace/home entry.
- Guest account without offline entitlement remains denied with localized policy copy until guest policy exists.
- i18n check: no new sign-in/onboarding user-facing strings bypass ARB/localization.
- Accessibility check: ready/error states are screen-reader reachable, controls keep 48x48 logical touch targets, and retry/back actions have labels.
- Dogfood Mailpit check: test/reset emails are captured locally, visible to operator, and not sent externally.
- Return-to-app gate: copied app preferences include `dogfood_auth_state_history_v1` proving `sso_in_progress`, `authenticated`, `workspace_bootstrap_loading`, and `workspace_ready` in order. A lone final `workspace_ready` value without that history is not dogfood completion evidence.
- Post-login usability gate: the same dogfood member run must also provide support-safe Chat and Files status evidence through `DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT`. `workspace_ready` alone proves authentication and bootstrap, not that the member can use the first workspace.
- Physical iPhone candidate check: install over Wi-Fi when device reachability works; if Wi-Fi install is unavailable, install over USB before asking Massimo to test.
- OpenClaw-facing iPhone entry command: use `tools/dogfood_iphone_entry.sh --check --device-id <paired-iphone-id> --local-ca-trust trusted` to verify toolchain, handoff generation, and device reachability without installing. Use `--dry-run` to print the delegated physical smoke command, and `--run` to generate `build/dogfood/iphone-entry/handoff.json`, build a profile/release app, install/update it, and launch the current deeplink. The command fails with `DEVICE_ID_REQUIRED`, `DEVICE_UNAVAILABLE_OR_LOCKED`, or `PHYSICAL_DEVICE_TLS_PENDING` when the phone is missing, locked, untrusted, or the local CA has not been confirmed.
- The canonical local defaults separate the app origin (`https://weave.test:44443`) from the API platform-config origin (`https://api.weave.test:44443/api/platform/config`). TestFlight is the preferred human iteration channel; the local profile runner is the engineering fallback.
- After first successful workspace entry, run `tools/dogfood_ios_session_restore_smoke.sh`. It terminates and relaunches the installed process and emits `DOGFOOD_SESSION_CONTINUITY_RESULT` only after the device-bound session is restored and an authenticated profile-facade request returns the app to `workspace_ready`.
- Trust stability check: the local TLS CA and leaf certificate fingerprints remain stable across normal stack restart/recreate unless explicit rotation is requested.
- iOS signing trust check: the dogfood app keeps the same bundle ID `com.massimotter.weave`, provisioning Team ID `KNDHGC2KV6`, developer certificate label `Apple Development: massimo164@me.com (6RUS2Z848X)`, signing identity/profile class, and installed-app trust assumptions across normal update or app-state reset. A normal update/app-state reset must not ask Massimo to trust the developer again.
- Physical reset check: update-in-place is the default, app-state reset is the trust-preserving fresh path, and destructive uninstall is explicit opt-in because it can remove the Developer App trust anchor.
- Trust-domain evidence distinguishes Apple Developer Mode, Developer App trust, local TLS trust for `weave.test`, iOS app signing/provisioning stability for the installed app, and AppAuth/OIDC browser/session trust.
- Repeated developer-team/profile trust prompts are a dogfood blocker, even if Wi-Fi or USB install technically succeeds.

## Remaining implementation slices

- Keep Mailpit directly connected to Keycloak; extend it beyond Keycloak only if backend-owned outbound email becomes an explicit product contract.
- Convert the current handoff/sign-in flow into a typed onboarding state machine instead of relying on route side effects.
- Add widget/integration tests for every localized state above.
- Add dogfood runbook steps for trust-preserving app-state reset, manual login, session restore, and Mailpit inbox verification.
- Add trust-stability evidence for local cert persistence, stable iOS signing/provisioning, and no repeated developer trust prompt after normal update/app-state reset.
