# Tasks: Encrypted contextual meetings contract

**Spec**: `specs/0003-contextual-meetings/spec.md`

## Implementation

- [x] Document architecture options and E2EE trade-offs.
- [x] Define signaling/media/caption/transcript/recording/metadata boundaries.
- [x] Extend channel meeting preview domain model with contextual attach points.
- [x] Encode device/join/mute/camera/participant/error UX requirements.
- [x] Keep recording/transcription off by default.
- [x] Add first-proof scenarios for join/start plus captions/transcript consent, privacy, retention, and audit evidence.

- [x] Add contract tests that guard vague security claims.

## Evidence

- [ ] `./gradlew specContract acceptanceContract`
- [ ] `cd client && flutter test test/features/chat/channel_workspace_test.dart test/architecture/meetings_contract_test.dart`
- [ ] `./gradlew clientCi`
- [ ] `make docs-check`
- [ ] GitHub PR checks green
