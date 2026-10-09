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
shell process still reports `AXIsProcessTrusted() == false`; that does not
prevent Xcode's UI runner from operating. The separate ChatGPT Computer Use
process still lacks its own Accessibility/Screen Recording grant and is not
needed for this lane. No Apple Events driver or additional broad permission is
part of the native acceptance runner.

The first XCUITest attempt inherited the app's provider link flags and could
not load `AppAuth.framework` in the UI runner. After isolating those flags and
using a fresh Xcode output directory, XCTest loaded but timed out in
`enableAutomationMode` before the test body ran. The owner then granted the
macOS automation request for Xcode. On the same Mac, XCUITest passed a real
Weave-window probe, a Safari-window probe, and a private one-use pipe probe
(one passed, zero skipped in each result). The repeated test after the grant
establishes a permission gate for Xcode's UI runner as the cause of the
automation-mode timeout.

A new checkout exposed another prerequisite: Xcode `build-for-testing` failed
before XCTest because Flutter's generated `FlutterInputs.xcfilelist` and
`FlutterOutputs.xcfilelist` and CocoaPods support file lists did not exist.
`flutter pub get` alone did not create all of them. `flutter build macos
--debug` prepared the native project, including the Rust Matrix dependency;
the same Xcode test build then passed. The native runner now performs that
preparation itself before starting XCTest and records a separate preparation
and Xcode build result. This is a checkout setup failure, not an AppAuth or
macOS activation result.

The first Safari form probe timed out while sending a URL through Safari's
application element. Its XCTest activity log identified that exact keystroke
operation. Opening a local HTTP form with `NSWorkspace` and then targeting its
accessibility text and password fields passed in 5.1 seconds (one passed,
zero skipped). This validates form-element interaction, but does not yet prove
that AppAuth's actual authentication window exposes the same elements or that
its native callback completes. The retained `NativeAcceptance` XCUITest scheme
is a browser driver; Flutter's existing integration test remains the product
oracle.

On exact candidate `7f7db0fe20`, the fresh stack, browser PKCE preparation,
macOS Flutter build, Xcode test build, and XCTest fixture transfer all passed.
Flutter launched and tapped sign-in. XCTest observed the disposable IdP URL and
focused Safari, then the `safari.webViews.textFields` query did not return
before the bounded driver timeout. Flutter's workspace assertion also failed.
This does not prove a credential or callback defect. Candidate `daae77f117`
also timed out: XCTest spent several minutes in a Weave-window accessibility
lookup, then observed the browser but blocked on `XCUIApplication.typeText`.
Candidate `d66b66d833` removed the preliminary window lookup, but its
Safari-only authority check failed: the observed foreground process was Weave,
consistent with AppAuth attaching its system authentication sheet to the
requesting application. It entered no credentials. The earlier `XCTAssertTrue`
check had continued after failure, so its subsequent stage marker had falsely
suggested that Safari had exposed the IdP. The current driver requires the
exact disposable IdP authority in whichever permitted application owns the
foreground, then posts account, Tab, password, and Return through macOS native
keyboard events. An unverified authority or a foreground change fails before
credential entry. The real Flutter callback and product assertions still
determine success; a focus change or failed submission must fail the run.

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

The optional `WEAVE_TEST_APP_PUBLIC_DOMAIN=weave.localhost` profile is for
native macOS runs. This host resolves directly to loopback on this Mac;
`weave.test` currently resolves to a LAN address, and `.local` resolution was
slow enough to time out a native request. The profile changes only disposable
stack hostnames and a per-run TLS leaf signed by a dedicated native acceptance
CA. That CA needs one macOS SSL trust approval before automated runs; a direct
per-run trust import timed out at a separate Keychain approval prompt. The
runner verifies the dedicated CA is already trusted and leaves the trust store
unchanged during tests. It gives Flutter a per-run Keychain account and passes
the disposable member's login only through a private named pipe to XCTest.
No credential is passed as a Flutter define.

## Required execution lane

The native lane needs a logged-in graphical macOS session, the exact disposable
`testApp` stack and dedicated member identity, trusted test CA and host routing
for AppAuth, and a driver that operates the actual system authentication window
and returns through AppAuth. The optional local command is:

```sh
python3 client/tool/setup_native_acceptance_ca.py --trust  # one-time macOS approval
WEAVE_SPEC_CORPUS_ROOT=/absolute/path/to/pinned/weave-specs-worktree \
WEAVE_TEST_APP_PUBLIC_DOMAIN=weave.localhost \
WEAVE_TEST_APP_NATIVE_CA_ROOT="$HOME/.local/share/weave/native-acceptance-ca" \
WEAVE_TEST_APP_NATIVE_RUNNER="$PWD/client/tool/run_native_product_acceptance.py" \
  ./gradlew testApp
```

`testApp` still requires a clean exact source candidate and tears down the
disposable stack. Its Java browser proof creates and admits the member before
calling the native runner. The runner builds the macOS UI target, lets XCTest
establish UI automation and consume the private member fixture, then starts the
existing Flutter product integration test and drives the native browser. It
requires both XCUITest and Flutter product markers to pass. The code path is
implemented but **has not yet completed a live local run**. The local attempt
at `9dc5c23d4a77` reached healthy Server/MCP and passed disposable Chromium
activation and generated User Files/Calendar before the native XCUITest driver
failed. A following exact attempt at `f871a964d1` reached the same native stage
but Xcode timed out while a stale Weave app process from the prior attempt
remained alive. The runner now terminates only orphaned apps from its checkout
and records sanitized Flutter/XCTest milestones. These are diagnostic fixes,
not native product acceptance evidence. The `Full Compose
E2E` job runs backend/Chromium and Matrix protocol evidence. No CI job
currently runs the real native AppAuth/product case.

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

- Execute the XCUITest driver against the actual `ASWebAuthenticationSession`
  prompt, IdP form and callback. Adjust selectors from that observed window;
  the standalone Safari form probe alone cannot qualify AppAuth.
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
