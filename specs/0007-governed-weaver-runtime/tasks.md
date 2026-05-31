# Tasks: Governed Weaver runtime and tool approval contract

**Spec**: `specs/0007-governed-weaver-runtime/spec.md`

- [x] T001 [#433] Document Weaver runtime profile schema and runtime/model/tool provider separation.
- [x] T002 [#433] Keep profile generated from workspace capability policy and disabled by default.
- [x] T003 [#446] Document OpenClaw fork/image/SBOM/scan evidence requirements without publishing a runtime image.
- [x] T004 [#446] Prove per-user runtime profile has support-safe user refs and isolation posture.
- [x] T005 [#447] Document ToolGrant/CapabilityGrant/ApprovalPolicy/ApprovalReceipt enforcement semantics.
- [x] T006 [#448] Implement Weaver domain tool registry v1 with grant-filtered discovery.
- [x] T007 [#448] Block unauthorized tool invocation and require approvals for write-like tools.
- [x] T008 [#448] Audit and redact tool invocation results.
- [x] T009 [#449] Add security/privacy/accessibility/support-safe evidence report.
- [ ] T010 [Evidence] Run `./gradlew specContract acceptanceContract serverCi docsCheck --console=plain`.

## Sprint 13 RuntimeProfile projection refresh

- [x] T011 [#519/#522] Define the signed `WeaverRuntimeProfile` as the only source consumed by the OpenClaw-derived runtime.
- [x] T012 [#519/#522] Preserve stable `channels.weave-chat` for Chat-domain provider changes while Matrix, Teams, iMessage, Slack, and future providers remain backend `providerRef` bindings.
- [x] T013 [#519/#522] Require CredentialRefs and short-lived runtime token references only; exclude provider secrets and OAuth refresh tokens from profiles, logs, support bundles, and release evidence.
- [x] T014 [#519/#522] Map the provider-change acceptance evidence to the Sprint 13 RuntimeProfile projection and Weaver/OpenClaw architecture boundary.
- [x] T015 [#526] Define the per-user runtime context/container lifecycle with signed RuntimeProfile input, isolated state/workspace/agentDir, internal-only network, quota/memory boundaries, and reload/restart/rollback/revocation gates.
- [x] T016 [#526] Add infra static evidence for lifecycle contract and support-bundle redaction of Weaver runtime tokens, SecretRefs, and raw provider material.
- [x] T015 [#524] Add Admin Console support-safe RuntimeProfile projection management for Chat, model, tool, skill, MCP, revocation, and audit previews.
