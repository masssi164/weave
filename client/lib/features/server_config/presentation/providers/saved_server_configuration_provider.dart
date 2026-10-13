import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

/// Reads the canonical saved organization manifest profile.
final savedServerConfigurationProvider =
    FutureProvider.autoDispose<ServerConfiguration?>((ref) {
      return ref
          .watch(serverConfigurationRepositoryProvider)
          .loadConfiguration();
    });
