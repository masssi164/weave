# Implementation plan: Matrix-native Calls and Meetings

**Spec**: `specs/0003-contextual-meetings/spec.md`  
**Issue**: #968  
**Status**: target locked; implementation pending; Experimental/Guarded  
**Complete handoff**: `docs/implementation-plans/matrixrtc-stateless-runtime.md`

## Summary

Migrate from the former member-facing LiveKit join-grant facade to Matrix v1.19 Native OAuth and pinned MatrixRTC Profile 0. LiveKit stays as the first transport/SFU. Preserve useful legacy fixtures behind guarded adapters until parity and rollback evidence permit removal.

## Ordered slices

1. Merge and pin the matching canonical weave-specs decisions and contracts.
2. Implement organization URL discovery, MAS Native OAuth, Keycloak upstream identity and token-type separation.
3. Add Profile-0 transport discovery plus exact pinned MSC golden fixtures.
4. Add the isolated Ruma `matrixrtc_wire` gap module and dual-read/single-write handling.
5. Implement the RTC Authorizer with independent identity, room, slot, member and policy checks.
6. Integrate LiveKit media without exposing provider administration or secrets.
7. Add the thin Flutter `NativeCallCoordinator` for CallKit and Android Core-Telecom.
8. Prove MatrixRTC media E2EE, device recovery, TURN/reconnect and multi-device behavior.
9. Add consent-governed recording/transcription and Files/WebDAV artifact lifecycle only after its separate gates pass.
10. remove the deprecated proprietary member paths after parity, rollback and migration gates pass.

## Required evidence

- spec corpus, acceptance and documentation conformance;
- exact wire fixtures and negative authorization/replay tests;
- foreign-client/Element interoperability;
- ciphertext-only SFU evidence for private calls;
- physical iOS and Android incoming/background/audio-route evidence;
- accessibility, consent, retention/export/delete and support-safe audit;
- repository scans for forbidden new `/api/weave/calls` and `com.weave.call.*` dependencies.

## Risks and mitigations

- **Open MSC drift**: freeze exact commits; inspect `/versions`; dual-read, MSC4143 single-write; never call the profile stable.
- **Identity confused with authorization**: keep Matrix OAuth, OIDC and Matrix OpenID credential types separate; verify room/call policy independently.
- **Ruma coverage lag**: isolate exact-name custom types and give every type an upstream deletion criterion.
- **SFU E2EE overclaim**: require MatrixRTC media E2EE and document metadata; keep recording/transcription default-off.
- **Legacy drift**: permit old code only behind a deprecated internal adapter with an owner, removal issue/date and parity tests.
- **Native lifecycle bugs**: keep OS integration thin and require physical-device evidence.

Documentation is not implementation evidence. Do not mark #968 complete or claim Ready until every applicable release gate is green.
