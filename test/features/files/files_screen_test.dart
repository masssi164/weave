import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import '../../helpers/server_config_test_data.dart';
import '../../helpers/test_app.dart';

class _FakeFilesRepository implements FilesRepository {
  _FakeFilesRepository({
    required this.connectionState,
    this.listings = const <String, DirectoryListing>{},
  });

  final FilesConnectionState connectionState;
  final Map<String, DirectoryListing> listings;
  final List<String> requestedPaths = <String>[];

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    requestedPaths.add(path);
    return listings[path] ?? const DirectoryListing(path: '/', entries: []);
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

void main() {
  group('FilesScreen', () {
    testWidgets('shows a connect action when Nextcloud is disconnected', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
      expect(
        find.text('Connect Nextcloud to browse your files.'),
        findsOneWidget,
      );
    });

    testWidgets('renders directory contents and allows folder navigation', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
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

      await tester.tap(
        find.ancestor(
          of: find.text('Documents').first,
          matching: find.byType(InkWell),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.ancestor(
          of: find.text('Reports').first,
          matching: find.byType(InkWell),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Documents').last);
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
      expect(find.text('Notes.txt'), findsOneWidget);
      expect(find.text('1 folder • 1 file'), findsOneWidget);
      expect(find.text('Up'), findsOneWidget);
      expect(find.text('Root'), findsOneWidget);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
