---
id: WEAVE-SPEC-0002
title: Context-driven workflow primitives
version: 0.1.0
status: proposed
domain: product-core
owner: weave-co-leader
github_issue: 218
supersedes: []
depends_on:
- WEAVE-SPEC-0001
acceptance_features:
- e2e/features/weave_spec_0002_acceptance.feature
evidence_gates:
- ./gradlew specContract
- ./gradlew acceptanceContract
- flutter test test/features/workflows/workflow_preview_provider_test.dart test/features/workflows/workflow_preview_panel_test.dart
---

# Feature specification: Context-driven workflow primitives

## Intent

Define the provider-neutral workflow primitives that let teams run expert processes from Weave context objects without requiring a visual-only workflow builder. The first slice is a linear, accessible preview contract that can attach workflow templates to channels, projects, and events; reference existing Weave context nodes; and keep agent participation proposal-first, approval-gated, and auditable.

2026-06-12 Northstar amendment: the first workflow execution stage is not uncontrolled automation. It is governed executable workflows with explicit policy boundaries, action preview, human approval gates for high-risk/destructive/external/provider/release actions, persisted approval receipts, outcome/compensation references, and immutable support-safe audit. Preview-only workflow UI remains insufficient for executable-workflow claims.


## Product boundaries

### In scope

- Workflow templates attachable to channel, project, and event contexts.
- Workflow runs that present ordered steps in a linear, screen-reader-friendly view.
- Step references to tasks, decisions, files, meetings, and agent runs through Context Graph node references.
- Agent participation rules for dry-run proposals, explicit approval, and audit evidence.
- Sample workflow templates for workspace onboarding, release preparation, and support incident resolution.
- MVP slice before any visual builder: preview/read-only workflow runs with explicit context and governed action metadata.

### Out of scope

- Visual workflow builder, drag-only workflow editing, or pointer-only execution paths.
- Silent agent writes, continuous room reading, or autonomous execution outside organization policy.
- Provider-specific workflow engines as the product model.
- Raw provider payloads, endpoints, credentials, or diagnostics in member workflow views.
- Cross-provider workflow migration automation beyond stable identifiers and support-safe evidence references.

### Non-negotiable constraints

- Weave remains product-first and provider-neutral.
- Normal members see Weave workflow objects and capability states, not provider workflow internals.
- Workflow steps must be operable and understandable without drag/drop or visual diagrams.
- Every context reference must resolve to a server-owned canonical Context Graph node or fail closed.
- Agent-assigned steps are dry-run/proposal-only until explicit approval and audit receipt exist.
- Accessibility, supportability, auditability, and deployability are release blockers.
- Weaver/OpenClaw runtime remains governed and disabled-by-default unless a later accepted spec enables runtime execution.

## User/admin/operator stories

### US1 - Linear workflow preview (Priority: P1)

**Actor**: Member
**Story**: As a member, I can open active workflows for my workspace context as a linear list of steps with owners, state, blockers, evidence, and next actions.
**Why now**: Issue #218 requires expert-grade workflows without inaccessible visual spaghetti.
**Independent test**: `flutter test test/features/workflows/workflow_preview_provider_test.dart test/features/workflows/workflow_preview_panel_test.dart`

**Acceptance scenarios**:

1. Given a selected channel, project, or event context, when workflows are previewed, then each run shows a template, run id, title, context label, ordered steps, explicit context references, evidence, blockers, and next action copy.
2. Given a workflow contains blocked or approval steps, when the member reviews the linear view, then blockers and approval needs are exposed in text and not by color alone.

### US2 - Context Graph references instead of duplicated data (Priority: P1)

**Actor**: Developer
**Story**: As a developer, I can model workflow step references as canonical Context Graph node references instead of copying task, decision, file, meeting, or agent-run data into workflows.
**Why now**: Workflows must remain portable across provider swaps and domain facades.
**Independent test**: Domain/unit tests verify reference kind, explicitness, evidence linkage, and fail-closed assumptions before backend execution exists.

**Acceptance scenarios**:

1. Given a workflow step references a task, decision, file, meeting, or agent run, when the workflow is rendered, then the workflow stores the node reference id/kind/label/source label and not raw provider data.
2. Given a reference is implicit or missing evidence, when explainability is evaluated, then the run is not considered explainable.

### US3 - Governed agent participation (Priority: P1)

**Actor**: Admin and member
**Story**: As an admin/member, I can see when an agent is assigned only to prepare a dry-run/proposal and when owner approval is required before action.
**Why now**: Agent participation is useful only if it preserves user rights, organization-whitelisted capabilities, and auditability.
**Independent test**: Provider tests assert agent actions disabled, dry-run-only steps, approval flags, and audit requirements.

