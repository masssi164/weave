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

  test(
    'primary chat provider is wired through the backend Chat facade',
    () async {
      final source = await File(
        'lib/features/chat/presentation/providers/chat_repository_provider.dart',
      ).readAsString();

      expect(source, contains('BackendChatRepository'));
      expect(source, contains('if (!FeatureFlags.legacyDirectMatrixChat)'));
      expect(
        source.indexOf('BackendChatRepository'),
        lessThan(source.indexOf('MatrixChatRepository(')),
      );
    },
  );
}
