# Native Flutter integration acceptance for #1533

Status: **open**. This record separates executable test code from observed
native product evidence. The #1475, #1479, and #1480 native acceptance gates
remain open until an automated macOS run completes the real AppAuth callback
and product journey on one disposable candidate.

## macOS launch diagnosis

On macOS 26.4.1 with Flutter 3.41.6, Dart 3.11.4, Xcode 26.5, and
`flutter_appauth` 12.0.0, `flutter test
integration_test/system_browser_auth_e2e_test.dart -d macos --verbose` built the
native app and connected to its VM service and integration-test harness. Flutter
then printed `Failed to foreground app; open returned 1`. Capturing the stderr
of Flutter's own `open <weave.app>` call identified the LaunchServices error:

```text
_LSOpenURLsWithCompletionHandler() failed with error -600.
```

The macOS SDK defines `-600` as `procNotFound` (no eligible process for the
descriptor). Flutter invokes `open` after attaching to the running app, without
waiting for LaunchServices to register it. An immediate standalone `open` after
the run succeeded. The available evidence supports an app-registration timing
failure in Flutter's best-effort foreground request; it does not establish an
application startup crash or a missing permission as its cause. The macOS
Makefile lane now places a narrow `open` wrapper on Flutter's `PATH`. It retries
only this error for up to two seconds and fails on other errors. The wrapper
does not change AppAuth or fabricate a successful sign-in. A successful
foreground request cannot prove that the authentication browser was usable.

The decisive false success was test configuration: all five native tests used
opt-in flags, so the invocation ended with `All tests skipped` and exit code 0.
The integration test now has an always-run guard that fails when no live journey
is selected. The product and Matrix mutation journeys also require the
disposable-stack flag before they can run. A native app build, successful VM
attachment, or all-skipped run is never reported as product acceptance.

An independent native fixture test, `integration_test/shell_navigation_e2e_test.dart`,
then ran on macOS. Its first run exposed a stale calendar test expectation:
the fixture advertised no create right, while the test expected an enabled
create action. The fixture now advertises the right and checks the actual
localized button is enabled. Both native fixture tests passed. Their
`NATIVE_SHELL_UI_RESULT` marker records `platform=macos` and
`evidenceMode=fixture-ui`; it is not OIDC, provider, or product acceptance.
An experimental `Native macOS Flutter fixture` CI job on
`915648ea9b51a11f8e57b8e0b2dce5dd9b4ea793` built and ran the native app,
then failed because the first test left a `SemanticsHandle` active. Both
fixture cases now unmount `WeaveApp` before test completion. On this Mac, the
unchanged fixture suite failed once with the same handle assertion and then
passed after a diagnostic run; instrumentation showed a constant handle count
while navigating the six screens. The cause of this intermittent failure
remains unresolved. The CI job was removed from the current candidate:
fixture execution cannot close the live AppAuth/product gate, and the native
product journey must pass reproducibly on this Mac before moving to CI.

## Authentication automation boundary

