import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/data/services/file_picker_files_export_saver.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/services/files_export_saver.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeFilesRepository
    implements
        FilesRepository,
        FilesEntryMutationRepository,
        FilesExportRepository,
        FilesSpaceRepository,
        FilesContentReplacementRepository {
  _FakeFilesRepository({
    required this.restoreConnectionHandler,
    required this.connectHandler,
    required this.disconnectHandler,
    required this.listDirectoryHandler,
    this.uploadFileHandler,
    this.createFolderHandler,
    this.deleteEntryHandler,
    this.downloadFileHandler,
    this.replaceContentHandler,
    this.spaceDirectoryHandler,
  });

  final Future<FileEntry> Function(FileEntry, FileUploadRequest)?
  replaceContentHandler;
  final Future<DirectoryListing> Function(String, String?)?
  spaceDirectoryHandler;
  @override
  Future<FileEntry> replaceContent(
    FileEntry entry,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) => replaceContentHandler!(entry, request);
  final Future<FilesConnectionState> Function() restoreConnectionHandler;
  final Future<FilesConnectionState> Function() connectHandler;
  final Future<void> Function() disconnectHandler;
  final Future<DirectoryListing> Function(String path) listDirectoryHandler;
  final Future<void> Function(
    String directoryPath,
    FileUploadRequest request,
    FileUploadProgressCallback? onProgress,
  )?
  uploadFileHandler;
  final Future<FileEntry> Function(String parentPath, String name)?
  createFolderHandler;
  final Future<void> Function(FileEntry entry)? deleteEntryHandler;
  final Future<FileDownload> Function(FileEntry entry)? downloadFileHandler;

  @override
  Future<FilesConnectionState> connect() => connectHandler();

  @override
  Future<void> disconnect() => disconnectHandler();

  @override
  Future<DirectoryListing> listDirectory(String path, {String? spaceRef}) =>
      spaceDirectoryHandler?.call(path, spaceRef) ?? listDirectoryHandler(path);

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
    String? spaceRef,
  }) {
    return uploadFileHandler?.call(directoryPath, request, onProgress) ??
        Future<void>.value();
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
    String? spaceRef,
  }) {
    final handler = createFolderHandler;
    if (handler != null) {
      return handler(parentPath, name);
    }
    return Future<FileEntry>.value(
      FileEntry(
        id: '$parentPath/$name',
        name: name,
        path: parentPath == '/' ? '/$name' : '$parentPath/$name',
        isDirectory: true,
      ),
    );
  }

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    await deleteEntryHandler?.call(entry);
  }

  @override
  Future<FileDownload> downloadFile(FileEntry entry) {
    final handler = downloadFileHandler;
    if (handler != null) {
      return handler(entry);
    }
    return Future<FileDownload>.value(
      FileDownload(fileName: entry.name, bytes: Uint8List(0)),
    );
  }

  @override
  Future<FilesConnectionState> restoreConnection() =>
      restoreConnectionHandler();
}

class _FakeFilesImportPicker implements FilesImportPicker {
  _FakeFilesImportPicker(this.request);

  final FileUploadRequest? request;

  @override
  Future<FileUploadRequest?> pickFile() async => request;
}

class _FakeFilesExportSaver implements FilesExportSaver {
  _FakeFilesExportSaver(this.destination);

  final String? destination;
  FileDownload? savedDownload;

  @override
  Future<FilesExportResult?> save(FileDownload download) async {
    savedDownload = download;
    final target = destination;
    if (target == null) {
      return null;
    }
    return FilesExportResult(fileName: download.fileName, destination: target);
  }
}

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  final ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {}
}

