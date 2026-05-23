import 'package:weave/features/app/domain/entities/integration_invalidation.dart';

abstract interface class WorkspaceInvalidationPort {
  void invalidate({
    required WorkspaceIntegration integration,
    required IntegrationInvalidationReason reason,
  });
}
