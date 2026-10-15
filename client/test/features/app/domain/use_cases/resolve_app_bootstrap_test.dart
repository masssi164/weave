import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/reconcile_identity_session.dart';
import 'package:weave/features/app/domain/use_cases/resolve_app_bootstrap.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeAppAuthPort implements AppAuthPort {
  Future<AuthState> Function(AuthConfiguration configuration)?
  restoreSessionHandler;

  AuthConfiguration? lastRestoreConfiguration;
  AuthState refreshResult = AuthState.authenticated(
    buildTestAuthSession(accessToken: 'refreshed-access-token'),
  );
  var refreshCalls = 0;
  var clearCalls = 0;

  @override
  Future<void> clearLocalSession() async {
    clearCalls++;
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    lastRestoreConfiguration = configuration;
    final handler = restoreSessionHandler;
    if (handler == null) {
      throw StateError('restoreSessionHandler was not configured.');
    }

    return handler(configuration);
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    refreshCalls++;
    return refreshResult;
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    throw UnimplementedError();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _FakeIdentitySessionPort implements IdentitySessionPort {
  IdentitySessionReconciliation result =
      IdentitySessionReconciliation.unchanged;
  var calls = 0;
  String? lastAccessToken;

  @override
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    calls++;
    lastAccessToken = accessToken;
    return result;
  }
}

class _FakeServerConfigurationPort implements ServerConfigurationPort {
  _FakeServerConfigurationPort({this.configuration});

  ServerConfiguration? configuration;
  Object? loadError;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async {
    final error = loadError;
    if (error != null) {
      throw error;
    }

    return configuration;
  }
}

class _FakeChatSessionPort implements ChatSessionPort {
  int ensureCalls = 0;
  ChatFailure? failure;
  Completer<void>? pending;

  @override
  Future<void> ensureSession() async {
    ensureCalls++;
    await pending?.future;
    if (failure case final failure?) throw failure;
  }

  @override
  Future<void> clearSession() async {}

  @override
  Future<void> signOut() async {}
}

ResolveAppBootstrap _buildUseCase({
  required _FakeAppAuthPort authPort,
  required _FakeServerConfigurationPort serverConfigurationPort,
  _FakeIdentitySessionPort? identitySessionPort,
  _FakeChatSessionPort? chatSessionPort,
  Future<ServerConfiguration> Function(ServerConfiguration)?
  refreshOrganizationManifest,
}) {
  final sessionPort = identitySessionPort ?? _FakeIdentitySessionPort();
  return ResolveAppBootstrap(
    authPort: authPort,
    chatSessionPort: chatSessionPort ?? _FakeChatSessionPort(),
    reconcileIdentitySession: ReconcileIdentitySession(
      identitySessionPort: sessionPort,
    ),
    serverConfigurationPort: serverConfigurationPort,
    refreshOrganizationManifest: refreshOrganizationManifest,
  );
}

