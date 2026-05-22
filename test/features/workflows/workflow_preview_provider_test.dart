import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/workflows/domain/entities/workflow_preview.dart';
import 'package:weave/features/workflows/presentation/providers/workflow_preview_provider.dart';

void main() {
  test('workflow preview exposes linear explicit and governed primitives', () {
    final snapshot = const WorkflowPreviewFacade().previewForWorkspace(
      contexts: const <WorkflowContextSeed>[
        WorkflowContextSeed(
          id: '!release:home.internal',
          kind: WorkflowContextSeedKind.channel,
          label: 'Release channel',
        ),
      ],
    );

    expect(snapshot.hasActiveWorkflows, isTrue);
    expect(snapshot.isLinearAccessiblePreview, isTrue);
    expect(snapshot.usesOnlyExplicitContext, isTrue);
    expect(snapshot.keepsAgentsGoverned, isTrue);

    final run = snapshot.runs.single;
    expect(run.title, 'Prepare a release');
    expect(run.contextLabel, 'Release channel');
    expect(run.backgroundRoomReadingEnabled, isFalse);
    expect(run.agentActionsEnabled, isFalse);
    expect(run.auditTrailRequired, isTrue);
    expect(run.steps.map((step) => step.kind), <WorkflowStepKind>[
      WorkflowStepKind.gate,
      WorkflowStepKind.step,
      WorkflowStepKind.approval,
    ]);
    expect(run.steps.any((step) => step.requiresApproval), isTrue);
    expect(run.steps.any((step) => step.blockers.isNotEmpty), isTrue);
    expect(
      run.steps.expand((step) => step.contextReferences).map((ref) => ref.kind),
      containsAll(<WorkflowContextReferenceKind>{
        WorkflowContextReferenceKind.channel,
        WorkflowContextReferenceKind.task,
        WorkflowContextReferenceKind.decision,
        WorkflowContextReferenceKind.file,
        WorkflowContextReferenceKind.agentRun,
      }),
    );
  });
}
