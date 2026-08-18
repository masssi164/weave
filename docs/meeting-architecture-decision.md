# Calls and MatrixRTC architecture decision

Status: active implementation projection of `weave-specs/domains/meetings-calls/spec.md`,
ADR 0011, and the revision-pinned MatrixRTC Profile 0 contract. The corpus is authoritative.

Weave Calls is a Matrix-native product surface. It is not a generic meeting-link wrapper, a
LiveKit-shaped member API, or an OpenAPI join-grant service.

## Decision

1. Member signaling uses Matrix Client-Server API v1.19 plus the pinned MatrixRTC Profile 0
   state and membership shapes.
2. Keycloak remains the identity backbone. Matrix Authentication Service (MAS) fronts Matrix
   Native OAuth and uses Keycloak upstream; Flutter never converts an OIDC identity token into
   an SFU credential.
3. An internal RTC Authorizer independently checks the current room, slot, member, device,
   policy, nonce, audience, and expiry before a short-lived SFU token can be issued.
4. LiveKit is the first replaceable southbound SFU adapter. It carries media but does not own
   Matrix signaling, product membership, domain authorization, consent, or the member contract.
5. MatrixRTC media E2EE is required for private media. Matrix room encryption does not by itself
   prove media, captions, transcripts, recordings, or metadata confidentiality.
6. The obsolete member `/api/calls` routes, native-boundary setup route, proprietary join grants,
   and identity-only token minting are removed without compatibility readers.

## Clean boundaries

| Boundary | Owner | Forbidden shortcut |
| --- | --- | --- |
| Login and organization identity | Keycloak through the Weave identity adapter | Treating an ID token as call authorization |
| Matrix OAuth | MAS with Keycloak upstream | Flutter-managed client secrets or direct password grants |
| Call signaling and membership | Matrix v1.19 plus pinned MatrixRTC Profile 0 | A parallel REST meeting/member state machine |
| Media authorization | Internal RTC Authorizer | Issuing an SFU token from identity proof alone |
| Media transport | Replaceable SFU adapter, initially LiveKit | Exposing provider admin APIs, URLs, keys, or room tokens to Flutter |
| Product policy, consent, and evidence | Weave Calls domain | Letting the SFU or runtime provider become policy authority |

## Authorization contract

The RTC Authorizer fails closed unless all current facts agree:

- authenticated Matrix subject and organization;
- encrypted room membership and permitted call role;
- MatrixRTC slot and member state;
- registered device binding and revocation state;
- capability and room policy;
- one-time nonce, exact SFU audience, and short expiry;
- media-E2EE requirement and supported client profile.

Authorization is reevaluated for join and refresh. Leave, kick, device revocation, policy change,
slot closure, or nonce reuse invalidates further token issuance. Support evidence records bounded
reason codes and correlations, never room content, access tokens, private media keys, provider
credentials, or raw downstream errors.

## Encryption, consent, and retention

| Surface | Required posture before enablement |
| --- | --- |
| Matrix signaling | Encrypted-room behavior, device verification/recovery, and failure handling are evidenced. |
| Media streams | MatrixRTC media E2EE, key rotation, participant change, and SFU metadata boundaries are evidenced. |
| Captions and transcripts | Off by default; explicit visible consent, storage, encryption, retention, export/delete, and accessibility behavior are defined. |
| Recordings | Off by default; explicit participant-visible consent, persistent indicators, storage encryption, retention, export/delete, and audit are defined. |
| Metadata | Participant, timing, device, network, and SFU routing metadata is minimized and never described as end-to-end encrypted. |

## UX and accessibility contract

Before Calls is promoted, Flutter and native bridges must provide:

- device selection for microphone, camera, and output where supported;
- a join preview with visible and announced microphone/camera state;
- keyboard-operable and screen-reader-labelled mute, camera, leave, and end controls;
- a participant list whose speaking and connection state is not color-only;
- clear reconnect, authorization-expired, device-revoked, and media-unavailable recovery;
- explicit consent and indicators for captions, transcripts, and recordings;
- physical-device proof for incoming-call handling, permissions, audio routing, backgrounding,
  interruption, join/revoke, and recovery.

## Current implementation boundary

The old member REST Calls facade and LiveKit join-grant implementation are deleted. The current
repository exposes provider-neutral Calls readiness and the Matrix surface only; it does not claim
that RTC Authorizer, TURN, MatrixRTC media E2EE, consent/artifact persistence, cross-client
interoperability, or physical-device evidence is complete.

Do not claim `secure meetings`, `encrypted meetings`, or `end-to-end encrypted meetings` without
naming and evidencing the signaling, media, captions, transcripts, recordings, and metadata
boundaries separately.

## Implementation references

- Canonical corpus: `../weave-specs/domains/meetings-calls/spec.md`
- MatrixRTC pin: `../weave-specs/contracts/matrixrtc-profile-v0.yaml`
- Product projection: `docs/architecture/domain-facade-protocol-projections.md`
- Acceptance: `e2e/features/calls_webrtc_join_grants.feature`
- Negative route guards: `server/src/test/java/com/massimotter/weave/backend/controller/FilesCalendarFacadeControllerTest.java`
- Member contract: `client/lib/features/chat/domain/entities/channel_workspace.dart`
