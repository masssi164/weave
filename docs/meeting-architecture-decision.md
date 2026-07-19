# Matrix-native Calls architecture decision

Status: **Accepted target; Experimental/Guarded implementation.** Canonical product and domain
truth remains the pinned Weave Specification Corpus. This projection defines the implementation
boundary and must not be read as live-runtime or release-readiness evidence.

## Decision

Calls are Core collaboration. The member northbound contract is Matrix Client-Server v1.19 plus
the exact revision-pinned `weave.matrixrtc/profile-0`. There is no member `/api/calls`, no member
`/api/weave/calls`, and no `com.weave.call.*` event. LiveKit is the first replaceable MatrixRTC
transport/SFU, not a Weave product API.

Matrix Authentication Service (MAS) is the Matrix-facing Native OAuth authorization server.
Keycloak remains the mandatory organization identity backbone and MAS uses it as its upstream
OIDC identity provider. A Matrix OAuth access token, an OIDC ID token, and a Matrix OpenID
credential are distinct artifacts and cannot substitute for one another.

## Strict MatrixRTC Profile 0

MatrixRTC is not a stable Matrix standard. Profile 0 freezes the live heads of MSC4143, MSC4195,
MSC4196, MSC4075, MSC4310, MSC4140, and MSC4354 as verified on 2026-07-19. It uses the MSC4143
slot/sticky-membership model and exact Profile-0 transport discovery for both reads and writes.

The cutover deliberately has no compatibility reader, unstable endpoint fallback, dual-read,
translation event, or proprietary bridge. An older proposal shape fails closed. This reduces
short-term MatrixRTC interoperability and keeps the capability Guarded, but gives the codebase
one wire truth. A future profile revision must be an explicit contract change.

## Foreign-client flow

1. Discover the homeserver from the organization URL through `/.well-known/matrix/client`.
2. Read `/_matrix/client/v1/auth_metadata`.
3. Register where required and use Authorization Code + PKCE S256 in the system browser.
4. Authenticate through MAS and its upstream Keycloak organization session.
5. Confirm identity with `/whoami`, inspect `/_matrix/client/versions`, and fetch the exact
   authenticated `/_matrix/client/v1/rtc/transports` Profile-0 endpoint.
6. Resolve or open `m.rtc.slot` and publish sticky `m.rtc.member`; use `m.rtc.notification` and
   `m.rtc.decline` for ringing and decline.
7. Request a separate Matrix OpenID credential and present it to the RTC Authorizer.
8. The authorizer rechecks current room membership, slot/member binding, organization policy,
   role, permissions, expiry, and replay state before returning a short-lived LiveKit JWT.
9. Connect over WebRTC/LiveKit.

OpenID establishes Matrix identity. It does not establish current room membership or authorize
a call.

## Ruma and Flutter ownership

The shared Rust/Ruma core owns discovery, Native OAuth, sync/E2EE, Profile-0 wire state, and key
events. Missing upstream wire types live in one `matrixrtc_wire` module with exact Matrix names,
golden fixtures, an upstream reference, and a deletion criterion. Local types do not fork Ruma's
`Any*Event` enums.

LiveKit owns the media transport. A thin, idempotent `NativeCallCoordinator` maps protocol state
to CallKit and Android Core-Telecom lifecycle and audio routing. It does not implement discovery,
signaling, authorization, Matrix state, or LiveKit room administration.

| OS action | Protocol/media action |
| --- | --- |
| Answer | join the slot, obtain a grant, start media |
| Decline before join | send `m.rtc.decline` |
| End after join | leave `m.rtc.member`, stop media |
| Remote end | report the call ended to the OS |
| Hold/mute | update local media/OS state unless a standard event exists |

OS UUIDs are local correlation identifiers, never Matrix protocol identifiers.

## Security, consent, and artifacts

Private calls require an encrypted Matrix room and MatrixRTC media E2EE. DTLS-SRTP alone is
transport encryption and is not an E2EE claim against the SFU. The SFU can still observe
connection and traffic metadata.

Recording and transcription are default-off. Enabling either requires participant-visible
consent plus a declared trusted decrypting participant/boundary or a separately named non-E2EE
profile. Governed artifacts use Files/WebDAV retention, export, and deletion policy. Calendar
stores a stable Matrix/slot reference, never a durable LiveKit credential.

## Removal and readiness

The proprietary Calls controller, in-memory call aggregate, join-grant DTOs, generated client
models, and their acceptance scenarios are deleted in the same cutover. They are not retained as
deprecated adapters. Existing dogfood state may be backed up for recovery evidence and reset;
there is no supported import or rollback path to the obsolete application contract.

The truthful claim remains **“MatrixRTC Profile 0 is specified and Guarded.”** A Ready claim is
blocked until exact wire fixtures, third-party interop, RTC authorization abuse tests, media-key
recovery, TURN/reconnect, physical iOS and Android, consent, artifact lifecycle, and WCAG 2.2 AA
plus EN 301 549 evidence all pass.
