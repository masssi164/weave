# Calls native boundary setup

Weave Calls and Meetings native integration separates OS call UI from media transport and provider administration. Native call surfaces must be driven by Weave meeting invitations, policy, and join grants.

## Current executable slice

`GET /api/calls/native-boundary-setup` returns authenticated, support-safe setup metadata for native call UI:

- iOS boundary: CallKit plus PushKit/VoIP routing concerns.
- Android boundary: Telecom / ConnectionService where supported.
- Flutter/native bridge role: setup, status, and incoming-call handoff only.
- Control-plane proof hooks: this native boundary setup route, workspace capability state, existing grant TTL/revocation tests, and native bridge contract tests.
- Support-safe blocked states for the remaining work: provider-neutral meetings facade endpoints, native OS implementations, physical-device camera/microphone/audio-route evidence, and separate consent/audit gates for recording, captions, and transcripts.

The response deliberately contains only Weave-owned paths and product boundaries. It must not include media-provider admin URLs, API keys, bearer tokens, raw diagnostics, or provider setup endpoints.

## Product boundary

OpenAPI remains useful for setup, status, policy, and join-grant provisioning. The OS call UI contract is still native: CallKit/PushKit on iOS and Telecom/ConnectionService on Android. Actual media/signaling remains separate and must use Weave-issued grants instead of exposing provider authority to Flutter or native call setup.

Full native availability remains blocked until physical-device evidence proves incoming call handling, permissions, audio routing, join/revoke behavior, and provider-neutral grant usage.
