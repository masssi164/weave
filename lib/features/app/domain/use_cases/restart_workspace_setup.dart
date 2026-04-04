import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/ports/workspace_invalidation_port.dart';

class RestartWorkspaceSetup {
  const RestartWorkspaceSetup({
    required AppAuthPort authPort,
    required ChatSessionPort chatSessionPort,
    required FilesSessionPort filesSessionPort,
    required ServerConfigurationPort serverConfigurationPort,
    required WorkspaceInvalidationPort workspaceInvalidationPort,
  }) : _authPort = authPort,
       _chatSessionPort = chatSessionPort,
       _filesSessionPort = filesSessionPort,
       _serverConfigurationPort = serverConfigurationPort,
       _workspaceInvalidationPort = workspaceInvalidationPort;

  final AppAuthPort _authPort;
  final ChatSessionPort _chatSessionPort;
  final FilesSessionPort _filesSessionPort;
  final ServerConfigurationPort _serverConfigurationPort;
  final WorkspaceInvalidationPort _workspaceInvalidationPort;

  Future<void> call() async {
    await _authPort.clearLocalSession();
    await _chatSessionPort.clearSession();
    await _filesSessionPort.disconnect();
    await _serverConfigurationPort.clearConfiguration();
    _workspaceInvalidationPort.invalidate(
      integration: WorkspaceIntegration.appAuth,
      reason: IntegrationInvalidationReason.restartSetup,
    );
    _workspaceInvalidationPort.invalidate(
      integration: WorkspaceIntegration.matrix,
      reason: IntegrationInvalidationReason.restartSetup,
    );
    _workspaceInvalidationPort.invalidate(
      integration: WorkspaceIntegration.nextcloud,
      reason: IntegrationInvalidationReason.restartSetup,
    );
  }
}
