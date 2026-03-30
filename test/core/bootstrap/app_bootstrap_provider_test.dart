import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.loadConfigurationHandler});

  Future<ServerConfiguration?> Function() loadConfigurationHandler;

  @override
  Future<ServerConfiguration?> loadConfiguration() {
    return loadConfigurationHandler();
  }

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {}

  @override
  Future<void> clearConfiguration() async {}
}

class _FakeOidcClient implements OidcClient {
  Future<OidcTokenBundle> Function(String refreshToken)? refreshHandler;

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(configuration) {
    throw UnimplementedError();
  }

  @override
  Future<void> endSession(configuration, {required String idTokenHint}) {
    throw UnimplementedError();
  }

  @override
  Future<OidcTokenBundle> refresh(
    configuration, {
    required String refreshToken,
  }) {
    final handler = refreshHandler;
    if (handler == null) {
      throw StateError('refreshHandler was not configured.');
    }

    return handler(refreshToken);
  }
}

void main() {
  group('AppBootstrap', () {
    ProviderContainer createContainer({
      required ServerConfigurationRepository repository,
      InMemorySecureStore? secureStore,
      _FakeOidcClient? oidcClient,
    }) {
      return ProviderContainer.test(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => repository,
          ),
          secureStoreProvider.overrideWithValue(
            secureStore ?? InMemorySecureStore(),
          ),
          oidcClientProvider.overrideWithValue(oidcClient ?? _FakeOidcClient()),
        ],
      );
    }

    test('returns needsSetup when no saved configuration exists', () async {
      final container = createContainer(
        repository: _FakeServerConfigurationRepository(
          loadConfigurationHandler: () async => null,
        ),
      );
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.needsSetup);
    });

    test(
      'returns needsSignIn when a configuration exists without a session',
      () async {
        final container = createContainer(
          repository: _FakeServerConfigurationRepository(
            loadConfigurationHandler: () async => buildTestConfiguration(),
          ),
        );
        addTearDown(container.dispose);

        final bootstrapState = await container.read(
          appBootstrapProvider.future,
        );

        expect(bootstrapState.phase, BootstrapPhase.needsSignIn);
      },
    );

    test('returns ready when a valid saved session exists', () async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        repository: _FakeServerConfigurationRepository(
          loadConfigurationHandler: () async => buildTestConfiguration(),
        ),
        secureStore: secureStore,
      );
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.ready);
    });

    test('returns ready when an expired session refresh succeeds', () async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(
          buildTestAuthSession(
            expiresAt: DateTime.now().toUtc().subtract(
              const Duration(minutes: 5),
            ),
          ),
        ).encode(),
      );
      final oidcClient = _FakeOidcClient()
        ..refreshHandler = (refreshToken) async {
          expect(refreshToken, 'refresh-token');
          return OidcTokenBundle(
            accessToken: 'refreshed-access',
            refreshToken: refreshToken,
            idToken: 'refreshed-id',
            expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
            tokenType: 'Bearer',
            scopes: const ['openid', 'offline_access'],
          );
        };
      final container = createContainer(
        repository: _FakeServerConfigurationRepository(
          loadConfigurationHandler: () async => buildTestConfiguration(),
        ),
        secureStore: secureStore,
        oidcClient: oidcClient,
      );
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.ready);
      expect(
        await secureStore.read(authSessionStorageKey),
        contains('refreshed-access'),
      );
    });

    test('returns error when configuration loading fails', () async {
      final repository = _FakeServerConfigurationRepository(
        loadConfigurationHandler: () async {
          throw const AppFailure.storage('Broken preferences.');
        },
      );
      final container = createContainer(repository: repository);
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.error);
      expect(bootstrapState.failure?.message, 'Broken preferences.');
    });

    test('retry re-reads storage and recovers to ready', () async {
      var shouldFail = true;
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final repository = _FakeServerConfigurationRepository(
        loadConfigurationHandler: () async {
          if (shouldFail) {
            throw const AppFailure.storage('Temporary read failure.');
          }

          return buildTestConfiguration();
        },
      );
      final container = createContainer(
        repository: repository,
        secureStore: secureStore,
      );
      addTearDown(container.dispose);

      final initialState = await container.read(appBootstrapProvider.future);
      expect(initialState.phase, BootstrapPhase.error);

      shouldFail = false;
      await container.read(appBootstrapProvider.notifier).retry();

      expect(
        container.read(appBootstrapProvider).requireValue.phase,
        BootstrapPhase.ready,
      );
    });
  });
}
