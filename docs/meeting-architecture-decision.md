# Meeting architecture decision record

Status: **Proposed target architecture; Experimental/Guarded.** This document replaces the earlier proprietary member-facing Calls API direction as a conformance proposal for issue #968. It does not claim that the code is implemented or release-ready. Canonical product/domain truth remains the pinned Weave Specification Corpus; this implementation-repo projection must be reconciled with the matching corpus PR before merge.

## Core decision

Calls are Core collaboration. The northbound contract is Matrix Client-Server plus the pinned **Weave MatrixRTC Profile 0**. There is no member `/api/weave/calls` and no `com.weave.call.*` event where Matrix/MSC semantics exist. LiveKit is the first replaceable MatrixRTC transport/SFU, not the product API.

Matrix Native OAuth is stable in Matrix v1.19. MatrixRTC is not: MSC4143/4195/4196/4075/4310 and their delayed/sticky dependencies are open drafts and partly inconsistent. Profile 0 therefore freezes exact commits as of 2026-07-19, uses the latest MSC4143 slot/sticky-membership model for writes, accepts known legacy shapes for reads, and remains `Experimental/Guarded`.

## Interoperable foreign-client flow

1. User enters `weave.example`; client discovers the homeserver via `/.well-known/matrix/client`.
2. Client reads `/_matrix/client/v1/auth_metadata`.
3. It uses OAuth Dynamic Client Registration where required and Authorization Code + PKCE/S256 in the system browser.
4. Matrix Authentication Service is the client-visible authorization server and may use Keycloak as upstream IdP. Keycloak remains the organization identity/entitlement backbone.
5. Client confirms the Matrix identity with `/whoami`, checks `/versions`, and fetches authenticated `GET /_matrix/client/v1/rtc/transports` when the stable MSC feature is advertised; Profile 0 defines the exact unstable fallback while the MSC remains open.
6. It resolves/opens `m.rtc.slot` and participates with sticky `m.rtc.member`; ringing/decline use `m.rtc.notification`/`m.rtc.decline`.
7. It requests a separate Matrix OpenID credential and presents it to the RTC Authorizer.
8. The authorizer validates identity plus available room/call policy and returns a short-lived, room/slot/member/permission-bound LiveKit JWT.
9. The client connects over WebRTC/LiveKit.

A Matrix OAuth access token, OIDC ID token and Matrix OpenID credential are three distinct artifacts. OpenID proves Matrix identity, not by itself current room/call membership.

## Wire profile and Ruma

Normative pins live in `contracts/matrixrtc-profile-v0.yaml`. MSC4143 is authoritative for `m.rtc.slot`, sticky `m.rtc.member`, `transports.published/can_subscribe`, transport discovery and singular `m.rtc.encryption_key`. The frozen MSC4195/4196 heads still show older `rtc_transports`, membership-relation/disconnect fields and one plural encryption-key reference. Profile 0 documents these conflicts, reads compatible old forms, and writes only the MSC4143 shape. Unknown fields are preserved.

Matrix is the facade; Ruma implements it. At the pinned Ruma commit, notification/decline and legacy MSC3401 membership exist, but not the complete current slot/sticky-member model. An isolated `matrixrtc_wire` crate/module supplies missing Ruma EventContent/Serde types using exact Matrix/MSC names (`m.rtc.slot`, `m.rtc.member`, `m.rtc.encryption_key`). Raw/generic event dispatch is used because custom types do not enter Ruma's `Any*Event` enums automatically. Each local type has an upstream issue and deletion criterion.

## RTC Authorizer

The authorizer exchanges a Matrix OpenID identity proof for an SFU token. Grants bind Matrix user, room, slot, member, LiveKit alias, permission set, nonce and short expiry. Publish/create and subscribe are least-privilege and separate where possible. Local membership and Weave policy are checked before grant issuance. Federated authorization stays `Guarded` until membership attestation is sufficiently standard and proven; OpenID alone is never accepted as call authorization.

## Flutter and native calls

The shared Rust/Ruma core owns Matrix discovery, OAuth, sync/E2EE and MatrixRTC wire state. The media client owns LiveKit/WebRTC. A thin `NativeCallCoordinator` maps protocol state to CallKit and Android Core-Telecom; those OS APIs provide call UI, lifecycle and audio routing—not discovery, signaling or media.

| OS action | Protocol/media action |
|---|---|
| Answer | join slot, obtain grant, start media |
| Decline before join | send `m.rtc.decline` |
| End after join | leave `m.rtc.member`, stop media |
| Remote end | report ended to OS |
| Hold/mute | update local media/OS state; no proprietary Matrix event |

Correlation is idempotent. OS UUIDs are local projections of Matrix identifiers and never become protocol IDs.

## Encryption and artifacts

Private calls require an encrypted Matrix room and MatrixRTC media E2EE. WebRTC DTLS-SRTP alone is transport encryption and does not hide media from the SFU. The SFU still sees traffic/connection metadata. Recording or transcription requires explicit participant-visible policy/consent and either a disclosed trusted decrypting participant or a separate non-E2EE profile; blanket E2EE claims are forbidden. Artifacts follow Files/WebDAV retention/export/delete policy.

## Scheduling and portability

CalDAV/iCalendar stores a stable Matrix/meeting reference, never a durable LiveKit token. Future meetings can change RTC transport without changing member identity or scheduled reference. An active call is not live-migrated: it ends/rejoins deliberately with visible interruption.

## Ready gate

- golden fixtures for every pinned MSC and dual-read/single-write tests;
- successful third-party Matrix/Element interoperability without Weave Calls API;
- local/federated authorization negative tests and short-token replay tests;
- MatrixRTC media-key lifecycle, stable device recovery and no-secret scans;
- TURN/reconnect/multi-device and physical iOS CallKit/Android Core-Telecom evidence;
- recording/transcription consent, accessibility and artifact retention/delete evidence.

Until all gates pass, the truthful claim is: **“MatrixRTC Profile 0 is specified and guarded; LiveKit integration is an implementation slice.”**


## Legacy migration boundary

Existing LiveKit readiness, capability, join-grant and client fixtures are retained only as guarded migration evidence. During implementation they may sit behind an internal deprecated adapter, but they must not remain a member northbound contract. Every retained path needs deprecation metadata, a removal issue/date, parity tests and a rollback reason. New code must not add a member `/api/weave/calls` dependency or proprietary `com.weave.call.*` events.

## Implementation status

This ADR locks a target and its release gates. It intentionally changes no production route, token issuer, event writer, Ruma type, Flutter media path or native OS integration. Those changes are sequenced in [the implementation plan](implementation-plans/matrixrtc-stateless-runtime.md).