**Acceptance scenarios**:

1. Given a step is assigned to an agent, when the workflow is shown, then the step states whether approval is required and whether the agent action is dry-run-only.
2. Given a workflow allows background room reading or direct agent actions without audit, when governance is evaluated, then it is not acceptable for the MVP preview.

## Functional requirements

- **FR-001**: Weave MUST model workflow templates with stable ids, titles, descriptions, and attachable context kinds.
- **FR-002**: Weave MUST model workflow runs with stable run ids, template references, context labels, ordered steps, and audit/governance flags.
- **FR-003**: Workflow steps MUST include id, kind, state, owner, assignee, next action, due label, context references, evidence, blockers, approval requirement, and agent dry-run state.
- **FR-004**: Workflow context references MUST use canonical node ids and kinds for channel, project, event, task, decision, file, meeting, and agent run references.
- **FR-005**: Workflow evidence MUST link back to explicit context references and remain support-safe.
- **FR-006**: The MVP member view MUST provide a linear, accessible path before any visual builder.
- **FR-007**: Agent workflow participation MUST be dry-run/proposal-only unless a later accepted spec defines approval receipts, audit persistence, and runtime execution.
- **FR-008**: Weave MUST NOT duplicate raw provider records, provider secrets, endpoints, or diagnostics into workflow objects.
- **FR-009**: Weave MUST include sample templates for onboarding a workspace, preparing a release, and resolving a support incident.
- **FR-010**: Workflow references that cannot be authorized or resolved through server-owned context contracts MUST fail closed before execution or provider access.
- **FR-011**: Executable workflow runs MUST persist a workflow instance, policy decision, action preview, approval receipt when required, execution outcome, rollback or compensation reference where applicable, and support-safe audit correlation id.
- **FR-012**: Workflow execution MUST fail closed when the workflow definition, policy version, approval receipt, tool contract, runtime profile, or referenced context node has drifted since approval.


## Domain model and contracts

- Canonical Weave entities affected: WorkflowTemplate, WorkflowRun, WorkflowStep, WorkflowContextReference, WorkflowEvidenceReference, WorkflowBlocker, WorkflowAssignee.
- Provider/category contracts affected: none directly in MVP; later execution must use server facades for chat, files, boards/tasks, calendar/events, meetings, decisions, and governed Weaver runtime.
- API/event contracts affected: future backend contract must expose workflow previews/runs with Context Graph node references; no provider-specific payloads in member responses.
- Policy/RBAC/capability keys affected: workflow preview availability, workflow execution approval, agent dry-run/proposal, audit receipt requirements.
- Audit/support evidence affected: workflow run id, step id, approval requirement, context reference ids, evidence ids, and support-safe source labels.

## Sample workflows

- Workspace onboarding: attaches to a project, references onboarding tasks and welcome-pack files, requires owner review before invites or agent-written copy.
- Release preparation: attaches to a channel, references decisions, release checklist tasks, release-note files, and dry-run agent review before publication.
- Support incident resolution: attaches to an incident event, references meeting notes, follow-up tasks, and dry-run agent summaries before owner approval.

## Acceptance and evidence mapping

- Gherkin feature path(s): `e2e/features/northstar_spec_decisions.feature` for governed executable-workflow claim control; preview-only unit/widget coverage remains in client tests.
- `e2e/scenario_mappings.json` marker(s): `NORTHSTAR_WORKFLOW_GOVERNANCE_RECEIPT`.
- Unit/widget/backend/admin/contract test path(s): `client/test/features/workflows/workflow_preview_provider_test.dart`, `client/test/features/workflows/workflow_preview_panel_test.dart`.
- Live Stack E2E required? no; MVP is local preview/domain contract only.
- Support-safe evidence artifact(s): local Flutter test output; later CI summary under `build/evidence/**` when merged through PR.

## Release and migration impact

- Member impact: accessible preview of workflow runs and blockers.
- Admin/operator impact: no provider setup surface in MVP; later policy/audit controls required for execution.
- Developer/API impact: establishes client/domain primitives that must be backed by server-owned context references before persistence/execution.
- Data migration/backfill: none for preview-only MVP.
- Rollback/reversibility: remove preview feature without provider/data migration.
- Release-notes label expected: `release-notes-feature`

## Open questions

- [ ] Which backend endpoint owns persisted workflow templates/runs after preview-only MVP?
- [ ] Which policy key names govern workflow execution approval and agent dry-run visibility?
- [ ] Which acceptance feature should cover first persisted workflow execution after preview-only MVP?
