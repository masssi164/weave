import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_dav_client.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_connection_state.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';
import 'package:weave/integrations/nextcloud/domain/services/nextcloud_connection_service.dart';

class _NoopHttpClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    throw UnimplementedError();
  }
}

class _FakeNextcloudConnectionService implements NextcloudConnectionService {
  NextcloudConnectionState restoreState =
      const NextcloudConnectionState.disconnected();
  NextcloudConnectionState connectState =
      const NextcloudConnectionState.disconnected();
  NextcloudFailure? restoreFailure;
  NextcloudFailure? connectFailure;
  NextcloudFailure? disconnectFailure;
  NextcloudFailure? liveSessionFailure;
  NextcloudSession? liveSession;
  int restoreCalls = 0;
  int connectCalls = 0;
  int disconnectCalls = 0;
  int requireLiveSessionCalls = 0;
  int invalidateCalls = 0;
  final List<NextcloudSession> invalidatedSessions = <NextcloudSession>[];

  @override
  Future<NextcloudConnectionState> connect() async {
    connectCalls++;
    final failure = connectFailure;
    if (failure != null) {
      throw failure;
    }
    return connectState;
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
    final failure = disconnectFailure;
    if (failure != null) {
      throw failure;
    }
  }

  @override
  Future<void> invalidateSession(NextcloudSession session) async {
    invalidateCalls++;
    invalidatedSessions.add(session);
  }

  @override
  Future<NextcloudSession> requireLiveSession() async {
    requireLiveSessionCalls++;
    final failure = liveSessionFailure;
    if (failure != null) {
      throw failure;
    }
    return liveSession!;
  }

  @override
  Future<NextcloudConnectionState> restoreConnection() async {
    restoreCalls++;
    final failure = restoreFailure;
    if (failure != null) {
      throw failure;
    }
    return restoreState;
  }
}

class _FakeNextcloudDavClient extends NextcloudDavClient {
  _FakeNextcloudDavClient() : super(httpClient: _NoopHttpClient());

  final Map<String, DirectoryListing> listingsByPath =
      <String, DirectoryListing>{};
  NextcloudFailure? directoryFailure;
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
      throw const NextcloudFailure.protocol(
        'No listing configured for the test.',
      );
    }
    return listing;
  }
}

void main() {
  group('NextcloudFilesRepository', () {
    late _FakeNextcloudConnectionService connectionService;
    late _FakeNextcloudDavClient davClient;
    late NextcloudFilesRepository repository;

    final liveSession = NextcloudSession.oidcBearer(
      baseUrl: Uri.parse('https://nextcloud.home.internal/'),
      userId: 'alice',
      accountLabel: 'Alice',
      bearerToken: 'live-token',
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
      connectionService = _FakeNextcloudConnectionService()
        ..restoreState = NextcloudConnectionState.connected(
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
          accountLabel: 'Alice',
        )
        ..connectState = NextcloudConnectionState.connected(
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
          accountLabel: 'Alice',
        )
        ..liveSession = liveSession;
      davClient = _FakeNextcloudDavClient()..listingsByPath['/'] = rootListing;
      repository = NextcloudFilesRepository(
        connectionService: connectionService,
        client: davClient,
      );
    });

    test(
      'restoreConnection maps shared connection state into files state',
      () async {
        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.connected);
        expect(state.baseUrl, Uri.parse('https://nextcloud.home.internal'));
        expect(state.accountLabel, 'Alice');
        expect(connectionService.restoreCalls, 1);
      },
    );

    test('connect maps shared failures into files failures', () async {
      connectionService.connectFailure = const NextcloudFailure.configuration(
        'Finish server setup before connecting Nextcloud.',
      );

      await expectLater(
        repository.connect(),
        throwsA(
          isA<FilesFailure>().having(
            (failure) => failure.type,
            'type',
            FilesFailureType.configuration,
          ),
        ),
      );
    });

    test('listDirectory uses the shared live session and DAV client', () async {
      final listing = await repository.listDirectory('/');

      expect(listing.entries.single.name, 'Documents');
      expect(connectionService.requireLiveSessionCalls, 1);
      expect(davClient.sessions.single.bearerToken, 'live-token');
      expect(davClient.paths.single, '/');
    });

    test(
      'listDirectory invalidates the shared session on invalid credentials',
      () async {
        davClient.directoryFailure = const NextcloudFailure.invalidCredentials(
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

        expect(connectionService.invalidateCalls, 1);
        expect(connectionService.invalidatedSessions.single, same(liveSession));
      },
    );
  });
}
