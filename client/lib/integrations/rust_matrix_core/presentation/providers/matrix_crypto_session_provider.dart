import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

final matrixCryptoSessionCoordinatorProvider =
    Provider<MatrixCryptoSessionCoordinator>((ref) {
      final secureStore = ref.watch(secureStoreProvider);
      final coordinator = MatrixCryptoSessionCoordinator(
        serverConfigurationRepository: ref.watch(
          serverConfigurationRepositoryProvider,
        ),
        authSessionRepository: ref.watch(authSessionRepositoryProvider),
        matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
          secureStore: secureStore,
        ),
        secureStore: secureStore,
        httpClient: ref.watch(weaveApiHttpClientProvider),
      );
      ref.onDispose(coordinator.disposePreservingCryptoState);
      return coordinator;
    });
