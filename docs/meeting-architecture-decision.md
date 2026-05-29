# Meeting architecture decision record

Status: product/architecture contract for issue #216. This is the meeting contract before enabling join/start controls.

Weave meetings are contextual collaboration surfaces attached to existing Weave work context. They are not generic external links and they are not enabled until encryption boundaries, consent, accessibility, and provider readiness are evidenced.

## Decision

Adopt a provider-neutral meeting capability contract in the member client and keep join/start fail-closed until the existing backend-owned LiveKit meetings provider contract proves its concrete media architecture.

Current direction:

1. Keep LiveKit as the active meetings/video-call provider key in the backend provider registry and runtime configuration.
2. Route all LiveKit rooms, session tokens, readiness, and diagnostics through a Weave backend facade; Flutter/member UI must only see Weave meeting capability state.
3. Treat Matrix as the chat/E2EE substrate and possible contextual signaling source, but do not promote MatrixRTC/Element Call as the active Weave meetings provider in this slice.
4. Keep MatrixRTC/Element Call as a future comparison option only if a later migration updates the backend registry, runtime docs, acceptance tests, and provider readiness contract consistently.
5. Do not expose provider URLs, room media tokens, SFU internals, or recording/caption storage providers in member UI.

## Architecture options

| Option | Fit | E2EE and confidentiality boundary | Operational trade-off | Decision |
| --- | --- | --- | --- | --- |
| LiveKit-style SFU behind Weave backend facade | Current active provider contract for meetings/video calls and good fit for scalable media operations. | Media may be encrypted in transit and can support E2EE modes, but SFU metadata and feature behavior must be documented. Recording/captions/transcripts need separate encryption and consent evidence. Matrix chat encryption must not be presented as covering LiveKit media. | Requires backend token minting, SFU operations, capability health, support bundle redaction, and explicit E2EE trade-off docs. | Active provider contract; still fail-closed until backend/media/accessibility evidence is configured and validated. |
| MatrixRTC / Element Call | Future comparison option for Matrix-native contextual calls, not the active Weave meetings provider contract. | Signaling can follow Matrix room encryption. Media confidentiality depends on MatrixRTC/Element Call mode, client support, SFU behavior, and documented key handling. | Requires registry/runtime/acceptance migration, MatrixRTC readiness, compatible clients, media backend evidence, and clear fallback states. | Future option only; do not silently replace the current LiveKit contract. |
| Generic hosted meeting links | Low product fit. | Encryption, metadata, retention, and recording behavior are provider-defined and often opaque to Weave. | Easy link insertion but weak sovereignty and portability. | Not acceptable as the primary Weave meeting model. |

## Encryption and evidence boundaries

| Boundary | Product statement | Required evidence before enabling |
| --- | --- | --- |
| Matrix signaling | Matrix remains the chat/E2EE substrate and may provide contextual room state, but it is not the active generic meetings provider. | Matrix E2EE diagnostic, room readiness, and failure-state evidence. |
| Media streams | Media confidentiality is documented for the active LiveKit-backed architecture before enablement; group calls must state SFU/client key handling and must not inherit Matrix chat E2EE claims. | LiveKit media transport readiness, E2EE mode evidence, and architecture trade-off record. |
| Captions | Off by default. If enabled, caption generation, storage, retention, and encryption are visible to users. | Consent UX, storage boundary, retention policy, and accessibility evidence. |
| Transcripts | Off by default. If enabled, transcript generation, storage, retention, and encryption are visible to users. | Consent UX, storage boundary, retention policy, export/delete behavior, and audit evidence. |
| Recordings | Off by default and unavailable without explicit consent and visible state. | Consent UX, recording indicator, storage encryption, retention policy, export/delete behavior, and audit evidence. |
| Metadata | Participant, timing, device, SFU routing, and diagnostic metadata is minimized and never described as end-to-end encrypted. | Support-safe metadata inventory and redaction tests. |

## Context attachment contract

Meeting surfaces can attach to:

- channel context: `channel:*` as the first supported member surface;
- calendar event context: `calendar-event:*` once calendar/event facade readiness exists;
- thread context: `thread:*` once thread identity and retention semantics are explicit.

The member client may show disabled attach points, but join/start stays disabled until backend capability, policy, and encryption evidence are all ready.

## UX and accessibility contract

Before join/start can be enabled, the meeting UI must cover:

- device selection for microphone, camera, and output where supported;
- join preview with visible camera and microphone state;
- mute and camera state that are keyboard-operable and screen-reader labelled;
- participant list with speaking/connection state that is not color-only;
- clear leave/end state and recovery path;
- provider/backend unavailable errors with retry and admin/operator setup guidance;
- captions/transcripts only when explicitly enabled and visibly consented.

## Consent and defaults

Recording, transcription, and captions are off by default. Enabling any of them requires explicit participant-visible consent, persistent in-meeting indicators, retention/export/delete policy, and audit evidence.

## Vague-claim guard

Do not claim `secure meetings`, `encrypted meetings`, or `end-to-end encrypted meetings` without naming the boundary and evidence: signaling, media, captions, transcripts, recordings, and metadata. Product copy must say what is known, what is disabled, and what still needs admin/provider evidence.

## Implementation references

- Domain contract: `client/lib/features/chat/domain/entities/channel_workspace.dart`
- Domain test: `client/test/features/chat/channel_workspace_test.dart`
- Architecture guard: `client/test/architecture/meetings_contract_test.dart`
- Product scope: `docs/product-calendar-e2ee-boards-scope.md`
- Guarded surfaces: `docs/roadmap-and-guarded-surfaces.md`
- Runtime provider contract: `server/docs/runtime-configuration.md`
- Provider boundary: `docs/provider-replacement-and-anti-silo-contract.md`

## MVP slice

The first shippable slice is a fail-closed contract:

- contextual meeting preview stays attached to a channel and declares future calendar-event/thread attach points;
- join/start controls remain disabled while backend media capability is absent;
- encryption boundaries are explicit and evidence-labelled;
- UX/accessibility requirements are enumerated before enablement;
- recording/transcription are off by default;
- tests prevent vague security claims without documented evidence.
