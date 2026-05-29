---
id: WEAVE-SPEC-0003
title: Encrypted contextual meetings contract
version: 0.1.0
status: proposed
domain: product-core
owner: weave-co-leader
github_issue: 216
supersedes: []
depends_on:
  - WEAVE-SPEC-0001
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
  - flutter test test/features/chat/channel_workspace_test.dart test/architecture/meetings_contract_test.dart
---

# Feature specification: Encrypted contextual meetings contract

## Intent

Define Weave's meeting architecture and product boundaries before enabling video/audio calls. Meetings must attach to Weave context, fail closed without backend/media evidence, and be honest about encryption boundaries for signaling, media, captions, transcripts, recordings, and metadata.

## Product boundaries

### In scope

- Provider-neutral meeting capability contract for channels, calendar events, and threads.
- Architecture decision record comparing MatrixRTC/Element Call and LiveKit-style SFU options.
- Explicit encryption/evidence boundaries for signaling, media, captions, transcripts, recordings, and metadata.
- UX/accessibility contract for device selection, join preview, mute/camera state, participant list, errors, and recovery.
- Recording/transcription defaults and consent requirements.
- Tests that block vague security claims without documented evidence.

### Out of scope

- Enabling live join/start controls before backend media capability exists.
- Provider-specific meeting links as the primary Weave meeting model.
- Recording, transcription, or captions without explicit consent and storage/retention evidence.
- Member UI exposure of provider URLs, room media tokens, SFU internals, credentials, or raw diagnostics.

### Non-negotiable constraints

- Weave remains provider-neutral; member surfaces speak Weave meeting concepts, not provider setup language.
- Join/start controls must fail closed until capability, policy, and evidence are ready.
- Encryption claims must name their exact boundary and evidence.
- Group calls must document SFU/client key handling before they are described as confidential.
- Accessibility, consent, supportability, auditability, and deployability are release blockers.

## User/admin/operator stories

### US1 - Contextual meeting surface (Priority: P1)

**Actor**: Member
**Story**: As a member, I can see meetings as part of channel/event/thread work context rather than disconnected links.
**Why now**: Issue #216 requires video meetings that differentiate Weave from generic meeting links.
**Independent test**: `flutter test test/features/chat/channel_workspace_test.dart`

**Acceptance scenarios**:

1. Given a channel workspace, when the meetings surface is rendered, then it carries the channel context id and declares channel/calendar-event/thread attach points.
2. Given backend media capability is absent, when join/start controls are evaluated, then they remain disabled with an explicit capability-unavailable reason.

### US2 - Explicit encryption boundaries (Priority: P1)

**Actor**: Admin/operator/security reviewer
**Story**: As a reviewer, I can see exactly which meeting boundaries are encrypted, disabled, or metadata-only before the feature is enabled.
**Why now**: Weave must avoid vague claims about security or E2EE.
**Independent test**: `flutter test test/architecture/meetings_contract_test.dart`

**Acceptance scenarios**:

1. Given a meeting contract, when encryption is described, then Matrix signaling, media, captions, transcripts, recordings, and metadata each have evidence requirements.
2. Given product copy uses broad security language, when contract tests run, then it must also name boundary evidence or fail.

### US3 - Consent and accessible join contract (Priority: P1)

**Actor**: Member
**Story**: As a member, I can understand device, mute/camera, participant, caption/transcript, recording, error, and recovery states before joining.
**Why now**: Meetings are collaboration-critical and cannot ship as pointer-only or consent-ambiguous UI.
**Independent test**: Domain and architecture tests assert UX requirements and off-by-default recording/transcription.

**Acceptance scenarios**:

1. Given a meeting preview, when UX requirements are inspected, then device selection, join preview, mute state, camera state, participant list, and error recovery are all documented.
2. Given recording or transcription is unavailable, when consent posture is evaluated, then both remain off and the preview requires explicit consent before enablement.

## Functional requirements

- **FR-001**: Weave MUST model meeting attach points for channel, calendar event, and thread contexts.
- **FR-002**: Weave MUST keep meeting join/start controls disabled until backend media capability, policy, and evidence are ready.
- **FR-003**: Weave MUST document MatrixRTC/Element Call and LiveKit-style SFU trade-offs before selecting an implementation.
- **FR-004**: Weave MUST define encryption/evidence boundaries for Matrix signaling, media streams, captions, transcripts, recordings, and metadata.
- **FR-005**: Weave MUST NOT describe metadata as end-to-end encrypted.
- **FR-006**: Recording and transcription MUST be off by default and require explicit participant-visible consent before enablement.
- **FR-007**: The meeting UX contract MUST cover device selection, join preview, mute state, camera state, participant list, errors, and recovery.
- **FR-008**: Member UI MUST NOT expose provider URLs, meeting tokens, SFU internals, credentials, or raw diagnostics.
- **FR-009**: Tests or contract checks MUST prevent vague security claims unless boundary evidence is documented.
- **FR-010**: Meeting capability failures MUST be support-safe and actionable for admin/operator setup.

## Domain model and contracts

- Canonical Weave entities affected: ChannelMeetingPreview, ChannelMeetingAttachPoint, ChannelMeetingEncryptionBoundary, ChannelMeetingUxRequirement, ChannelMeetingControl.
- Provider/category contracts affected: future meeting backend facade, MatrixRTC/Element Call readiness, optional LiveKit-style SFU readiness.
- API/event contracts affected: future capability endpoint must expose Weave meeting readiness, not provider internals.
- Policy/RBAC/capability keys affected: meeting join/start, recording, transcription, captions, participant management.
- Audit/support evidence affected: capability state, encryption boundary evidence, consent state, metadata inventory, support-safe diagnostics.

## Acceptance and evidence mapping

- Gherkin feature path(s): none for proposed fail-closed contract slice.
- `e2e/scenario_mappings.json` marker(s): none for proposed fail-closed contract slice.
- Unit/widget/backend/admin/contract test path(s): `client/test/features/chat/channel_workspace_test.dart`, `client/test/architecture/meetings_contract_test.dart`.
- Live Stack E2E required? no; join/start stays disabled.
- Support-safe evidence artifact(s): local Flutter/Gradle test output; CI summary under `build/evidence/**` when merged through PR.

## Release and migration impact

- Member impact: clearer meeting readiness and contextual meeting boundaries; no enabled media join yet.
- Admin/operator impact: explicit evidence requirements before enabling meeting providers.
- Developer/API impact: extends channel workspace domain model with meeting attach points, encryption boundaries, and UX requirements.
- Data migration/backfill: none for fail-closed contract.
- Rollback/reversibility: remove meeting contract fields/UI references without provider data migration.
- Release-notes label expected: `release-notes-feature`

## Open questions

- [ ] Which backend facade owns MatrixRTC/Element Call readiness and token issuance?
- [ ] Which policy keys govern recording, transcription, captions, and participant management?
- [ ] Which acceptance feature should cover first live join/start flow after backend capability exists?
