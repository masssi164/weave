# iOS dogfood distribution

Weave uses one stable iOS application identity for engineering builds, TestFlight builds, and later release builds: bundle identifier `com.massimotter.weave`, Apple team `KNDHGC2KV6`, and Keychain application identifier `$(AppIdentifierPrefix)com.massimotter.weave`. A normal app iteration must update in place. It must not delete the installed app, rotate the bundle identifier, or clear the saved Weave profile, OIDC refresh session, Matrix device ID, or Matrix crypto-store passphrase.

## Human tester channel

TestFlight is the preferred physical-iPhone dogfood channel. Testers install through Apple instead of trusting an Apple Development certificate on the device, and subsequent builds retain the application and Keychain identity. The development-signed profile runner remains an engineering fallback for local deeplink and LAN diagnostics. If that fallback requests repeated Developer App trust after an ordinary update, use TestFlight instead of asking the tester to repeat the trust action.

The GitHub `iOS Dogfood` workflow runs only for `dogfood` or an explicit manual dispatch. Its upload job uses the protected `ios-dogfood` environment. Configure required reviewers and these environment secrets:

- `APPLE_DISTRIBUTION_CERTIFICATE_P12_BASE64`
- `APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD`
- `APPLE_PROVISIONING_PROFILE_BASE64`
- `APPLE_PROVISIONING_PROFILE_NAME`
- `APP_STORE_CONNECT_API_KEY_ID`
- `APP_STORE_CONNECT_ISSUER_ID`
- `APP_STORE_CONNECT_API_PRIVATE_KEY_BASE64`

Create the App Store Connect app record and TestFlight tester group once. Configure automatic distribution for the intended internal tester group, or complete Apple's beta review before using an external tester group. The workflow validates the archive bundle/build identity, uploads with Apple's command-line tooling, and emits support-safe evidence without certificate, profile, API key, or member credential material.

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

Ordinary app close, process termination, token refresh, sign-out, and in-place update preserve these values. Explicit account removal is the destructive boundary that deletes the Matrix device ID, passphrase, and encrypted store. Uninstall/reinstall is tested as recovery on a new device, not as session continuity.

Repeat this gate after installing the next TestFlight or development-signed build in place, then open an encrypted room and confirm that previously decrypted history remains readable without a new-device prompt. Record `MATRIX_E2EE_IPHONE_RELAUNCH` only when the same profile, OIDC session, Matrix device, and encrypted store are observed support-safely. A destructive uninstall is a separate recovery test: it may disrupt development trust and is outside the session-continuity guarantee.
