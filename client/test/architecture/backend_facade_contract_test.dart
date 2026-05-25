import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'primary files provider is wired through the backend-facade seam',
    () async {
      final source = await File(
        'lib/features/files/presentation/providers/files_repository_provider.dart',
      ).readAsString();

      expect(source, contains('BackendFilesRepository'));
      expect(source, contains('legacyDirectNextcloudFilesRepositoryProvider'));
      expect(source, isNot(contains('integrations/nextcloud')));
      expect(
        source,
        isNot(contains('data/repositories/nextcloud_files_repository.dart')),
      );
      expect(source, isNot(contains('nextcloudDavClientProvider')));
    },
  );

  test('calendar provider exposes a backend-facade seam, not CalDAV', () async {
    final source = await File(
      'lib/features/calendar/presentation/providers/calendar_provider.dart',
    ).readAsString();

    expect(source, contains('CalendarFacadeClient'));
    expect(source, isNot(contains('CalDavClient')));
    expect(source, isNot(contains('caldav_client.dart')));
  });

  test('member Chat screen stays on Weave-domain readiness language', () async {
    final screen = await File(
      'lib/features/chat/presentation/chat_screen.dart',
    ).readAsString();
    final l10n =
        jsonDecode(await File('lib/l10n/app_en.arb').readAsString())
            as Map<String, dynamic>;

    expect(screen, isNot(contains('firstRunStatusProvider')));
    expect(screen, isNot(contains('moduleProvisioning.matrix')));
    expect(screen, isNot(contains('matrixProvisioning')));

    final memberChatCopy = <String>[
      l10n['chatConnectingLabel'] as String,
      l10n['chatConnectingHint'] as String,
      l10n['chatConnectButton'] as String,
      l10n['chatStaleRoomsGuidance'] as String,
      l10n['helpChatBody'] as String,
    ].join('\n');

    for (final forbidden in <String>[
      'Connect'
          ' Matrix',
      'Connecting'
          ' to Matrix',
      'refresh'
          ' Matrix',
      'connect Mat'
          'rix if asked',
      'homes'
          'erver',
      'raw pr'
          'ovider',
      'provider d'
          'iagnostics',
      'credentia'
          'l-bearing',
      'Bea'
          'rer ',
      'access'
          '_token',
    ]) {
      expect(memberChatCopy, isNot(contains(forbidden)), reason: forbidden);
    }
  });
}
