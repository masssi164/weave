import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

abstract interface class ServerConfigurationPort {
  Future<ServerConfiguration?> loadConfiguration();

  Future<void> clearConfiguration();
}
