import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

void main() {
  group('FilePickerFilesImportPicker', () {
    test(
      'opens a single native picker and treats cancellation as no-op',
      () async {
        final calls =
            <
              ({
                String? dialogTitle,
                bool allowMultiple,
                bool withData,
                bool withReadStream,
              })
            >[];
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = true,
                bool withData = true,
                bool withReadStream = false,
              }) async {
                calls.add((
                  dialogTitle: dialogTitle,
                  allowMultiple: allowMultiple,
                  withData: withData,
                  withReadStream: withReadStream,
                ));
                return null;
              },
        );

        final request = await picker.pickFile();

        expect(request, isNull);
        expect(calls, hasLength(1));
        expect(calls.single.dialogTitle, 'Choose a file to upload');
        expect(calls.single.allowMultiple, isFalse);
        expect(calls.single.withData, isFalse);
        expect(calls.single.withReadStream, isTrue);
      },
    );

    test('uses the picked file read stream for upload', () async {
      final stream = Stream<List<int>>.fromIterable(const [
        [1, 2],
        [3, 4],
      ]);
      final picker = FilePickerFilesImportPicker(
        pickFiles:
            ({
              String? dialogTitle,
              bool allowMultiple = false,
              bool withData = false,
              bool withReadStream = false,
            }) async => FilePickerResult([
              PlatformFile(name: 'brief.txt', size: 4, readStream: stream),
            ]),
      );

      final request = await picker.pickFile();

      expect(request?.fileName, 'brief.txt');
      expect(request?.sizeInBytes, 4);
      expect(await request?.byteStream.expand((chunk) => chunk).toList(), [
        1,
        2,
        3,
        4,
      ]);
    });

    test(
      'falls back to picked file bytes when a stream is unavailable',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async => FilePickerResult([
                PlatformFile(
                  name: 'brief.txt',
                  size: 4,
                  bytes: Uint8List.fromList([1, 2, 3, 4]),
                ),
              ]),
        );

        final request = await picker.pickFile();

        expect(request?.fileName, 'brief.txt');
        expect(request?.sizeInBytes, 4);
        expect(await request?.byteStream.expand((chunk) => chunk).toList(), [
          1,
          2,
          3,
          4,
        ]);
      },
    );

    test(
      'falls back to a native path on desktop-style picker results',
      () async {
        final tempDirectory = await Directory.systemTemp.createTemp(
          'weave-file-picker-test-',
        );
        addTearDown(() => tempDirectory.delete(recursive: true));
        final file = File('${tempDirectory.path}/brief.txt');
        await file.writeAsBytes([1, 2, 3, 4]);
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async => FilePickerResult([
                PlatformFile(name: 'brief.txt', size: 4, path: file.path),
              ]),
        );

        final request = await picker.pickFile();

        expect(request?.fileName, 'brief.txt');
        expect(request?.sizeInBytes, 4);
        expect(await request?.byteStream.expand((chunk) => chunk).toList(), [
          1,
          2,
          3,
          4,
        ]);
      },
    );

    test('treats platform cancellation exceptions as no-op', () async {
      final picker = FilePickerFilesImportPicker(
        pickFiles:
            ({
              String? dialogTitle,
              bool allowMultiple = false,
              bool withData = false,
              bool withReadStream = false,
            }) async {
              throw PlatformException(code: 'CANCELLED');
            },
      );

      await expectLater(picker.pickFile(), completion(isNull));
    });

    test(
      'maps platform permission failures to a friendly files failure',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async {
                throw PlatformException(
                  code: 'permission_denied',
                  message: 'Storage permission denied',
                );
              },
        );

        await expectLater(
          picker.pickFile(),
          throwsA(
            isA<FilesFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  FilesFailureType.storage,
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  'Weave could not access the selected file. Check file or document picker permissions and try again.',
                ),
          ),
        );
      },
    );

    test(
      'maps native picker configuration failures to a friendly files failure',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async {
                throw PlatformException(
                  code: 'missing_entitlement',
                  message: 'Document picker entitlement is missing',
                );
              },
        );

        await expectLater(
          picker.pickFile(),
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
                  'This app build is missing file access permissions for the native picker.',
                ),
          ),
        );
      },
    );

    test(
      'maps unavailable native picker failures to a friendly files failure',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async {
                throw PlatformException(code: 'missing_plugin');
              },
        );

        await expectLater(
          picker.pickFile(),
          throwsA(
            isA<FilesFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  FilesFailureType.unsupportedPlatform,
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  'Native file picking is unavailable on this platform.',
                ),
          ),
        );
      },
    );

    test(
      'maps unknown native picker failures to a retryable friendly files failure',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async {
                throw PlatformException(
                  code: 'picker_failed',
                  message: 'Provider crashed',
                );
              },
        );

        await expectLater(
          picker.pickFile(),
          throwsA(
            isA<FilesFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  FilesFailureType.unknown,
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  'Unable to choose a file for upload (picker_failed Provider crashed).',
                ),
          ),
        );
      },
    );

    test(
      'reports a friendly failure when the picked file is not readable',
      () async {
        final picker = FilePickerFilesImportPicker(
          pickFiles:
              ({
                String? dialogTitle,
                bool allowMultiple = false,
                bool withData = false,
                bool withReadStream = false,
              }) async => FilePickerResult([
                PlatformFile(name: 'remote-only.txt', size: 4),
              ]),
        );

        await expectLater(
          picker.pickFile(),
          throwsA(
            isA<FilesFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  FilesFailureType.unsupportedPlatform,
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  contains('remote-only.txt'),
                ),
          ),
        );
      },
    );
  });
}
