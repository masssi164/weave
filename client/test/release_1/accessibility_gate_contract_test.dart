import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('accessibility release gate', () {
    final checklist = File('../docs/accessibility-release-gate.md');

    test('documents automated and manual evidence for every critical flow', () {
      expect(checklist.existsSync(), isTrue);

      final markdown = checklist.readAsStringSync();

      for (final requiredSection in <String>[
        'Automated CI evidence',
        'Manual assistive-technology evidence required before release sign-off',
        'Non-negotiable pass criteria',
        'Release accounting',
        'Sprint 4 dogfood evidence gate',
        'Sprint 11 provider-reality accessibility replacement scope',
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
        'Weave Home',
        'Channel Work Rooms',
        'Decision Ledger',
        'Meeting Capsule',
        'Weaver Scout',
        'Provider apply and recovery gates',
        'Member domain provider reality',
      ]) {
        expect(
          markdown,
          contains(flow),
          reason: 'Missing current release accessibility flow: $flow',
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

    test(
      'tracks Sprint 11 manual replacement evidence without treating it as a pass',
      () {
        final template = File(
          '../docs/evidence/sprint-11-manual-accessibility-evidence-template.md',
        );
        expect(template.existsSync(), isTrue);

        final templateMarkdown = template.readAsStringSync();
        expect(templateMarkdown, contains('Issues: #480, #489'));
        expect(
          templateMarkdown,
          contains(
            'This file is not pass evidence until every result cell is completed',
          ),
        );
        expect(templateMarkdown, contains('Provider apply and recovery gates'));
        expect(
          templateMarkdown,
          contains('Member domain surfaces provider reality'),
        );
      },
    );

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
              'Missing current release accessibility validation command: $command',
        );
      }
    });
  });
}
