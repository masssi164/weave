import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';

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
      );
      ref.onDispose(coordinator.disposePreservingCryptoState);
      return coordinator;
    });
