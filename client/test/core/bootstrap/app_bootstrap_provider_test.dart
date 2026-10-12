import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
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
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_identity_session_port.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.loadConfigurationHandler});

  Future<ServerConfiguration?> Function() loadConfigurationHandler;
  Future<void> Function(ServerConfiguration)? saveConfigurationHandler;

  @override
  Future<ServerConfiguration?> loadConfiguration() {
    return loadConfigurationHandler();
  }

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    await saveConfigurationHandler?.call(configuration);
  }

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
      AppStartDiscoveryClient? discoveryClient,
    }) {
      return ProviderContainer.test(
        overrides: [
          appStartDiscoveryClientProvider.overrideWith(
            (ref) =>
                discoveryClient ??
                buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => repository,
          ),
          secureStoreProvider.overrideWithValue(
            secureStore ?? InMemorySecureStore(),
          ),
          oidcClientProvider.overrideWithValue(oidcClient ?? _FakeOidcClient()),
          identitySessionPortProvider.overrideWithValue(
            FakeIdentitySessionPort(),
          ),
        ],
      );
    }

    for (final successful in [true, false]) {
      test(
        successful
            ? 'bootstrap publishes refreshed canonical API endpoint to retained profile consumers'
            : 'failed bootstrap refresh preserves the existing profile consumer authority',
        () async {
          var stored = buildTestConfiguration();
          final previous = stored;
          final updatedApi = Uri.parse('https://api-v2.home.internal/api');
          final manifest = Map<String, dynamic>.from(stored.validatedManifest!)
            ..['userApiBaseUrl'] = updatedApi.toString();
          final repository =
              _FakeServerConfigurationRepository(
                  loadConfigurationHandler: () async => stored,
                )
                ..saveConfigurationHandler = (value) async {
                  stored = value;
                };
          final secure = InMemorySecureStore();
          await secure.write(
            authSessionStorageKey,
            AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
          );
          final container = createContainer(
            repository: repository,
            secureStore: secure,
            discoveryClient: AppStartDiscoveryClient(
              httpClient: MockClient((request) async {
                expect(
                  request.url,
                  Uri.parse('https://weave.home.internal/api/platform/config'),
                );
                return http.Response(
                  successful ? jsonEncode(manifest) : '{}',
                  successful ? 200 : 503,
                  headers: {'content-type': 'application/json'},
                );
              }),
            ),
          );
          addTearDown(container.dispose);
          final subscription = container.listen(
            savedServerConfigurationProvider,
            (_, _) {},
          );
          addTearDown(subscription.close);
          expect(
            await container.read(savedServerConfigurationProvider.future),
            same(previous),
          );
          final bootstrap = await container.read(appBootstrapProvider.future);
          final observed = await container.read(
            savedServerConfigurationProvider.future,
          );
          if (successful) {
            expect(bootstrap.phase, BootstrapPhase.ready);
            expect(stored.serviceEndpoints.backendApiBaseUrl, updatedApi);
            expect(observed!.serviceEndpoints.backendApiBaseUrl, updatedApi);
            final session = await container.read(
              weaveAuthenticatedSessionProvider.future,
            );
            expect(session, isNotNull);
            expect(session!.apiBaseUrl, updatedApi);
          } else {
            expect(bootstrap.phase, BootstrapPhase.error);
            expect(stored, same(previous));
            expect(observed, same(previous));
          }
        },
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
