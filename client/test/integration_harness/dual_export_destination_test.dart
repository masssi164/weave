import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import '../../integration_test/dual_ios_collaboration_test.dart'
    show nativeDualExportSnapshot, requireNativeDualExportDestination;

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
