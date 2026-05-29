import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/workflows/domain/entities/workflow_preview.dart';
import 'package:weave/features/workflows/presentation/providers/workflow_preview_provider.dart';

void main() {
  test('workflow preview exposes linear explicit and governed primitives', () {
    final snapshot = const WorkflowPreviewFacade().previewForWorkspace(
      contexts: const <WorkflowContextSeed>[
        WorkflowContextSeed(
          id: 'channel:release',
          kind: WorkflowContextSeedKind.channel,
          label: 'Release channel',
        ),
        WorkflowContextSeed(
          id: 'project:workspace-launch',
          kind: WorkflowContextSeedKind.project,
          label: 'Workspace launch',
        ),
        WorkflowContextSeed(
          id: 'event:support-incident',
          kind: WorkflowContextSeedKind.event,
          label: 'Support incident',
        ),
      ],
    );

    expect(snapshot.hasActiveWorkflows, isTrue);
    expect(snapshot.isLinearAccessiblePreview, isTrue);
    expect(snapshot.usesOnlyExplicitContext, isTrue);
    expect(snapshot.keepsAgentsGoverned, isTrue);
    expect(snapshot.runs.map((run) => run.title), <String>[
      'Onboard a workspace',
      'Prepare a release',
      'Resolve a support incident',
    ]);

    final onboarding = snapshot.runs.first;
    expect(onboarding.template.id, 'template:workspace-onboarding');
    expect(
      onboarding.template.canAttachTo(WorkflowContextReferenceKind.project),
      isTrue,
    );
    expect(onboarding.contextLabel, 'Workspace launch');

    final release = snapshot.runs[1];
    expect(release.template.id, 'template:release-prep');
    expect(
      release.template.canAttachTo(WorkflowContextReferenceKind.channel),
      isTrue,
    );
    expect(release.contextLabel, 'Release channel');
    expect(release.backgroundRoomReadingEnabled, isFalse);
    expect(release.agentActionsEnabled, isFalse);
    expect(release.auditTrailRequired, isTrue);
    expect(release.steps.map((step) => step.kind), <WorkflowStepKind>[
      WorkflowStepKind.gate,
      WorkflowStepKind.step,
      WorkflowStepKind.approval,
    ]);

    final support = snapshot.runs.last;
    expect(support.template.id, 'template:support-incident');
    expect(
      support.template.canAttachTo(WorkflowContextReferenceKind.event),
      isTrue,
    );
    expect(support.contextLabel, 'Support incident');

    final steps = snapshot.runs.expand((run) => run.steps).toList();
    expect(steps.any((step) => step.requiresApproval), isTrue);
    expect(steps.any((step) => step.blockers.isNotEmpty), isTrue);
    expect(steps.every((step) => step.agentDryRunOnly), isTrue);

    expect(
      steps.expand((step) => step.contextReferences).map((ref) => ref.kind),
      containsAll(<WorkflowContextReferenceKind>{
        WorkflowContextReferenceKind.channel,
        WorkflowContextReferenceKind.project,
        WorkflowContextReferenceKind.event,
        WorkflowContextReferenceKind.task,
        WorkflowContextReferenceKind.decision,
        WorkflowContextReferenceKind.file,
        WorkflowContextReferenceKind.meeting,
        WorkflowContextReferenceKind.agentRun,
      }),
    );
  });
}
