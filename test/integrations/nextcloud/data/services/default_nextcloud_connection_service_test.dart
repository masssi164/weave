import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/nextcloud/data/services/default_nextcloud_connection_service.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_auth_client.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_dav_access_validator.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_login_launcher.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_connection_state.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';
import 'package:weave/integrations/nextcloud/domain/repositories/nextcloud_session_repository.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _NoopHttpClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    throw UnimplementedError();
  }
}

class _FakeAuthSessionRepository implements AuthSessionRepository {
  AuthState state = const AuthState.signedOut();
  int restoreCalls = 0;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    restoreCalls++;
    return state;
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _FakeNextcloudAuthClient extends NextcloudAuthClient {
  _FakeNextcloudAuthClient()
    : super(
        httpClient: _NoopHttpClient(),
        loginLauncher: _FakeNextcloudLoginLauncher(),
      );

  NextcloudSession? appPasswordSessionToReturn;
  final Map<String, NextcloudSession> bearerSessionsByToken =
      <String, NextcloudSession>{};
  final Map<String, NextcloudFailure> bearerFailuresByToken =
      <String, NextcloudFailure>{};
  int connectCalls = 0;
  int createBearerSessionCalls = 0;
  int revokeCalls = 0;
  final List<NextcloudSession> revokedSessions = <NextcloudSession>[];

  @override
  Future<NextcloudSession> connect(Uri configuredBaseUrl) async {
    connectCalls++;
    final session = appPasswordSessionToReturn;
    if (session == null) {
      throw const NextcloudFailure.protocol(
        'No app-password session configured.',
      );
    }
    return session;
  }

  @override
  Future<NextcloudSession> createBearerSession({
    required Uri configuredBaseUrl,
    required String bearerToken,
    String? accountLabelHint,
  }) async {
    createBearerSessionCalls++;
    final failure = bearerFailuresByToken[bearerToken];
    if (failure != null) {
      throw failure;
    }

    final session = bearerSessionsByToken[bearerToken];
    if (session == null) {
      throw const NextcloudFailure.invalidCredentials(
        'The saved Nextcloud credentials are no longer valid.',
      );
    }
    return session;
  }

  @override
  Future<void> revokeAppPassword(NextcloudSession session) async {
    revokeCalls++;
    revokedSessions.add(session);
  }
}

class _FakeNextcloudDavAccessValidator implements NextcloudDavAccessValidator {
  final Map<String, NextcloudFailure> failuresByToken =
      <String, NextcloudFailure>{};
  final List<NextcloudSession> sessions = <NextcloudSession>[];

  @override
  Future<void> validateRootAccess(NextcloudSession session) async {
    sessions.add(session);
    final token = session.bearerToken;
    final failure = token == null ? null : failuresByToken[token];
    if (failure != null) {
      throw failure;
    }
  }
}

class _FakeNextcloudLoginLauncher implements NextcloudLoginLauncher {
  @override
  Future<void> launch(Uri loginUri) async {}
}

class _FakeNextcloudSessionRepository implements NextcloudSessionRepository {
  NextcloudSession? session;
  int clearCalls = 0;
  int saveCalls = 0;
  NextcloudFailure? saveFailure;

  @override
  Future<void> clearSession() async {
    clearCalls++;
    session = null;
  }

  @override
  Future<NextcloudSession?> readSession() async => session;

