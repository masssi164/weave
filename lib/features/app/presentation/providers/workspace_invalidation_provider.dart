import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';

class WorkspaceInvalidationState {
  WorkspaceInvalidationState({
    Map<WorkspaceIntegration, IntegrationInvalidation> integrations =
        const <WorkspaceIntegration, IntegrationInvalidation>{},
  }) : integrations =
           Map<WorkspaceIntegration, IntegrationInvalidation>.unmodifiable(
             integrations,
           );

  final Map<WorkspaceIntegration, IntegrationInvalidation> integrations;

  IntegrationInvalidation? forIntegration(WorkspaceIntegration integration) {
    return integrations[integration];
  }
}

class WorkspaceInvalidationController
    extends Notifier<WorkspaceInvalidationState> {
  @override
  WorkspaceInvalidationState build() => WorkspaceInvalidationState();

  void invalidate({
    required WorkspaceIntegration integration,
    required IntegrationInvalidationReason reason,
  }) {
    final current = state.forIntegration(integration);
    final next = IntegrationInvalidation(
      integration: integration,
      reason: reason,
      sequence: (current?.sequence ?? 0) + 1,
    );

    state = WorkspaceInvalidationState(
      integrations: <WorkspaceIntegration, IntegrationInvalidation>{
        ...state.integrations,
        integration: next,
      },
    );
  }
}

final workspaceInvalidationProvider =
    NotifierProvider<
      WorkspaceInvalidationController,
      WorkspaceInvalidationState
    >(WorkspaceInvalidationController.new);

final integrationInvalidationProvider =
    Provider.family<IntegrationInvalidation?, WorkspaceIntegration>((
      ref,
      integration,
    ) {
      return ref
          .watch(workspaceInvalidationProvider)
          .forIntegration(integration);
    });
