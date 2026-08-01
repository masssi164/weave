# iOS dogfood distribution

Weave uses one stable iOS application identity for engineering builds, TestFlight builds, and later release builds: bundle identifier `com.massimotter.weave`, Apple team `KNDHGC2KV6`, and Keychain application identifier `$(AppIdentifierPrefix)com.massimotter.weave`. A normal app iteration must update in place. It must not delete the installed app, rotate the bundle identifier, or clear the saved Weave profile, OIDC refresh session, Matrix device ID, or Matrix crypto-store passphrase.

## Human tester channel

TestFlight is the preferred physical-iPhone dogfood channel. Testers install through Apple instead of trusting an Apple Development certificate on the device, and subsequent builds retain the application and Keychain identity. The development-signed profile runner remains an engineering fallback for local deeplink and LAN diagnostics. If that fallback requests repeated Developer App trust after an ordinary update, use TestFlight instead of asking the tester to repeat the trust action.

The GitHub `iOS Dogfood` workflow runs after a successful `Test Stack Deploy` for the exact `dogfood` lane commit, or by an explicit manual recovery dispatch that names the candidate and deployment run. It resolves the protected `dev` source commit from deployment evidence and consumes only the isolated Live Stack run URL recorded in that deployment's test-stack manifest; it never selects a merely recent successful run. Candidate-manifest digest and all four immutable runtime image references must agree across deployment, live automation, Simulator, and distribution evidence, while the isolated and persistent Compose namespaces remain deliberately distinct. Before any archive or physical-device installation, a fresh iPhone Simulator runs the five release-required shell tabs plus nested Profile (Home, Chat, Files, Calendar, Settings, and Profile) from that exact source. Simulator output is explicitly recorded as `fixture-ui`; the workflow combines it with the provider-backed Live Stack artifact, and never upgrades fixture repositories into identity, authorization, or provider evidence. Raw Flutter, Xcode and device-install output remains private in both the Simulator and stable-signing fallback lanes; only allowlisted support-safe markers, exact cleanup records and candidate-bound evidence are uploaded. The upload job uses the protected `ios-dogfood` environment.

Environment ownership is role-based and must be configured in GitHub:

- `dogfood`: Weave release owner or dogfood operator; an approval request expires after 24 hours and is then reported as blocked or superseded.
- `ios-dogfood`: Weave release owner plus the client/iOS release owner; an approval request expires after 24 hours and is then reported as blocked or superseded.

Superseded pending iOS candidates are cancelled through workflow concurrency while the newest dogfood candidate is preserved. A waiting review is never distribution success; readiness evidence records the environment, workflow run URL, commit, and required approver role. Configure required reviewers and these environment secrets:

- `APPLE_DISTRIBUTION_CERTIFICATE_P12_BASE64`
- `APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD`
- `APPLE_PROVISIONING_PROFILE_BASE64`
- `APPLE_PROVISIONING_PROFILE_NAME`
- `APP_STORE_CONNECT_API_KEY_ID`
- `APP_STORE_CONNECT_ISSUER_ID`
- `APP_STORE_CONNECT_API_PRIVATE_KEY_BASE64`
- `WEAVE_IOS_DEVICE_ID` for the protected development-signed fallback only

Create the App Store Connect app record and TestFlight tester group once. Configure automatic distribution for the intended internal tester group, or complete Apple's beta review before using an external tester group. The workflow validates the archive bundle/build identity, uploads with Apple's command-line tooling, and emits support-safe evidence without certificate, profile, API key, or member credential material.

The archive embeds and exposes the source candidate commit, version, build number, bundle identifier, and workflow evidence reference in support-safe Settings diagnostics. Distribution evidence separately records the `dogfood` lane commit and candidate-manifest digest. The physical acceptance gate must verify both identities before testing. Simulator archive or smoke results do not substitute for physical-iPhone VoiceOver acceptance, session continuity, system-browser authentication, or interaction.

## Tester-confirmed physical protocol

Physical outcomes are recorded only after the tester performs them. The protected
`Physical iPhone Human Test` workflow accepts a base64-encoded, support-safe
`weave.physical-iphone-human-submission.v1` document and validates it against the exact successful
deployment and iOS distribution runs. The submission contains no tester identity, email,
credential, token, private path, or raw provider identifier; it carries only a hashed tester
reference, aggregate VoiceOver/session/navigation statuses, and the structured protocol.

The protocol has exactly twenty rows: invitation receipt and open, Keycloak activation, signed app
launch, Authorization Code with PKCE, normal session, refresh, logout/relogin, Files UI, Calendar
UI, Calls UI, Weaver grant, MCP discovery, `files.search`, File resource open, revoke, immediate
rejection, regrant, restored access, and identity continuity. Every row records expected outcome,
actual outcome, UTC timestamp, status, and a support-safe evidence reference. A missing Calls or
other required UI capability is recorded as `blocked`; it is never converted into a pass.

