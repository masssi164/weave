---
id: WEAVE-SPEC-0003
title: Matrix-native contextual Calls and Meetings
version: 0.2.0
status: proposed
domain: product-core
owner: weave-co-leader
github_issue: 968
supersedes:
- issue-216-livekit-member-facade-direction
depends_on:
- WEAVE-SPEC-0001
acceptance_features:
- e2e/features/weave_spec_0003_acceptance.feature
evidence_gates:
- ./gradlew specContract
- ./gradlew acceptanceContract
- flutter test test/features/chat/channel_workspace_test.dart test/architecture/meetings_contract_test.dart
---

# Feature specification: Matrix-native contextual Calls and Meetings

## Truth and status

This repo-local packet is a **transitional conformance projection**, not canonical product truth. Canonical truth is the pinned Weave Specification Corpus. The target is proposed and **Experimental/Guarded**; it does not claim that MatrixRTC, LiveKit integration, native calls or the stateless Weaver runtime are implemented or Ready.

This revision supersedes the earlier direction that made LiveKit join grants and a Weave member Calls API the long-term facade. Existing implementation and fixtures remain guarded migration evidence only.

## Intent

Calls are Core collaboration attached to Matrix rooms, channels, calendar events and threads. The member northbound contract is Matrix v1.19 Client-Server plus the exact pinned `weave.matrixrtc/profile-0`. LiveKit is the first replaceable RTC transport/SFU, not the product API.

## In scope

- organization-URL discovery, Matrix Native OAuth, MAS and Keycloak upstream identity;
- MatrixRTC slot, sticky membership, ringing, decline and media-key events;
- an RTC Authorizer that separates Matrix identity proof from room/call authorization;
- a Ruma facade with one isolated exact-name `matrixrtc_wire` gap module;
- LiveKit/WebRTC media behind short-lived least-privilege grants;
- thin Flutter CallKit/Core-Telecom coordination;
- explicit media E2EE, consent, artifact and metadata boundaries;
- contextual references from chat, calendar and files;
- guarded migration and removal criteria for legacy Calls paths.

## Out of scope

- a member `/api/weave/calls` data plane;
- proprietary `com.weave.call.*` events where Matrix/MSC semantics exist;
- exposing LiveKit admin APIs, API secrets, long-lived tokens or provider diagnostics;
- treating a Matrix OpenID identity proof as room/call authorization;
- calling open MatrixRTC MSCs a stable standard;
- claiming DTLS-SRTP alone is end-to-end encryption against the SFU;
- recording/transcription without explicit participant-visible consent and a disclosed decryption boundary;
- lossless migration of an active call between RTC transports.

## Interoperable client flow

1. Discover the homeserver through `/.well-known/matrix/client`.
2. Read `/_matrix/client/v1/auth_metadata`.
3. Use Dynamic Client Registration where required and Authorization Code + PKCE S256.
4. Authenticate through Matrix Authentication Service, with Keycloak as upstream organizational IdP.
5. Verify with `/whoami`, inspect `/versions`, and use the Profile-0 selected authenticated RTC transport endpoint.
6. Resolve/open `m.rtc.slot` and publish sticky `m.rtc.member`.
7. Request a separate Matrix OpenID credential.
8. Exchange it at the RTC Authorizer after independent room/call policy checks.
9. Connect to LiveKit over WebRTC with a short-lived bound JWT.

## Functional requirements

- **FR-001**: Calls MUST attach to Matrix room/channel, calendar-event and thread context.
- **FR-002**: Join/start MUST remain fail-closed until discovery, policy, authorization, media and evidence gates pass.
- **FR-003**: The northbound contract MUST be Matrix v1.19 plus the exact pinned MatrixRTC Profile 0.
- **FR-004**: MAS MUST be the Matrix-facing Native OAuth authorization server and MUST use Keycloak as upstream organizational identity provider for the target deployment.
- **FR-005**: Matrix OAuth access tokens, OIDC ID tokens and Matrix OpenID credentials MUST be distinct code types.
- **FR-006**: Profile 0 MUST use exact MSC commits, dual-read/single-write compatibility and MSC4143 as the authoritative write model.
- **FR-007**: New member paths MUST NOT depend on `/api/weave/calls` or `com.weave.call.*` events.
- **FR-008**: Missing Ruma types MUST live in one `matrixrtc_wire` module with exact Matrix names, golden fixtures, upstream tracking and deletion criteria.
- **FR-009**: The RTC Authorizer MUST verify identity and current room/slot/member/policy authorization independently.
- **FR-010**: LiveKit grants MUST be short-lived, replay-resistant and bound to user, room, slot, member/device, alias, permissions, policy revision and nonce.
- **FR-011**: Federated authorization MUST remain Guarded until sufficiently standard, proven membership attestation exists.
- **FR-012**: The shared Rust/Ruma core MUST own Matrix discovery, OAuth, sync/E2EE and MatrixRTC state.
- **FR-013**: A thin NativeCallCoordinator MUST own only CallKit/Core-Telecom lifecycle, audio routing and idempotent correlation.
- **FR-014**: Private calls MUST require an encrypted Matrix room and MatrixRTC media E2EE.
- **FR-015**: Product claims MUST state SFU-visible metadata and MUST NOT call DTLS-SRTP alone E2EE against the SFU.
- **FR-016**: Recording and transcription MUST be off by default and require visible consent plus a disclosed trusted decrypting boundary or separate non-E2EE profile.
- **FR-017**: Governed artifacts MUST use Files/WebDAV retention, export and deletion policy.
- **FR-018**: Calendar MUST store a stable Matrix/slot reference, never a durable LiveKit credential.
- **FR-019**: Legacy join-grant/readiness code MAY remain only behind an internal deprecated adapter with owner, removal date/issue, parity and rollback tests.
- **FR-020**: No Ready claim is allowed until interoperability, authorization, E2EE, recovery, TURN/reconnect, physical-device, consent and accessibility gates pass.

## Domain and implementation contracts

- Matrix room, `m.rtc.slot`, sticky `m.rtc.member`, notification and decline are protocol truth.
- Weave owns policy, entitlement, provider readiness, audit, consent and governed artifact rules.
- The RTC Authorizer projects Weave policy into short-lived transport grants.
- LiveKit owns media transport behavior only.
- CallKit and Android Core-Telecom own OS call UI/lifecycle/audio routing only.
- Existing `ChannelMeetingPreview`, attach-point, encryption-boundary and UX-requirement entities remain useful contextual projections; provider-specific IDs never become their canonical identity.

## Acceptance and evidence

- Golden JSON fixtures cover every pinned MSC, aliases, unknown fields and draft-conflict resolution.
- A foreign client joins without any Weave member Calls endpoint.
- Negative tests cover wrong room/member/device, stale membership, closed slot, escalation, expiry and replay.
- Private-call evidence proves ciphertext-only SFU media and device/key recovery.
- Physical iOS and Android evidence covers incoming/background/audio route behavior.
- Consent, recording/transcription, late join/revoke, accessibility and WebDAV artifact deletion are evidenced.
- Repository scans and migration records prove legacy paths are guarded and removable.

Normative repo-local projections:

- `docs/meeting-architecture-decision.md`
- `docs/architecture/matrixrtc-profile-0.yaml`
- `docs/implementation-plans/matrixrtc-stateless-runtime.md`
