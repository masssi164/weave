import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// V01_ADMIN_CONSOLE_MVP / issue #350: normal members consume Weave product state only.
void main() {
  test(
    'member client does not call admin/provider control-plane APIs directly',
    () {
      final libFiles = Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'));

      final forbiddenFragments = <String>[
        '/admin/control-plane',
        '/admin/identity/readiness',
        '/identity/readiness',
        '/admin/providers',
        '/admin/policies',
        '/admin/audit',
        'secretref://',
        'SecretRef inventory',
        'provider replacement dry-run',
      ];

      for (final file in libFiles) {
        final source = file.readAsStringSync();
        for (final fragment in forbiddenFragments) {
          expect(
            source,
            isNot(contains(fragment)),
            reason:
                '${file.path} must not expose admin/provider control-plane fragment `$fragment` to normal members.',
          );
        }
      }
    },
  );

  test(
    'member client does not add optional provider SDK imports outside approved legacy Matrix seam',
    () {
      final libFiles = Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'));

      final forbiddenImportFragments = <String>[
        'package:slack_',
        'package:microsoft_graph',
        'package:msgraph',
        'package:nextcloud',
        'package:livekit',
        'package:caldav',
      ];

      for (final file in libFiles) {
        final source = file.readAsStringSync();
        for (final fragment in forbiddenImportFragments) {
          expect(
            source,
            isNot(contains(fragment)),
            reason:
                '${file.path} must use Weave backend facades instead of optional provider SDK imports.',
          );
        }
      }
    },
  );
}
