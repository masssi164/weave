import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/app/domain/ports/workspace_invalidation_port.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';

class ApplyServerConfigurationChanges {
  const ApplyServerConfigurationChanges({
    required AppAuthPort authPort,
    required ChatSessionPort chatSessionPort,
    required FilesSessionPort filesSessionPort,
    required WorkspaceInvalidationPort workspaceInvalidationPort,
  }) : _authPort = authPort,
       _chatSessionPort = chatSessionPort,
       _filesSessionPort = filesSessionPort,
       _workspaceInvalidationPort = workspaceInvalidationPort;

  final AppAuthPort _authPort;
  final ChatSessionPort _chatSessionPort;
  final FilesSessionPort _filesSessionPort;
  final WorkspaceInvalidationPort _workspaceInvalidationPort;

  Future<void> call(ServerConfigurationSaveResult result) async {
    if (result.authConfigurationChanged) {
      await _authPort.clearLocalSession();
      _workspaceInvalidationPort.invalidate(
        integration: WorkspaceIntegration.appAuth,
        reason: IntegrationInvalidationReason.authConfigurationChanged,
      );
    }

    if (result.matrixHomeserverChanged) {
      await _chatSessionPort.clearSession();
      _workspaceInvalidationPort.invalidate(
        integration: WorkspaceIntegration.matrix,
        reason: IntegrationInvalidationReason.matrixHomeserverChanged,
      );
    }

    if (result.nextcloudBaseUrlChanged) {
      await _filesSessionPort.disconnect();
      _workspaceInvalidationPort.invalidate(
        integration: WorkspaceIntegration.nextcloud,
        reason: IntegrationInvalidationReason.nextcloudBaseUrlChanged,
      );
    }

    if (result.backendApiBaseUrlChanged) {
      _workspaceInvalidationPort.invalidate(
        integration: WorkspaceIntegration.weaveBackend,
        reason: IntegrationInvalidationReason.backendApiBaseUrlChanged,
      );
    }
  }
}
