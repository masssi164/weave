import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/workflows/domain/entities/workflow_preview.dart';

final workflowPreviewFacadeProvider = Provider<WorkflowPreviewFacade>(
  (ref) => const WorkflowPreviewFacade(),
);

enum WorkflowContextSeedKind { channel, project, event }

class WorkflowContextSeed {
  const WorkflowContextSeed({
    required this.id,
    required this.kind,
    required this.label,
  });

  final String id;
  final WorkflowContextSeedKind kind;
  final String label;
}

class WorkflowPreviewFacade {
  const WorkflowPreviewFacade();

  WorkflowPreviewSnapshot previewForWorkspace({
    List<WorkflowContextSeed> contexts = const <WorkflowContextSeed>[],
  }) {
    final channel = _contextOrDefault(
      contexts,
      WorkflowContextSeedKind.channel,
      const WorkflowContextSeed(
        id: 'channel:general',
        kind: WorkflowContextSeedKind.channel,
        label: 'general',
      ),
    );
    final project = _contextOrDefault(
      contexts,
      WorkflowContextSeedKind.project,
      const WorkflowContextSeed(
        id: 'project:workspace-launch',
        kind: WorkflowContextSeedKind.project,
        label: 'Workspace launch',
      ),
    );
    final incident = _contextOrDefault(
      contexts,
      WorkflowContextSeedKind.event,
      const WorkflowContextSeed(
        id: 'event:support-incident',
        kind: WorkflowContextSeedKind.event,
        label: 'Support incident',
      ),
    );

    return WorkflowPreviewSnapshot(
      runs: <WorkflowRunPreview>[
        _workspaceOnboardingRun(project),
        _releasePreparationRun(channel),
        _supportIncidentRun(incident),
      ],
    );
  }

  WorkflowContextSeed _contextOrDefault(
    List<WorkflowContextSeed> contexts,
    WorkflowContextSeedKind kind,
    WorkflowContextSeed fallback,
  ) {
    return contexts.where((seed) => seed.kind == kind).firstOrNull ?? fallback;
  }

  WorkflowRunPreview _workspaceOnboardingRun(WorkflowContextSeed project) {
    final projectReference = _contextReference(project, 'Selected project');
    const taskReference = WorkflowContextReference(
      id: 'task:onboarding-checklist',
      kind: WorkflowContextReferenceKind.task,
      label: 'Workspace onboarding checklist',
      sourceLabel: 'Linked board task',
    );
    const fileReference = WorkflowContextReference(
      id: 'file:welcome-pack',
      kind: WorkflowContextReferenceKind.file,
      label: 'Welcome pack',
      sourceLabel: 'Selected file',
    );
    const checklistEvidence = WorkflowEvidenceReference(
      id: 'evidence:onboarding-checklist',
      label: 'Checklist has owners for first-day tasks',
      sourceLabel: 'Linked task state',
      contextReferenceId: 'task:onboarding-checklist',
    );
    const welcomePackEvidence = WorkflowEvidenceReference(
      id: 'evidence:welcome-pack',
      label: 'Welcome pack is ready for member review',
      sourceLabel: 'Selected file metadata',
      contextReferenceId: 'file:welcome-pack',
    );

    return WorkflowRunPreview(
      template: const WorkflowTemplatePreview(
        id: 'template:workspace-onboarding',
        title: 'Onboard a workspace',
        description:
            'Attach project context, checklist evidence, and owner approval before inviting members.',
        attachableContextKinds: <WorkflowContextReferenceKind>[
          WorkflowContextReferenceKind.project,
        ],
      ),
      runId: 'run:workspace-onboarding-preview',
      title: 'Onboard a workspace',
      contextLabel: project.label,
      backgroundRoomReadingEnabled: false,
      agentActionsEnabled: false,
      auditTrailRequired: true,
      steps: <WorkflowStepPreview>[
        WorkflowStepPreview(
          id: 'step:assign-onboarding-owners',
          kind: WorkflowStepKind.step,
          title: 'Assign onboarding owners',
          state: WorkflowStepState.ready,
          ownerLabel: 'Workspace owner',
          assignee: const WorkflowAssignee(
            label: 'Workspace owner',
            kind: WorkflowAssigneeKind.person,
          ),
          nextAction: 'Confirm task owners before sending invitations.',
          dueLabel: 'Before invite',
          contextReferences: <WorkflowContextReference>[
            projectReference,
            taskReference,
          ],
          evidence: const <WorkflowEvidenceReference>[checklistEvidence],
          blockers: const <WorkflowBlocker>[],
          requiresApproval: false,
          agentDryRunOnly: true,
        ),
        WorkflowStepPreview(
          id: 'step:review-welcome-pack',
          kind: WorkflowStepKind.approval,
          title: 'Review welcome pack',
          state: WorkflowStepState.waitingForApproval,
          ownerLabel: 'Workspace owner',
          assignee: const WorkflowAssignee(
            label: 'Onboarding coach',
            kind: WorkflowAssigneeKind.agent,
          ),
          nextAction: 'Ask the owner to approve the welcome pack draft.',
          dueLabel: 'Before invite',
          contextReferences: <WorkflowContextReference>[
            projectReference,
            fileReference,
          ],
          evidence: const <WorkflowEvidenceReference>[welcomePackEvidence],
          blockers: const <WorkflowBlocker>[],
          requiresApproval: true,
          agentDryRunOnly: true,
        ),
      ],
    );
  }

