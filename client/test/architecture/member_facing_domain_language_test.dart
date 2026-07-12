import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('member-facing canonical domain language', () {
    final en = _loadArb('lib/l10n/app_en.arb');
    final de = _loadArb('lib/l10n/app_de.arb');

    test('keeps Files and Calendar labels semantically separate', () {
      for (final entry in <String, Map<String, Object?>>{
        'en': en,
        'de': de,
      }.entries) {
        final locale = entry.key;
        final arb = entry.value;

        expect(
          arb['providerCategoryFilesDetail'],
          isNot(contains(RegExp('calendar|kalender', caseSensitive: false))),
          reason: '$locale Files detail must not conflate Calendar.',
        );
        expect(
          arb['providerCategoryCalendarDetail'],
          isNot(contains(RegExp('files|datei', caseSensitive: false))),
          reason: '$locale Calendar detail must not conflate Files.',
        );
        expect(
          arb['filesProductBoundaryBody'],
          isNot(contains(RegExp('calendar|kalender', caseSensitive: false))),
          reason: '$locale Files boundary copy must stay in the Files domain.',
        );
      }
    });

    test('describes chat encryption as contextual security state', () {
      expect(
        en['helpPrivacySecurityBody'],
        allOf(
          contains('encrypted rooms'),
          contains('recovery'),
          contains('device trust'),
        ),
      );
      expect(
        de['helpPrivacySecurityBody'],
        allOf(
          contains('verschlüsselte Räume'),
          contains('Wiederherstellung'),
          contains('Gerätevertrauen'),
        ),
      );
      expect(
        de['chatSecuritySectionDescription'],
        contains('verschlüsselte Chaträume'),
      );
    });

    test('does not use dogfood provider labels in member handbook copy', () {
      final memberCopyKeys = <String>[
        'filesProductBoundaryBody',
        'helpWhatIsWeaveBody',
        'helpSignInBody',
        'helpPrivacySecurityBody',
      ];
      final forbidden = RegExp(
        'Nextcloud|Matrix|Keycloak|OpenProject|LiveKit|Dateien und Kalender|Files and Calendar',
        caseSensitive: false,
      );

      for (final entry in <String, Map<String, Object?>>{
        'en': en,
        'de': de,
      }.entries) {
        for (final key in memberCopyKeys) {
          expect(
            entry.value[key],
            isNot(contains(forbidden)),
            reason:
                '${entry.key} $key must stay provider-neutral and domain-specific.',
          );
        }
      }
    });
  });
}

Map<String, Object?> _loadArb(String path) {
  return (jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>)
      .cast<String, Object?>();
}
