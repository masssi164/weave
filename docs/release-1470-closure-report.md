# Weave #1470 release closure report

This report records executed evidence and the protected delivery gates; it does not announce a release.
Live issue state and final promotion results are authoritative at
[#1470](https://github.com/masssi164/weave/issues/1470).

## Authority and integrated source

The accepted release contract is `masssi164/weave-specs/main`
`c726993168651f1109259f9a80cc23117d24a37f`, especially
`steering/release-2026-10-product-consolidation.md`. Provider adoption,
migration, cutover and rollback belong to #1498. Public northbound DAV,
Runner/workflows, broad cell orchestration, Calls and independent third-party
Matrix interoperability are outside this release.

PR [#1533](https://github.com/masssi164/weave/pull/1533) integrated into protected
`dev` at `5d78cade66199edfde6c47cea2661c453c4f602d`. Its tree equals qualified
PR source `d106855a51eabee87bb57749473d5048aadaa558`. Exact-head
[readiness Core CI](https://github.com/masssi164/weave/actions/runs/38085842856)
and [readiness Full Compose E2E](https://github.com/masssi164/weave/actions/runs/38085842852)
passed; [Native Provider Gate](https://github.com/masssi164/weave/actions/runs/38083483807)
and [Native Persistence Closure](https://github.com/masssi164/weave/actions/runs/38083483819)
also passed on that PR source. The protected dev commit's own
[Core CI](https://github.com/masssi164/weave/actions/runs/38088279001) and
[Full Compose E2E](https://github.com/masssi164/weave/actions/runs/38088279016)
provide the separate integrated-source results. A pending, skipped or failed
result does not count as acceptance. Earlier-source passes never substitute for
a later candidate's required checks.

## Child outcomes and evidence

| Story | Acceptance evidence | Delivery disposition |
| --- | --- | --- |
| [#1471](https://github.com/masssi164/weave/issues/1471) scope and architecture | [Integrated entry-point and historical disposition audit](https://github.com/masssi164/weave/issues/1471#issuecomment-6078804271), pinned accepted corpus, retained security and recovery requirements. | Closed; no further historical prose cleanup required. |
| [#1472](https://github.com/masssi164/weave/issues/1472) server-owned contracts | [User/Admin generation audit](https://github.com/masssi164/weave/issues/1472#issuecomment-6075334537), deterministic generation, explicit operation IDs, schemas and real error contracts, principal separation and consumer compilation. | Closed; regenerated revocation failure metadata is included in #1533. |
| [#1473](https://github.com/masssi164/weave/issues/1473) generated consumers | [Repository consumer inventory](https://github.com/masssi164/weave/issues/1473#issuecomment-6078813173); #1558, #1559 and #1560 are integrated. Flutter User, Admin UI Admin, MCP and JVM E2E use shared generated contracts. | Closed. Deliberate Matrix/OIDC/provider and malformed-security probes remain separate protocol tests. |
| [#1474](https://github.com/masssi164/weave/issues/1474) standalone identity | Disposable IdP/Server/MCP/PostgreSQL, real native browser PKCE, current organization/member authorization, expiry with fresh controls, logout, durable Admin revocation, refresh denial and later same-identity reauthorization. Independent wrong-principal/issuer/audience/client/scope negatives remain enforced. | Exact integrated-source acceptance is recorded in the linked story after its required checks pass. |
| [#1475](https://github.com/masssi164/weave/issues/1475) bounded Matrix Chat | [Versioned v3 profile](reference/matrix-client-server-support-profile.md), native Rust Matrix SDK and real OpenClaw at the same Weave facade, business-room send/read/sync, current room/member/device denial, restart and refresh. | Final integrated-source acceptance remains open; five Supported rows are the required profile. |
| [#1476](https://github.com/masssi164/weave/issues/1476) stable resources/bindings | [Persistence and invariant audit](https://github.com/masssi164/weave/issues/1476#issuecomment-6075355496): transient browse, explicit idempotent materialization, stable IDs, one active binding, private mappings, access removal and restore. | Closed; populated-provider replacement remains #1498. |
| [#1479](https://github.com/masssi164/weave/issues/1479) usable product | Two native processes prove one sign-in, generated Files upload/read and Calendar CRUD, business Chat, navigation and lifecycle. Real OpenClaw proves Files/Calendar catalog, writes, version conflicts and current permission denial with independent API readback. Admin identity/setup/readiness remains separate. | Final integrated-source acceptance remains open; focused keyboard/focus and Calendar loading/empty assertions are completed in existing #1544. |
| [#1480](https://github.com/masssi164/weave/issues/1480) integrated CI/E2E | Exact-source generated contracts/SDKs, stale/unsupported/duplicate-ID failures and consumer compilation; real disposable native/API/MCP/Matrix expectations, cross-organization denial and zero-resource cleanup. Protected dev requires real Gradle CI, label and Full Compose contexts with strict protection and administrator enforcement. | Exact integrated-source acceptance is recorded in the linked story after its required checks pass. No skipped compatibility job substitutes for a required check. |
| [#1481](https://github.com/masssi164/weave/issues/1481) main delivery | Existing #1544 enforces exact lane ancestry/tree, successful exact dogfood deployment and separate owner human evidence; distribution notices and README claims are reconciled. Weaver work is already merged and verified below. | Closure requires preceding outcomes, exact dogfood/human acceptance, remote Weave main and post-merge CI. |

## Native execution and independent assertions

The existing Gherkin architecture is retained: `e2e/features/`,
`e2e/scenario_mappings.json` and `e2e/suites/scenario_catalog.json` map the
product expectations to executable tests. The native product, member-session
revocation and foreign-organization scenarios execute actual assertions;
mapping or marker existence alone is not acceptance. See
[native commands and diagnosis](native-flutter-integration.md) and
[Matrix evidence](evidence/matrix/release-business-room-v3.md).

The readiness Full run executed both native Flutter processes and the real
generated Admin revocation journey. It proved retained unexpired User/Admin/
Matrix denial, old refresh denial, later real PKCE reauthorization, idempotent
replay that preserves the new session, and logout before Chat initialization.
Its product JSON SHA-256 is
`feeb2df4c8c44de443d84248aa974d5500a5bd158a78206fdac093117a3236b8`;
teardown SHA-256 is
`27c48aa2d1168abc24d9ea7b083f67cd945e8c9e9fe8c8cb13dd398b901aa0e0`.
Remaining containers, networks, volumes and other owned resources were zero.
Raw authentication/native logs remain private; uploaded evidence contains no
credentials or private host paths.

The relevant widget suites run in Core CI through `generatedApiCi -> clientCi`.
Existing assertions cover semantics, loading, errors, access loss and session
states. #1544 adds actual Tab/Enter/Escape traversal and focus return for Files
and Calendar and keyboard sending in a plain business room, plus Calendar's
loading-to-empty transition. All 53 affected-suite tests passed locally before
integration. Widget semantics are not an automated VoiceOver claim; final
human accessibility acceptance remains separate.

## Versions and support limits

Recorded native environment: macOS 26.4.1, Xcode 26.5 (17F42), Flutter 3.41.6,
Dart 3.11.4; actual CI SDK framework revision
`db50e20168db8fee486b9abf32fc912de3bc5b6a`, engine revision
`425cfb54d01a9472b3e81d9e76fd63a4a44cfbcb`. Locked client dependencies include
Matrix SDK 0.18.0 with recorded downstream patches, Ruma 0.16.0, Flutter Rust
Bridge 2.13.0-beta.4, AppAuth 12.0.0 and secure storage 10.0.0. OpenAPI
Generator 7.17.0 is pinned in the declared generation pipeline. The selected
Chat/Files/Calendar providers are `weave-native` at the tested source. Keycloak
is 26.7.1 with the recorded downstream built-in-policy patch; PostgreSQL is
configured as 16.9-alpine. Runtime evidence records observed image IDs and
source/spec commits. This is not a claim that the local run used immutable
manifest-bound image references.

Authorized non-E2EE business rooms are the required Chat profile. E2EE,
cross-signing, SAS, legacy-device adoption and encrypted backup/recovery remain
Guarded. An explicitly encrypted room fails closed when its required capability
is unavailable. The selected native providers have no independent downstream
authentication session; their service/persistence restart and member refresh
are proven. External Synapse/Nextcloud session-loss recovery is unclaimed.
macOS execution does not qualify iOS, Android or a physical device.

## Licensing, provenance and final delivery gate

Weaver remote main is `00a1714055763e162b545399add1df791c400f92`. Existing
licensing [#43](https://github.com/masssi164/weaver/pull/43), README
[#45](https://github.com/masssi164/weaver/pull/45) and focused claim correction
[#50](https://github.com/masssi164/weaver/pull/50) are merged.
[Distribution CI](https://github.com/masssi164/weaver/actions/runs/38071855908)
passed on that exact source. Signed OpenClaw v2026.9.8 commit
`fc23bc864e4553c2d215e479eeec47b67a0bf943` retains upstream ancestry and MIT;
EUPL applies only to the approved original Weaver scope. Existing third-party
grants remain intact. Weave's Server/MCP images carry the accepted EUPL metadata
and public notices, with Matrix vendor notices retained in Server.

Continue existing #1544, verify its exact integrated source, then promote the
accepted tree through protected `dev -> dogfood -> main`. Dogfood deployment
requires explicit authorization under the workspace operating contract. The
final owner human result must follow successful automated evidence on that exact
deployed commit. No agent or job fabricates that result. Verify the remote main
SHA, ancestry and applicable post-merge CI before closing #1481 and #1470.
Production publication is a separate decision.
