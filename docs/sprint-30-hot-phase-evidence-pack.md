# Sprint 30 hot-phase evidence pack

Sprint 30 evidence is bounded to dogfood-readiness contracts, local dry-run checks, and support-safe handoff artifacts. It does not claim public production readiness or unrestricted autonomous personal-assistant availability.

## Product positioning

Exact slogan: **Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.**

Claim boundary: the slogan is used only beside copy that says governed Weaver assistance is activated by organization policy, user rights, explicit approvals, audit receipts, revocation, and evidence gates.

## Unified profile-driven setup evidence

Canonical fixture: `release/sprint-30-hot-phase/profile-driven-setup.fixture.json`.

The fixture defines one script family, `weavectl profile apply`, for `dev`, `local-lan-dogfood`, `public-dogfood`, and `production`. Differences are profile variables only. No separate test-only member flow exists.

`local-lan-dogfood` is valid for the first real iPhone dogfood when the phone can reach the Mac over LAN. The phone handoff policy rejects `127.0.0.1`, `localhost`, and Mac-only `.local` assumptions. Public DNS and trusted internet TLS are not mandatory for that LAN profile.

## Member onboarding evidence

The normal member path is:

1. Open invite link, deep link, QR, or organization URL.
2. Complete SSO.
3. Land in the Weave workspace home.

The normal member path must never ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.

## Operator/Admin evidence

The operator target is one profile-driven command or the equivalent Weave Control/Admin Console action that prepares stack readiness, reachability, invite handoff, and support-safe evidence. Evidence may contain profile name, reachability mode, handoff ref, readiness ref, policy version, profile hash, audit ref, and redacted endpoint class. Evidence must not contain raw secrets, credential URLs, provider payloads, member private content, or raw downstream errors.

## Weaver governance evidence

Weaver is a governed per-user PA runtime, not an M365-locked autonomous suite. Organization policy is configured through Weave Control/Admin Console and consumed by Weaver as source of truth. The authority model is user rights plus organization-whitelisted tools/capabilities. Unknown actions and capabilities fail closed. Background mode is read-only/risk-detection until a mobile action request, approval receipt, audit event, and revocation boundary exist.

Mobile action request and approval receipt events are specified in `release/sprint-30-hot-phase/weaver-mobile-action-events.fixture.json`. The fixture requires `weaver.action_request.created` and `weaver.action_request.receipt`, denies unknown capabilities, expires requests closed, emits support-safe audit/revocation refs, and forbids raw prompts, member private content, raw provider payloads, secrets, credential URLs, and raw downstream errors in phone payloads.

Cross-repo Weaver contracts are in `masssi164/weaver` Sprint 30 issues `#13`, `#14`, `#15`, and `#16`.

## Gate summary

Required local gates for this evidence pack:

- `./gradlew docsCheck`
- `./gradlew releaseEvidenceCheck`
- `./gradlew adminCi` when Admin Console or Weave Control surfaces change
- `./gradlew clientCi` when member-visible onboarding copy changes
- `./gradlew ci` before final merge if cross-stack code changes remain

Final closure requires green GitHub Actions on merged PRs and a `main` verification run after the last merge.
