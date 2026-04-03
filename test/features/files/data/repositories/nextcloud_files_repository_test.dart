import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
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

import '../../../../helpers/server_config_test_data.dart';

class _NoopHttpClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    throw UnimplementedError();
  }
}

class _FakeNextcloudAuthClient extends NextcloudAuthClient {
  _FakeNextcloudAuthClient()
    : super(
        httpClient: _NoopHttpClient(),
        loginLauncher: _FakeNextcloudLoginLauncher(),
      );

  NextcloudSession? sessionToReturn;
  int connectCalls = 0;
  int revokeCalls = 0;

  @override
  Future<NextcloudSession> connect(Uri configuredBaseUrl) async {
    connectCalls++;
    final session = sessionToReturn;
    if (session == null) {
      throw const FilesFailure.protocol('No session configured for the test.');
    }
    return session;
  }

  @override
  Future<void> revokeAppPassword(NextcloudSession session) async {
    revokeCalls++;
  }
}

class _FakeNextcloudClient extends NextcloudClient {
  _FakeNextcloudClient() : super(httpClient: _NoopHttpClient());

  DirectoryListing? listingToReturn;
  String? lastPath;

  @override
  Future<DirectoryListing> listDirectory(NextcloudSession session, String path) async {
    lastPath = path;
    final listing = listingToReturn;
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

class _FakeServerConfigurationRepository implements ServerConfigurationRepository {
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
    late _FakeNextcloudAuthClient authClient;
    late _FakeNextcloudClient client;
    late _FakeNextcloudSessionRepository sessionRepository;
    late _FakeServerConfigurationRepository configurationRepository;
    late NextcloudFilesRepository repository;

    final session = NextcloudSession(
      baseUrl: Uri.parse('https://nextcloud.home.internal/'),
      loginName: 'alice@example.com',
      userId: 'alice',
      appPassword: 'app-password',
    );

    setUp(() {
      authClient = _FakeNextcloudAuthClient();
      client = _FakeNextcloudClient();
      sessionRepository = _FakeNextcloudSessionRepository();
      configurationRepository = _FakeServerConfigurationRepository(
        buildTestConfiguration(),
      );
      repository = NextcloudFilesRepository(
        authClient: authClient,
        client: client,
        sessionRepository: sessionRepository,
        serverConfigurationRepository: configurationRepository,
      );
    });

    test('restoreConnection returns disconnected when no session is stored', () async {
      final state = await repository.restoreConnection();

      expect(state.status, FilesConnectionStatus.disconnected);
      expect(state.baseUrl, Uri.parse('https://nextcloud.home.internal'));
    });

    test('connect persists the Nextcloud session', () async {
      authClient.sessionToReturn = session;

      final state = await repository.connect();

      expect(state.status, FilesConnectionStatus.connected);
      expect(sessionRepository.saveCalls, 1);
      expect(sessionRepository.session?.userId, 'alice');
    });

    test('connect revokes the app password when secure storage persistence fails', () async {
      authClient.sessionToReturn = session;
      sessionRepository.saveFailure = const FilesFailure.storage(
        'Unable to save the Nextcloud session.',
      );

      await expectLater(
        repository.connect(),
        throwsA(
          isA<FilesFailure>().having(
            (failure) => failure.type,
            'type',
            FilesFailureType.storage,
          ),
        ),
      );

      expect(authClient.revokeCalls, 1);
    });

    test('listDirectory delegates to the WebDAV client for the saved session', () async {
      sessionRepository.session = session;
      client.listingToReturn = const DirectoryListing(
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

      final listing = await repository.listDirectory('/');

      expect(client.lastPath, '/');
      expect(listing.entries.single.name, 'Documents');
    });

    test('restoreConnection clears a stale session when the configured server changes', () async {
      sessionRepository.session = session;
      configurationRepository.configuration = buildTestConfiguration(
        nextcloudBaseUrl: 'https://other-nextcloud.home.internal',
      );

      final state = await repository.restoreConnection();

      expect(state.status, FilesConnectionStatus.disconnected);
      expect(sessionRepository.clearCalls, 1);
      expect(sessionRepository.session, isNull);
    });
  });
}
