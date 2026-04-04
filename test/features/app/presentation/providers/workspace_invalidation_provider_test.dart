import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';

void main() {
  group('workspace invalidation provider', () {
    test('exposes an unmodifiable integration map', () {
      final container = ProviderContainer.test();
      addTearDown(container.dispose);

      container
          .read(workspaceInvalidationProvider.notifier)
          .invalidate(
            integration: WorkspaceIntegration.matrix,
            reason: IntegrationInvalidationReason.matrixHomeserverChanged,
          );

      final state = container.read(workspaceInvalidationProvider);

      expect(
        () => state.integrations[WorkspaceIntegration.appAuth] =
            const IntegrationInvalidation(
              integration: WorkspaceIntegration.appAuth,
              reason: IntegrationInvalidationReason.authConfigurationChanged,
              sequence: 1,
            ),
        throwsUnsupportedError,
      );
    });
  });
}
