import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import '../../helpers/server_config_test_data.dart';
import '../../helpers/test_app.dart';

class _FakeFilesRepository
    implements FilesRepository, FilesEntryMutationRepository {
  _FakeFilesRepository({
    required this.connectionState,
    this.listings = const <String, DirectoryListing>{},
    this.listDirectoryHandler,
    this.uploadFileHandler,
    this.createFolderHandler,
    this.deleteEntryHandler,
  });

  final FilesConnectionState connectionState;
  final Map<String, DirectoryListing> listings;
  final Future<DirectoryListing> Function(String path)? listDirectoryHandler;
  final Future<void> Function(
    String directoryPath,
    FileUploadRequest request,
    FileUploadProgressCallback? onProgress,
  )?
  uploadFileHandler;
  final Future<FileEntry> Function(String parentPath, String name)?
  createFolderHandler;
  final Future<void> Function(FileEntry entry)? deleteEntryHandler;
  final List<String> requestedPaths = <String>[];

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    requestedPaths.add(path);
    final handler = listDirectoryHandler;
    if (handler != null) {
      return handler(path);
    }
    return listings[path] ?? const DirectoryListing(path: '/', entries: []);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    await uploadFileHandler?.call(directoryPath, request, onProgress);
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
  Future<FilesConnectionState> restoreConnection() async => connectionState;
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

class _FakeFilesImportPicker implements FilesImportPicker {
  _FakeFilesImportPicker(this.request);

  final FileUploadRequest? request;

  @override
  Future<FileUploadRequest?> pickFile() async => request;
}

void main() {
  group('FilesScreen', () {
    testWidgets('shows a connect action when Nextcloud is disconnected', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Connect Nextcloud'), findsNWidgets(2));
      expect(find.text('Files are not connected'), findsOneWidget);
      expect(
        find.text('Connect Nextcloud to browse your files.'),
        findsOneWidget,
      );
    });

    testWidgets('shows shared guidance when a connected folder is empty', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No files yet'), findsOneWidget);
      expect(
        find.text(
          'Upload a file or create a folder when you are ready to add workspace files.',
        ),
        findsOneWidget,
      );
      expect(find.text('Refresh'), findsAtLeastNWidgets(1));
    });

    testWidgets('renders directory contents and allows folder navigation', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'folder-1',
                name: 'Documents',
                path: '/Documents',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents': DirectoryListing(
            path: '/Documents',
            entries: [
              FileEntry(
                id: 'folder-2',
                name: 'Reports',
                path: '/Documents/Reports',
                isDirectory: true,
              ),
              FileEntry(
                id: 'file-1',
                name: 'Notes.txt',
                path: '/Documents/Notes.txt',
                isDirectory: false,
              ),
            ],
          ),
          '/Documents/Reports': DirectoryListing(
            path: '/Documents/Reports',
            entries: [
              FileEntry(
                id: 'file-2',
                name: 'Q2.pdf',
                path: '/Documents/Reports/Q2.pdf',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Documents'), findsAtLeastNWidgets(1));

      await tester.tap(find.widgetWithText(ListTile, 'Documents'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ActionChip, 'Documents'));
      await tester.pumpAndSettle();

      expect(
        repository.requestedPaths,
        containsAllInOrder([
          '/',
          '/Documents',
          '/Documents/Reports',
          '/Documents',
        ]),
      );
      await tester.scrollUntilVisible(
        find.text('Notes.txt'),
        100,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Notes.txt'), findsOneWidget);
      expect(find.text('1 folder • 1 file'), findsOneWidget);
      expect(find.text('Up'), findsOneWidget);
      expect(find.text('Root'), findsOneWidget);
    });

    testWidgets(
      'keeps the last known directory visible after refresh failure',
      (tester) async {
        var listCalls = 0;
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
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

        await tester.pumpWidget(
          createTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Roadmap.pdf'), findsOneWidget);

        await tester.tap(
          find.widgetWithText(AccessibleButton, 'Refresh').first,
        );
        await tester.pumpAndSettle();

        expect(listCalls, 2);
        expect(find.text('Showing last known folder'), findsOneWidget);
        expect(
          find.text('Files could not be refreshed while offline.'),
          findsOneWidget,
        );
        await tester.scrollUntilVisible(
          find.text('Roadmap.pdf'),
          100,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text('Roadmap.pdf'), findsOneWidget);
        expect(find.text('2.0 KB'), findsOneWidget);
        expect(find.text('Refresh folder'), findsOneWidget);
      },
    );

    testWidgets('marks the current breadcrumb and lets users jump back home', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'folder-1',
                name: 'Documents',
                path: '/Documents',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents': DirectoryListing(
            path: '/Documents',
            entries: [
              FileEntry(
                id: 'folder-2',
                name: 'Reports',
                path: '/Documents/Reports',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents/Reports': DirectoryListing(
            path: '/Documents/Reports',
            entries: [
              FileEntry(
                id: 'file-2',
                name: 'Q2.pdf',
                path: '/Documents/Reports/Q2.pdf',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(ListTile, 'Documents'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();

      final reportsChip = tester.widget<ActionChip>(
        find.widgetWithText(ActionChip, 'Reports'),
      );
      expect(reportsChip.onPressed, isNull);
      expect(find.bySemanticsLabel('Current folder: Reports'), findsOneWidget);
      expect(find.bySemanticsLabel('Open folder: Root'), findsOneWidget);

      await tester.tap(find.widgetWithText(ActionChip, 'Root'));
      await tester.pumpAndSettle();

      expect(repository.requestedPaths.last, '/');
      expect(find.text('Documents'), findsAtLeastNWidgets(1));
    });

    testWidgets('includes file metadata in row semantics', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
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
          ),
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Roadmap.pdf'), findsOneWidget);
      expect(find.text('2.0 KB'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Roadmap.pdf, file. 2.0 KB'),
        findsOneWidget,
      );
    });

    testWidgets('uploads a picked file with completion feedback', (
      tester,
    ) async {
      var uploadComplete = false;
      var uploadedDirectoryPath = '';
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: uploadComplete
                ? const [
                    FileEntry(
                      id: 'file-1',
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
          onProgress?.call(request.sizeInBytes, request.sizeInBytes);
          uploadComplete = true;
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
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
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      expect(uploadedDirectoryPath, '/');
      expect(find.text('Uploaded brief.txt.'), findsOneWidget);
      expect(find.text('brief.txt'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Uploaded brief.txt.'),
        findsAtLeastNWidgets(1),
      );
    });

    testWidgets('shows accessible in-progress upload feedback', (tester) async {
      final uploadCompleter = Completer<void>();
      var uploadComplete = false;
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: uploadComplete
                ? const [
                    FileEntry(
                      id: 'file-1',
                      name: 'brief.txt',
                      path: '/brief.txt',
                      isDirectory: false,
                      sizeInBytes: 4,
                    ),
                  ]
                : const [],
          );
        },
        uploadFileHandler: (_, request, onProgress) async {
          onProgress?.call(2, request.sizeInBytes);
          await uploadCompleter.future;
          uploadComplete = true;
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(
                FileUploadRequest(
                  fileName: 'brief.txt',
                  sizeInBytes: 4,
                  byteStream: Stream<List<int>>.fromIterable(const [
                    [1, 2],
                    [3, 4],
                  ]),
                ),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pump();

      expect(find.text('Uploading brief.txt: 50%'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Upload progress for brief.txt: 50 percent'),
        findsOneWidget,
      );

      uploadCompleter.complete();
      await tester.pumpAndSettle();

      expect(find.text('Uploaded brief.txt.'), findsOneWidget);
      expect(find.text('brief.txt'), findsOneWidget);
    });

    testWidgets(
      'announces indeterminate upload progress without a misleading percent',
      (tester) async {
        final uploadCompleter = Completer<void>();
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
          uploadFileHandler: (_, _, _) async {
            await uploadCompleter.future;
          },
        );

        await tester.pumpWidget(
          createTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              filesImportPickerProvider.overrideWithValue(
                _FakeFilesImportPicker(
                  const FileUploadRequest(
                    fileName: 'brief.txt',
                    sizeInBytes: 0,
                    byteStream: Stream<List<int>>.empty(),
                  ),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('Upload'));
        await tester.pump();

        expect(find.text('Uploading brief.txt…'), findsOneWidget);
        expect(
          find.bySemanticsLabel('Upload progress for brief.txt: 0 percent'),
          findsNothing,
        );
        expect(
          find.bySemanticsLabel('Uploading brief.txt…'),
          findsAtLeastNWidgets(1),
        );

        uploadCompleter.complete();
        await tester.pumpAndSettle();
      },
    );

    testWidgets('cancelled file picker leaves no upload status behind', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'existing.txt',
                path: '/existing.txt',
                isDirectory: false,
                sizeInBytes: 8,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
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
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      expect(find.text('existing.txt'), findsOneWidget);
      expect(find.text('Upload failed.'), findsNothing);
      expect(find.text('Choose a file to upload…'), findsNothing);
      expect(repository.requestedPaths, ['/']);
    });

    testWidgets('shows a friendly accessible upload failure', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'existing.txt',
                path: '/existing.txt',
                isDirectory: false,
                sizeInBytes: 8,
              ),
            ],
          ),
        },
        uploadFileHandler: (_, _, _) async {
          throw const FilesFailure.storage(
            'There is not enough storage available to upload this file.',
          );
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
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
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      const message =
          'There is not enough storage available to upload this file.';
      expect(find.text(message), findsOneWidget);
      expect(find.bySemanticsLabel(message), findsAtLeastNWidgets(1));
      expect(repository.requestedPaths, ['/']);
    });

    testWidgets('creates a folder and refreshes the current directory', (
      tester,
    ) async {
      var createdFolderName = '';
      var createdParentPath = '';
      var creationComplete = false;
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
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
          createdFolderName = name;
          creationComplete = true;
          return FileEntry(
            id: 'folder-1',
            name: name,
            path: '/$name',
            isDirectory: true,
          );
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('New folder'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Plans');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Create'));
      await tester.pumpAndSettle();

      expect(createdParentPath, '/');
      expect(createdFolderName, 'Plans');
      expect(find.text('Created folder Plans.'), findsOneWidget);
      expect(find.text('Plans'), findsAtLeastNWidgets(1));
      expect(
        find.bySemanticsLabel('Created folder Plans.'),
        findsAtLeastNWidgets(1),
      );
    });

    testWidgets('confirms deletion, removes the entry, and shows feedback', (
      tester,
    ) async {
      FileEntry? deletedEntry;
      var deletionComplete = false;
      const fileEntry = FileEntry(
        id: 'file-1',
        name: 'old.txt',
        path: '/old.txt',
        isDirectory: false,
      );
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: deletionComplete ? const [] : const [fileEntry],
          );
        },
        deleteEntryHandler: (entry) async {
          deletedEntry = entry;
          deletionComplete = true;
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Delete old.txt'));
      await tester.pumpAndSettle();
      expect(find.text('Delete old.txt?'), findsOneWidget);
      await tester.tap(find.text('Delete').last);
      await tester.pumpAndSettle();

      expect(deletedEntry, fileEntry);
      expect(find.text('Deleted old.txt.'), findsOneWidget);
      expect(find.text('old.txt'), findsNothing);
      expect(
        find.bySemanticsLabel('Deleted old.txt.'),
        findsAtLeastNWidgets(1),
      );
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
