import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

abstract interface class ServerConfigurationRepository {
  Future<ServerConfiguration?> loadConfiguration();

  Future<void> saveConfiguration(ServerConfiguration configuration);

  Future<void> clearConfiguration();
}
