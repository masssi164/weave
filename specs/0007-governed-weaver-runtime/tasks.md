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
