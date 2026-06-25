# Dogfood auth onboarding plan

Status: active implementation gate for the next physical iPhone dogfood attempt.

## Product decision

Mobile members need long-lived sessions in normal use. Organizations still need a security boundary, so long-lived sessions are an explicit identity entitlement rather than a client-side workaround. For the dogfood Keycloak realm, `owner`, `admin`, `operator`, and `member` groups receive the built-in `offline_access` role; `guest` is excluded until guest-session policy is defined.

The Weave mobile app may request `offline_access`. If the identity provider denies it, the app must show localized product copy and a support code, not a raw provider error.

## Dogfood mail catcher

Use Mailpit for dogfood-only mail capture. It belongs in `infra/weave-workspace` as a local/dogfood service with:

- SMTP endpoint for stack services: `weave-mailpit:1025`.
- Operator web/API inbox: `http://127.0.0.1:8025` by default, optionally proxied only on dogfood/local profiles.
- No production mail path, no public Internet exposure, and no real external delivery.
- Support-bundle redaction for message bodies unless a future evidence task explicitly stores sanitized fixtures.

This replaces ambiguous "mail catcher/mailkit-style" wording with one concrete local stack component.

## Onboarding state machine

The member app should model these states explicitly:

- `handoff_received`: a join/deep link was opened and stored.
- `platform_config_loaded`: `/api/platform/config` was fetched from the product origin.
- `ready_for_sso`: issuer/client/redirect configuration is complete.
- `sso_in_progress`: AppAuth/browser sign-in is active.
- `authenticated`: access token and refresh/offline token were saved.
- `workspace_bootstrap_loading`: authenticated backend/profile/capability bootstrap is running.
- `workspace_ready`: member lands in the authenticated Weave workspace.
- `recoverable_error`: localized retryable issue such as network, TLS trust, invite refresh needed, or offline-session entitlement missing.
- `terminal_setup_error`: support-safe state requiring operator action.

The ready/prepared screen must not reappear after successful credentials unless the session was not saved or the authenticated bootstrap explicitly failed with localized recovery guidance.

## Acceptance test matrix

- Fresh install, handoff link, successful SSO, app lands in workspace/home and persists a refresh/offline token.
- Force-quit/reopen with saved session, no login prompt, backend profile/capability bootstrap succeeds.
- Expired access token with valid refresh/offline token, refresh succeeds without interactive login.
- Missing `offline_access` role, app shows localized offline-session entitlement guidance in English and German.
- User cancels SSO, app shows localized cancelled state and stays ready for retry.
- Wrong/stale handoff origin, app shows localized invite refresh guidance without raw provider URLs.
- Reinstall app, open same current handoff, manual login succeeds and records fresh handoff evidence.
- Manual sign-in from saved configuration without handoff, successful workspace/home entry.
- Guest account without offline entitlement remains denied with localized policy copy until guest policy exists.
- i18n check: no new sign-in/onboarding user-facing strings bypass ARB/localization.
- Accessibility check: ready/error states are screen-reader reachable, controls keep 48x48 logical touch targets, and retry/back actions have labels.
- Dogfood Mailpit check: test/reset emails are captured locally, visible to operator, and not sent externally.

## Remaining implementation slices

- Extend Mailpit coverage beyond Keycloak if backend-owned outbound email becomes part of dogfood.
- Convert the current handoff/sign-in flow into a typed onboarding state machine instead of relying on route side effects.
- Add widget/integration tests for every localized state above.
- Add dogfood runbook steps for app reinstall, manual login, session restore, and Mailpit inbox verification.
