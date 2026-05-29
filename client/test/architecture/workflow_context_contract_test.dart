import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('workflow context contract maps issue 218 acceptance', () async {
    final contract = await File(
      '../docs/workflow-context-contract.md',
    ).readAsString();
    final domain = await File(
      'lib/features/workflows/domain/entities/workflow_preview.dart',
    ).readAsString();
    final provider = await File(
      'lib/features/workflows/presentation/providers/workflow_preview_provider.dart',
    ).readAsString();

    for (final required in <String>[
      'Workflow template',
      'Workflow run',
      'Step',
      'Gate',
      'Approval',
      'Evidence',
      'Blocker',
      'Assignee',
      'Context Graph references',
      'Agent participation rules',
      'MVP slice before a visual builder',
      'Onboard a workspace',
      'Prepare a release',
      'Resolve a support incident',
    ]) {
      expect(contract, contains(required));
    }

    for (final forbidden in <String>[
      'background room scanning',
      'autonomous agent execution',
      'direct provider-control-plane calls from member UI',
    ]) {
      expect(contract, contains(forbidden));
    }

    expect(domain, contains('class WorkflowTemplatePreview'));
    expect(domain, contains('class WorkflowRunPreview'));
    expect(domain, contains('class WorkflowStepPreview'));
    expect(domain, contains('class WorkflowEvidenceReference'));
    expect(domain, contains('class WorkflowBlocker'));
    expect(domain, contains('class WorkflowAssignee'));

    expect(provider, contains('backgroundRoomReadingEnabled: false'));
    expect(provider, contains('agentActionsEnabled: false'));
    expect(provider, contains('auditTrailRequired: true'));
    expect(provider, contains('agentDryRunOnly: true'));
    expect(provider, contains('WorkflowContextReferenceKind.project'));
    expect(provider, contains('WorkflowContextReferenceKind.event'));
    expect(provider, contains('WorkflowContextReferenceKind.meeting'));
  });
}