  WorkflowRunPreview _releasePreparationRun(WorkflowContextSeed channel) {
    final channelReference = _contextReference(
      channel,
      'Selected chat channel',
    );
    const decisionReference = WorkflowContextReference(
      id: 'decision:release-readiness',
      kind: WorkflowContextReferenceKind.decision,
      label: 'Release readiness decision',
      sourceLabel: 'Captured decision record',
    );
    const taskReference = WorkflowContextReference(
      id: 'task:release-checklist',
      kind: WorkflowContextReferenceKind.task,
      label: 'Release checklist task',
      sourceLabel: 'Linked board task',
    );
    const fileReference = WorkflowContextReference(
      id: 'file:release-notes-draft',
      kind: WorkflowContextReferenceKind.file,
      label: 'Release notes draft',
      sourceLabel: 'Selected file',
    );
    const agentReference = WorkflowContextReference(
      id: 'agent-run:release-coach-dry-run',
      kind: WorkflowContextReferenceKind.agentRun,
      label: 'Release coach dry-run',
      sourceLabel: 'Governed agent preview',
    );

    const releaseDecisionEvidence = WorkflowEvidenceReference(
      id: 'evidence:release-readiness',
      label: 'Decision captured from chat',
      sourceLabel: 'Decision record with message source',
      contextReferenceId: 'decision:release-readiness',
    );
    const checklistEvidence = WorkflowEvidenceReference(
      id: 'evidence:release-checklist',
      label: 'Checklist still has open items',
      sourceLabel: 'Linked task state',
      contextReferenceId: 'task:release-checklist',
    );
    const draftEvidence = WorkflowEvidenceReference(
      id: 'evidence:release-notes-draft',
      label: 'Draft notes need owner review',
      sourceLabel: 'Selected file metadata',
      contextReferenceId: 'file:release-notes-draft',
    );

    return WorkflowRunPreview(
      template: const WorkflowTemplatePreview(
        id: 'template:release-prep',
        title: 'Prepare a release',
        description:
            'Attach channel decisions, release tasks, and release-note evidence before publish.',
        attachableContextKinds: <WorkflowContextReferenceKind>[
          WorkflowContextReferenceKind.channel,
        ],
      ),
      runId: 'run:release-prep-preview',
      title: 'Prepare a release',
      contextLabel: channel.label,
      backgroundRoomReadingEnabled: false,
      agentActionsEnabled: false,
      auditTrailRequired: true,
      steps: <WorkflowStepPreview>[
        WorkflowStepPreview(
          id: 'step:confirm-readiness',
          kind: WorkflowStepKind.gate,
          title: 'Confirm release readiness',
          state: WorkflowStepState.done,
          ownerLabel: 'Product owner',
          assignee: const WorkflowAssignee(
            label: 'Product owner',
            kind: WorkflowAssigneeKind.person,
          ),
          nextAction: 'Decision is captured and linked.',
          dueLabel: 'Done',
          contextReferences: <WorkflowContextReference>[
            channelReference,
            decisionReference,
          ],
          evidence: const <WorkflowEvidenceReference>[releaseDecisionEvidence],
          blockers: const <WorkflowBlocker>[],
          requiresApproval: false,
          agentDryRunOnly: true,
        ),
        WorkflowStepPreview(
          id: 'step:clear-blockers',
          kind: WorkflowStepKind.step,
          title: 'Clear release blockers',
          state: WorkflowStepState.blocked,
          ownerLabel: 'Engineering lead',
          assignee: const WorkflowAssignee(
            label: 'Engineering lead',
            kind: WorkflowAssigneeKind.person,
          ),
          nextAction: 'Review the linked checklist and assign the open item.',
          dueLabel: 'Next working day',
          contextReferences: <WorkflowContextReference>[
            channelReference,
            taskReference,
          ],
          evidence: const <WorkflowEvidenceReference>[checklistEvidence],
          blockers: const <WorkflowBlocker>[
            WorkflowBlocker(
              id: 'blocker:open-checklist-item',
              description: 'One checklist item still needs an owner.',
              ownerLabel: 'Engineering lead',
              evidenceIds: <String>['evidence:release-checklist'],
            ),
          ],
          requiresApproval: false,
          agentDryRunOnly: true,
        ),
        const WorkflowStepPreview(
          id: 'step:approve-notes',
          kind: WorkflowStepKind.approval,
          title: 'Approve release notes',
          state: WorkflowStepState.waitingForApproval,
          ownerLabel: 'Workspace owner',
          assignee: WorkflowAssignee(
            label: 'Release coach',
            kind: WorkflowAssigneeKind.agent,
          ),
          nextAction:
              'Ask the owner to review the draft before any agent action.',
          dueLabel: 'Before publish',
          contextReferences: <WorkflowContextReference>[
            fileReference,
            agentReference,
          ],
          evidence: <WorkflowEvidenceReference>[draftEvidence],
          blockers: <WorkflowBlocker>[],
          requiresApproval: true,
          agentDryRunOnly: true,
        ),
      ],
    );
  }