  @override
  Future<void> saveSession(NextcloudSession session) async {
    final failure = saveFailure;
    if (failure != null) {
      throw failure;
    }

    saveCalls++;
    this.session = session;
  }
}

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
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
  group('DefaultNextcloudConnectionService', () {
    late _FakeAuthSessionRepository authSessionRepository;
    late _FakeNextcloudAuthClient authClient;
    late _FakeNextcloudDavAccessValidator davAccessValidator;
    late _FakeNextcloudSessionRepository sessionRepository;
    late _FakeServerConfigurationRepository configurationRepository;
    late DefaultNextcloudConnectionService service;

    final bearerSession = NextcloudSession.oidcBearer(
      baseUrl: Uri.parse('https://nextcloud.home.internal/'),
      userId: 'alice',
      accountLabel: 'alice',
      bearerToken: 'id-token',
    );
    final appPasswordSession = NextcloudSession.appPassword(
      baseUrl: Uri.parse('https://nextcloud.home.internal/'),
      loginName: 'alice@example.com',
      userId: 'alice',
      appPassword: 'app-password',
    );

    setUp(() {
      authSessionRepository = _FakeAuthSessionRepository();
      authClient = _FakeNextcloudAuthClient();
      davAccessValidator = _FakeNextcloudDavAccessValidator();
      sessionRepository = _FakeNextcloudSessionRepository();
      configurationRepository = _FakeServerConfigurationRepository(
        buildTestConfiguration(
          nextcloudBaseUrl: 'https://nextcloud.home.internal',
        ),
      );
      service = DefaultNextcloudConnectionService(
        authClient: authClient,
        davAccessValidator: davAccessValidator,
        authSessionRepository: authSessionRepository,
        sessionRepository: sessionRepository,
        serverConfigurationRepository: configurationRepository,
      );
    });

    test(
      'restoreConnection returns disconnected when no session is stored',
      () async {
        final state = await service.restoreConnection();

        expect(state.status, NextcloudConnectionStatus.disconnected);
        expect(state.baseUrl, Uri.parse('https://nextcloud.home.internal'));
      },
    );

    test(
      'restoreConnection allows HTTP files URLs for local dev stacks',
      () async {
        configurationRepository.configuration = buildTestConfiguration(
          nextcloudBaseUrl: 'http://files.home.internal',
        );

        final state = await service.restoreConnection();

        expect(state.status, NextcloudConnectionStatus.disconnected);
        expect(state.baseUrl, Uri.parse('http://files.home.internal'));
      },
    );

    test(
      'connect stores a bearer-mode session when OIDC bearer access works',
      () async {
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerSessionsByToken['id-token'] = bearerSession;

        final state = await service.connect();

        expect(state.status, NextcloudConnectionStatus.connected);
        expect(authClient.connectCalls, 0);
        expect(sessionRepository.saveCalls, 1);
        expect(sessionRepository.session?.usesOidcBearer, isTrue);
        expect(sessionRepository.session?.bearerToken, isNull);
        expect(davAccessValidator.sessions.single.bearerToken, 'id-token');
      },
    );

    test(
      'connect falls back to the Login Flow when bearer DAV access is unavailable',
      () async {
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerFailuresByToken['id-token'] =
            const NextcloudFailure.invalidCredentials(
              'The saved Nextcloud credentials are no longer valid.',
            );
        authClient.bearerFailuresByToken['access-token'] =
            const NextcloudFailure.protocol(
              'Nextcloud returned an unexpected WebDAV status (401).',
            );
        authClient.appPasswordSessionToReturn = appPasswordSession;

        final state = await service.connect();

        expect(state.status, NextcloudConnectionStatus.connected);
        expect(authClient.connectCalls, 1);
        expect(sessionRepository.session?.usesAppPassword, isTrue);
        expect(sessionRepository.session?.appPassword, 'app-password');
      },
    );

    test(
      'restoreConnection migrates a saved app-password session to bearer mode when bearer works',
      () async {
        sessionRepository.session = appPasswordSession;
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerSessionsByToken['id-token'] = bearerSession;

        final state = await service.restoreConnection();

        expect(state.status, NextcloudConnectionStatus.connected);
        expect(sessionRepository.session?.usesOidcBearer, isTrue);
        expect(authClient.revokeCalls, 1);
        expect(authClient.revokedSessions.single.appPassword, 'app-password');
      },
    );

    test(
      'restoreConnection keeps a saved app-password session when bearer is unavailable',
      () async {
        sessionRepository.session = appPasswordSession;
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerFailuresByToken['id-token'] =
            const NextcloudFailure.invalidCredentials(
              'The saved Nextcloud credentials are no longer valid.',
            );
        authClient.bearerFailuresByToken['access-token'] =
            const NextcloudFailure.protocol(
              'Nextcloud returned an unexpected WebDAV status (401).',
            );

        final state = await service.restoreConnection();

        expect(state.status, NextcloudConnectionStatus.connected);
        expect(sessionRepository.session?.usesAppPassword, isTrue);
        expect(authClient.revokeCalls, 0);
      },
    );

    test(
      'requireLiveSession resolves a live bearer session for a stored bearer marker',
      () async {
        sessionRepository.session = bearerSession.toPersistedSession();
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerSessionsByToken['id-token'] = bearerSession;

        final session = await service.requireLiveSession();

        expect(session.usesOidcBearer, isTrue);
        expect(session.bearerToken, 'id-token');
      },
    );

    test(
      'requireLiveSession clears a stored bearer marker when bearer access is unavailable',
      () async {
        sessionRepository.session = bearerSession.toPersistedSession();

        await expectLater(
          service.requireLiveSession(),
          throwsA(
            isA<NextcloudFailure>().having(
              (failure) => failure.type,
              'type',
              NextcloudFailureType.invalidCredentials,
            ),
          ),
        );

        expect(sessionRepository.session, isNull);
      },
    );

    test(
      'invalidateSession clears and revokes an app-password session',
      () async {
        sessionRepository.session = appPasswordSession;

        await service.invalidateSession(appPasswordSession);

        expect(sessionRepository.session, isNull);
        expect(authClient.revokeCalls, 1);
      },
    );

    test(
      'disconnect clears a stored bearer marker without revoking an app password',
      () async {
        sessionRepository.session = bearerSession.toPersistedSession();

        await service.disconnect();

        expect(sessionRepository.session, isNull);
        expect(authClient.revokeCalls, 0);
      },
    );

    test(
      'restoreConnection clears a stale session when the configured server changes',
      () async {
        sessionRepository.session = appPasswordSession;
        configurationRepository.configuration = buildTestConfiguration(
          nextcloudBaseUrl: 'https://other-nextcloud.home.internal',
        );

        final state = await service.restoreConnection();

        expect(state.status, NextcloudConnectionStatus.disconnected);
        expect(sessionRepository.clearCalls, 1);
        expect(sessionRepository.session, isNull);
        expect(authClient.revokeCalls, 1);
      },
    );
  });
}
