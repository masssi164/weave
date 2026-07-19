---
id: WEAVE-SPEC-0003
title: Matrix-native contextual Calls and Meetings
version: 0.2.0
status: proposed
capability_state: experimental-guarded
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

## Intent

Calls are Core collaboration attached to Matrix rooms, channels, calendar events, and threads.
The member northbound contract is Matrix v1.19 Client-Server plus the exact pinned
`weave.matrixrtc/profile-0`. LiveKit is the first replaceable RTC transport/SFU, not a product
API. The capability is Experimental/Guarded until live interoperability, security, media,
device, consent, and accessibility evidence passes.

## Product boundaries

### In scope

- organization-URL discovery, Matrix Native OAuth, MAS, and Keycloak upstream identity;
- MatrixRTC slot, sticky membership, ringing, decline, and media-key events;
- an RTC Authorizer that separates Matrix identity proof from current room/call authorization;
- a Ruma facade with one exact-name `matrixrtc_wire` gap module;
- LiveKit/WebRTC media behind short-lived, least-privilege authorization;
- thin Flutter CallKit/Core-Telecom coordination;
- explicit media E2EE, consent, artifact, retention, and metadata boundaries;
- contextual references from Matrix, CalDAV/iCalendar, and Files/WebDAV.

### Out of scope

- a member `/api/calls` or `/api/weave/calls` surface;
- proprietary `com.weave.call.*` events or compatibility readers;
- exposing LiveKit administration, secrets, durable credentials, or provider diagnostics;
- treating Matrix OpenID identity proof as current room/call authorization;
- calling open MatrixRTC MSCs a stable standard;
- recording/transcription without visible consent and a disclosed decryption boundary;
- lossless live migration of an active call between transports.

### Non-negotiable constraints

- Keycloak is the organization IDM backbone; MAS owns the Matrix-facing Native OAuth role.
- Profile 0 uses one exact MatrixRTC shape for reads and writes; no dual-read or legacy fallback.
- Join/start fails closed until discovery, policy, RTC authorization, media, and evidence gates pass.
- Private-call claims require Matrix room encryption and MatrixRTC media E2EE evidence.
- DTLS-SRTP alone is not described as E2EE against the SFU.
- Accessibility, consent, supportability, auditability, and deployability are release blockers.

## User/admin/operator stories

### US1 - Contextual Matrix-native call (Priority: P1)

**Actor**: Member

**Story**: As a member, I join a call from Matrix room/calendar context without a proprietary
Weave Calls API.

**Independent test**: architecture and foreign-client conformance tests reject proprietary routes.

### US2 - Exact authorization and encryption truth (Priority: P1)

**Actor**: Security reviewer

**Story**: As a reviewer, I can distinguish MAS/Keycloak identity, Matrix OpenID proof, current
room authorization, transport grants, media E2EE, SFU-visible metadata, and artifact decryption.

### US3 - Accessible native call lifecycle (Priority: P1)

**Actor**: Mobile member

**Story**: As a mobile member, CallKit/Core-Telecom behavior remains a thin accessible projection
of MatrixRTC and media state, with deterministic answer, decline, end, hold, mute, and recovery.

## Interoperable client flow

1. Discover the homeserver through `/.well-known/matrix/client`.
2. Read `/_matrix/client/v1/auth_metadata`.
3. Use registration where required and Authorization Code + PKCE S256.
4. Authenticate through MAS with Keycloak as its upstream organization IdP.
5. Verify with `/whoami`, inspect `/versions`, and fetch the exact Profile-0 RTC transport endpoint.
6. Resolve/open `m.rtc.slot` and publish sticky `m.rtc.member`.
7. Request a separate Matrix OpenID credential.
8. Exchange it at the RTC Authorizer after current room/call policy checks.
9. Connect to LiveKit over WebRTC with a short-lived, bound JWT.

## Functional requirements

