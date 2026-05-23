import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class ServerConfigurationSaveResult {
  const ServerConfigurationSaveResult({
    required this.configuration,
    required this.authConfigurationChanged,
    required this.matrixHomeserverChanged,
    required this.nextcloudBaseUrlChanged,
    required this.backendApiBaseUrlChanged,
  });

  final ServerConfiguration configuration;
  final bool authConfigurationChanged;
  final bool matrixHomeserverChanged;
  final bool nextcloudBaseUrlChanged;
  final bool backendApiBaseUrlChanged;

  bool get hasSessionImpact =>
      authConfigurationChanged ||
      matrixHomeserverChanged ||
      nextcloudBaseUrlChanged ||
      backendApiBaseUrlChanged;
}
