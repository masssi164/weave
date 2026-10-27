# Dogfood auth onboarding plan

Status: authentication/onboarding acceptance and diagnostic inventory. The current protected iPhone preparation flow is [artifact-based member delivery](ios-dogfood-distribution.md): prepared organization, Space and ordinary members, real two-member collaboration, then in-place device startup. Personal sign-in belongs to subsequent HIL; the detailed onboarding probes below remain separately selectable and do not require an extra automated owner login before handoff.

## Product decision

Mobile members need long-lived sessions in normal use. Organizations still need a security boundary, so long-lived sessions are an explicit identity entitlement rather than a client-side workaround. Keycloak is the identity authority. For the dogfood realm, approved `weave-app` client roles receive the built-in `offline_access` role; `guest` is excluded until guest-session policy is defined. `operator` is not a member product role.

The Weave mobile app may request `offline_access`. If the identity provider denies it, the app must show localized product copy and a support code, not a raw provider error.

## Dogfood mail catcher

Use Mailpit for dogfood-only mail capture. It belongs in `infra/weave-workspace` as a local/dogfood service with:

- SMTP endpoint for stack services: `weave-mailpit:1025`.
- Human dogfood inbox: `WEAVE_MAILPIT_URL` from the reviewed deployment profile (`https://mail.weave.test` for the standard-port Dogfood example), through Caddy only for explicitly configured private-LAN CIDRs. Runner-local checks may use the loopback API as an internal implementation detail.
- SMTP remains private on the Docker network and is never routed through Caddy or exposed to the LAN.
- No production mail path, no public Internet exposure, and no real external delivery.
- Support-bundle redaction for message bodies unless a future evidence task explicitly stores sanitized fixtures.
- A bounded SQLite database on `weave_dogfood_mailpit_data`, retaining the latest 500 messages across ordinary container replacement and excluding that database from support bundles.

Full Compose E2E does not share the dogfood Compose project, its three session
volumes, or human data. It uses a run-unique disposable project and cleanup
boundary. `dogfoodReset` is the explicit development action that discards the
human dogfood identity and inbox when a clean test state is wanted.

This replaces ambiguous "mail catcher/mailkit-style" wording with one concrete local stack component.

## First-owner setup

The setup owner is established as the first Keycloak organization owner by the bounded
Server-owned bootstrap. The designated member tester is a separate identity. The identity is distinct from disposable automation principals and is not
part of the static realm baseline. The bootstrap may create the invitation once or exactly resend
the same correlated pending invitation; it cannot create a second human and is unavailable after a
human exists. Routine deployment never receives a human writer or realm-admin credential.

The supported initial path is the manual GitHub **Dogfood Owner Invitation** workflow, Safari at
the reviewed private Mailpit URL, and normal OIDC sign-in in Weave. After activation, the owner uses
the Admin Console or authenticated Server invitation API for later members. Active-account password
or passkey recovery stays in Keycloak.

## Activation invite lifecycle

Dogfood member activation must not depend on publishing a password or long-lived token. An owner or admin creates a Keycloak Organization invitation through the Weave Admin API. Keycloak sends the short-lived activation email directly to Mailpit and owns the activation token, expiry, resend, deletion, registration, credentials, organization membership, organization groups, and `weave-app` client roles. Weave stores only temporary role/group provisioning intent when those assignments cannot be attached to the pending Keycloak invitation.

After browser activation, the email completion link, QR code, app/universal link, and manually entered server URI all resolve the same secret-free organization access contract. They must not contain passwords, bearer tokens, refresh tokens, Keycloak action-token URLs, raw provider payloads, SecretRefs, or credential URLs. Treat the action URL inside Mailpit as a secret identity-provider artifact: open it only in the system browser activation path and never paste it into docs, logs, app preferences, QR payloads, GitHub comments, or support bundles.

Support-safe activation evidence may record hashed username/email, the invite reference, role/group, required action names, TTL, and whether Mailpit captured the message. It must not record the action URL or token value.

