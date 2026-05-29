# Implementation plan: Encrypted contextual meetings contract

**Spec**: `specs/0003-contextual-meetings/spec.md`
**Branch**: `issue-216-meetings-contract`
**Date**: 2026-05-29

## Summary

Implement a fail-closed meeting contract for issue #216. The slice documents architecture options and extends the channel workspace meeting domain so future join/start work cannot claim security without boundary evidence.

## Steps

1. Add the meeting architecture decision record in `docs/meeting-architecture-decision.md`.
2. Extend `ChannelMeetingPreview` with attach points, encryption boundaries, and UX requirements.
3. Cover the domain contract in `client/test/features/chat/channel_workspace_test.dart`.
4. Add an architecture guard in `client/test/architecture/meetings_contract_test.dart`.
5. Update documentation navigation and release notes.
6. Run `specContract`, `acceptanceContract`, `clientCi`, `docs-check`, release label check, and GitHub CI.

## Risks and mitigations

- Risk: product copy overstates E2EE. Mitigation: boundary/evidence tables and architecture tests.
- Risk: provider internals leak into member UI. Mitigation: provider-neutral domain fields and existing member-client boundary tests.
- Risk: accessibility is deferred. Mitigation: UX requirements are release-blocking before join/start enablement.
