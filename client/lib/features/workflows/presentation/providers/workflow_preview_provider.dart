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
    final context =
        contexts
            .where((seed) => seed.kind == WorkflowContextSeedKind.channel)
            .firstOrNull ??
        const WorkflowContextSeed(
          id: 'channel:general',
          kind: WorkflowContextSeedKind.channel,
          label: 'general',
        );

    final channelReference = WorkflowContextReference(
      id: 'channel:${context.id}',
      kind: WorkflowContextReferenceKind.channel,
      label: context.label,
      sourceLabel: 'Selected chat channel',
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

    return WorkflowPreviewSnapshot(
      runs: <WorkflowRunPreview>[
        WorkflowRunPreview(
          templateId: 'template:release-prep',
          runId: 'run:release-prep-preview',
          title: 'Prepare a release',
          contextLabel: context.label,
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
              evidence: const <WorkflowEvidenceReference>[
                releaseDecisionEvidence,
              ],
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
              nextAction:
                  'Review the linked checklist and assign the open item.',
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
        ),
      ],
    );
  }
}
