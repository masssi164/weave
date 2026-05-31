---
id: WEAVE-SPEC-0007
title: Governed Weaver runtime and tool approval contract
version: 0.1.0
status: implementing
domain: weaver-runtime
owner: weave-security-compliance-lead
github_issue: 433
supersedes: []
depends_on:
  - WEAVE-SPEC-0001
acceptance_features:
  - e2e/features/v0_1_dogfood_release.feature
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
  - ./gradlew serverCi
---

# Feature specification: Governed Weaver runtime and tool approval contract

## Intent

Define the first implementation-ready Weaver runtime contract without making Weave agent-first. Weaver remains optional, disabled by default, per-user, auditable, and generated from organization policy plus user rights.

## In scope

- OpenClaw-derived per-user runtime profile schema.
- Clear separation between runtime provider, model provider, and tool provider.
- Per-user isolation boundary for workspace, memory, and sessions.
- Domain-scoped Weave tool registry generated from approved capabilities.
- Approval receipts for write/delete/external-send/provider-switch actions.
- Security, privacy, accessibility, and support-safe release evidence requirements.

## Out of scope

- Starting real runtime containers.
- Publishing a production image or release.
- Autonomous Weaver writes without approval receipts.
- Raw provider-token delivery to runtime.
- Admin visibility into member private memory by default.
- Live infrastructure mutation.

## Functional requirements

- **FR-001**: The runtime profile MUST expose runtime provider, model provider, and tool provider as separate concepts.
- **FR-002**: The runtime profile MUST be generated from workspace capability policy and MUST remain disabled by default.
- **FR-003**: The runtime profile MUST use support-safe user references and MUST NOT contain raw provider tokens.
- **FR-004**: The runtime profile MUST express tools as Weave domain capabilities, not provider APIs.
- **FR-005**: Tool discovery MUST filter by the generated user grants.
- **FR-006**: Unauthorized tool invocation MUST be blocked and audited.
- **FR-007**: Write/delete/external-send/provider-switch actions MUST require approval receipts before invocation.
- **FR-008**: Tool schemas MUST be explicit, versioned, and domain-scoped.
- **FR-009**: Tool results and failures MUST be support-safe and redacted before returning to the runtime.
- **FR-010**: Admin/operator evidence MUST include policy posture and audit metadata, not private member memory content.
- **FR-011**: Release evidence MUST capture OpenClaw fork/image digest/SBOM/scan references before any release claim.
- **FR-012**: Weaver approval UX MUST be screen-reader accessible and must not rely on color-only state.

## Initial tool set

- `calendar.search_events` read-only.
- `boards.search_tasks` read-only.
- `files.search` read-only.
- `chat.search_messages` read-only or guarded by chat policy.
- `notifications.create_action_request` guarded external-send.
- `boards.comment` write-with-approval.

## Acceptance mapping

- `@weave-v01-governed-weaver-runtime-policy` proves profile generation, disabled-by-default posture, policy intersection, and audit.
- `@weave-v01-governed-weaver-tool-registry` proves domain-tool discovery, blocking, approval receipts, redaction, and audit.
- `@weave-v01-provider-switch-portability` remains the governing acceptance boundary for provider-switch approval and support-safe evidence.

## Evidence

- `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`
- `server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java`
- `docs/governed-weaver-runtime-security-contract.md`
- `docs/evidence/weaver-security-privacy-accessibility-report.md`
