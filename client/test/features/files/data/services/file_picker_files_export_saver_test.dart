import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/services/file_picker_files_export_saver.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

void main() {
  test(
    'completed export retains actual selected URI, name, MIME and bytes',
    () async {
      final bytes = Uint8List.fromList([1, 2, 3]);
      var calls = 0;
      final saver = FilePickerFilesExportSaver(
        saveFile:
            ({
              required fileName,
              required bytes,
              mimeType = 'application/octet-stream',
              dialogTitle,
            }) async {
              calls++;
              expect(fileName, 'brief.txt');
              expect(bytes, [1, 2, 3]);
              expect(mimeType, 'text/plain');
              return Uri.file('/owned/Documents/selected/brief 2.txt');
            },
      );
      final result = await saver.save(
        FileDownload(
          fileName: 'brief.txt',
          bytes: bytes,
          mediaType: 'text/plain',
        ),
      );
      expect(calls, 1);
      expect(result!.fileName, 'brief.txt');
      expect(result.destination, '/owned/Documents/selected/brief 2.txt');
    },
  );

  test('cancellation does not publish export completion', () async {
    var calls = 0;
    final saver = FilePickerFilesExportSaver(
      saveFile:
          ({
            required fileName,
            required bytes,
            mimeType = 'application/octet-stream',
            dialogTitle,
          }) async {
            calls++;
            expect(mimeType, 'application/octet-stream');
            return null;
          },
    );
    expect(
      await saver.save(
        FileDownload(
          fileName: 'brief.bin',
          bytes: Uint8List.fromList([0, 255]),
        ),
      ),
      isNull,
    );
    expect(calls, 1);
  });

  test(
    'non-file platform URI stays intact instead of becoming a local path',
    () async {
      final selected = Uri.parse('content://documents/owned-selected');
      final saver = FilePickerFilesExportSaver(
        saveFile:
            ({
              required fileName,
              required bytes,
              mimeType = 'application/octet-stream',
              dialogTitle,
            }) async => selected,
      );
      final result = await saver.save(
        FileDownload(fileName: 'brief.txt', bytes: Uint8List(0)),
      );
      expect(result!.destination, selected.toString());
    },
  );
  for (final (code, type) in [
    ('permission_denied', FilesFailureType.storage),
    ('unsupported', FilesFailureType.unsupportedPlatform),
    ('unexpected', FilesFailureType.unknown),
  ]) {
    test('native export $code preserves typed failure and cause', () async {
      final cause = PlatformException(
        code: code,
        message: 'private native details',
      );
      final saver = FilePickerFilesExportSaver(
        saveFile:
            ({
              required fileName,
              required bytes,
              mimeType = 'application/octet-stream',
              dialogTitle,
            }) async => throw cause,
      );
      await expectLater(
        saver.save(FileDownload(fileName: 'brief.txt', bytes: Uint8List(0))),
        throwsA(
          isA<FilesFailure>()
              .having((failure) => failure.type, 'type', type)
              .having(
                (failure) => failure.cause,
                'original cause',
                same(cause),
              ),
        ),
      );
    });
  }
}