The Flutter test taps the actual Weave sign-in control. Production
`FlutterAppAuthOidcClient` invokes `flutter_appauth`'s native
`ASWebAuthenticationSession`; macOS assigns its callback to the requesting app.
The existing Compose E2E Playwright browser proves invitation, activation,
Authorization Code/PKCE, and backend behavior, but its Chromium session is not
the native AppAuth session. Passing a token, callback URL, or mocked OIDC client
to Flutter would bypass the required integration boundary.
Safari WebDriver is also insufficient for the native callback: Apple confines
its WebDriver sessions to isolated automation windows, while AppAuth asks the
system browser to open a separate authentication session. See
[Apple's Safari WebDriver isolation contract](https://developer.apple.com/documentation/safari-developer-tools/webdriver).

The local graphical Aqua session and native Flutter target are present. The
shell process reports `AXIsProcessTrusted() == false`; a separate ChatGPT
Computer Use attempt reported pending Accessibility and Screen Recording
permission. These facts constrain desktop UI automation from those processes.
They do **not** show that Flutter's test runner or `open` needs those
permissions. A future XCUITest UI driver would require Accessibility for its
actual Xcode Helper process, as described in [Apple's UI automation guidance](https://developer.apple.com/documentation/xcuiautomation/recording-ui-automation-for-testing).
Screen Recording is only needed if that driver
captures pixels. Apple Events permission would only be needed by an Apple
Events based driver. No broad host permission should be requested in lieu of
an executable native test.

A temporary XCUITest UI target was also probed locally and then removed. The
first attempt inherited the app's provider link flags and could not load
`AppAuth.framework` in the test runner. After isolating the UI test link flags
and using a fresh Xcode output directory, the runner loaded XCTest, but
`xcodebuild test` failed after 72 seconds with `Timed out while enabling
automation mode`. A process sample placed the wait inside
`XCUIInitializeForUITesting` → `enableAutomationModeWithError`; the test body
never ran and no new Weave process launched. The shell's Accessibility denial
is consistent with a UI automation permission boundary, but the exact TCC
decision for the XCUITest runner could not be read, so permission is not
claimed as the proven sole cause. No UI-test target or new framework is
included in this candidate.

## Existing executable coverage

`client/integration_test/system_browser_auth_e2e_test.dart` exercises the
production `WeaveApp`, AppAuth session repository, generated User API backed
Files and Calendar repositories, and native Rust/Matrix SDK. The product case
now asserts Files upload/list/download bytes and stable ID after session
reconstruction; Calendar agenda/create/read/update/delete; business-room
Matrix send/read and device retention; refresh, in-process app state reconstruction,
logout, and denial of Files, Calendar, and Matrix after logout. It requires a
disposable stack because it mutates provider data. These assertions are **test
implementation only** until a native run actually completes them.

The existing Gherkin mapping `@weave-live-auth-shell` in
`e2e/features/live_stack_app.feature` names
`PHYSICAL_AUTH_SESSION_RESULT` as native runtime evidence. No automated native
run has emitted that marker. Additional native product scenarios should be
mapped only when their driver executes the corresponding assertions and the
live evidence parser observes their markers. A Gherkin compatible Dart package
would not solve the macOS browser control or callback problem; the current
feature mapping and `integration_test` assertions remain the right structure.

## Required execution lane

The native lane needs a logged-in graphical macOS session, a fresh app profile,
the exact disposable `testApp` stack and dedicated member identity, trusted
test CA and host routing for AppAuth, and a driver that operates the actual
macOS system authentication browser and returns through AppAuth. It must run
`make -C client physical-device-product-e2e` with `WEAVE_PHYSICAL_DEVICE_ID=macos`
and the stack's endpoint variables, capture sanitized pass/fail markers, and
tear down the stack. The `Full Compose E2E` job runs backend/Chromium and
Matrix protocol evidence. No CI job currently runs the real native
AppAuth/product case.

Current reproducible diagnostic command:

```sh
cd client
flutter test integration_test/system_browser_auth_e2e_test.dart -d macos \
  --plain-name 'native acceptance requires an explicitly selected live journey'
```

Without a selected journey this command must exit nonzero. To claim native
product acceptance, the command must execute the product test with the
disposable-stack and endpoint defines, emit `NATIVE_PRODUCT_SIGN_IN_RESULT`,
and exit zero. CI must require that observed result; skipped tests, manual
interaction, and backend-only markers do not satisfy it.
This guard was executed locally on the current candidate: the native app built,
the test ran, and it exited 1 with `No native acceptance journey was selected`.

Native fixture smoke, which uses in-memory identities and repositories:

```sh
cd client
PATH="$PWD/tool/native_macos_open:$PATH" \
  flutter test integration_test/shell_navigation_e2e_test.dart -d macos
```

## Remaining gates

- Implement and execute a macOS driver for the actual
  `ASWebAuthenticationSession` prompt, IdP form, and callback without token
  injection. Validate any required TCC grant against the requesting process.
  Resolve the local XCUITest `enableAutomationMode` timeout before treating it
  as a viable driver.
- Resolve the intermittent native fixture `SemanticsHandle` assertion and
  demonstrate a repeatable local pass before transferring this lane to CI.
- Provision a fresh native profile and disposable identity alongside `testApp`,
  including trusted CA and local host routing. Run the journey locally first,
  then move the same passing lane to CI with cleanup and sanitized evidence.
- Exercise server-side revocation, wrong-account and cross-organization denial,
  recoverable downstream-session loss, and a real process restart in that native
  lane. Logout denial alone does not prove revocation; rebuilding `WeaveApp` in
  the same process does not prove process restart.
- Keep iOS and Android acceptance unclaimed. No iOS simulator is provisioned on
  the current host, and the Android SDK is absent; macOS evidence cannot replace
  platform-specific tests.
