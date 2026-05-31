# Accessible workflow context contract

Status: companion contract projection for `specs/0002-context-driven-workflows/spec.md` and issue #218. The pinned Weave Specification Corpus remains canonical product/domain truth; this repo-local spec and page are implementation conformance evidence for the workflow model before any visual builder.

Weave workflows are linear, context-driven work records. They help people and governed agents coordinate work without turning the primary experience into a visual-only diagram canvas. A diagram can be added later, but the accessible linear view is the primary member-facing representation.

## Primitives

| Primitive | Contract |
| --- | --- |
| Workflow template | A reusable workflow definition that declares which Context Graph node kinds it can attach to. Initial attach points are `channel`, `project`, and `event`. |
| Workflow run | One started instance of a template. It stores the selected context node, ordered steps, current blockers, and audit requirement. |
| Step | A linear unit of work with a type, status, owner, assignee, next action, due label, context references, and evidence references. |
| Gate | A step that records a decision or readiness check before later work may be treated as complete. |
| Approval | A step that must be approved by the owning person before a suggested action can run or be shared. |
| Evidence | A support-safe pointer to an existing context node or artifact. It explains why the step is in its current state. |
| Blocker | A named condition preventing progress, with an owner and linked evidence ids. |
| Assignee | A person or governed agent assigned to the step. Agent assignment never grants autonomous execution by itself. |

## Context Graph references

Workflows do not duplicate chat messages, files, tasks, meetings, decisions, events, or agent outputs. They store references to Context Graph nodes and short display labels only.

Required node reference behavior:

- Context references use stable ids such as `channel:*`, `project:*`, `event:*`, `task:*`, `decision:*`, `file:*`, `meeting:*`, and `agent-run:*`.
- A workflow step may cite several context references, but every reference must be explicit and visible to the user.
- Evidence points back to a context reference id so reviewers can understand the source without exposing raw provider payloads.
- Background room reading is off for the MVP contract; users attach context intentionally.
- Provider-specific state stays behind existing Weave facades and support-safe evidence boundaries.

## Accessible linear view

The first workflow UI is a linear list, not a drag-only board or diagram.

The view must expose:

- workflow title, selected context, step count, blocker count, and next action;
- each step's type, status, owner, due label, next action, blockers, and evidence;
- the same status and blocker information through screen-reader semantics;
- keyboard-reachable actions for opening a step and reviewing evidence;
- non-color-only status indicators.

The client contract lives in:

- `client/lib/features/workflows/domain/entities/workflow_preview.dart`
- `client/lib/features/workflows/presentation/providers/workflow_preview_provider.dart`
- `client/lib/features/workflows/presentation/widgets/workflow_preview_panel.dart`
- `client/test/features/workflows/workflow_preview_provider_test.dart`
- `client/test/features/workflows/workflow_preview_panel_test.dart`

## Agent participation rules

Agents can be assignees, but the MVP workflow slice is dry-run only.

Rules:

1. Agent steps must show the agent assignee, context references, evidence, and next action before any owner decision.
2. `agentDryRunOnly` remains true for every MVP step.
3. `agentActionsEnabled` remains false at workflow-run level until admin policy, permissions, audit, and approvals are connected.
4. Approval steps require the owning person before generated summaries, drafts, or suggested actions are shared.
5. `auditTrailRequired` remains true for every workflow run.
6. Agent outputs are represented as `agent-run:*` context references, not hidden side effects.

## Sample workflows

| Sample workflow | Attach point | Purpose | MVP behavior |
| --- | --- | --- | --- |
| Onboard a workspace | `project` | Coordinate owners, checklist evidence, and welcome-pack approval before inviting members. | Linear steps with task and file evidence; agent help is approval-gated. |
| Prepare a release | `channel` | Link release decisions, checklist blockers, and release-note approval. | Linear gate/step/approval sequence with visible blocker evidence. |
| Resolve a support incident | `event` | Link incident triage, meeting notes, follow-up task, and support-summary approval. | Agent summary is dry-run only and must be approved before sharing. |

## MVP slice before a visual builder

The shippable MVP slice is the accessible workflow preview contract:

- domain primitives for templates, runs, steps, gates, approvals, evidence, blockers, and assignees;
- explicit Context Graph node references instead of duplicated data;
- linear screen-reader-friendly workflow panel;
- sample workflow fixtures for onboarding, release preparation, and support incident resolution;
- tests proving explicit context, governed agent dry-run behavior, and accessible semantics.

Not in the MVP slice:

- visual workflow builder;
- drag-only diagram editing;
- autonomous agent execution;
- background room scanning;
- direct provider-control-plane calls from member UI.

## Acceptance mapping

Issue #218 acceptance is satisfied by this contract and the client workflow preview evidence:

- primitives: `WorkflowTemplatePreview`, `WorkflowRunPreview`, `WorkflowStepPreview`, `WorkflowEvidenceReference`, `WorkflowBlocker`, and `WorkflowAssignee`;
- linear view: `WorkflowPreviewPanel` and its widget test;
- Context Graph references: `WorkflowContextReference` and evidence `contextReferenceId` links;
- agent rules: dry-run, approval, and audit flags in `WorkflowRunPreview` and `WorkflowStepPreview`;
- sample workflows: onboarding a workspace, preparing a release, resolving a support incident;
- MVP slice: accessible preview contract before any visual builder.
