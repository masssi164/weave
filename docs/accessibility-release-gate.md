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

## Manual assistive-technology evidence required before release sign-off

Record the tester, date, platform, assistive technology, result, and evidence link in the release issue or runbook. Manual-only checks are a gate; they may not be inferred from green widget tests.

| Flow | Mobile screen reader | Desktop keyboard/screen reader | Text scaling | Required result |
| --- | --- | --- | --- | --- |
| Sign-in/setup handoff | VoiceOver or TalkBack reads every field, action, and endpoint review row | Keyboard can complete setup and sign-in handoff without pointer-only steps | 200% text does not hide required actions | Pass or release-blocking bug |
| Profile view/edit | Fields and validation errors are announced; save success/failure is announced | Tab order follows display name → locale → timezone → save | Long display names/locales do not clip critical controls | Pass or release-blocking bug |
| Main navigation/settings | Bottom navigation labels are announced and current destination is clear | Navigation and settings actions are reachable by keyboard | Settings cards remain readable | Pass or release-blocking bug |
| Chat room list/message list/composer | Room names, message authors/content, composer, and send are announced | Keyboard can open a room and send a message | Message list/composer remain usable | Pass or release-blocking bug |
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
