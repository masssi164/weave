import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

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
  AuthState? refreshedState;
  int refreshCalls = 0;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    refreshCalls++;
    return refreshedState ?? state;
  }

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
    test('always uses the backend facade in release client paths', () {
      final container = ProviderContainer(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWithValue(
            _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
          authSessionRepositoryProvider.overrideWithValue(
            _FakeAuthSessionRepository(
              AuthState.authenticated(buildTestAuthSession()),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(filesRepositoryProvider),
        isA<BackendFilesRepository>(),
      );
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
      'lists files through the Weave WebDAV data plane with the Weave token',
      () async {
        late http.Request capturedRequest;
        final client = MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            '''
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/files/Team/</d:href>
                <d:propstat><d:prop><d:displayname>Team</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/files/Team/Design/</d:href>
                <d:propstat><d:prop><d:displayname>Design</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/files/Team/readme.md</d:href>
                <d:propstat><d:prop><d:displayname>readme.md</d:displayname><d:resourcetype/><d:getcontentlength>42</d:getcontentlength><d:getcontenttype>text/markdown</d:getcontenttype><d:getlastmodified>Sat, 04 Jul 2026 12:01:00 GMT</d:getlastmodified></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            ''',
            207,
            headers: {'content-type': 'application/xml'},
          );
        });

        final listing = await repository(client).listDirectory('/Team');

        expect(capturedRequest.method, 'PROPFIND');
        expect(
          capturedRequest.url.toString(),
          'https://api.home.internal/dav/files/Team',
        );
        expect(capturedRequest.headers['authorization'], 'Bearer files-token');
        expect(capturedRequest.headers['depth'], '1');
        expect(listing.path, '/Team');
        expect(listing.entries, hasLength(2));
        expect(listing.entries.first.isDirectory, isTrue);
        expect(listing.entries.last.sizeInBytes, 42);
        expect(
          listing.entries.last.modifiedAt,
          DateTime.utc(2026, 7, 4, 12, 1),
        );
      },
    );

    test('fails closed when WebDAV files response is malformed', () async {
      final client = MockClient(
        (_) async => http.Response('<d:multistatus>', 207),
      );

      await expectLater(
        repository(client).listDirectory('/Team'),
        throwsA(
          isA<FilesFailure>()
              .having(
                (failure) => failure.type,
                'type',
                FilesFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                contains('invalid WebDAV files listing'),
              ),
        ),
      );
    });

    test('downloads files through the Weave WebDAV data plane', () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        return http.Response.bytes(
          const [1, 2, 3],
          200,
          headers: {
            'content-disposition':
                "attachment; filename*=UTF-8''readme%20export.md",
          },
        );
      });
      final backendRepository = repository(client);

      final download = await backendRepository.downloadFile(
        const FileEntry(
          id: 'files:/Team/readme.md',
          name: 'readme.md',
          path: '/Team/readme.md',
          isDirectory: false,
        ),
      );

      expect(download.fileName, 'readme export.md');
      expect(download.bytes, <int>[1, 2, 3]);
      expect(requests.single.headers['authorization'], 'Bearer files-token');
      expect(requests.map((request) => '${request.method} ${request.url}'), [
        'GET https://api.home.internal/dav/files/Team/readme.md',
      ]);
    });

    test('writes files through the Weave WebDAV data plane', () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        return switch (request.method) {
          'PUT' => http.Response('', 201, headers: {'etag': '"created"'}),
          'MKCOL' => http.Response(
            '',
            201,
            headers: {'location': '/dav/files/Team/Design/'},
          ),
          'DELETE' => http.Response('', 204),
          _ => http.Response('', 500),
        };
      });
      final backendRepository = repository(client);
      final progress = <String>[];

      await backendRepository.uploadFile(
        '/Team',
        FileUploadRequest(
          fileName: 'notes.txt',
          sizeInBytes: 5,
          byteStream: Stream<List<int>>.fromIterable(const [
            [1, 2],
            [3, 4, 5],
          ]),
        ),
        onProgress: (uploaded, total) => progress.add('$uploaded/$total'),
      );
      final created = await backendRepository.createFolder(
        parentPath: '/Team',
        name: 'Design',
      );
      await backendRepository.deleteEntry(
        const FileEntry(
          id: 'files:/Team/old.md',
          name: 'old.md',
          path: '/Team/old.md',
          isDirectory: false,
        ),
      );

      expect(progress, ['2/5', '5/5']);
      expect(created.path, '/Team/Design');
      expect(created.isDirectory, isTrue);
      expect(requests.map((request) => '${request.method} ${request.url}'), [
        'PUT https://api.home.internal/dav/files/Team/notes.txt',
        'MKCOL https://api.home.internal/dav/files/Team/Design',
        'DELETE https://api.home.internal/dav/files/Team/old.md',
      ]);
      expect(requests[0].headers['authorization'], 'Bearer files-token');
      expect(requests[0].headers['if-none-match'], '*');
      expect(requests[0].headers['content-type'], 'application/octet-stream');
      expect(requests[0].bodyBytes, <int>[1, 2, 3, 4, 5]);
      expect(requests[1].headers['if-none-match'], '*');
      expect(requests[2].headers['authorization'], 'Bearer files-token');
    });

    test('rejects unsupported WebDAV child names before network calls', () async {
      var networkCalls = 0;
      final backendRepository = repository(
        MockClient((_) async {
          networkCalls++;
          return http.Response('', 500);
        }),
      );

      await expectLater(
        backendRepository.uploadFile(
          '/Team',
          FileUploadRequest(
            fileName: '..',
            sizeInBytes: 1,
            byteStream: Stream<List<int>>.fromIterable(const [
              [1],
            ]),
          ),
        ),
        throwsA(
          isA<FilesFailure>()
              .having(
                (failure) => failure.type,
                'type',
                FilesFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                contains('file name is not valid'),
              ),
        ),
      );

      await expectLater(
        backendRepository.createFolder(
          parentPath: '/Team',
          name: r'bad\name',
        ),
        throwsA(
          isA<FilesFailure>().having(
            (failure) => failure.type,
            'type',
            FilesFailureType.protocol,
          ),
        ),
      );
      expect(networkCalls, 0);
    });

    test('maps WebDAV write precondition failures support-safely', () async {
      final client = MockClient(
        (_) async => http.Response(
          '''
          <?xml version="1.0" encoding="UTF-8"?>
          <d:error xmlns:d="DAV:">
            <d:responsedescription>The file operation conflicts with the current workspace state.</d:responsedescription>
          </d:error>
          ''',
          412,
          headers: {'content-type': 'application/xml'},
        ),
      );

      await expectLater(
        repository(client).uploadFile(
          '/Team',
          FileUploadRequest(
            fileName: 'notes.txt',
            sizeInBytes: 5,
            byteStream: Stream<List<int>>.fromIterable(const [
              [1, 2, 3, 4, 5],
            ]),
          ),
        ),
        throwsA(
          isA<FilesFailure>()
              .having(
                (failure) => failure.type,
                'type',
                FilesFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                allOf(
                  contains('conflicts with the current workspace state'),
                  isNot(contains('Nextcloud')),
                  isNot(contains('remote.php')),
                ),
              ),
        ),
      );
    });

    test(
      'refreshes the Weave session once after a backend 401 and retries',
      () async {
        authSessionRepository.refreshedState = AuthState.authenticated(
          buildTestAuthSession(accessToken: 'fresh-files-token'),
        );
        final authorizationHeaders = <String?>[];
        final client = MockClient((request) async {
          authorizationHeaders.add(request.headers['authorization']);
          if (authorizationHeaders.length == 1) {
            return http.Response(
              jsonEncode({'message': 'Authentication is required.'}),
              401,
            );
          }
          return http.Response(
            '''
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/files/</d:href>
                <d:propstat><d:prop><d:displayname>Files</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            ''',
            207,
            headers: {'content-type': 'application/xml'},
          );
        });

        final listing = await repository(client).listDirectory('/');

        expect(listing.entries, isEmpty);
        expect(authSessionRepository.refreshCalls, 1);
        expect(authorizationHeaders, [
          'Bearer files-token',
          'Bearer fresh-files-token',
        ]);
      },
    );

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

    test('uses support-safe memberImpact instead of raw backend message', () async {
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({
            'message':
                'Nextcloud WebDAV failed at https://files.home.internal/remote.php/dav',
            'memberImpact':
                'Files need admin attention before members can use them reliably.',
          }),
          503,
        ),
      );

      await expectLater(
        repository(client).listDirectory('/'),
        throwsA(
          isA<FilesFailure>()
              .having(
                (failure) => failure.type,
                'type',
                FilesFailureType.configuration,
              )
              .having(
                (failure) => failure.message,
                'message',
                allOf(
                  contains('Files need admin attention'),
                  isNot(contains('Nextcloud')),
                  isNot(contains('WebDAV')),
                  isNot(contains('home.internal')),
                ),
              ),
        ),
      );
    });
  });
}