  WorkflowRunPreview _supportIncidentRun(WorkflowContextSeed event) {
    final eventReference = _contextReference(event, 'Selected event');
    const meetingReference = WorkflowContextReference(
      id: 'meeting:support-triage',
      kind: WorkflowContextReferenceKind.meeting,
      label: 'Support triage call',
      sourceLabel: 'Linked meeting',
    );
    const taskReference = WorkflowContextReference(
      id: 'task:support-follow-up',
      kind: WorkflowContextReferenceKind.task,
      label: 'Support follow-up task',
      sourceLabel: 'Linked board task',
    );
    const agentReference = WorkflowContextReference(
      id: 'agent-run:support-summary-dry-run',
      kind: WorkflowContextReferenceKind.agentRun,
      label: 'Support summary dry-run',
      sourceLabel: 'Governed agent preview',
    );
    const triageEvidence = WorkflowEvidenceReference(
      id: 'evidence:support-triage',
      label: 'Triage notes are linked to the incident',
      sourceLabel: 'Linked meeting summary',
      contextReferenceId: 'meeting:support-triage',
    );
    const followUpEvidence = WorkflowEvidenceReference(
      id: 'evidence:support-follow-up',
      label: 'Follow-up task has owner and next action',
      sourceLabel: 'Linked task state',
      contextReferenceId: 'task:support-follow-up',
    );
    const summaryEvidence = WorkflowEvidenceReference(
      id: 'evidence:support-summary-dry-run',
      label: 'Agent summary is ready for owner approval',
      sourceLabel: 'Governed agent preview',
      contextReferenceId: 'agent-run:support-summary-dry-run',
    );

    return WorkflowRunPreview(
      template: const WorkflowTemplatePreview(
        id: 'template:support-incident',
        title: 'Resolve a support incident',
        description:
            'Attach incident event, meeting, task, and governed agent summary evidence.',
        attachableContextKinds: <WorkflowContextReferenceKind>[
          WorkflowContextReferenceKind.event,
        ],
      ),
      runId: 'run:support-incident-preview',
      title: 'Resolve a support incident',
      contextLabel: event.label,
      backgroundRoomReadingEnabled: false,
      agentActionsEnabled: false,
      auditTrailRequired: true,
      steps: <WorkflowStepPreview>[
        WorkflowStepPreview(
          id: 'step:capture-triage',
          kind: WorkflowStepKind.gate,
          title: 'Capture triage decision',
          state: WorkflowStepState.done,
          ownerLabel: 'Support lead',
          assignee: const WorkflowAssignee(
            label: 'Support lead',
            kind: WorkflowAssigneeKind.person,
          ),
          nextAction: 'Triage decision is captured and linked.',
          dueLabel: 'Done',
          contextReferences: <WorkflowContextReference>[
            eventReference,
            meetingReference,
          ],
          evidence: const <WorkflowEvidenceReference>[triageEvidence],
          blockers: const <WorkflowBlocker>[],
          requiresApproval: false,
          agentDryRunOnly: true,
        ),
        WorkflowStepPreview(
          id: 'step:assign-follow-up',
          kind: WorkflowStepKind.step,
          title: 'Assign follow-up owner',
          state: WorkflowStepState.inProgress,
          ownerLabel: 'Support lead',
          assignee: const WorkflowAssignee(
            label: 'Support lead',
            kind: WorkflowAssigneeKind.person,
          ),
          nextAction: 'Confirm the follow-up owner and customer-safe action.',
          dueLabel: 'Today',
          contextReferences: <WorkflowContextReference>[
            eventReference,
            taskReference,
          ],
          evidence: const <WorkflowEvidenceReference>[followUpEvidence],
          blockers: const <WorkflowBlocker>[],
          requiresApproval: false,
          agentDryRunOnly: true,
        ),
        const WorkflowStepPreview(
          id: 'step:approve-support-summary',
          kind: WorkflowStepKind.approval,
          title: 'Approve support summary',
          state: WorkflowStepState.waitingForApproval,
          ownerLabel: 'Support lead',
          assignee: WorkflowAssignee(
            label: 'Support coach',
            kind: WorkflowAssigneeKind.agent,
          ),
          nextAction: 'Approve the agent summary before it is shared.',
          dueLabel: 'Before customer update',
          contextReferences: <WorkflowContextReference>[agentReference],
          evidence: <WorkflowEvidenceReference>[summaryEvidence],
          blockers: <WorkflowBlocker>[],
          requiresApproval: true,
          agentDryRunOnly: true,
        ),
      ],
    );
  }

  WorkflowContextReference _contextReference(
    WorkflowContextSeed seed,
    String sourceLabel,
  ) {
    return WorkflowContextReference(
      id: seed.id,
      kind: switch (seed.kind) {
        WorkflowContextSeedKind.channel => WorkflowContextReferenceKind.channel,
        WorkflowContextSeedKind.project => WorkflowContextReferenceKind.project,
        WorkflowContextSeedKind.event => WorkflowContextReferenceKind.event,
      },
      label: seed.label,
      sourceLabel: sourceLabel,
    );
  }
}
