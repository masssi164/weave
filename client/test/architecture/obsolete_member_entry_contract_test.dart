import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('obsolete global member-entry architecture cannot return', () async {
    final releaseSources = await Future.wait(
      Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'))
          .map((file) => file.readAsString()),
    );
    final compiledSource = releaseSources.join('\n');

    for (final forbidden in <String>[
      'firstRunStatusProvider',
      'FirstRunScreen',
      '/api/onboarding/status',
      '/first-run',
      '/welcome',
    ]) {
      expect(
        compiledSource,
        isNot(contains(forbidden)),
        reason: '$forbidden is an obsolete global member-entry contract.',
      );
    }

    for (final path in <String>['lib/l10n/app_en.arb', 'lib/l10n/app_de.arb']) {
      final messages =
          (jsonDecode(await File(path).readAsString()) as Map<String, dynamic>)
              .cast<String, Object?>();
      expect(
        messages.keys.where((key) => key.startsWith('firstRun')),
        isEmpty,
        reason: '$path must not define obsolete global readiness messages.',
      );
      expect(
        messages.keys.where((key) => key.startsWith('welcome')),
        isEmpty,
        reason:
            '$path must expose Organization Access directly without a parallel welcome flow.',
      );
      final userCopy = messages.values.whereType<String>().join('\n');
      for (final obsoleteCopy in <String>[
        'First-run status',
        'first-run status',
        'Checking your Weave workspace',
        'Your Weave workspace is being prepared',
        'Module readiness',
        'Workspace setup needs admin attention before every capability is ready',
        'Erststart-Status',
        'Weave-Erststart-Status',
        'Weave-Arbeitsbereich wird geprüft',
        'Dein Weave-Arbeitsbereich wird vorbereitet',
        'Modulbereitschaft',
        'Die Einrichtung des Arbeitsbereichs benötigt Admin-Aufmerksamkeit',
      ]) {
        expect(
          userCopy,
          isNot(contains(obsoleteCopy)),
          reason: '$path still contains obsolete member-entry copy.',
        );
      }
    }
  });
}
