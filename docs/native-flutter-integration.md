# Native Flutter integration acceptance for #1533

Status: **local native journey passed; CI and remaining lifecycle gates open**.
This record separates executable test code from observed native product
evidence. A disposable local `testApp` run at source commit
`6b7006425cfa3e58504fceeca6ffaa99604add58` completed the real AppAuth
callback, generated Files and Calendar operations, native Rust Matrix business
room send/read, refresh, a restart of Server, Keycloak and PostgreSQL, and a
second native Flutter process restoring the same session and references. It
also proved logout denial and supported OpenClaw Matrix readback.
Both Flutter test processes and the overall Gradle task exited zero. This is local
macOS evidence; the #1475, #1479, and #1480 integrated closure gates still
require the exact CI candidate and the lifecycle gaps below.

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
Candidate `1184a0c07e` started the helper after the native app was visible and
accepted the system consent, but failed `issuer-not-observed`: Accessibility
still exposed only Safari's abandoned auth window for an earlier disposable
port. The current run's issuer port never appeared, so no credential was
entered. Pre-run cleanup now closes only a Safari window titled `Sign in to
weave` that contains the disposable IdP host and port while no Weave app is
running. The standalone cleanup returned `NATIVE_STALE_BROWSER_RESULT
status=closed`; a full fresh AppAuth callback remains unverified.
Candidate `1a812db673` again accepted macOS consent but found only the prior
disposable IdP port. Safari had changed that stale window's title to a page-load
error, so the title-scoped cleanup missed it. Cleanup now matches the exact
disposable IdP host with a port while no Weave app runs, independent of Safari's
transient title. The native Flutter test also reports its configured issuer
port as a sanitized diagnostic, allowing a future run to distinguish stale
browser state from a stale Flutter build. No credential was entered in either
failed run.
Candidate `6ac5fa1f78` closed that stale page before launch, accepted macOS
consent, and matched Flutter's reported issuer port to the new Safari auth
window. The driver found the username field, then failed at immediate value
readback before form submission. A harmless probe on the abandoned page showed
WebKit applies `AXUIElementSetAttributeValue` asynchronously: the old field
value was returned immediately and the new value after 0.5 seconds. The driver
now waits up to two seconds for the username value. It never logs or reads the
password value; the live IdP and Flutter callback remain the outcome checks.
Candidate `476054e872` submitted the real username step and reached Keycloak's
password page (`NATIVE_AUTH_STAGE phase=username-submitted`). That page exposes
two Accessibility text fields: one unlabeled field and one uniquely titled
`Password`. The previous single-field assumption timed out with
`password-not-observed`; no password was entered. The driver now selects a
uniquely labeled password control inside the verified current IdP window.
Candidate `bf805d45e3` submitted both real Keycloak steps, but Flutter failed
after callback while saving its session. A focused native Flutter probe using
the same secure-storage plugin and a disposable non-secret value reproduced
Keychain OSStatus `-34018` (`A required entitlement isn't present`). The app's
ad hoc debug signature lacked Keychain access groups. Both macOS entitlement
files now declare that capability. The project placeholder bundle ID
`com.example.weave` cannot be registered to the available development team,
so the local test uses a dedicated configurable signing bundle ID and Apple
Development certificate. The signed native Keychain probe then passed; the
built app's TeamIdentifier, bundle ID, and Keychain entitlement were verified.
This is a test signing configuration, not a change to the public OIDC contract.
The full OIDC product journey has not yet passed with this signed build.
Candidate `41f4130dc1` built with verified development signing and Keychain
entitlement, but Safari exposed an empty Accessibility child tree for the
current IdP web area (`username-not-observed`), despite the page being visible.
The form was not submitted. On that expired disposable page, a harmless probe
showed that focusing the verified Safari web area, pressing Tab, and sending
Unicode keyboard events directly to Safari's process focused the labeled
username field and entered the probe; the focused element and window were
readable through Accessibility. The driver now uses this targeted input path
only when the normal uniquely labeled form control is absent. It still
requires the exact current issuer window and focused field label, and Flutter
still determines whether the AppAuth callback and product journey pass. This
fallback has not yet passed a live full run.
Candidate `3c7dd1e58c` completed both live Keycloak form steps through the
signed native app, and Flutter emitted `phase=workspace-ready` after the real
AppAuth callback. The native integration test then failed during its product
assertions, with no `NATIVE_PRODUCT_SIGN_IN_RESULT` pass marker. The runner had
retained only broad milestones, so the exact failing assertion was unavailable
from that attempt. The product test now emits support-safe Files, Calendar,
Matrix, navigation, refresh, session restoration, and logout phase markers;
the runner retains only source line numbers from Flutter failures. This is
diagnostic evidence, not a passed native product test.

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
WEAVE_NATIVE_SIGNING_TEAM="<10-character Apple development team ID>" \
WEAVE_NATIVE_SIGNING_BUNDLE_ID="<provisioned test app bundle ID>" \
WEAVE_TEST_APP_NATIVE_RUNNER="$PWD/client/tool/run_native_product_acceptance.py" \
WEAVE_TEST_APP_RELEASE_MCP=true \
  ./gradlew testApp
