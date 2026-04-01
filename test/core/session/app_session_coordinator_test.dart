import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:weave/core/session/app_session_coordinator.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/server_config_test_data.dart';

class _FakeAuthSessionRepository implements AuthSessionRepository {
  int signOutCalls = 0;
  int clearLocalSessionCalls = 0;
  AuthConfiguration? lastConfiguration;

  @override
  Future<void> clearLocalSession() async {
    clearLocalSessionCalls++;
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    signOutCalls++;
    lastConfiguration = configuration;
  }
}

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.configuration});

  ServerConfiguration? configuration;
  int clearConfigurationCalls = 0;

  @override
  Future<void> clearConfiguration() async {
    clearConfigurationCalls++;
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

void main() {
  group('AppSessionCoordinator', () {
    test('signOut clears app auth and Matrix session state', () async {
      final authRepository = _FakeAuthSessionRepository();
      final chatRepository = FakeChatRepository();
      final container = ProviderContainer(
        overrides: [
          authSessionRepositoryProvider.overrideWithValue(authRepository),
          chatRepositoryProvider.overrideWithValue(chatRepository),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => _FakeServerConfigurationRepository(
              configuration: buildTestConfiguration(),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(container.read(matrixSessionInvalidationProvider), 0);

      await container.read(appSessionCoordinatorProvider).signOut();

      expect(authRepository.signOutCalls, 1);
      expect(chatRepository.signOutCalls, 1);
      expect(container.read(matrixSessionInvalidationProvider), 1);
    });

    test(
      'restartSetup clears Matrix session and server configuration',
      () async {
        final authRepository = _FakeAuthSessionRepository();
        final chatRepository = FakeChatRepository();
        final serverRepository = _FakeServerConfigurationRepository(
          configuration: buildTestConfiguration(),
        );
        final container = ProviderContainer(
          overrides: [
            authSessionRepositoryProvider.overrideWithValue(authRepository),
            chatRepositoryProvider.overrideWithValue(chatRepository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => serverRepository,
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(appSessionCoordinatorProvider).restartSetup();

        expect(authRepository.clearLocalSessionCalls, 1);
        expect(chatRepository.clearSessionCalls, 1);
        expect(serverRepository.clearConfigurationCalls, 1);
        expect(container.read(matrixSessionInvalidationProvider), 1);
      },
    );

    test('homeserver changes clear only the Matrix session', () async {
      final authRepository = _FakeAuthSessionRepository();
      final chatRepository = FakeChatRepository();
      final container = ProviderContainer(
        overrides: [
          authSessionRepositoryProvider.overrideWithValue(authRepository),
          chatRepositoryProvider.overrideWithValue(chatRepository),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => _FakeServerConfigurationRepository(
              configuration: buildTestConfiguration(),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container
          .read(appSessionCoordinatorProvider)
          .handleConfigurationSaved(
            ServerConfigurationSaveResult(
              configuration: buildTestConfiguration(),
              authConfigurationChanged: false,
              matrixHomeserverChanged: true,
            ),
          );

      expect(authRepository.clearLocalSessionCalls, 0);
      expect(chatRepository.clearSessionCalls, 1);
      expect(container.read(matrixSessionInvalidationProvider), 1);
    });
  });
}
