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

The local graphical Aqua session and native Flutter target are present. After
the Xcode approval, a local macOS Accessibility query reported
`AXIsProcessTrusted() == true`; Xcode's UI runner also starts successfully.
The separate ChatGPT Computer Use
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
its native callback completes. The exploratory `NativeAcceptance` XCUITest
scheme now retains only an optional native-window launch probe. Flutter's
existing integration test remains the product oracle.

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
suggested that Safari had exposed the IdP. That experimental driver required the
exact disposable IdP authority in whichever permitted application owns the
foreground, then posts account, Tab, password, and Return through macOS native
keyboard events. An unverified authority or a foreground change fails before
credential entry. The real Flutter callback and product assertions still
determine success; a focus change or failed submission must fail the run.

The installed `flutter_appauth` 12.0.0 macOS source confirms the mechanism:
`AppAuthMacOSAuthorization` passes `NSApplication.keyWindow` to
`OIDExternalUserAgentMac`, which starts `ASWebAuthenticationSession` with that
window as its presentation anchor. Safari WebDriver's tab inventory and a
standalone Safari XCUITest target are therefore insufficient to identify the
native AppAuth sheet. Candidate `f37e238909` still did not expose the full
disposable authority in the foreground application's accessibility tree. The
driver now records only bounded, non-secret host, consent, and field-presence
booleans, while Flutter reports its auth failure category separately. No
callback or native product claim follows from those diagnostics.

The exact `4d88d792c5` run reported `host=false`, `consent=false`, and
`fields=false` in the foreground Weave accessibility tree. Flutter had tapped
its sign-in finder, but 15 seconds later the auth controller was idle with no
failure. That means the earlier `appauth-requested` marker did not prove that
the tap reached an enabled on-screen button. The test now scrolls the control
into view, verifies it is enabled, records the resulting controller state, and
fails if no OIDC transition occurs. It emits `appauth-requested` only after
observing the busy auth state.

Candidate `947e33b121` then observed `busy=true` throughout native OIDC, but
XCTest never saw a permitted foreground owner. The runner now passes the exact
compiled Flutter executable path through its private fixture. XCTest may make
one normal macOS activation request for that running executable, and records
whether the request succeeded. It does not activate another installed Weave
copy or enter credentials until the disposable IdP is visible in the
foreground application.

Candidate `40b31e1bb2` confirmed the exact compiled Weave app activated and
Flutter entered the production AppAuth request (`busy=true`), but the browser
driver still found no IdP page. A local screen capture showed the missing
first-use macOS consent: “weave” wanted to use `auth.weave.localhost` to sign
in. This is a system `ASWebAuthenticationSession` prompt, not a Keycloak form.
The foreground owner could be `UserNotificationCenter` or Weave depending on
notification focus. A read-only native Accessibility inspection identified a
`com.apple.UserNotificationCenter` window containing both the exact test host
and app name, with exactly one `Fortfahren` button. XCTest's
`XCUIApplication.debugDescription` did not expose that hosted prompt. The
exact `40b31e1bb2` run failed with `passed=0 failed=1 skipped=0`.
Candidate `5d48bf338f` then compiled a direct Accessibility action into
XCTest, but its full run again failed at the issuer window. A separate,
passed Xcode startup probe reported `NATIVE_XCTEST_AX_TRUST status=untrusted`:
Xcode's UI automation grant does not grant direct Accessibility access to the
isolated `RunnerUITests-Runner` process. A local diagnostic process with
Accessibility access verified the host, app name, and unique Continue button
and pressed that exact element. The AppAuth request then failed with a platform
exception before callback; this diagnostic does not count as automated test
coverage. The native runner now starts the same narrow system-consent check as
a separate process alongside XCTest. It records whether the prompt was
accepted, absent, or inaccessible. Flutter's callback and product assertions
remain the acceptance oracle. The next run also records sanitized AppAuth SDK
error codes so the remaining browser failure can be classified.

Candidate `21d6452550` made the system consent action reproducible
(`NATIVE_MACOS_CONSENT_RESULT status=accepted`) and exposed the first product
defect. AppAuth returned OAuth authorization code `-6`; the disposable
Keycloak log independently recorded `LOGIN_ERROR` with invalid requested
scopes `openid profile email offline_access weave:workspace`. The accepted
identity specification defines `weave:workspace` as a non-requestable default
client scope, and the already passing Java browser journey requests only
`openid profile email`. The native Flutter client now requests that same
three-scope set. Keycloak must still put the exact `weave:workspace` scope in
the issued token, and the native journey must prove refresh and session
restoration using its real refresh token. The OIDC service test now asserts
the literal requested scope set so the generated client cannot act as its own
oracle. This correction has passed the focused Flutter test and static
analysis; the complete native run remains unverified. Candidate `8da7d75ea4`
reached the real Keycloak page, where a screen and Accessibility inspection
showed separate username and password steps. The prior XCUITest driver had
assumed one form and used global keyboard events, so the attempt was stopped
before claiming or continuing credential entry. The native runner now passes
the disposable member to a scoped macOS Accessibility helper over its private
pipe. That helper must find the exact current IdP authority in Safari, fill
the observed username and password fields separately, and submit the real
forms. An unrelated system prompt or missing/ambiguous field blocks input.
No Flutter token, callback URL or mocked OIDC response is supplied. This new
driver has not yet passed a full live run. The first two exact runs against it
(`db5f126c97`, `9d21a11dc0`) failed closed before credential entry. The
second run identified `app-not-observed`: a separate Accessibility process saw
the exact compiled Weave app while the helper, started earlier, kept polling
an unchanged `NSWorkspace.runningApplications` snapshot. Safari also showed a
login page from the prior disposable IdP port, which the exact-authority guard
correctly rejected. The runner now waits for this checkout's native app process
before starting the helper; the helper runs AppKit's event loop between polls
so application and browser lifecycle notifications are processed. This fix
requires a fresh exact local run before it can count as acceptance evidence.
The aborted AppAuth session also left the system's first-use consent window
after its app process stopped. A scoped pre-run cleanup now requires no Weave
app to be running and cancels only a window containing the disposable IdP host
and Weave app name with one Cancel button. It was exercised locally and
reported `NATIVE_STALE_CONSENT_RESULT status=cleared`; it does not authenticate
or stand in for product evidence.

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
the disposable member's login only through a private named pipe to the native
browser helper.
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
calling the native runner. The runner builds the macOS target, starts the
existing Flutter product integration test, and drives the system browser with
the narrowly scoped native helper. It requires both browser-form completion
and Flutter product markers to pass. The code path is
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

- Execute the native Accessibility driver against the actual
  `ASWebAuthenticationSession` prompt and two-step IdP form. Flutter must
  observe the real AppAuth callback and authorized product state; a browser
  submit marker alone cannot qualify the journey.
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