## Canonical bootstrap

Both invitation completion and existing-account login consume the same OrgManifest v2. Valid host input is normalized to HTTPS. The public `/join` completion page exposes only the organization origin through its app link, QR code and copyable text. Authentication uses the native system browser and Authorization Code with PKCE, preserving passkey and MFA support. Flutter never collects passwords or asks members for issuers, realms, client IDs or provider endpoints. Files and Calendar use the generated User API; the native Matrix facade is the accepted protocol exception.

Persisted profiles keep only the organization origin, validated manifest and validation timestamp. Transport configuration is derived from that manifest. Origin-bearing older profiles are migration inputs and require fresh discovery before authentication.

## Onboarding state machine

The member app should model these states internally for UX and recovery. They
are **not** a persisted diagnostic history or an alternative test interface:

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

- Delivery gate: deterministic checks run on `dev`; the protected `dogfood` candidate consumes hosted artifacts and runs mandatory Compose plus concurrent dual-iOS Flutter UI collaboration with two ordinary members. macOS desktop testing is manual-only. Physical startup preparation starts after qualification and deployment pass, preserves the installed session and leaves HIL pending.
- First install or ordinary organization access, handoff link, successful SSO, app lands in workspace/home and persists a refresh/offline token.
- Admin-created Keycloak Organization invitation uses a short TTL, Mailpit capture, support-safe evidence, and no initial password output.
- Email completion link, QR code, and manual server URI converge on the same Organisation access screen and explicit Sign in action.
- Force-quit/reopen with saved session, no login prompt, backend profile/capability bootstrap succeeds.
- Expired access token with valid refresh/offline token, refresh succeeds without interactive login.
- Missing `offline_access` role, app shows localized offline-session entitlement guidance in English and German.
- User cancels SSO, app shows localized cancelled state and stays ready for retry.
- Wrong/stale handoff origin, app shows localized invite refresh guidance without raw provider URLs.
- Ordinary sign-out and sign-in exercise session recovery through product controls. There is no test-only app-state reset bridge. A deliberate infrastructure reset has its separate bounded scope below.
- Manual sign-in from saved configuration without handoff, successful workspace/home entry.
- Guest account without offline entitlement remains denied with localized policy copy until guest policy exists.
- i18n check: no new sign-in/onboarding user-facing strings bypass ARB/localization.
- Accessibility check: ready/error states are screen-reader reachable, controls keep 48x48 logical touch targets, and retry/back actions have labels.
- Dogfood Mailpit check: test/reset emails are captured locally, visible to operator, and not sent externally.
- Exact-candidate native product evidence: `tools/dogfood_functional_smoke.py` produces a support-safe receipt from ordinary UI operations, two independent members, Chat/Files readback, and restart/update continuity. `functional_smoke_passed` in that same module validates the receipt against the qualified candidate, source tree, specification and deployment run. `tools/verify_main_promotion_evidence.py` consumes it; neither reads private app preferences.
- Owner invitation activation and manual first sign-in remain separate human acceptance evidence. The isolated `FreshProductFlow` full mode separately tests first-owner and member activation with the normal browser and Mailpit. An ordinary-member functional receipt or observed startup does not prove that a human completed activation or HIL.
- Physical iPhone candidate check: install over Wi-Fi when device reachability works; if Wi-Fi install is unavailable, install over USB before asking Massimo to test.
- `Prepare Human Test` is the physical delivery entry point. `tools/prepare_iphone_artifact.py` signs and installs the qualified artifact and observes ordinary startup without compiling or replacing the tester's login. The former local installer and private-plist diagnostic chain have been removed.
- Members enter only the installation origin (`https://weave.test` for the standard-port Dogfood example). The product gateway delivers the canonical OrgManifest v2 at `/api/platform/config`; its generated User API base is `https://api.weave.test/api` in that example. Other installations retain their configured public origins and ports. The active development channel is the development-signed in-place profile update; TestFlight is not part of the dogfood prerequisite.
- The existing native product runners relaunch the installed process and verify current identity and authenticated Chat/Files state from ordinary controls and actual readback. The deployment smoke also verifies an in-place update; no preference ledger is accepted as session-continuity evidence.
- Trust stability check: the local TLS CA and leaf certificate fingerprints remain stable across normal stack restart/recreate unless explicit rotation is requested.
- iOS signing trust check: the dogfood app keeps the same bundle ID `com.massimotter.weave`, provisioning Team ID `KNDHGC2KV6`, developer certificate label `Apple Development: massimo164@me.com (6RUS2Z848X)`, signing identity/profile class, and installed-app trust assumptions across a normal update. A normal update must not ask Massimo to trust the developer again.
- Physical installation check: update-in-place is the protected path. Normal preparation never uninstalls the app or clears its configuration, session or Keychain state.
- Trust-domain evidence distinguishes Apple Developer Mode, Developer App trust, local TLS trust for `weave.test`, iOS app signing/provisioning stability for the installed app, and AppAuth/OIDC browser/session trust.
- Repeated developer-team/profile trust prompts are a dogfood blocker, even if Wi-Fi or USB install technically succeeds.

