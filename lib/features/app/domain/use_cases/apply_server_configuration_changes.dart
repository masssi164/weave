import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';

class ApplyServerConfigurationChanges {
  const ApplyServerConfigurationChanges({
    required AppAuthPort authPort,
    required ChatSessionPort chatSessionPort,
    required FilesSessionPort filesSessionPort,
  }) : _authPort = authPort,
       _chatSessionPort = chatSessionPort,
       _filesSessionPort = filesSessionPort;

  final AppAuthPort _authPort;
  final ChatSessionPort _chatSessionPort;
  final FilesSessionPort _filesSessionPort;

  Future<void> call(ServerConfigurationSaveResult result) async {
    if (result.authConfigurationChanged) {
      await _authPort.clearLocalSession();
    }

    if (result.matrixHomeserverChanged) {
      await _chatSessionPort.clearSession();
      _chatSessionPort.invalidateActiveSession();
    }

    if (result.nextcloudBaseUrlChanged) {
      await _filesSessionPort.disconnect();
    }
  }
}