- **FR-001**: Calls MUST attach to Matrix room/channel, calendar-event, and thread context.
- **FR-002**: Join/start MUST fail closed until discovery, policy, authorization, media, and evidence gates pass.
- **FR-003**: The northbound contract MUST be Matrix v1.19 plus exact pinned MatrixRTC Profile 0.
- **FR-004**: MAS MUST be the Matrix-facing Native OAuth server and use Keycloak as upstream IDM.
- **FR-005**: Matrix OAuth access, OIDC ID, and Matrix OpenID credentials MUST be distinct types.
- **FR-006**: Profile 0 MUST accept and emit only its exact MSC4143-based wire shape.
- **FR-007**: Current member paths MUST NOT expose `/api/calls`, `/api/weave/calls`, or `com.weave.call.*`.
- **FR-008**: Missing Ruma types MUST live only in `matrixrtc_wire` with exact names and fixtures.
- **FR-009**: The RTC Authorizer MUST independently verify identity and current room/call policy.
- **FR-010**: LiveKit grants MUST be short-lived, replay-resistant, least-privilege, and fully bound.
- **FR-011**: Federated authorization MUST remain Guarded until membership attestation is proven.
- **FR-012**: The shared Rust/Ruma core MUST own Matrix discovery, OAuth, sync/E2EE, and RTC state.
- **FR-013**: NativeCallCoordinator MUST own only OS lifecycle, audio routing, and correlation.
- **FR-014**: Private calls MUST require an encrypted Matrix room and MatrixRTC media E2EE.
- **FR-015**: Product claims MUST state SFU-visible metadata and reject DTLS-SRTP-only E2EE claims.
- **FR-016**: Recording/transcription MUST be off by default with visible consent and decryption truth.
- **FR-017**: Governed artifacts MUST use Files/WebDAV retention, export, and deletion policy.
- **FR-018**: Calendar MUST store a stable Matrix/slot reference, never a durable LiveKit credential.
- **FR-019**: The proprietary Calls API, join models, fixtures, and generated clients MUST be removed.
- **FR-020**: Ready requires interop, authz, E2EE, recovery, TURN, device, consent, and a11y proof.

## Recording/transcription defaults and consent requirements

Recording and transcription are disabled by default. Enabling either requires explicit,
participant-visible consent, a visible active-state indicator, a named trusted decrypting
participant/boundary or separately named non-E2EE profile, provenance, retention, export,
deletion, revocation, late-join behavior, and accessible caption/transcript evidence.

## Domain model and contracts

- Protocol truth: Matrix room, `m.rtc.slot`, sticky `m.rtc.member`, notification, decline, and key events.
- Weave truth: organization policy, entitlement, provider readiness, consent, audit, and artifact rules.
- RTC Authorizer: current Weave policy projected into a short-lived transport grant.
- LiveKit: media transport only; no stable product identifiers or member administration API.
- Flutter: shared Rust/Ruma protocol core plus thin native OS and media coordination.
- Existing contextual preview/attach-point/encryption/UX entities remain provider-neutral projections.

## Acceptance and evidence mapping

- Exact JSON fixtures cover each pinned profile event and reject old/ambiguous wire shapes.
- A foreign client discovers and joins without any Weave member Calls endpoint.
- Negative tests cover wrong room/member/device, stale membership, escalation, expiry, and replay.
- Private-call evidence covers ciphertext-only SFU media and device/key recovery.
- Physical iOS and Android evidence covers background, incoming, and audio-route behavior.
- Consent, accessibility, artifact retention/export/delete, and support-safe evidence are required.
- Repository scans reject proprietary Calls routes, events, DTOs, and generated models.

## Release and migration impact

- Member impact: the unfinished proprietary Calls surface is removed; MatrixRTC remains unavailable until guarded gates pass.
- Admin/operator impact: MAS, RTC transport, authorizer, TURN, media E2EE, and consent readiness become explicit.
- Developer impact: call signaling and membership move to Rust/Ruma MatrixRTC; LiveKit becomes transport-only.
- Data impact: incompatible dogfood call state may be backed up and reset; no application import/compatibility path exists.
- Rollback: revert the whole cutover commit before new target state exists; never run old and new contracts together.
- Release label: `release-notes-feature`.

## Open questions

- [ ] Which standard/proven attestation closes federated RTC authorization?
- [ ] Which trusted decrypting profiles may be offered for recording/transcription?
- [ ] Which physical-device matrix is sufficient for the first Guarded pilot?
