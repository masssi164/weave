import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Release 1 accessibility gate', () {
    final checklist = File('docs/release-1-accessibility-gate.md');

    test('documents automated and manual evidence for every critical flow', () {
      expect(checklist.existsSync(), isTrue);

      final markdown = checklist.readAsStringSync();

      for (final requiredSection in <String>[
        'Automated CI evidence',
        'Manual assistive-technology evidence required before Release 1 sign-off',
        'Non-negotiable pass criteria',
        'Release accounting',
      ]) {
        expect(
          markdown,
          contains(requiredSection),
          reason: 'Missing accessibility gate section: $requiredSection',
        );
      }

      for (final flow in <String>[
        'Sign-in/setup handoff',
        'Main navigation/settings',
        'Profile view/edit',
        'Chat room list/message list/composer',
        'Files list/upload/download/status/error',
        'Calendar list/create/delete/status/error',
        'Admin/status surfaces consumed by app',
      ]) {
        expect(
          markdown,
          contains(flow),
          reason: 'Missing Release 1 accessibility flow: $flow',
        );
      }

      for (final manualGate in <String>[
        'Mobile screen reader',
        'Desktop keyboard/screen reader',
        'Text scaling',
        'VoiceOver or TalkBack',
      ]) {
        expect(
          markdown,
          contains(manualGate),
          reason: 'Missing manual accessibility evidence gate: $manualGate',
        );
      }
    });

    test('keeps the gate tied to executable validation commands', () {
      final markdown = checklist.readAsStringSync();

      for (final command in <String>[
        'flutter gen-l10n',
        'dart run build_runner build --delete-conflicting-outputs',
        'dart format --output=none --set-exit-if-changed .',
        'flutter analyze --fatal-infos',
        'flutter test',
        'make offline-contract-test',
      ]) {
        expect(
          markdown,
          contains(command),
          reason:
              'Missing Release 1 accessibility validation command: $command',
        );
      }
    });
  });
}
