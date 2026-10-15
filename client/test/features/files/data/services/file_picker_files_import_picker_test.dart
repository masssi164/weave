import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

final class _PickedFile extends PlatformFile {
  _PickedFile({
    required this.name,
    required this.size,
    required this.stream,
    this.lengthFailure,
  });
  @override
  final String name;
  final int? size;
  final Object? lengthFailure;
  final Stream<Uint8List> Function() stream;
  @override
  Uri get uri => Uri(scheme: 'memory', path: name);
  @override
  Never get xFile => throw UnimplementedError();
  @override
  int? lengthSync() => size;
  @override
  Future<int?> length() async {
    if (lengthFailure != null) throw lengthFailure!;
    return size;
  }

  @override
  Stream<Uint8List> readAsByteStream() => stream();
  @override
  Future<Uint8List> readAsBytes() async => Uint8List.fromList(
    await readAsByteStream().expand((chunk) => chunk).toList(),
  );
}

void main() {
  group('FilePickerFilesImportPicker', () {
    test('one single-file selection treats cancellation as no-op', () async {
      final calls = <String?>[];
      final picker = FilePickerFilesImportPicker(
        pickFile: ({dialogTitle}) async {
          calls.add(dialogTitle);
          return null;
        },
      );
      expect(await picker.pickFile(), isNull);
      expect(calls, ['Choose a file to upload']);
    });

    test(
      'lazy picked stream retains byte order without eager buffering',
      () async {
        var listened = false;
        final selected = _PickedFile(
          name: 'brief.txt',
          size: 4,
          stream: () async* {
            listened = true;
            yield Uint8List.fromList([1, 2]);
            yield Uint8List.fromList([3, 4]);
          },
        );
        final picker = FilePickerFilesImportPicker(
          pickFile: ({dialogTitle}) async => selected,
        );
        final request = await picker.pickFile();
        expect(listened, isFalse);
        expect(request!.fileName, 'brief.txt');
        expect(request.sizeInBytes, 4);
        expect(await request.byteStream.expand((chunk) => chunk).toList(), [
          1,
          2,
          3,
          4,
        ]);
        expect(listened, isTrue);
      },
    );

    test('platform-provided stream reads an actual local file', () async {
      final directory = await Directory.systemTemp.createTemp('weave-picked-');
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/brief.txt');
      await file.writeAsBytes([1, 2, 3, 4]);
      final picker = FilePickerFilesImportPicker(
        pickFile: ({dialogTitle}) async => _PickedFile(
          name: 'brief.txt',
          size: await file.length(),
          stream: () => file.openRead().map(Uint8List.fromList),
        ),
      );
      final request = await picker.pickFile();
      expect(await request!.byteStream.expand((chunk) => chunk).toList(), [
        1,
        2,
        3,
        4,
      ]);
    });

    for (final size in [null, -1, 0]) {
      test(
        'unknown or invalid length fails closed; real zero is valid: $size',
        () async {
          var streamOpened = false;
          final picker = FilePickerFilesImportPicker(
            pickFile: ({dialogTitle}) async => _PickedFile(
              name: 'brief.txt',
              size: size,
              stream: () {
                streamOpened = true;
                return const Stream.empty();
              },
            ),
          );
          if (size == 0) {
            expect((await picker.pickFile())!.sizeInBytes, 0);
            expect(streamOpened, isTrue);
          } else {
            await expectLater(
              picker.pickFile(),
              throwsA(
                isA<FilesFailure>().having(
                  (f) => f.type,
                  'type',
                  FilesFailureType.storage,
                ),
              ),
            );
            expect(streamOpened, isFalse);
          }
        },
      );
    }

    test(
      'length read failures map to existing storage failure with original cause',
      () async {
        for (final error in [
          PlatformException(code: 'permission_denied'),
          const FileSystemException('private read failed'),
        ]) {
          final picker = FilePickerFilesImportPicker(
            pickFile: ({dialogTitle}) async => _PickedFile(
              name: 'brief.txt',
              size: null,
              stream: () => const Stream.empty(),
              lengthFailure: error,
            ),
          );
          await expectLater(
            picker.pickFile(),
            throwsA(
              isA<FilesFailure>()
                  .having((f) => f.type, 'type', FilesFailureType.storage)
                  .having((f) => f.cause, 'cause', same(error)),
            ),
          );
        }
      },
    );

    test(
      'a later native stream failure is not replaced by invented bytes',
      () async {
        final error = StateError('original stream failure');
        final picker = FilePickerFilesImportPicker(
          pickFile: ({dialogTitle}) async => _PickedFile(
            name: 'brief.txt',
            size: 4,
            stream: () => Stream<Uint8List>.error(error),
          ),
        );
        final request = await picker.pickFile();
        await expectLater(request!.byteStream.toList(), throwsA(same(error)));
      },
    );

    test('platform cancellation remains a no-op', () async {
      final picker = FilePickerFilesImportPicker(
        pickFile: ({dialogTitle}) async =>
            throw PlatformException(code: 'CANCELLED'),
      );
      expect(await picker.pickFile(), isNull);
    });

    for (final vector in {
      'permission_denied': FilesFailureType.storage,
      'missing_entitlement': FilesFailureType.configuration,
      'missing_plugin': FilesFailureType.unsupportedPlatform,
      'picker_failed': FilesFailureType.unknown,
    }.entries) {
      test('maps native selection failure ${vector.key}', () async {
        final picker = FilePickerFilesImportPicker(
          pickFile: ({dialogTitle}) async =>
              throw PlatformException(code: vector.key),
        );
        await expectLater(
          picker.pickFile(),
          throwsA(
            isA<FilesFailure>().having((f) => f.type, 'type', vector.value),
          ),
        );
      });
    }
  });
}
