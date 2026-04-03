import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_auth_client.dart';
import 'package:weave/features/files/data/services/nextcloud_client.dart';
import 'package:weave/features/files/data/services/nextcloud_login_launcher.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/entities/nextcloud_session.dart';
import 'package:weave/features/files/domain/repositories/nextcloud_session_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

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
  final Map<String, FilesFailure> bearerFailuresByToken =
      <String, FilesFailure>{};
  int connectCalls = 0;
  int createBearerSessionCalls = 0;
  int revokeCalls = 0;
  final List<NextcloudSession> revokedSessions = <NextcloudSession>[];

  @override
  Future<NextcloudSession> connect(Uri configuredBaseUrl) async {
    connectCalls++;
    final session = appPasswordSessionToReturn;
    if (session == null) {
      throw const FilesFailure.protocol('No app-password session configured.');
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
      throw const FilesFailure.invalidCredentials(
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

class _FakeNextcloudClient extends NextcloudClient {
  _FakeNextcloudClient() : super(httpClient: _NoopHttpClient());

  final Map<String, DirectoryListing> listingsByPath =
      <String, DirectoryListing>{};
  FilesFailure? directoryFailure;
  final List<NextcloudSession> sessions = <NextcloudSession>[];
  final List<String> paths = <String>[];

  @override
  Future<DirectoryListing> listDirectory(
    NextcloudSession session,
    String path,
  ) async {
    sessions.add(session);
    paths.add(path);
    final failure = directoryFailure;
    if (failure != null) {
      throw failure;
    }

    final listing = listingsByPath[path];
    if (listing == null) {
      throw const FilesFailure.protocol('No listing configured for the test.');
    }
    return listing;
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
  FilesFailure? saveFailure;

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
  group('NextcloudFilesRepository', () {
    late _FakeAuthSessionRepository authSessionRepository;
    late _FakeNextcloudAuthClient authClient;
    late _FakeNextcloudClient client;
    late _FakeNextcloudSessionRepository sessionRepository;
    late _FakeServerConfigurationRepository configurationRepository;
    late NextcloudFilesRepository repository;

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
    const rootListing = DirectoryListing(
      path: '/',
      entries: [
        FileEntry(
          id: 'folder-1',
          name: 'Documents',
          path: '/Documents',
          isDirectory: true,
        ),
      ],
    );

    setUp(() {
      authSessionRepository = _FakeAuthSessionRepository();
      authClient = _FakeNextcloudAuthClient();
      client = _FakeNextcloudClient()..listingsByPath['/'] = rootListing;
      sessionRepository = _FakeNextcloudSessionRepository();
      configurationRepository = _FakeServerConfigurationRepository(
        buildTestConfiguration(),
      );
      repository = NextcloudFilesRepository(
        authClient: authClient,
        client: client,
        authSessionRepository: authSessionRepository,
        sessionRepository: sessionRepository,
        serverConfigurationRepository: configurationRepository,
      );
    });

    test(
      'restoreConnection returns disconnected when no files session is stored',
      () async {
        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.disconnected);
        expect(state.baseUrl, Uri.parse('https://nextcloud.home.internal'));
      },
    );

    test(
      'connect stores a bearer-mode files session when OIDC bearer access works',
      () async {
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerSessionsByToken['id-token'] = bearerSession;

        final state = await repository.connect();

        expect(state.status, FilesConnectionStatus.connected);
        expect(authClient.connectCalls, 0);
        expect(sessionRepository.saveCalls, 1);
        expect(sessionRepository.session?.usesOidcBearer, isTrue);
        expect(sessionRepository.session?.bearerToken, isNull);
        expect(client.paths, ['/']);
      },
    );

    test(
      'connect falls back to the Login Flow when bearer DAV access is unavailable',
      () async {
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerFailuresByToken['id-token'] =
            const FilesFailure.invalidCredentials(
              'The saved Nextcloud credentials are no longer valid.',
            );
        authClient.bearerFailuresByToken['access-token'] =
            const FilesFailure.protocol(
              'Nextcloud returned an unexpected WebDAV status (401).',
            );
        authClient.appPasswordSessionToReturn = appPasswordSession;

        final state = await repository.connect();

        expect(state.status, FilesConnectionStatus.connected);
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

        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.connected);
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
            const FilesFailure.invalidCredentials(
              'The saved Nextcloud credentials are no longer valid.',
            );
        authClient.bearerFailuresByToken['access-token'] =
            const FilesFailure.protocol(
              'Nextcloud returned an unexpected WebDAV status (401).',
            );

        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.connected);
        expect(sessionRepository.session?.usesAppPassword, isTrue);
        expect(authClient.revokeCalls, 0);
      },
    );

    test(
      'listDirectory resolves a live bearer session for a stored bearer marker',
      () async {
        sessionRepository.session = bearerSession.toPersistedSession();
        authSessionRepository.state = AuthState.authenticated(
          buildTestAuthSession(),
        );
        authClient.bearerSessionsByToken['id-token'] = bearerSession;

        final listing = await repository.listDirectory('/');

        expect(listing.entries.single.name, 'Documents');
        expect(client.sessions.single.usesOidcBearer, isTrue);
        expect(client.sessions.single.bearerToken, 'id-token');
      },
    );

    test('listDirectory clears an invalid app-password session', () async {
      sessionRepository.session = appPasswordSession;
      client.directoryFailure = const FilesFailure.invalidCredentials(
        'The saved Nextcloud credentials are no longer valid.',
      );

      await expectLater(
        repository.listDirectory('/'),
        throwsA(
          isA<FilesFailure>().having(
            (failure) => failure.type,
            'type',
            FilesFailureType.invalidCredentials,
          ),
        ),
      );

      expect(sessionRepository.session, isNull);
      expect(authClient.revokeCalls, 1);
    });

    test(
      'disconnect clears a stored bearer marker without revoking an app password',
      () async {
        sessionRepository.session = bearerSession.toPersistedSession();

        await repository.disconnect();

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

        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.disconnected);
        expect(sessionRepository.clearCalls, 1);
        expect(sessionRepository.session, isNull);
        expect(authClient.revokeCalls, 1);
      },
    );
  });
}