void main() {
  group('ResolveAppBootstrap', () {
    test(
      'refreshes manifest after restoration before identity reconciliation',
      () async {
        final auth = _FakeAppAuthPort()
          ..restoreSessionHandler = (_) async =>
              AuthState.authenticated(buildTestAuthSession());
        final identity = _FakeIdentitySessionPort();
        var calls = 0;
        final profile = buildTestConfiguration().copyWith(
          organizationOrigin: Uri.parse('https://weave.example'),
          validatedManifest: const {'schemaVersion': 2},
        );
        final useCase = _buildUseCase(
          authPort: auth,
          identitySessionPort: identity,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: profile,
          ),
          refreshOrganizationManifest: (saved) async {
            expect(auth.lastRestoreConfiguration, isNotNull);
            expect(identity.calls, 0);
            calls++;
            return saved;
          },
        );
        expect((await useCase()).phase, BootstrapPhase.ready);
        expect(calls, 1);
        expect(identity.calls, 1);
      },
    );
    test(
      'incompatible current manifest clears session and never reconciles or starts Chat',
      () async {
        final auth = _FakeAppAuthPort()
          ..restoreSessionHandler = (_) async =>
              AuthState.authenticated(buildTestAuthSession());
        final identity = _FakeIdentitySessionPort();
        final chat = _FakeChatSessionPort();
        final useCase = _buildUseCase(
          authPort: auth,
          identitySessionPort: identity,
          chatSessionPort: chat,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: buildTestConfiguration().copyWith(
              organizationOrigin: Uri.parse('https://weave.example'),
              validatedManifest: const {'schemaVersion': 2},
            ),
          ),
          refreshOrganizationManifest: (_) async =>
              throw const AppFailure.validation('identity mismatch'),
        );
        expect((await useCase()).phase, BootstrapPhase.error);
        expect(auth.clearCalls, 1);
        expect(identity.calls, 0);
        expect(chat.ensureCalls, 0);
      },
    );
    test(
      'legacy profile without recoverable origin returns member setup before auth',
      () async {
        final auth = _FakeAppAuthPort();
        final useCase = _buildUseCase(
          authPort: auth,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: buildTestConfiguration(withManifest: false),
          ),
          refreshOrganizationManifest: (_) async =>
              throw StateError('must not fetch inferred origin'),
        );
        expect((await useCase()).phase, BootstrapPhase.needsSetup);
        expect(auth.lastRestoreConfiguration, isNull);
        expect(auth.clearCalls, 1);
      },
    );
    test(
      'origin-only migration refreshes before AppAuth and again after restoration',
      () async {
        var calls = 0;
        final auth = _FakeAppAuthPort()
          ..restoreSessionHandler = (_) async {
            expect(calls, 1);
            return AuthState.authenticated(buildTestAuthSession());
          };
        final useCase = _buildUseCase(
          authPort: auth,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: buildTestConfiguration(
              withManifest: false,
            ).copyWith(organizationOrigin: Uri.parse('https://weave.example')),
          ),
          refreshOrganizationManifest: (saved) async {
            calls++;
            return saved.copyWith(
              validatedManifest: const {'schemaVersion': 2},
            );
          },
        );
        expect((await useCase()).phase, BootstrapPhase.ready);
        expect(calls, 2);
      },
    );

    test('returns needsSetup when no configuration exists', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = _buildUseCase(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(),
      );

      final state = await useCase.call();

      expect(state.phase, BootstrapPhase.needsSetup);
      expect(authPort.lastRestoreConfiguration, isNull);
    });

    test('returns needsSignIn when auth restoration is signed out', () async {
      final authPort = _FakeAppAuthPort()
        ..restoreSessionHandler = (_) async => const AuthState.signedOut();
      final useCase = _buildUseCase(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(clientId: ' weave-mobile '),
        ),
      );

      final state = await useCase.call();

      expect(state.phase, BootstrapPhase.needsSignIn);
      expect(authPort.lastRestoreConfiguration?.clientId, 'weave-mobile');
    });

    test('returns ready when auth restoration succeeds', () async {
      final authPort = _FakeAppAuthPort()
        ..restoreSessionHandler = (_) async =>
            AuthState.authenticated(buildTestAuthSession());
      final identitySessionPort = _FakeIdentitySessionPort();
      final chatSessionPort = _FakeChatSessionPort();
      final useCase = _buildUseCase(
        authPort: authPort,
        identitySessionPort: identitySessionPort,
        chatSessionPort: chatSessionPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(),
        ),
      );

      final state = await useCase.call();

      expect(state.phase, BootstrapPhase.ready);
      expect(identitySessionPort.calls, 1);
      expect(identitySessionPort.lastAccessToken, 'access-token');
      expect(chatSessionPort.ensureCalls, 1);
    });

    test('keeps Weave ready when authorized Chat needs recovery', () async {
      final authPort = _FakeAppAuthPort()
        ..restoreSessionHandler = (_) async =>
            AuthState.authenticated(buildTestAuthSession());
      final chatSessionPort = _FakeChatSessionPort()
        ..failure = const ChatFailure.sessionRequired('Chat unavailable.');
      final useCase = _buildUseCase(
        authPort: authPort,
        chatSessionPort: chatSessionPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(),
        ),
      );

      expect((await useCase.call()).phase, BootstrapPhase.ready);
      expect(chatSessionPort.ensureCalls, 1);
    });

    test(
      'slow Chat preparation never blocks the shell and late failures stay contained',
      () async {
        final authPort = _FakeAppAuthPort()
          ..restoreSessionHandler = (_) async =>
              AuthState.authenticated(buildTestAuthSession());
        final pending = Completer<void>();
        final chatPort = _FakeChatSessionPort()..pending = pending;
        final useCase = _buildUseCase(
          authPort: authPort,
          chatSessionPort: chatPort,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: buildTestConfiguration(),
          ),
        );
        final ready = await useCase.call().timeout(const Duration(seconds: 1));
        expect(ready.phase, BootstrapPhase.ready);
        expect(pending.isCompleted, isFalse);
        expect(chatPort.ensureCalls, 1);
        pending.completeError(StateError('Chat transport failed later'));
        await Future<void>.delayed(Duration.zero);
        expect(ready.phase, BootstrapPhase.ready);
        expect(authPort.clearCalls, 0);
      },
    );

    test(
      'requires sign-in when restored access needs reauthorization',
      () async {
        final authPort = _FakeAppAuthPort()
          ..restoreSessionHandler = (_) async =>
              AuthState.authenticated(buildTestAuthSession());
        final identitySessionPort = _FakeIdentitySessionPort()
          ..result = IdentitySessionReconciliation.reauthorizationRequired;
        final useCase = _buildUseCase(
          authPort: authPort,
          identitySessionPort: identitySessionPort,
          serverConfigurationPort: _FakeServerConfigurationPort(
            configuration: buildTestConfiguration(),
          ),
        );

        final state = await useCase.call();

        expect(state.phase, BootstrapPhase.needsSignIn);
        expect(identitySessionPort.calls, 1);
        expect(authPort.clearCalls, 1);
        expect(authPort.refreshCalls, 0);
      },
    );

    test('maps auth failures to bootstrap storage errors', () async {
      final authPort = _FakeAppAuthPort()
        ..restoreSessionHandler = (_) async {
          throw const AuthFailure.storage('Broken secure store.');
        };
      final useCase = _buildUseCase(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(),
        ),
      );

      final state = await useCase.call();

      expect(state.phase, BootstrapPhase.error);
      expect(state.failure?.type, AppFailureType.storage);
      expect(state.failure?.message, 'Broken secure store.');
    });
  });
}