## Remaining implementation slices

- Keep Mailpit directly connected to Keycloak; extend it beyond Keycloak only if backend-owned outbound email becomes an explicit product contract.
- Convert the current handoff/sign-in flow into a typed onboarding state machine instead of relying on route side effects.
- Add widget/integration tests for every localized state above.
- Keep ordinary sign-in, session restore and private Mailpit activation coverage assigned to the existing external owners in [iOS Dogfood distribution](ios-dogfood-distribution.md#external-qualification-owners).
- Add trust-stability evidence for local cert persistence, stable iOS signing/provisioning, and no repeated developer trust prompt after a normal in-place update.


## Session retirement and shared identity context

The implementation dependency for member preparation is the declarative Keycloak Admin
HTTP boundary in #1732. The existing `KeycloakIdentityAdminClient` remains responsible
for current provider reads, managed fixture sequencing, operation admission and safe errors.
Preparation and default-Space bootstrap now derive their already-verified identity contexts
through `OrganizationIdentityContextResolver.resolveVerifiedMember`; neither manufactures a
JWT, opens a second provider transport, or infers another tenant/role.

An explicit fixed Dogfood reset retires both
`~/.weave/dogfood/functional-smoke-identities.json` and its
`functional-smoke-identities.preparation-ledger.json` sidecar, together with the two generated
owner-bootstrap session records. Ownership, private file modes, regular-file type and hardlink/
symlink boundaries are checked before teardown and again before retirement. Other private files,
persistent TLS roots, infrastructure credentials and installed iPhone/Keychain state are not reset.
Ordinary `up` and `down` preserve the fixture and ledger.

The existing operation lock serializes preparation and reset. Before destructive teardown,
reset persists `generated/operations/session-reset.pending.json` under the selected environment's
generated root. Partial teardown or interrupted record retirement leaves that fence in place;
normal preparation and `up` fail with `dogfood-session-reset-incomplete`. Rerun the explicit reset
to finish retiring that session. Do not delete the fence manually or restore only one sidecar.
The fence is removed only after all exact records have been retired and their containing
directories synced. If the subsequent empty-stack startup fails, ordinary `up` can safely retry.

Setup-owner evidence does not establish ordinary-member handoff readiness. Routine delivery
selects one stable ordinary member, A or B, from the same reviewed scenario and verifies its
current identity and shared-Space access. First-owner creation remains an explicit, separate
setup action. The broader initial-admin recipe in #1742 retains its own remaining acceptance
scope; this delivery correction does not grant broader roles or memberships. Unit/contract tests
are not live collaboration or physical VoiceOver acceptance.
