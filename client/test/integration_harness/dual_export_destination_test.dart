import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import '../../integration_test/dual_ios_collaboration_test.dart'
    show
        nativeDualExportSnapshot,
        requireNativeDualExportDestination,
        createNativeDualExportSentinel,
        requireNativeDualExportSentinelUnchanged;

void main() {
  late Directory documents;
  setUp(() async {
    final directory = await Directory.systemTemp.createTemp('owned-export-');
    documents = Directory(await directory.resolveSymbolicLinks());
  });
  tearDown(() async {
    await documents.delete(recursive: true);
  });
  test(
    'export sentinel never replaces an existing document and detects damage',
    () async {
      final sentinel = await createNativeDualExportSentinel(
        documents,
        'owned-run',
      );
      final original = await sentinel.readAsBytes();
      await requireNativeDualExportSentinelUnchanged(sentinel, 'owned-run');
      await expectLater(
        createNativeDualExportSentinel(documents, 'owned-run'),
        throwsA(isA<FileSystemException>()),
      );
      expect(await sentinel.readAsBytes(), original);
      await sentinel.writeAsString('changed');
      await expectLater(
        requireNativeDualExportSentinelUnchanged(sentinel, 'owned-run'),
        throwsA(isA<TestFailure>()),
      );
      await sentinel.delete();
      await expectLater(
        requireNativeDualExportSentinelUnchanged(sentinel, 'owned-run'),
        throwsA(isA<TestFailure>()),
      );
      await Link(sentinel.path).create('${documents.path}/foreign');
      await expectLater(
        requireNativeDualExportSentinelUnchanged(sentinel, 'owned-run'),
        throwsA(isA<TestFailure>()),
      );
      await expectLater(
        createNativeDualExportSentinel(documents, '../foreign'),
        throwsA(isA<TestFailure>()),
      );
    },
  );
  test(
    'actual selected renamed file proves export, staging does not',
    () async {
      final staging = File('${documents.path}/brief.txt');
      await staging.writeAsString('staged before picker');
      final before = await nativeDualExportSnapshot(documents);
      final selected = File('${documents.path}/Selected/brief 2.txt');
      await selected.parent.create();
      await selected.writeAsString('actual chosen bytes');
      final verified = await requireNativeDualExportDestination(
        documents,
        before,
        selected.path,
      );
      expect(await verified.readAsString(), 'actual chosen bytes');
      await expectLater(
        requireNativeDualExportDestination(documents, before, staging.path),
        throwsA(isA<TestFailure>()),
      );
    },
  );
  test(
    'outside, symlink, missing and absent selected destinations fail closed',
    () async {
      final before = await nativeDualExportSnapshot(documents);
      final actual = File('${documents.path}/actual.txt');
      await actual.writeAsString('bytes');
      final link = Link('${documents.path}/link.txt');
      await link.create(actual.path);
      for (final selected in [
        null,
        'relative.txt',
        '${documents.path}/../outside.txt',
        link.path,
        '${documents.path}/missing.txt',
        documents.path,
      ]) {
        await expectLater(
          requireNativeDualExportDestination(documents, before, selected),
          throwsA(isA<TestFailure>()),
        );
      }
    },
  );
}