void main() {
  group('FilesController', () {
    for (final denied in [false, true]) {
      test(
        'native picker replacement ${denied ? 'denial clears content' : 'succeeds and refreshes'}',
        () async {
          const entry = FileEntry(
            id: 'file-id',
            name: 'notes.txt',
            path: '/notes.txt',
            isDirectory: false,
            allowedActions: {'download', 'updateContent'},
          );
          var replacements = 0;
          final repository = _FakeFilesRepository(
            restoreConnectionHandler: () async =>
                FilesConnectionState.connected(
                  baseUrl: Uri.parse('https://api.home.internal/api'),
                  accountLabel: 'alice',
                ),
            connectHandler: () async => throw UnimplementedError(),
            disconnectHandler: () async {},
            listDirectoryHandler: (path) async =>
                DirectoryListing(path: path, entries: [entry]),
            replaceContentHandler: (selected, request) async {
              replacements++;
              expect(selected.id, entry.id);
              expect(request.fileName, 'selected.txt');
              expect(
                await request.byteStream.expand((chunk) => chunk).toList(),
                [4, 5, 6],
              );
              if (denied) {
                throw const FilesFailure.permissionDenied('Access revoked.');
              }
              return entry;
            },
          );
          final container = ProviderContainer(
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              filesImportPickerProvider.overrideWithValue(
                _FakeFilesImportPicker(
                  FileUploadRequest(
                    fileName: 'selected.txt',
                    sizeInBytes: 3,
                    byteStream: Stream.value([4, 5, 6]),
                  ),
                ),
              ),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          );
          addTearDown(container.dispose);
          await container.read(filesProvider.future);
          await container.read(filesProvider.notifier).pickAndReplace(entry);
          final state = container.read(filesProvider).requireValue;
          expect(replacements, 1);
          expect(
            state.entryActionStatus.phase,
            denied
                ? FilesEntryActionPhase.failed
                : FilesEntryActionPhase.replacedContent,
          );
          expect(state.directoryListing == null, denied);
          expect(state.isBusy, false);
        },
      );
    }
    test(
      'authorized Space selects root; unknown Space clears private listing',
      () async {
        final scopes = <String?>[];
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async =>
              DirectoryListing(path: path, entries: []),
          spaceDirectoryHandler: (path, scope) async {
            scopes.add(scope);
            return DirectoryListing(path: path, entries: []);
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => const MemberSpaceAccessSnapshot(
                visibleSpaceRefs: {'workspace-default'},
                defaultSpaceReadable: true,
              ),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);
        await container.read(weaveApiMemberSpacesProvider.future);
        await container.read(filesProvider.future);
        await container
            .read(filesProvider.notifier)
            .selectRootSpace('workspace-default');
        expect(scopes, [null, 'workspace-default']);
        expect(
          container.read(filesProvider).requireValue.rootSpaceRef,
          'workspace-default',
        );
        await container.read(filesProvider.notifier).selectRootSpace('unknown');
        expect(scopes, [null, 'workspace-default']);
        expect(
          container.read(filesProvider).requireValue.directoryListing,
          isNull,
        );
        expect(
          container.read(filesProvider).requireValue.directoryFailure?.type,
          FilesFailureType.permissionDenied,
        );
      },
    );
    test('revoked Space cannot republish a late replacement result', () async {
      const entry = FileEntry(
        id: 'shared',
        name: 'shared.txt',
        path: '/shared.txt',
        isDirectory: false,
        allowedActions: {'download', 'updateContent'},
      );
      final pending = Completer<FileEntry>();
      final started = Completer<void>();
      var visible = <String>{'workspace-default'};
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        connectHandler: () async => throw UnimplementedError(),
        disconnectHandler: () async {},
        listDirectoryHandler: (path) async =>
            DirectoryListing(path: path, entries: [entry]),
        replaceContentHandler: (entry, request) {
          started.complete();
          return pending.future;
        },
      );
      final container = ProviderContainer(
        overrides: [
          filesRepositoryProvider.overrideWithValue(repository),
          filesImportPickerProvider.overrideWithValue(
            _FakeFilesImportPicker(
              FileUploadRequest(
                fileName: 'shared.txt',
                sizeInBytes: 1,
                byteStream: Stream.value([1]),
              ),
            ),
          ),
          weaveApiMemberSpacesProvider.overrideWith(
            (ref) async => MemberSpaceAccessSnapshot(
              visibleSpaceRefs: visible,
              defaultSpaceReadable: visible.isNotEmpty,
            ),
          ),
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
        ],
      );
      addTearDown(container.dispose);
      await container.read(weaveApiMemberSpacesProvider.future);
      await container.read(filesProvider.future);
      await container
          .read(filesProvider.notifier)
          .selectRootSpace('workspace-default');
      final replacing = container
          .read(filesProvider.notifier)
          .pickAndReplace(entry);
      await started.future;
      visible = {};
      container.invalidate(weaveApiMemberSpacesProvider);
      await container.read(weaveApiMemberSpacesProvider.future);
      pending.complete(entry);
      await replacing;
      final state = container.read(filesProvider).requireValue;
      expect(state.directoryListing, isNull);
      expect(state.directoryFailure?.type, FilesFailureType.permissionDenied);
      expect(
        state.entryActionStatus.phase,
        isNot(FilesEntryActionPhase.replacedContent),
      );
    });
    test('restores the saved session and loads the root directory', () async {
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        connectHandler: () async => throw UnimplementedError(),
        disconnectHandler: () async {},
        listDirectoryHandler: (path) async {
          expect(path, '/');
          return const DirectoryListing(
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
        },
      );
      final container = ProviderContainer(
        overrides: [
          filesRepositoryProvider.overrideWithValue(repository),
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
        ],
      );
      addTearDown(container.dispose);

      final state = await container.read(filesProvider.future);

      expect(state.connectionState.status, FilesConnectionStatus.connected);
      expect(state.directoryListing?.entries.single.name, 'Documents');
      expect(state.directoryFailure, isNull);
    });

    test(
      'manual refresh failure keeps the last known directory visible',
      () async {
        var listCalls = 0;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            listCalls += 1;
            if (listCalls > 1) {
              throw const FilesFailure.protocol(
                'Files could not be refreshed while offline.',
              );
            }
            return const DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'file-1',
                  name: 'Roadmap.pdf',
                  path: '/Roadmap.pdf',
                  isDirectory: false,
                  sizeInBytes: 2048,
                ),
              ],
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(filesProvider.future);
        await container.read(filesProvider.notifier).refresh();
        final state = container.read(filesProvider).requireValue;

        expect(listCalls, 2);
        expect(state.directoryListing?.entries.single.name, 'Roadmap.pdf');
        expect(state.directoryFailure?.type, FilesFailureType.protocol);
        expect(
          state.directoryFailure?.message,
          'Files could not be refreshed while offline.',
        );
        expect(state.isBusy, isFalse);
      },
    );

    test(
      'marks the session invalid when restoring the root directory fails with invalid credentials',
      () async {
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            throw const FilesFailure.invalidCredentials(
              'The Weave sign-in needs to be renewed.',
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        final state = await container.read(filesProvider.future);

        expect(state.connectionState.status, FilesConnectionStatus.invalid);
        expect(
          state.connectionState.message,
          'The Weave sign-in needs to be renewed.',
        );
        expect(state.directoryListing, isNull);
        expect(
          state.directoryFailure?.type,
          FilesFailureType.invalidCredentials,
        );
      },
    );

    test(
      'a Files permission denial keeps the Weave session connected',
      () async {
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (_) async =>
              throw const FilesFailure.permissionDenied(
                'This member cannot inspect that folder.',
              ),
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        final state = await container.read(filesProvider.future);
        expect(state.connectionState.status, FilesConnectionStatus.connected);
        expect(state.directoryListing, isNull);
        expect(state.directoryFailure?.type, FilesFailureType.permissionDenied);
        expect(
          state.directoryFailure?.message,
          'This member cannot inspect that folder.',
        );
      },
    );

    test(
      'connect clears stale directory data when the new session is invalid',
      () async {
        var connected = false;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => connected
              ? FilesConnectionState.connected(
                  baseUrl: Uri.parse('https://api.home.internal/api'),
                  accountLabel: 'alice',
                )
              : FilesConnectionState.disconnected(
                  baseUrl: Uri.parse('https://api.home.internal/api'),
                ),
          connectHandler: () async {
            connected = true;
            return FilesConnectionState.connected(
              baseUrl: Uri.parse('https://api.home.internal/api'),
              accountLabel: 'alice',
            );
          },
          disconnectHandler: () async {
            connected = false;
          },
          listDirectoryHandler: (path) async {
            if (!connected) {
              return const DirectoryListing(path: '/', entries: []);
            }

            throw const FilesFailure.invalidCredentials(
              'The Weave sign-in needs to be renewed.',
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        final initialState = await container.read(filesProvider.future);
        expect(
          initialState.connectionState.status,
          FilesConnectionStatus.disconnected,
        );

        await container.read(filesProvider.notifier).connect();
        final state = container.read(filesProvider).requireValue;

        expect(state.connectionState.status, FilesConnectionStatus.invalid);
        expect(state.directoryListing, isNull);
        expect(
          state.directoryFailure?.type,
          FilesFailureType.invalidCredentials,
        );
        expect(state.isBusy, isFalse);
      },
    );

    test(
      'unavailable Files readiness never starts a directory request',
      () async {
        var listCalls = 0;
        final unavailable = FilesConnectionState.unavailable(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          message: 'Files access is blocked by workspace policy.',
        );
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => unavailable,
          connectHandler: () async => unavailable,
          disconnectHandler: () async {},
          listDirectoryHandler: (_) async {
            listCalls++;
            return const DirectoryListing(path: '/', entries: []);
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        expect(
          (await container.read(filesProvider.future)).connectionState.status,
          FilesConnectionStatus.unavailable,
        );
        await container.read(filesProvider.notifier).connect();

        expect(listCalls, 0);
        expect(
          container.read(filesProvider).requireValue.connectionState.status,
          FilesConnectionStatus.unavailable,
        );
      },
    );

    test(
      'uploads a picked file, reports completion, and refreshes the folder',
      () async {
        var uploadedDirectoryPath = '';
        var uploadedFileName = '';
        var uploadComplete = false;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            return DirectoryListing(
              path: path,
              entries: uploadComplete
                  ? const [
                      FileEntry(
                        id: 'upload-1',
                        name: 'brief.txt',
                        path: '/brief.txt',
                        isDirectory: false,
                        sizeInBytes: 4,
                      ),
                    ]
                  : const [],
            );
          },
          uploadFileHandler: (directoryPath, request, onProgress) async {
            uploadedDirectoryPath = directoryPath;
            uploadedFileName = request.fileName;
            onProgress?.call(2, request.sizeInBytes);
            onProgress?.call(request.sizeInBytes, request.sizeInBytes);
            uploadComplete = true;
          },
        );
        final picker = _FakeFilesImportPicker(
          FileUploadRequest(
            fileName: 'brief.txt',
            sizeInBytes: 4,
            byteStream: Stream<List<int>>.fromIterable(const [
              [1, 2],
              [3, 4],
            ]),
          ),
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(picker),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(filesProvider.future);
        await container.read(filesProvider.notifier).pickAndUpload();
        final state = container.read(filesProvider).requireValue;

        expect(uploadedDirectoryPath, '/');
        expect(uploadedFileName, 'brief.txt');
        expect(state.uploadStatus.phase, FilesUploadPhase.completed);
        expect(state.uploadStatus.fileName, 'brief.txt');
        expect(state.uploadStatus.progressFraction, 1);
        expect(state.directoryListing?.entries.single.name, 'brief.txt');
        expect(state.isBusy, isFalse);
      },
    );

    test(
      'cancelled upload picker leaves the current directory unchanged',
      () async {
        var uploadAttempts = 0;
        var listCalls = 0;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            listCalls += 1;
            return const DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'existing-1',
                  name: 'existing.txt',
                  path: '/existing.txt',
                  isDirectory: false,
                  sizeInBytes: 8,
                ),
              ],
            );
          },
          uploadFileHandler: (_, _, _) async {
            uploadAttempts += 1;
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(null),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        final initialState = await container.read(filesProvider.future);
        await container.read(filesProvider.notifier).pickAndUpload();
        final state = container.read(filesProvider).requireValue;

        expect(uploadAttempts, 0);
        expect(listCalls, 1);
        expect(state.uploadStatus.phase, FilesUploadPhase.idle);
        expect(state.directoryListing, same(initialState.directoryListing));
        expect(state.directoryListing?.entries.single.name, 'existing.txt');
        expect(state.directoryFailure, isNull);
        expect(state.isBusy, isFalse);
      },
    );

    test(
      'failed upload keeps the directory and shows a friendly failure',
      () async {
        var listCalls = 0;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            listCalls += 1;
            return const DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'existing-1',
                  name: 'existing.txt',
                  path: '/existing.txt',
                  isDirectory: false,
                  sizeInBytes: 8,
                ),
              ],
            );
          },
          uploadFileHandler: (_, _, _) async {
            throw const FilesFailure.storage(
              'There is not enough storage available to upload this file.',
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(
                FileUploadRequest(
                  fileName: 'brief.txt',
                  sizeInBytes: 4,
                  byteStream: Stream<List<int>>.fromIterable(const [
                    [1, 2, 3, 4],
                  ]),
                ),
              ),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(filesProvider.future);
        await container.read(filesProvider.notifier).pickAndUpload();
        final state = container.read(filesProvider).requireValue;

        expect(listCalls, 1);
        expect(state.uploadStatus.phase, FilesUploadPhase.failed);
        expect(state.uploadStatus.fileName, 'brief.txt');
        expect(
          state.uploadStatus.failure?.message,
          'There is not enough storage available to upload this file.',
        );
        expect(state.directoryListing?.entries.single.name, 'existing.txt');
        expect(state.directoryFailure, isNull);
        expect(state.isBusy, isFalse);
      },
    );

    test('creates a folder and refreshes the current directory', () async {
      var createdParentPath = '';
      var createdName = '';
      var creationComplete = false;
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        connectHandler: () async => throw UnimplementedError(),
        disconnectHandler: () async {},
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: path,
            entries: creationComplete
                ? const [
                    FileEntry(
                      id: 'folder-1',
                      name: 'Plans',
                      path: '/Plans',
                      isDirectory: true,
                    ),
                  ]
                : const [],
          );
        },
        createFolderHandler: (parentPath, name) async {
          createdParentPath = parentPath;
          createdName = name;
          creationComplete = true;
          return FileEntry(
            id: 'folder-1',
            name: name,
            path: '/$name',
            isDirectory: true,
          );
        },
      );
      final container = ProviderContainer(
        overrides: [
          filesRepositoryProvider.overrideWithValue(repository),
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(filesProvider.future);
      await container.read(filesProvider.notifier).createFolder(' Plans ');
      final state = container.read(filesProvider).requireValue;

      expect(createdParentPath, '/');
      expect(createdName, 'Plans');
      expect(
        state.entryActionStatus.phase,
        FilesEntryActionPhase.createdFolder,
      );
      expect(state.entryActionStatus.entryName, 'Plans');
      expect(state.directoryListing?.entries.single.name, 'Plans');
      expect(state.isBusy, isFalse);
    });

    test('deletes an entry and refreshes the current directory', () async {
      FileEntry? deletedEntry;
      var deletionComplete = false;
      const fileEntry = FileEntry(
        id: 'file-1',
        name: 'old.txt',
        path: '/old.txt',
        isDirectory: false,
      );
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        connectHandler: () async => throw UnimplementedError(),
        disconnectHandler: () async {},
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: path,
            entries: deletionComplete ? const [] : const [fileEntry],
          );
        },
        deleteEntryHandler: (entry) async {
          deletedEntry = entry;
          deletionComplete = true;
        },
      );
      final container = ProviderContainer(
        overrides: [
          filesRepositoryProvider.overrideWithValue(repository),
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(filesProvider.future);
      await container.read(filesProvider.notifier).deleteEntry(fileEntry);
      final state = container.read(filesProvider).requireValue;

      expect(deletedEntry, fileEntry);
      expect(state.entryActionStatus.phase, FilesEntryActionPhase.deletedEntry);
      expect(state.entryActionStatus.entryName, 'old.txt');
      expect(state.directoryListing?.entries, isEmpty);
      expect(state.isBusy, isFalse);
    });

    test('exports a file through the backend and native saver', () async {
      const fileEntry = FileEntry(
        id: 'file-1',
        name: 'brief.txt',
        path: '/brief.txt',
        isDirectory: false,
      );
      FileEntry? downloadedEntry;
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        connectHandler: () async => throw UnimplementedError(),
        disconnectHandler: () async {},
        listDirectoryHandler: (path) async =>
            const DirectoryListing(path: '/', entries: [fileEntry]),
        downloadFileHandler: (entry) async {
          downloadedEntry = entry;
          return FileDownload(
            fileName: entry.name,
            bytes: Uint8List.fromList(<int>[1, 2, 3]),
          );
        },
      );
      final saver = _FakeFilesExportSaver('/Users/alice/brief.txt');
      final container = ProviderContainer(
        overrides: [
          filesRepositoryProvider.overrideWithValue(repository),
          filesExportSaverProvider.overrideWithValue(saver),
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                _FakeServerConfigurationRepository(buildTestConfiguration()),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(filesProvider.future);
      await container.read(filesProvider.notifier).exportEntry(fileEntry);
      final state = container.read(filesProvider).requireValue;

      expect(downloadedEntry, fileEntry);
      expect(saver.savedDownload?.bytes, <int>[1, 2, 3]);
      expect(
        state.entryActionStatus.phase,
        FilesEntryActionPhase.exportedEntry,
      );
      expect(state.entryActionStatus.entryName, 'brief.txt');
      expect(state.entryActionStatus.destination, '/Users/alice/brief.txt');
      expect(state.isBusy, isFalse);
    });
  });
}