```

`testApp` requires a clean exact source candidate and tears down the disposable
stack. Its Java browser proof creates and admits the member before calling the
native runner. The runner builds and signs the macOS target, starts the existing
Flutter product integration test, and drives the system browser with a narrowly
scoped native helper. It requires browser form completion, Flutter product
markers, and a zero Flutter test exit. At `8f13cde956`, the local run emitted
`NATIVE_PRODUCT_SIGN_IN_RESULT status=passed`,
`FLUTTER_NATIVE_TEST_RUN status=passed`, and
`NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed`, followed by
`BUILD SUCCESSFUL in 7m 9s` for `testApp`. The `Full Compose E2E` job now selects
this same native runner and fails when its signing/CA prerequisites are absent;
its first run on `f657aacbe922828e29391999c50ca6bc92d39474`
([workflow 38010551714](https://github.com/masssi164/weave/actions/runs/38010551714))
passed the isolated stack and OpenClaw Matrix proof but failed before the
native app build in the stale-consent driver. That run discarded the driver's
specific failure stage. The next candidate reports that bounded stage and
checks Accessibility trust in the runner process before starting the stack.
The subsequent local run at `d6395ab177` emitted
`NATIVE_PRODUCT_INITIAL_RESULT status=passed`, two successful Flutter harness
exits, `NATIVE_PROCESS_RESTART_RESULT status=passed`, and
`NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed`. `testApp` exited zero in
10m 41s. Its support-safe evidence records specification commit
`c726993168651f1109259f9a80cc23117d24a37f`, OpenClaw client 2026.9.8,
real Files/Calendar invocation, PostgreSQL restart, and revocation denial.
The first CI run on that same commit
([workflow 38012031290](https://github.com/masssi164/weave/actions/runs/38012031290))
failed in the new preflight with `NATIVE_ACCESSIBILITY_RESULT status=denied`;
the disposable stack was not started. macOS TCC attributes the denied
`kTCCServiceAccessibility` request to the runner's Node executable at
`actions-runners/weave-live-mac-mini/externals.2.337.0/node20/bin/node`.
The Xcode authorization given to its UI test runner does not cover this
separate CI process. Accessibility for that exact runner executable is the
current host prerequisite; Screen Recording and Apple Events are not required
by the test driver. The CI run remains failed until a fresh unattended run
completes the native app assertions.
The next local run at `6b7006425cfa3e58504fceeca6ffaa99604add58`
reused the existing isolated collaboration-service restart control between
the two Flutter processes. It emitted
`NATIVE_SERVICE_RESTART_RESULT status=passed` after Server, Keycloak and
PostgreSQL restarted and returned healthy. The second Flutter process restored
the same member, Matrix device, business-room message, stable file ID and
downloaded file bytes without another browser sign-in. It then proved logout
denial. Both Flutter harness processes exited zero;
`NATIVE_PROCESS_RESTART_RESULT status=passed` and
`NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed` were emitted, and `testApp`
exited zero in 11m 46s. The support-safe evidence file records candidate
`6b7006425cfa3e58504fceeca6ffaa99604add58` and specification
`c726993168651f1109259f9a80cc23117d24a37f`. This local result does not
replace the pending exact-candidate CI run. The selected `weave-native` Chat,
Files and Calendar adapters use durable Weave state and have no external
provider session to lose. The result truthfully records
`southboundProviderDependencyObserved=false`; it does not claim an external
provider outage or recovery proof. The accepted release contract keeps recovery
binding when an external provider capability is enabled. Current deployment
configuration blocks optional Synapse and Nextcloud activation pending their
separate qualification, so enabling them just to manufacture this test would
violate that gate. The proposed [#1480 acceptance clarification](https://github.com/masssi164/weave/issues/1480#issuecomment-6092353586)
records the conditional downstream-session criterion without treating an
unrun external-provider test as passed.

Sanitized terminal markers from that local run:

```text
NATIVE_PRODUCT_INITIAL_RESULT status=passed login=single files=generated-upload-read calendar=generated-crud matrix=native businessRoomSendRead=true refresh=true sessionReopen=true appStateRecreated=true checkpointPrivate=true supportSafe=true
FLUTTER_NATIVE_TEST_RUN status=passed
NATIVE_SERVICE_RESTART_RESULT status=passed backend=healthy keycloak=healthy postgres=healthy supportSafe=true
NATIVE_PRODUCT_STAGE phase=process-restart-restored
NATIVE_PRODUCT_STAGE phase=logout-denial-passed
FLUTTER_NATIVE_TEST_RUN status=passed
NATIVE_PROCESS_RESTART_RESULT status=passed
NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed
BUILD SUCCESSFUL in 11m 46s
```

The next exact-candidate CI run at `5ee1a4485e5921c9ac6317715e9f3972db1042b1`
([workflow 38013608116](https://github.com/masssi164/weave/actions/runs/38013608116))
failed at the same preflight before stack startup. A failed-job rerun (attempt
2) reproduced the denial at 03:52:55 local time. The macOS TCC attribution
identified the versioned GitHub runner Node executable as the responsible
process and returned `authValue=0`, `Denied (System Set)`. The exact executable
is `~/actions-runners/weave-live-mac-mini/externals.2.337.0/node20/bin/node`.
Launching the same Swift preflight from a local terminal, even through that
Node binary, passed because the responsible process attribution differed; it
does not establish the runner grant. Xcode's Accessibility grant is likewise
separate. The native product result remains local evidence and CI has not yet
executed the native test on this candidate.
The focused Flutter widget suites for sign-in, Files, Calendar, Chat list and
Chat room passed locally: 69 tests, zero failures. They exercise screen
semantics and representative loading, empty, error, revoked-access and session
states. This is widget evidence alongside the native journey; it is not a
claim that VoiceOver, iOS or Android behavior was exercised.
The signing team, development certificate, and provisioning profile must be
available to the logged-in macOS test user. CI reads the nonsecret signing team
and bundle ID from repository variables `WEAVE_NATIVE_SIGNING_TEAM` and
`WEAVE_NATIVE_SIGNING_BUNDLE_ID`; the same runner user must have the trusted CA
at `~/.local/share/weave/native-acceptance-ca`. The runner generates a temporary
Xcode configuration, verifies the signed bundle and Keychain entitlement, and
removes that configuration afterward. An ad hoc signed build cannot qualify
native session storage.

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

## Unattended macOS runner diagnosis, 2026-10-10

After the runner's Accessibility grant was applied, the exact candidate
`3c118bfc612be2c224981e90aa418016dccc46f6` reached Xcode but failed
framework `CodeSign` with `errSecInternalComponent` in [Full Compose E2E
38036936172](https://github.com/masssi164/weave/actions/runs/38036936172).
The runner process reported one valid Apple Development identity, so identity
discovery was insufficient to prove private-key use. A disposable LaunchAgent
using the same user, signing identity and `/usr/bin/codesign` reproduced the
failure with `SessionCreate=true` and signed the same test binary with that
field absent. The GitHub runner's LaunchAgent had `SessionCreate=true`; only
that field was removed and the service restarted after the owner approved the
security-session change. The original plist has a local reversible backup.
The public repository now requires maintainer approval for every external
contributor's PR workflow, and the `Full Compose E2E` job routes fork PRs to a
GitHub-hosted runner where it fails before checkout. A workflow check alone is
not the trust boundary because PR authors can edit workflows; the repository
approval setting is enforced by GitHub. A dedicated CI signing identity remains
preferable to a personal login keychain for long-term runner isolation.

On exact candidate `986c93ed3569d815b52caf5b7fb8a2423a8bd473`, [CI run
38037973487, attempt 2](https://github.com/masssi164/weave/actions/runs/38037973487)
reported `NATIVE_BUILD_PREPARATION_RESULT status=passed`,
`NATIVE_SIGNING_RESULT status=passed` and
`NATIVE_APP_LAUNCH_RESULT status=passed`. It then failed at
`NATIVE_APP_AUTH_DRIVER_RESULT status=failed stage=username-readback`, before
the product assertions. In the actual Safari Keycloak window, the username
field was empty after the driver's unfocused AXValue write. Focused input was
visible in accessibility readback. The driver now focuses the field, verifies
its exact value, and falls back to targeted keyboard input only when needed.

Local `./gradlew --no-daemon specCorpusConformance testApp` on exact commit
`d77bbc6d7a50a606bbf85776415d90927bfbfead` passed in 9m 17s with pinned
specifications `c726993168651f1109259f9a80cc23117d24a37f`. It used macOS
26.4.1, Xcode 26.5, Flutter 3.41.6, Keycloak 26.7.1 and OpenClaw 2026.9.8.
The disposable app emitted browser PKCE login, generated Files upload/read,
generated Calendar CRUD, native Rust Matrix business-room send/read, refresh,
second-process restoration after Server/Keycloak/PostgreSQL restart, room-leave
denial, logout denial, two zero-exit Flutter harness processes and
`NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed`. The stack and per-run secrets
were cleaned up. This is local evidence; the exact-head unattended CI rerun is
still required.

## Remaining gates

- Run the exact committed candidate in the protected self-hosted macOS `Full
  Compose E2E` lane, with native product marker and zero Flutter/Gradle exit.
  The prior local fixture `SemanticsHandle` failure is not accepted as a product
  result; the product journey unmounts its app before the harness finishes and
  passed locally. Fixture stability remains a separate diagnostic concern.
- Reconcile server-side revocation, wrong-account and cross-organization denial
  across the native and black-box lanes. The local two-process result proves
  native process and collaboration-service restart; logout denial does not
  prove server-side revocation. External downstream-session recovery becomes
  executable acceptance when such a provider is enabled; the current native
  release profile has no such session.
- Keep iOS and Android acceptance unclaimed. No iOS simulator is provisioned on
  the current host (`xcrun simctl list devices available` listed only the iOS
  runtime header), and the Android SDK is absent (`adb` and `emulator` were not
  on `PATH`). `flutter devices --machine` listed only macOS and Chrome. macOS
  evidence cannot replace platform-specific tests. The OIDC, generated API,
  authorization and server-side Matrix assertions are shared product behavior;
  browser handoff, callback delivery, secure storage and app lifecycle need
  separate native platform evidence before an iOS or Android claim.
