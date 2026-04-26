import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';

void main() {
  group('filesRepositoryProvider backend-facade seam', () {
    test('keeps backend facade disabled as the compatibility default', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      expect(container.read(useBackendFilesFacadeProvider), isFalse);
    });

    test(
      'can be switched to the backend files facade without Nextcloud deps',
      () {
        final container = ProviderContainer(
          overrides: [useBackendFilesFacadeProvider.overrideWithValue(true)],
        );
        addTearDown(container.dispose);

        expect(
          container.read(filesRepositoryProvider),
          isA<BackendFilesRepository>(),
        );
      },
    );
  });

  group('BackendFilesRepository', () {
    const repository = BackendFilesRepository();

    test(
      'reports the backend files facade as unavailable until implemented',
      () async {
        final state = await repository.restoreConnection();

        expect(state.status, FilesConnectionStatus.misconfigured);
        expect(state.message, BackendFilesRepository.unavailableMessage);
      },
    );

    test(
      'blocks product file operations instead of using direct Nextcloud',
      () async {
        await expectLater(
          repository.connect(),
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
                  BackendFilesRepository.unavailableMessage,
                ),
          ),
        );

        await expectLater(
          repository.listDirectory('/'),
          throwsA(isA<FilesFailure>()),
        );

        await expectLater(
          repository.uploadFile(
            '/',
            const FileUploadRequest(
              fileName: 'notes.txt',
              sizeInBytes: 0,
              byteStream: Stream<List<int>>.empty(),
            ),
          ),
          throwsA(isA<FilesFailure>()),
        );
      },
    );
  });
}