The final `Human Testing Readiness` workflow accepts only the successful physical workflow run ID.
It downloads the immutable protocol artifact and verifies the entire live → deployment → iOS →
physical run graph before emitting schema-v3 readiness evidence. It cannot synthesize physical
outcomes itself.

The Flutter native-assets hook derives `IPHONEOS_DEPLOYMENT_TARGET` from the iOS target version supplied by Flutter and passes it explicitly to the Matrix Rust bridge build. Keep that value target-derived: Xcode build phases can otherwise replace the Cargo child process deployment target with an older default, producing Rust and C objects that cannot be linked into the app. Non-iOS bridge builds must not receive the iOS variable.

### Development-signed in-place fallback

When TestFlight credentials are unavailable but the stable Weave bundle is already installed on a paired physical iPhone, use the bounded fallback below. It refuses a first install, bundle/team changes, credential-bearing evidence URLs, and non-candidate build numbers. The fallback keeps the production Keychain access group but omits Associated Domains because Apple Personal Development Teams cannot provision that capability. Production and TestFlight builds continue to use `Runner.entitlements` with Associated Domains enabled.

```sh
WEAVE_IOS_DEVICE_ID=<paired-device-id> \
WEAVE_CANDIDATE_COMMIT=<full-candidate-sha> \
WEAVE_CANDIDATE_EVIDENCE_REF=https://github.com/<owner>/<repo>/pull/<number> \
WEAVE_BUILD_NUMBER=<positive-unique-build-number> \
tools/dogfood_ios_development_fallback.sh
```

The command compiles the exact diagnostic identity, signs with `RunnerDevelopment.entitlements`, verifies the signed bundle and Keychain group, installs over `com.massimotter.weave` without uninstall, launches the updated app, and writes `build/dogfood/ios-development-fallback/ios-development-fallback.json`. That artifact records the fallback channel but deliberately leaves session continuity unclaimed; run the session-continuity gate separately after the member reaches `workspace_ready`.

For a deployed dogfood candidate, prefer recording this path through the protected workflow so the same canonical distribution artifact feeds the readiness manifest:

```sh
gh workflow run ios-dogfood.yml --ref dogfood \
  -f candidate_sha=<full-candidate-sha> \
  -f deployment_run_id=<successful-test-stack-run-id> \
  -f upload_to_testflight=false
```

The `ios-dogfood` environment must hold `WEAVE_IOS_DEVICE_ID`, and the paired iPhone must be available to `weave-live-mac-mini`. The workflow runs the same fail-closed script, uploads `ios-dogfood-distribution.json` with channel `stable-signing-fallback`, and never converts installation alone into a session-continuity or VoiceOver pass.

## Session continuity gate

After the member has completed SSO and reached `workspace_ready`, run:

```sh
WEAVE_IOS_DEVICE_ID=<paired-device-id> \
WEAVE_DOGFOOD_EVIDENCE_DIR=build/dogfood/iphone-session-restore \
tools/dogfood_ios_session_restore_smoke.sh
```

The script copies support-safe preferences before launch, terminates and relaunches the installed app, and copies preferences again. The client appends `session_restored` only after the device-bound OIDC session has been restored and the authenticated profile facade succeeds; it then records `workspace_ready`. The checker emits `DOGFOOD_SESSION_CONTINUITY_RESULT` without reading or exporting Keychain contents.

Encrypted Chat continuity adds three device-local values to that contract:

- `matrix_device_identity_v1` stays in the app Keychain and identifies the same Matrix device after relaunch or update.
- `matrix_crypto_store_passphrase_v1_<profile-hash>` stays in the Keychain and unlocks the Matrix Rust SDK SQLite store under application support.
- The profile hash binds API origin, Matrix user ID, and Matrix device ID, so an OIDC access-token refresh rebinds the same encrypted store rather than creating a new device.
- The backend retains only a hash of the OIDC session-to-device binding. The same refreshed session reopens that device, while a revoked session cannot evade revocation by presenting a new Matrix device ID.

Ordinary app close, process termination, token refresh, a temporary network failure, sign-out, and in-place update preserve these values. A rejected OIDC refresh grant clears the unusable network session but does not silently rotate the Matrix device. Explicit account removal is the destructive boundary that deletes the Matrix device ID, passphrase, and encrypted store. Uninstall/reinstall is tested as recovery on a new device, not as session continuity.

Repeat this gate after installing the next TestFlight or development-signed build in place, then open an encrypted room and confirm that previously decrypted history remains readable without a new-device prompt. Record `MATRIX_E2EE_IPHONE_RELAUNCH` only when the same profile, OIDC session, Matrix device, and encrypted store are observed support-safely. A destructive uninstall is a separate recovery test: it may disrupt development trust and is outside the session-continuity guarantee.
