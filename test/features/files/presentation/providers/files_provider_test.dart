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
        FilesExportRepository {
  _FakeFilesRepository({
    required this.restoreConnectionHandler,
    required this.connectHandler,
    required this.disconnectHandler,
    required this.listDirectoryHandler,
    this.uploadFileHandler,
    this.createFolderHandler,
    this.deleteEntryHandler,
    this.downloadFileHandler,
  });

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
  Future<DirectoryListing> listDirectory(String path) =>
      listDirectoryHandler(path);

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) {
    return uploadFileHandler?.call(directoryPath, request, onProgress) ??
        Future<void>.value();
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
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
    test('restores the saved session and loads the root directory', () async {
      final repository = _FakeFilesRepository(
        restoreConnectionHandler: () async => FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
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
      'marks the session invalid when restoring the root directory fails with invalid credentials',
      () async {
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
          connectHandler: () async => throw UnimplementedError(),
          disconnectHandler: () async {},
          listDirectoryHandler: (path) async {
            throw const FilesFailure.invalidCredentials(
              'The saved Nextcloud credentials are no longer valid.',
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
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
          'The saved Nextcloud credentials are no longer valid.',
        );
        expect(state.directoryListing, isNull);
        expect(
          state.directoryFailure?.type,
          FilesFailureType.invalidCredentials,
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
                  baseUrl: Uri.parse('https://files.home.internal'),
                  accountLabel: 'alice',
                )
              : FilesConnectionState.disconnected(
                  baseUrl: Uri.parse('https://files.home.internal'),
                ),
          connectHandler: () async {
            connected = true;
            return FilesConnectionState.connected(
              baseUrl: Uri.parse('https://files.home.internal'),
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
              'The saved Nextcloud credentials are no longer valid.',
            );
          },
        );
        final container = ProviderContainer(
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
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
      'uploads a picked file, reports completion, and refreshes the folder',
      () async {
        var uploadedDirectoryPath = '';
        var uploadedFileName = '';
        var uploadComplete = false;
        final repository = _FakeFilesRepository(
          restoreConnectionHandler: () async => FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
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
            baseUrl: Uri.parse('https://files.home.internal'),
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
            baseUrl: Uri.parse('https://files.home.internal'),
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
          baseUrl: Uri.parse('https://files.home.internal'),
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
          baseUrl: Uri.parse('https://files.home.internal'),
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
          baseUrl: Uri.parse('https://files.home.internal'),
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
