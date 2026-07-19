# Implementation plan: Matrix-native Calls strict cutover

**Spec**: `specs/0003-contextual-meetings/spec.md`

**Issue**: #968

**Status**: target accepted; implementation Guarded

## Ordered slices

1. Pin the matching canonical weave-specs corpus and Profile-0 contract.
2. Delete proprietary member Calls routes, DTOs, generated models, tests, and discovery claims.
3. Add organization discovery, MAS Native OAuth, Keycloak upstream identity, and token-type separation.
4. Add exact Profile-0 transport discovery and pinned MSC golden fixtures; reject old wire shapes.
5. Add the isolated Ruma `matrixrtc_wire` gap module.
6. Implement RTC Authorizer identity, membership, role, policy, expiry, and replay checks.
7. Integrate LiveKit as transport without exposing provider administration or secrets.
8. Add thin Flutter `NativeCallCoordinator` integrations for CallKit and Android Core-Telecom.
9. Prove media E2EE, key/device recovery, TURN, reconnect, physical devices, and accessibility.
10. Add recording/transcription only after consent, decryption, artifact, and retention gates pass.

There is no compatibility phase. Older application routes and MatrixRTC proposal shapes fail
closed after cutover.

## Validation

- `./gradlew specCorpusConformance acceptanceContract`
- `./gradlew serverCi`
- `cd client && flutter test test/architecture/meetings_contract_test.dart`
- repository scans for `/api/calls`, `/api/weave/calls`, `com.weave.call.*`, and old Calls DTOs

Documentation and offline guards are not live Calls evidence. Issue #968 remains open and the
capability remains Guarded until every runtime, interop, security, device, and accessibility gate passes.
