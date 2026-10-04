import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
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
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

String _readiness({
  bool enabled = true,
  String policyState = 'allowed',
  String readiness = 'ready',
  List<String> grants = const ['files.read', 'files.upload'],
  String? memberImpact,
}) => jsonEncode({
  'enabled': enabled,
  'policyState': policyState,
  'readiness': readiness,
  'grantedCapabilities': grants,
  if (memberImpact != null) 'memberImpact': memberImpact,
});

Map<String, Object?> _item({
  String id = 'file:123e4567-e89b-12d3-a456-426614174000',
  String parentId = BackendFilesRepository.rootFileId,
  String name = 'notes.txt',
  String path = '/notes.txt',
  String kind = 'file',
  List<String> actions = const ['inspect', 'download'],
}) => {
  'fileId': id,
  'parentFileId': parentId,
  'name': name,
  'displayPath': path,
  'kind': kind,
  'size': kind == 'file' ? 3 : 0,
  'mediaType': kind == 'file' ? 'text/plain' : null,
  'modifiedAt': '2026-10-04T10:00:00Z',
  'revision': 'sha256:revision',
  'allowedActions': actions,
};

String _listing({
  String parentId = BackendFilesRepository.rootFileId,
  List<String> actions = const ['listChildren', 'createFolder', 'upload'],
  List<Map<String, Object?>> items = const [],
}) => jsonEncode({
  'parentFileId': parentId,
  'allowedActions': actions,
  'items': items,
});

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
  group('filesRepositoryProvider', () {
    test('uses the generated User API repository for release members', () {
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

  group('BackendFilesRepository generated operations', () {
    late _FakeServerConfigurationRepository configurationRepository;
    late _FakeAuthSessionRepository authSessionRepository;

    BackendFilesRepository repository(http.Client client) =>
        BackendFilesRepository(
          httpClient: client,
          serverConfigurationRepository: configurationRepository,
          authSessionRepository: authSessionRepository,
        );

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
      'readiness grants gate browser; denied and unknown stay closed',
      () async {
        var calls = 0;
        final ready = repository(
          MockClient((request) async {
            calls++;
            expect(request.url.path, '/api/files/readiness');
            expect(request.headers['authorization'], 'Bearer files-token');
            return http.Response(_readiness(), 200);
          }),
        );
        expect(
          (await ready.restoreConnection()).status,
          FilesConnectionStatus.connected,
        );
        expect(calls, 1);

        final denied = repository(
          MockClient(
            (_) async => http.Response(
              _readiness(
                policyState: 'denied',
                memberImpact: 'Ask your admin.',
              ),
              200,
            ),
          ),
        );
        final deniedState = await denied.connect();
        expect(deniedState.status, FilesConnectionStatus.unavailable);
        expect(deniedState.message, 'Ask your admin.');

        final missingGrant = repository(
          MockClient(
            (_) async =>
                http.Response(_readiness(grants: const ['files.upload']), 200),
          ),
        );
        expect(
          (await missingGrant.connect()).status,
          FilesConnectionStatus.unavailable,
        );

        final unknown = repository(
          MockClient(
            (_) async => http.Response(_readiness(readiness: 'unknown'), 200),
          ),
        );
        expect(
          (await unknown.connect()).status,
          FilesConnectionStatus.unavailable,
        );
      },
    );

    test('readiness retries once after refreshed Weave token', () async {
      authSessionRepository.refreshedState = AuthState.authenticated(
        buildTestAuthSession(accessToken: 'fresh-token'),
      );
      final seen = <String?>[];
      final files = repository(
        MockClient((request) async {
          seen.add(request.headers['authorization']);
          return request.headers['authorization'] == 'Bearer files-token'
              ? http.Response('{}', 401)
              : http.Response(_readiness(), 200);
        }),
      );
      expect((await files.connect()).status, FilesConnectionStatus.connected);
      expect(seen, ['Bearer files-token', 'Bearer fresh-token']);
      expect(authSessionRepository.refreshCalls, 1);
    });

    test('Files list retries once with the refreshed User token', () async {
      authSessionRepository.refreshedState = AuthState.authenticated(
        buildTestAuthSession(accessToken: 'fresh-token'),
      );
      final seen = <String?>[];
      final files = repository(
        MockClient((request) async {
          expect(request.url.path, '/api/files/items');
          seen.add(request.headers['authorization']);
          return request.headers['authorization'] == 'Bearer files-token'
              ? http.Response('{}', 401)
              : http.Response(_listing(), 200);
        }),
      );
      expect((await files.listDirectory('/')).entries, isEmpty);
      expect(seen, ['Bearer files-token', 'Bearer fresh-token']);
      expect(authSessionRepository.refreshCalls, 1);
    });

    test(
      'lists typed items with opaque IDs, paths and server actions',
      () async {
        final files = repository(
          MockClient((request) async {
            expect(request.url.path, '/api/files/items');
            expect(
              request.url.queryParameters['parentId'],
              BackendFilesRepository.rootFileId,
            );
            expect(request.headers['authorization'], 'Bearer files-token');
            return http.Response(_listing(items: [_item()]), 200);
          }),
        );
        final listing = await files.listDirectory('/');
        expect(listing.parentFileId, BackendFilesRepository.rootFileId);
        expect(listing.allows('createFolder'), isTrue);
        expect(listing.entries.single.id, startsWith('file:'));
        expect(listing.entries.single.path, '/notes.txt');
        expect(listing.entries.single.revision, 'sha256:revision');
        expect(listing.entries.single.allows('download'), isTrue);
      },
    );

    test(
      'nested navigation resolves opaque folder IDs, never DAV paths',
      () async {
        const folderId = 'file:123e4567-e89b-12d3-a456-426614174001';
        final paths = <String>[];
        final files = repository(
          MockClient((request) async {
            paths.add(request.url.toString());
            final parent = request.url.queryParameters['parentId'];
            if (parent == BackendFilesRepository.rootFileId) {
              return http.Response(
                _listing(
                  items: [
                    _item(
                      id: folderId,
                      name: 'Team',
                      path: '/Team',
                      kind: 'folder',
                      actions: const ['inspect', 'listChildren'],
                    ),
                  ],
                ),
                200,
              );
            }
            expect(parent, folderId);
            return http.Response(
              _listing(
                parentId: folderId,
                items: [_item(parentId: folderId, path: '/Team/notes.txt')],
              ),
              200,
            );
          }),
        );
        final listing = await files.listDirectory('/Team');
        expect(listing.path, '/Team');
        expect(listing.parentFileId, folderId);
        expect(listing.entries.single.path, '/Team/notes.txt');
        expect(paths, everyElement(contains('/api/files/items')));
        expect(paths, isNot(contains(contains('/dav/files'))));
      },
    );

    test('malformed parent and item mapping fail closed', () async {
      final wrongParent = repository(
        MockClient(
          (_) async => http.Response(_listing(parentId: 'file:wrong'), 200),
        ),
      );
      await expectLater(
        wrongParent.listDirectory('/'),
        throwsA(isA<FilesFailure>()),
      );
      final wrongItem = repository(
        MockClient(
          (_) async => http.Response(
            _listing(items: [_item(parentId: 'file:wrong')]),
            200,
          ),
        ),
      );
      await expectLater(
        wrongItem.listDirectory('/'),
        throwsA(isA<FilesFailure>()),
      );
      final pathLikeId = repository(
        MockClient(
          (_) async => http.Response(
            _listing(items: [_item(id: 'file:../private')]),
            200,
          ),
        ),
      );
      await expectLater(
        pathLikeId.listDirectory('/'),
        throwsA(isA<FilesFailure>()),
      );
    });

    test(
      'download validates exact bytes, length, digest and strong ETag',
      () async {
        final bytes = utf8.encode('abc');
        final digest = base64Encode(sha256.convert(bytes).bytes);
        final entry = FileEntry(
          id: _item()['fileId']! as String,
          name: 'notes.txt',
          path: '/notes.txt',
          isDirectory: false,
          allowedActions: const {'download'},
        );
        final files = repository(
          MockClient((request) async {
            expect(request.url.path, '/api/files/items/${entry.id}/content');
            expect(request.headers['authorization'], 'Bearer files-token');
            return http.Response.bytes(
              bytes,
              200,
              headers: {
                'content-type': 'text/plain',
                'content-length': '${bytes.length}',
                'content-digest': 'sha-256=:$digest:',
                'etag': '"sha256-${sha256.convert(bytes)}"',
              },
            );
          }),
        );
        final content = await files.downloadFile(entry);
        expect(content.bytes, bytes);
        expect(content.fileName, 'notes.txt');

        final tampered = repository(
          MockClient(
            (_) async => http.Response.bytes(
              bytes,
              200,
              headers: {
                'content-type': 'text/plain',
                'content-length': '${bytes.length}',
                'content-digest': 'sha-256=:wrong:',
                'etag': '"sha256-${sha256.convert(bytes)}"',
              },
            ),
          ),
        );
        await expectLater(
          tampered.downloadFile(entry),
          throwsA(
            isA<FilesFailure>().having(
              (failure) => failure.type,
              'type',
              FilesFailureType.protocol,
            ),
          ),
        );

        final wrongEtag = repository(
          MockClient(
            (_) async => http.Response.bytes(
              bytes,
              200,
              headers: {
                'content-type': 'text/plain',
                'content-length': '${bytes.length}',
                'content-digest': 'sha-256=:$digest:',
                'etag': '"sha256-${sha256.convert(utf8.encode('other'))}"',
              },
            ),
          ),
        );
        await expectLater(
          wrongEtag.downloadFile(entry),
          throwsA(
            isA<FilesFailure>().having(
              (failure) => failure.type,
              'type',
              FilesFailureType.protocol,
            ),
          ),
        );
      },
    );

    test(
      'folder creation requires generated JSON, absent-name and durable key',
      () async {
        final seen = <http.Request>[];
        final files = repository(
          MockClient((request) async {
            seen.add(request);
            if (request.method == 'GET') return http.Response(_listing(), 200);
            expect(request.method, 'POST');
            expect(request.url.path, '/api/files/items/folders');
            expect(request.headers['if-none-match'], '*');
            expect(
              request.headers['idempotency-key'],
              hasLength(greaterThanOrEqualTo(16)),
            );
            expect(jsonDecode(request.body), {
              'parentFileId': BackendFilesRepository.rootFileId,
              'name': 'Reports',
            });
            return http.Response(
              jsonEncode(
                _item(
                  name: 'Reports',
                  path: '/Reports',
                  kind: 'folder',
                  actions: const ['inspect', 'listChildren'],
                ),
              ),
              200,
            );
          }),
        );
        final folder = await files.createFolder(
          parentPath: '/',
          name: 'Reports',
        );
        expect(folder.isDirectory, isTrue);
        expect(folder.id, startsWith('file:'));
        expect(seen.length, 2);
      },
    );

    test(
      'upload sends exact bounded binary body and idempotency headers',
      () async {
        final progress = <int>[];
        final files = repository(
          MockClient((request) async {
            if (request.method == 'GET') return http.Response(_listing(), 200);
            expect(request.url.path, '/api/files/items/uploads');
            expect(
              request.url.queryParameters['parentId'],
              BackendFilesRepository.rootFileId,
            );
            expect(request.url.queryParameters['name'], 'notes.txt');
            expect(request.headers['content-type'], 'application/octet-stream');
            expect(request.headers['if-none-match'], '*');
            expect(
              request.headers['idempotency-key'],
              hasLength(greaterThanOrEqualTo(16)),
            );
            expect(request.bodyBytes, [1, 2, 3]);
            return http.Response(jsonEncode(_item()), 200);
          }),
        );
        await files.uploadFile(
          '/',
          FileUploadRequest(
            fileName: 'notes.txt',
            sizeInBytes: 3,
            byteStream: Stream.fromIterable(const [
              [1],
              [2, 3],
            ]),
          ),
          onProgress: (done, _) => progress.add(done),
        );
        expect(progress, [3]);
      },
    );

    test(
      'source stream failure stops upload without sending a partial body',
      () async {
        var posts = 0;
        final files = repository(
          MockClient((request) async {
            if (request.method == 'GET') return http.Response(_listing(), 200);
            posts++;
            return http.Response(jsonEncode(_item()), 200);
          }),
        );
        await expectLater(
          files.uploadFile(
            '/',
            FileUploadRequest(
              fileName: 'notes.txt',
              sizeInBytes: 3,
              byteStream: Stream<List<int>>.error(StateError('read failed')),
            ),
          ),
          throwsA(isA<FilesFailure>()),
        );
        expect(posts, 0);
      },
    );

    test(
      'generated binary transport propagates an upload source error',
      () async {
        final client = MockClient(
          (_) async => http.Response(jsonEncode(_item()), 200),
        );
        final api = user_api.FilesUserApi(
          weaveUserApiClient(
            apiBaseUrl: Uri.parse('https://api.home.internal/api'),
            accessToken: 'files-token',
            httpClient: client,
          ),
        );
        await expectLater(
          api.uploadFilesItemContent(
            BackendFilesRepository.rootFileId,
            'notes.txt',
            '*',
            'abcdef0123456789abcdef0123456789',
            http.MultipartFile(
              'body',
              Stream<List<int>>.error(StateError('source failed')),
              3,
            ),
          ),
          throwsA(anything),
        );
      },
    );

    test(
      'unsupported mutations and denied actions never use a fallback route',
      () async {
        var posts = 0;
        final files = repository(
          MockClient((request) async {
            if (request.method == 'GET') {
              return http.Response(
                _listing(actions: const ['listChildren']),
                200,
              );
            }
            posts++;
            return http.Response('{}', 200);
          }),
        );
        const entry = FileEntry(
          id: 'file:123e4567-e89b-12d3-a456-426614174000',
          name: 'notes.txt',
          path: '/notes.txt',
          isDirectory: false,
        );
        await expectLater(
          files.createFolder(parentPath: '/', name: 'No'),
          throwsA(isA<FilesFailure>()),
        );
        await expectLater(
          files.deleteEntry(entry),
          throwsA(isA<FilesFailure>()),
        );
        await expectLater(
          files.copyEntry(entry, destinationPath: '/copy.txt'),
          throwsA(isA<FilesFailure>()),
        );
        await expectLater(
          files.moveEntry(entry, destinationPath: '/move.txt'),
          throwsA(isA<FilesFailure>()),
        );
        expect(posts, 0);
      },
    );

    test('auth rejection and provider failure remain support-safe', () async {
      final forbidden = repository(
        MockClient(
          (_) async => http.Response(
            jsonEncode({
              'memberImpact': 'Ask an administrator.',
              'message': 'raw-secret',
            }),
            403,
          ),
        ),
      );
      await expectLater(
        forbidden.listDirectory('/'),
        throwsA(
          isA<FilesFailure>()
              .having(
                (failure) => failure.type,
                'type',
                FilesFailureType.permissionDenied,
              )
              .having(
                (failure) => failure.message,
                'message',
                'Ask an administrator.',
              ),
        ),
      );
      final unavailable = repository(
        MockClient((_) async => http.Response('{}', 503)),
      );
      await expectLater(
        unavailable.listDirectory('/'),
        throwsA(
          isA<FilesFailure>().having(
            (failure) => failure.type,
            'type',
            FilesFailureType.configuration,
          ),
        ),
      );
    });
  });
}
