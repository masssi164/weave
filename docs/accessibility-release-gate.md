# Accessibility Release Gate

Status: v0.1 accessibility gate checklist
Owner: `client/` frontend, with admin/recovery evidence from `infra/`
Baseline: WCAG 2.1 AA for critical user and admin flows

This checklist is the release evidence index for issue #112. It does not replace live assistive-technology testing; it makes the automated and manual evidence required before v0.1 release explicit and auditable.

## Automated CI evidence

Run these before requesting release approval:

```bash
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

The automated suite must include widget or contract coverage for these accessibility-relevant behaviors:

| Flow | Required automated evidence | Current test anchor |
| --- | --- | --- |
| Sign-in/setup handoff | labeled setup/sign-in actions, keyboard-friendly form progression, readable endpoint review | `test/features/onboarding/setup_flow_test.dart`, `test/features/auth/sign_in_screen_test.dart`, `test/release_1/release_golden_paths_test.dart` |
| Main navigation/settings | accessible destination labels, visible text labels, safe sign-out and config recovery | `test/features/shell/app_shell_test.dart`, `test/features/settings/settings_screen_test.dart`, `test/release_1/release_golden_paths_test.dart` |
| Profile view/edit | labeled editable fields, required-field validation, disabled saving state, live-region save/error feedback | `test/features/profile/profile_summary_card_test.dart`, `test/features/profile/backend_user_profile_repository_test.dart` |
| Chat room list/message list/composer | room list labels, message timeline text, composer send action, retryable error copy | `test/features/chat/chat_screen_test.dart`, `test/features/chat/chat_room_screen_test.dart` |
| Files list/upload/download/status/error | readable connection states, file/folder labels, upload completion/error copy, breadcrumb navigation | `test/features/files/files_screen_test.dart`, `test/release_1/release_golden_paths_test.dart` |
| Calendar list/create/delete/status/error | readable event/state copy and backend-facade status, without direct CalDAV product bypass | `test/features/calendar/calendar_screen_test.dart` |
| Admin/status surfaces consumed by app | workspace capability/status labels and degraded/recovery states are text based, not color-only | `test/features/app/weave_app_backend_capability_flow_test.dart`, `test/features/settings/settings_screen_test.dart` |
| Shared controls | minimum target sizing and explicit semantic labels for reusable buttons | `test/core/a11y/semantic_button_test.dart`, `test/core/widgets/core_widgets_test.dart` |
| v0.1 baseline journey | setup, sign-in, chat, files, settings recovery, and gated workspace surfaces remain coherent in one flow | `test/release_1/release_golden_paths_test.dart`, `test/release_1/v0_1_release_spine_contract_test.dart` |

## Sprint 4 dogfood evidence gate

Sprint 4 adds work-room surfaces that must be usable with a screen reader, Braille display, keyboard, and high text scaling before they are called dogfood-ready. Each PR that changes these flows links this section, records the automated evidence it touches, and notes which manual rows still need live assistive-technology sign-off.

| Sprint 4 flow | Required evidence shape | Current automated anchor |
| --- | --- | --- |
| Weave Home | deterministic traversal across DMs, favorites, channels, and AI/Weaver areas; no raw provider setup copy in normal member paths | `test/features/chat/chat_screen_test.dart`, `test/release_1/ux_release_copy_contract_test.dart` |
| Channel Work Rooms | tab labels and status semantics for chat, decisions, files, boards, calendar, meetings, and Weaver; no color-only state | `test/features/chat/channel_workspace_test.dart`, `test/release_1/v0_1_release_spine_contract_test.dart` |
| Decision Ledger | text-first create/read flow with source references, lifecycle state, owner/time, and keyboard-reachable capture actions | `test/features/chat/decision_evidence_provider_test.dart`, `test/features/chat/chat_room_screen_test.dart` |
| Meeting Capsule | fail-closed join/start controls, consent/evidence copy, and clear Matrix-chat vs media-protection boundaries | `test/features/chat/channel_workspace_test.dart`, `test/release_1/v0_1_release_spine_contract_test.dart` |
| Weaver Scout | read-only/proposal-only status, citable source list, support-safe failure copy, and explicit approval-receipt requirement | `test/features/chat/channel_workspace_test.dart`, `test/release_1/ux_release_copy_contract_test.dart` |

## Sprint 9 product-readiness accessibility scope

Sprint 9 treats setup, provider switching/report review, Calls/LiveKit readiness, Weaver approvals, and member capability states as release-blocking accessibility flows. Automated evidence must prove keyboard-reachable controls, visible labels, support-safe status text, and no color-only readiness states; manual evidence must cover screen-reader traversal before an RC can be promoted.

| Sprint 9 flow | Required evidence shape | Current automated anchor |
| --- | --- | --- |
| Admin setup and domain registry review | keyboard-accessible setup progression, stable domain labels, and no raw provider setup in member paths | `test/features/onboarding/setup_flow_test.dart`, `test/release_1/v0_1_release_spine_contract_test.dart` |
| Provider switching and report review | dry-run, lossy, conflict, rollback, member-impact, and blocked-apply states are reachable and text-first | `admin-console/src/App.test.tsx`, `server/src/test/java/com/massimotter/weave/backend/controller/AdminControlPlaneControllerTest.java` |
| Calls/LiveKit readiness | join/start states fail closed with labeled controls and honest media/E2EE readiness copy | `test/features/chat/channel_workspace_test.dart`, `docs/meeting-architecture-decision.md` |
| Weaver approvals | group enablement, tool approval, member opt-in, and unauthorized-tool blocks are announced and audit refs are not color-only | `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java` |
| Member capability states | provider-neutral states are exposed as semantic text and never require provider diagnostics | `test/features/settings/settings_screen_test.dart`, `test/features/app/weave_app_backend_capability_flow_test.dart` |

## Sprint 10 manual accessibility closure scope

Sprint 10 release closure requires an explicit manual assistive-technology result or a named temporary waiver. Green widget/Admin Console tests are not sufficient to claim manual screen-reader, keyboard-only, or text-scaling completion.

Current Sprint 10 artifact: [manual accessibility evidence waiver](evidence/sprint-10-manual-accessibility-waiver.md). The waiver is temporary, owner-bound, scoped, and expires before v0.1 RC promotion unless replaced by real manual evidence.

| Sprint 10 flow | Required manual evidence shape | Current artifact |
| --- | --- | --- |
| Admin Console provider apply and recovery | Keyboard-only traversal reaches category, adapter, dry-run, consequence confirmation, readiness test, replacement dry-run evidence, and blocked/enabled apply state without pointer-only steps. | Waived pending live AT execution: `docs/evidence/sprint-10-manual-accessibility-waiver.md` |
| Fresh/stale apply evidence messaging | Screen reader announces fresh vs stale dry-run evidence, missing gates, consequence confirmation requirement, member impact, rollback/support boundary, and apply status as text. | Waived pending live AT execution: `docs/evidence/sprint-10-manual-accessibility-waiver.md` |
| Admin Console 200% text scaling | Provider apply gates, consequence confirmation, evidence refs, and recovery copy remain readable and operable at 200% zoom/text scaling. | Waived pending live AT execution: `docs/evidence/sprint-10-manual-accessibility-waiver.md` |
| Baseline member/admin critical traversal | Existing release-gate rows remain passed or have linked release-blocking issues; no pass may be inferred from automated tests alone. | Waived pending live AT execution: `docs/evidence/sprint-10-manual-accessibility-waiver.md` |

## Sprint 11 provider-reality accessibility replacement scope

Sprint 11 must replace, not extend, the Sprint 10 waiver before v0.1 RC promotion. The working evidence template is [Sprint 11 manual accessibility evidence template](evidence/sprint-11-manual-accessibility-evidence-template.md); it is not pass evidence until completed with real tester, date, platform/browser, assistive technology, result, evidence link, and any linked release-blocking issue.

Release evidence distinguishes live-runtime checks from offline/spec checks: `e2e/scenario_mappings.json` marks credentialed provider-reality scenarios as `live-runtime`, while this accessibility gate and the Sprint 11 manual evidence template remain offline release-accounting artifacts until live assistive-technology execution fills them.

| Sprint 11 flow | Required replacement evidence shape | Current artifact |
| --- | --- | --- |
| Admin setup and provider category review | Keyboard-only and screen-reader traversal reaches identity, chat, files, documents, calendar, boards, calls, and Weaver readiness without pointer-only steps or raw provider setup leakage to members. | Pending execution: `docs/evidence/sprint-11-manual-accessibility-evidence-template.md` |
| Provider apply and recovery gates | Category, adapter, dry-run, consequence confirmation, readiness test, replacement evidence, blocked apply, enabled apply, rollback/support boundary, and fresh/stale evidence states are reachable and announced as text. | Pending execution: `docs/evidence/sprint-11-manual-accessibility-evidence-template.md` |
| Member domain provider reality | Files, Calendar, Boards, Calls, and Documents states are announced as available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later with support-safe fallback copy. | Pending execution: `docs/evidence/sprint-11-manual-accessibility-evidence-template.md` |
| Admin Console and member 200% text scaling | Provider apply gates, evidence refs, recovery copy, and member domain state cards remain readable and operable at 200% zoom/text scaling. | Pending execution: `docs/evidence/sprint-11-manual-accessibility-evidence-template.md` |

## Manual assistive-technology evidence required before release sign-off

Record the tester, date, platform, assistive technology, result, and evidence link in the release issue or runbook. Manual-only checks are a gate; they may not be inferred from green widget tests.

| Flow | Mobile screen reader | Desktop keyboard/screen reader | Text scaling | Required result |
| --- | --- | --- | --- | --- |
| Sign-in/setup handoff | VoiceOver or TalkBack reads every field, action, and endpoint review row | Keyboard can complete setup and sign-in handoff without pointer-only steps | 200% text does not hide required actions | Pass or release-blocking bug |
| Profile view/edit | Fields and validation errors are announced; save success/failure is announced | Tab order follows display name → locale → timezone → save | Long display names/locales do not clip critical controls | Pass or release-blocking bug |
| Main navigation/settings | Bottom navigation labels are announced and current destination is clear | Navigation and settings actions are reachable by keyboard | Settings cards remain readable | Pass or release-blocking bug |
| Chat room list/message list/composer | Room names, message authors/content, composer, and send are announced | Keyboard can open a room and send a message | Message list/composer remain usable | Pass or release-blocking bug |
| Weave Home and Channel Work Rooms | Home sections, channel tabs, capability states, and active tab changes are announced without preview/provider setup vocabulary | Keyboard can move through Home sections and switch channel work-room tabs without pointer-only steps | Tabs and cards remain readable and do not hide critical status | Pass or release-blocking bug |
| Decision Ledger, Meeting Capsule, and Weaver Scout | Decision records, meeting fail-closed controls, and Weaver source/receipt requirements are announced with enough context to act safely | Decision capture, meeting controls, and Weaver tab content are reachable in deterministic order | Ledger, capsule, and scout cards remain readable at 200% | Pass or release-blocking bug |
| Files list/upload/download/status/error | File/folder rows, breadcrumbs, upload/download states, and errors are announced | Keyboard can navigate folders and trigger file actions | File rows and breadcrumbs remain usable | Pass or release-blocking bug |
| Calendar list/create/delete/status/error | Calendar states/events/actions are announced without color-only status | Keyboard can reach event actions where enabled | Event rows/forms remain usable | Pass or release-blocking bug |
| Admin/status/recovery copy | Degraded/offline states and recovery instructions are understandable | Recovery/status actions are keyboard reachable | Diagnostic copy remains readable | Pass or release-blocking bug |

## Non-negotiable pass criteria

- All critical interactive controls have visible text or an explicit accessible label.
- Icon-only controls must have semantic labels.
- Status, validation, and failures are never communicated by color alone.
- Form fields have visible labels and user-facing validation copy.
- Loading, empty, error, and success states are readable by screen readers.
- Critical controls remain reachable and operable with keyboard on desktop targets.
- Critical mobile flows remain usable with VoiceOver or TalkBack.
- Text scaling to 200% must not block sign-in, profile edit, chat send, files navigation, calendar basics, or recovery paths.

## Release accounting

- Automated evidence is attached by CI run URL and local command output.
- Manual evidence is attached to the release gate issue with platform/device notes.
- Any failed row creates or links a blocking issue and prevents release sign-off until fixed or explicitly deferred by product decision.
- Calendar and admin/recovery rows may remain marked `blocked` only when the corresponding backend/infra live-stack gate is also explicitly blocked; do not call current release accessibility complete while those flows lack evidence.

## Sprint 12 permanent release-promotion gate

Accessibility evidence is now a release-promotion gate, not a waiver pattern. The machine-readable gate summary is `release/accessibility-gate.json`; manual evidence uses `docs/evidence/accessibility/sprint-12-manual-at-template.md`.

Critical flows fail promotion when required evidence is missing, when a blocker is unresolved, or when a waiver is expired. Waivers are exceptional, must link to a GitHub issue, must name an expiry, and cannot silently pass RC promotion.

The gate maps Flutter semantics, keyboard traversal, text-scale/reflow, Admin Console route/interactions, and manual assistive-technology observations to release flows: provider setup, provider migration dry-run/member impact preview, identity offboarding/ownership transfer, and Matrix Chat E2EE recovery/cannot-decrypt states.
