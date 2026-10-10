# Matrix business-room profile v3 evidence

This evidence qualifies the five `Supported` rows in the [Matrix Client-Server support profile](../../reference/matrix-client-server-support-profile.md) for Weave-owned Flutter and real OpenClaw clients using authorized non-E2EE business rooms. Protected dev integration and every later candidate are independently tracked in the [release closure report](../../release-1470-closure-report.md).

## Exact-source unattended qualification and integration

Source `d106855a51eabee87bb57749473d5048aadaa558` passed
[Core CI](https://github.com/masssi164/weave/actions/runs/38085842856) and
[Full Compose E2E](https://github.com/masssi164/weave/actions/runs/38085842852),
as well as [Native Provider Gate](https://github.com/masssi164/weave/actions/runs/38083483807)
and [Native Persistence Closure](https://github.com/masssi164/weave/actions/runs/38083483819).
Both selected native Flutter processes genuinely executed. Native SDK and
real OpenClaw assertions exercised the same Weave Matrix endpoint, with
independent authorization, sync/readback and business-room send expectations.
Real expiry, retained bearer logout denial, administrator revocation and
second-organization denial passed. Product JSON SHA-256:
`feeb2df4c8c44de443d84248aa974d5500a5bd158a78206fdac093117a3236b8`;
cleanup verified zero remaining owned resources.

PR #1533 integrated that same tree into protected `dev` at
`5d78cade66199edfde6c47cea2661c453c4f602d`. Its own post-merge
[Core](https://github.com/masssi164/weave/actions/runs/38088279001) and
[Full](https://github.com/masssi164/weave/actions/runs/38088279016) results are
separately required. Later delivery source must pass its own checks; this record does not claim dogfood, human acceptance or remote main.

## Current exact local product run

- Executable source: `6f974dff7356bc78a9d1376e929988e9f86e8696`; accepted specification corpus: `c726993168651f1109259f9a80cc23117d24a37f`.
- The documented disposable `./gradlew --no-daemon --max-workers=2 specCorpusConformance testApp` passed in 14m 59s overall, with both native macOS Flutter processes exiting zero. See [native execution instructions and diagnostics](../../native-flutter-integration.md).
- Actual native AppAuth browser Authorization Code + PKCE established one Weave member session. The Rust Matrix SDK used the advertised HTTPS Weave Matrix facade without another login or provider credential, discovered an authorized business room, sent a message, observed readback/sync, refreshed the member session and restored the same room after an app-process and Server/Keycloak/PostgreSQL restart.
- The restored native process waited for the original bearer to expire beyond production clock skew, required expired User and Matrix rejection after fresh-session positive controls, left its own room and lost timeline access, then logged out and observed retained unexpired User and Matrix bearer denial. Native logout consent was automated and independently verified.
- Real OpenClaw 2026.9.8 used the same Weave Matrix endpoint and passed business-room send/read. The JVM journey independently asserted discovery, whoami, membership, sync/readback, send, outsider read/write denial and persistence across two collaboration passes. The active Chat provider was `weave-native` behind `ChatProviderPort`.
- A real foreign-organization PKCE identity was denied by generated User/Admin operations and Matrix admission. Generated Admin revocation denied retained unexpired User/Admin/Matrix bearers and revoked refresh credentials; later real PKCE reauthorization restored the same identity, and idempotent revocation replay preserved its new session. Logout before Chat initialization also denied retained and refreshed member bearers.
- Support-safe local product JSON: `build/test-app/weave-e2e-8f1ada0fa9e034f3/weave-test-app-evidence.json`, SHA-256 `d9f7c56efdc511494b23fd37cb0b80cb720305f8ec522f3ed8f7bf39556e68ef`. Observed runtime image IDs and source/spec commits are recorded separately. Teardown verified zero remaining owned resources. Raw private native logs are not release artifacts.

## Independent assertions and historical qualification

`MatrixClientServerProjectionControllerTest` exercises the protocol rows, device possession, transaction scoping, logout/revocation and explicit rejection of unsupported routes. Persistence tests cover concurrent replay, committed ordering, cold history pagination and durable revocation cutoff continuity. Real-signature/JWKS decoder tests reject wrong issuer, audience, organization, principal substitution and expired/revoked credentials. Generated clients are transport consumers; independent expected results remain the semantic oracle.

Earlier source `24c92cd2873947e5430647baef6f114c64a2905d` passed [Core CI](https://github.com/masssi164/weave/actions/runs/38075527322), [Full Compose E2E attempt 2](https://github.com/masssi164/weave/actions/runs/38075527418), [Native Provider Gate](https://github.com/masssi164/weave/actions/runs/38075527324) and [Native Persistence Closure](https://github.com/masssi164/weave/actions/runs/38075527381). The current source adds retained User/Admin revocation and runner lifecycle corrections; that historical CI does not qualify the new source.

The earlier `d279f07683` run qualified the v3 profile locally, while its CI [38022631802](https://github.com/masssi164/weave/actions/runs/38022631802) failed Accessibility preflight before executing the native journey. Its second-organization proof was also absent. Those attempts remain failed/unexecuted evidence. The runner grant was subsequently applied, real CI native execution passed on `24c92cd287`, and the current local run exercised the real foreign-organization identity. The `092a82dc9b` attempt failed the second native launch harness after its first native and administrator-revocation journeys passed; it is not a complete acceptance pass.

## Remaining release boundaries

- The latest exact-head CI and protected integrated `dev` proof remain required; dogfood, genuine human acceptance and remote `main` belong to #1481.
- Key upload/query/claim, device signing, to-device delivery, room-key backup, multi-device E2EE recovery, verification and encrypted-room accessibility remain `Guarded`. An explicitly encrypted room must fail closed until its required crypto/device profile is supported; no plaintext downgrade is qualified.
- Provider adoption/migration/cutover belongs to #1498. The selected native providers have no independent downstream authentication session; Synapse/Nextcloud session-loss behavior is unclaimed.
- macOS qualification does not establish iOS, Android or independent third-party Matrix interoperability.
