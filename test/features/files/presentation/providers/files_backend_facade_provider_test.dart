import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeAuthSessionRepository implements AuthSessionRepository {
  _FakeAuthSessionRepository(this.state);

  AuthState state;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;
}

void main() {
  group('filesRepositoryProvider backend-facade seam', () {
    test('uses the backend facade as the MVP default', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      expect(container.read(useBackendFilesFacadeProvider), isTrue);
    });

    test('keeps direct Nextcloud behind an explicit fallback switch', () {
      final container = ProviderContainer(
        overrides: [useBackendFilesFacadeProvider.overrideWithValue(false)],
      );
      addTearDown(container.dispose);

      expect(container.read(useBackendFilesFacadeProvider), isFalse);
    });
  });

  group('BackendFilesRepository', () {
    late _FakeServerConfigurationRepository configurationRepository;
    late _FakeAuthSessionRepository authSessionRepository;

    BackendFilesRepository repository(http.Client client) {
      return BackendFilesRepository(
        httpClient: client,
        serverConfigurationRepository: configurationRepository,
        authSessionRepository: authSessionRepository,
      );
    }

    setUp(() {
      configurationRepository = _FakeServerConfigurationRepository(
        buildTestConfiguration(
          backendApiBaseUrl: 'https://api.home.internal/api',
        ),
      );
      authSessionRepository = _FakeAuthSessionRepository(
        AuthState.authenticated(
          buildTestAuthSession(accessToken: 'files-token'),
        ),
      );
    });

    test(
      'restores as connected when Weave auth and backend URL are present',
      () async {
        final state = await repository(
          MockClient((_) async => http.Response('', 500)),
        ).restoreConnection();

        expect(state.status, FilesConnectionStatus.connected);
        expect(state.baseUrl, Uri.parse('https://api.home.internal/api'));
        expect(state.accountLabel, BackendFilesRepository.accountLabel);
      },
    );

    test(
      'lists files through the backend facade with the Weave token',
      () async {
        late http.Request capturedRequest;
        final client = MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'path': '/Team',
              'items': [
                {
                  'id': 'files:/Team/Design',
                  'name': 'Design',
                  'path': '/Team/Design',
                  'type': 'folder',
                  'mimeType': null,
                  'size': null,
                  'modifiedAt': '2026-04-26T09:00:00Z',
                  'downloadable': false,
                },
                {
                  'id': 'files:/Team/readme.md',
                  'name': 'readme.md',
                  'path': '/Team/readme.md',
                  'type': 'file',
                  'mimeType': 'text/markdown',
                  'size': 42,
                  'modifiedAt': '2026-04-26T09:05:00Z',
                  'downloadable': true,
                },
              ],
            }),
            200,
          );
        });

        final listing = await repository(client).listDirectory('/Team');

        expect(capturedRequest.method, 'GET');
        expect(
          capturedRequest.url.toString(),
          'https://api.home.internal/api/files?path=%2FTeam',
        );
        expect(capturedRequest.headers['authorization'], 'Bearer files-token');
        expect(listing.path, '/Team');
        expect(listing.entries, hasLength(2));
        expect(listing.entries.first.isDirectory, isTrue);
        expect(listing.entries.last.sizeInBytes, 42);
      },
    );

    test(
      'creates folders, prepares downloads, and deletes via backend endpoints',
      () async {
        final requests = <http.Request>[];
        final client = MockClient((request) async {
          requests.add(request);
          if (request.method == 'POST') {
            expect(jsonDecode(request.body), {
              'parentPath': '/Team',
              'name': 'Design',
            });
            return http.Response(
              jsonEncode({
                'id': 'files:/Team/Design',
                'name': 'Design',
                'path': '/Team/Design',
                'type': 'folder',
                'downloadable': false,
              }),
              200,
            );
          }
          return http.Response('', 204);
        });
        final backendRepository = repository(client);

        final folder = await backendRepository.createFolder(
          parentPath: '/Team',
          name: 'Design',
        );
        await backendRepository.prepareDownload('files:/Team/readme.md');
        await backendRepository.delete('files:/Team/old.md');

        expect(folder.path, '/Team/Design');
        expect(requests.map((request) => '${request.method} ${request.url}'), [
          'POST https://api.home.internal/api/files/folders',
          'GET https://api.home.internal/api/files/files:%2FTeam%2Freadme.md/download',
          'DELETE https://api.home.internal/api/files/files:%2FTeam%2Fold.md',
        ]);
      },
    );

    test('uploads multipart data through the backend facade', () async {
      late http.BaseRequest capturedRequest;
      final client = _RecordingStreamClient((request) async {
        capturedRequest = request;
        final body = await request.finalize().toBytes();
        expect(utf8.decode(body), contains('hello'));
        return http.StreamedResponse(const Stream<List<int>>.empty(), 200);
      });
      final progress = <int>[];

      await repository(client).uploadFile(
        '/Team',
        FileUploadRequest(
          fileName: 'notes.txt',
          sizeInBytes: 5,
          byteStream: Stream<List<int>>.fromIterable([utf8.encode('hello')]),
        ),
        onProgress: (uploadedBytes, _) => progress.add(uploadedBytes),
      );

      expect(capturedRequest.method, 'POST');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/files/upload?parentPath=%2FTeam',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer files-token');
      expect(progress, contains(5));
    });

    test(
      'maps backend auth rejection without falling back to direct Nextcloud',
      () async {
        final client = MockClient(
          (_) async => http.Response(
            jsonEncode({
              'message': 'The Weave backend rejected the current session.',
            }),
            401,
          ),
        );

        await expectLater(
          repository(client).listDirectory('/'),
          throwsA(
            isA<FilesFailure>().having(
              (failure) => failure.type,
              'type',
              FilesFailureType.invalidCredentials,
            ),
          ),
        );
      },
    );
  });
}

class _RecordingStreamClient extends http.BaseClient {
  _RecordingStreamClient(this.handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return handler(request);
  }
}
