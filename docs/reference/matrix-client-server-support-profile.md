# Matrix Client-Server support profile

Profile version: `weave.matrix-client-server/v1`

Status: qualification in progress

Authority: `steering/release-2026-10-product-consolidation.md`, Weave #1475

This is the normative compatibility boundary for the Weave-owned northbound Matrix Client-Server facade. The facade is the accepted product target; the table records what has actually qualified for a compatibility claim. It does not assert that the current gateway, identity session, provider routing, or third-party clients are ready. Weave does not claim general Matrix homeserver or federation support.

`Supported` requires a protocol assertion and a real independent-client journey against the public Weave endpoint on the same qualified build. `Guarded` means implementation or evidence is incomplete and the route must not be advertised as compatible. A guarded route that currently returns success under a legacy bearer still needs correction or qualification before release. `Unsupported` means the request must fail explicitly without a Chat mutation. The checker at `tools/check_matrix_support_profile.py` rejects a Supported row without both named assertions and a checked-in integrated evidence reference; it does not replace running those assertions.

## Protocol boundary

Server Matrix wire parsing and projection belong to `rust/matrix-protocol` through its closed JNI operation set. Java owns current identity, authorization, canonical Chat state, provider selection, idempotency and sync. `rust/matrix-client` alone owns Flutter client crypto. The server must not build a second Chat ledger or substitute a proprietary Chat REST API.

## Endpoint profile

| ID | Surface | Status | Protocol assertion | Independent-client assertion | Integrated evidence or gap |
| --- | --- | --- | --- | --- | --- |
| discovery | `/.well-known/matrix/client`, `/_matrix/client/v1/auth_metadata`, `/versions` | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#matrixDiscoveryIsPublicAndPointsAtTheWeaveFacade` | pending | Public gateway and OAuth metadata are not proven. The current facade has no usable OAuth metadata, registered Matrix client or demonstrated stable device-scope issuance; the pinned native SDK requests older MSC2967 scopes. PlatformConfig currently advertises `WEAVE_MATRIX_URL` while the facade's default Matrix server name is `api.weave.test`; `/versions` requires the API bearer and advertises an unqualified protocol version. |
| whoami | `/account/whoami` | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#whoamiUsesRumaValidatedIdentityDerivedFromOidcSubject` | pending | Separate Matrix audience, OAuth session, and independent client are unproven. |
| sync | `/sync` | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#syncProjectsCanonicalChatThroughRustWithStableWeaveCursor` | pending | Requires multi-instance high-water, replay and no-skip proof; token must not expose provider cursors. |
| room-state | Joined rooms, members, state and timeline reads | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#joinedRoomsAndMessagesUseCanonicalIdentifiers` | pending | Public independent client, complete membership semantics and org-scoped provider routing are unproven. |
| room-send | Room event send with `txnId` | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#sendParsesInRustAndForwardsTransactionForCanonicalIdempotency` | pending | Requires exact tenant, user, device, method, endpoint, transaction and binding-revision idempotency under concurrency. |
| redaction | Room redaction | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#redactionProjectsItsOwnEventIdInsteadOfReusingTheTargetId` | pending | Canonical mutation, authorization and provider-neutral readback are not integrated proof. |
| receipt | Read receipts | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#stockOpenClawMemberReceiptAndTypingCallsStayOnCanonicalChat` | pending | Durable multi-instance receipt state and independent client are unproven. |
| typing | Typing | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#stockOpenClawMemberReceiptAndTypingCallsStayOnCanonicalChat` | pending | Typing is ephemeral; loss across restart is allowed, but authorization and client behavior need proof. |
| account-data | Per-user account data | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#openClawStartupCanLoadPushRulesCreateAFilterAndSyncAccountData` | pending | Tenant/user isolation and independent client need proof. |
| keys | Key upload, query, claim and changes | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#keyLifecycleToDeviceSyncAndLostDeviceRevocationAreDeviceScoped` | pending | Atomic one-time-key claims, device-list changes and real two-device E2EE need proof. |
| device-signing | Device signing and signatures | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#signatureUploadPreservesDeviceSelfSignatureAndIdentityKeys` | pending | OAuth-sensitive action, cross-signing and independent client need proof. |
| send-to-device | `/sendToDevice` | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#keyLifecycleToDeviceSyncAndLostDeviceRevocationAreDeviceScoped` | pending | Requires durable per-device ordering and acknowledgement across instances. |
| room-key-backup | Room-key backup | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#roomKeyBackupStoresOnlyOpaqueRecoveryPayloads` | pending | Ciphertext-only storage, authorization, recovery and independent client need proof. |
| device-revoke | Device revocation | Guarded | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#keyLifecycleToDeviceSyncAndLostDeviceRevocationAreDeviceScoped` | pending | Revocation must invalidate the distinct Matrix session across restart and instances. |
| unknown-client-route | Unimplemented Client-Server routes | Unsupported | `server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java#unimplementedClientServerRouteFailsExplicitlyWithoutChatMutation` | pending | Current response is 404 `M_NOT_FOUND`; standard error compatibility still needs review. |

No row is currently qualified as Supported. The existing MockMvc assertions are useful implementation evidence, but they use a Weave API test bearer and are not independent Matrix-client or public-gateway evidence. No guarded route may be promoted from compilation, a mocked response, or a document edit alone.

## Encryption invariant

The server may store public key metadata and opaque encrypted event, to-device and backup payloads only. It must never own user private identity keys, Olm/Megolm private session state, client recovery secrets or plaintext derived by decrypting encrypted room events. A client without validated E2EE capability must not enter an encrypted conversation through downgrade or server-side decryption.

## Identity and provider invariants

The member endpoint must be the credential-free HTTPS Weave Matrix URL advertised by OrgManifest v2. The Matrix OAuth session has its own audience, lifetime and device binding; the Weave API bearer must not authorize Matrix requests. One normal Weave OIDC sign-in can reuse the IdP SSO context without a second user-facing credential prompt. Revocation, wrong-account and cross-organization requests must fail closed.

The server name in an authenticated Matrix user ID returned by `/account/whoami` must equal the authority of the advertised Weave Matrix endpoint. An enrolled client must not silently accept a provider URL or a different server name.

The selected `ChatProviderPort` is southbound. Changing its one active organization/module binding must not change the member Matrix URL or stable Matrix references. A staged candidate must not receive member traffic. This invariant is a release gate, not a claim that the current process-wide adapter selection satisfies it.

## Qualification gate

Promote one row to Supported only after its named protocol assertion, independent maintained Matrix-client assertion, and sanitized integrated evidence run on the same exact build. The gate must include an authenticated negative route, the wrong bearer audience, revoked device, wrong account and cross-organization denial where relevant. Sync, transaction send and to-device delivery additionally require committed PostgreSQL concurrency, ordering and restart evidence. The support profile version changes when a previously guarded public promise is promoted or removed.
