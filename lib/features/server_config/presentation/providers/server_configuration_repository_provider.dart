import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

final serverConfigurationRepositoryProvider =
    Provider<ServerConfigurationRepository>((ref) {
      final store = ref.watch(preferencesStoreProvider);
      final deriver = ref.watch(serviceEndpointDeriverProvider);
      return SharedPreferencesServerConfigurationRepository(
        store: store,
        deriver: deriver,
      );
    });
