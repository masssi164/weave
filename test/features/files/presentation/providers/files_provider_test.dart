import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeFilesRepository implements FilesRepository {
  _FakeFilesRepository({
    required this.restoreConnectionHandler,
    required this.connectHandler,
    required this.disconnectHandler,
    required this.listDirectoryHandler,
  });

  final Future<FilesConnectionState> Function() restoreConnectionHandler;
  final Future<FilesConnectionState> Function() connectHandler;
  final Future<void> Function() disconnectHandler;
  final Future<DirectoryListing> Function(String path) listDirectoryHandler;

  @override
  Future<FilesConnectionState> connect() => connectHandler();

  @override
  Future<void> disconnect() => disconnectHandler();

  @override
  Future<DirectoryListing> listDirectory(String path) =>
      listDirectoryHandler(path);

  @override
  Future<FilesConnectionState> restoreConnection() =>
      restoreConnectionHandler();
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
          baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
            baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
                  baseUrl: Uri.parse('https://nextcloud.home.internal'),
                  accountLabel: 'alice',
                )
              : FilesConnectionState.disconnected(
                  baseUrl: Uri.parse('https://nextcloud.home.internal'),
                ),
          connectHandler: () async {
            connected = true;
            return FilesConnectionState.connected(
              baseUrl: Uri.parse('https://nextcloud.home.internal'),
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
  });
}
