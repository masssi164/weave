import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class SignOutWorkspace {
  const SignOutWorkspace({
    required AppAuthPort authPort,
    required ChatSessionPort chatSessionPort,
    required FilesSessionPort filesSessionPort,
    required ServerConfigurationPort serverConfigurationPort,
  }) : _authPort = authPort,
       _chatSessionPort = chatSessionPort,
       _filesSessionPort = filesSessionPort,
       _serverConfigurationPort = serverConfigurationPort;

  final AppAuthPort _authPort;
  final ChatSessionPort _chatSessionPort;
  final FilesSessionPort _filesSessionPort;
  final ServerConfigurationPort _serverConfigurationPort;

  Future<void> call() async {
    final configuration = await _serverConfigurationPort.loadConfiguration();

    if (configuration != null && configuration.hasCompleteAuthConfiguration) {
      await _authPort.signOut(_toAuthConfiguration(configuration));
    } else {
      await _authPort.clearLocalSession();
    }

    await _chatSessionPort.signOut();
    await _filesSessionPort.disconnect();
    _chatSessionPort.invalidateActiveSession();
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
