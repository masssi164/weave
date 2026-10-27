import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/files_routes.dart';

void main() {
  test(
    'release-scope member copy does not leak preview or provider setup language',
    () async {
      final arb =
          jsonDecode(await File('lib/l10n/app_en.arb').readAsString())
              as Map<String, dynamic>;
      final releaseMemberCopy = arb.entries.where((entry) {
        if (entry.key.startsWith('@') || entry.value is! String) {
          return false;
        }

        const chatMemberKeys = <String>{
          'chatOverviewTitle',
          'chatOverviewDescription',
          'chatHomeHeroTitle',
          'chatHomeHeroDescription',
          'chatHomeUnreadMetric',
          'chatHomeChannelsMetric',
          'chatHomePeopleMetric',
          'chatHomeContinueButton',
          'chatOverviewSectionCount',
          'chatFavoritesSectionTitle',
          'chatFavoritesSectionDescription',
          'chatFavoritesSectionEmpty',
          'chatPersonalMessagesSectionTitle',
          'chatPersonalMessagesSectionDescription',
          'chatPersonalMessagesSectionEmpty',
          'chatChannelsSectionTitle',
          'chatChannelsSectionDescription',
          'chatChannelsSectionEmpty',
        };

        return entry.key.startsWith('channelWorkspace') ||
            chatMemberKeys.contains(entry.key) ||
            entry.key == 'settingsAdminBoundaryTitle' ||
            entry.key == 'settingsAdminBoundaryDescription' ||
            entry.key == 'helpSettingsBody' ||
            entry.key == 'helpTroubleshootingBody';
      });

      final forbiddenReleaseLanguage = RegExp(
        r'\b(preview|scaffold|coming soon|roadmap)\b',
        caseSensitive: false,
      );
      final forbiddenProviderSetupLanguage = RegExp(
        r'\b(OIDC|LiveKit|Nextcloud|CalDAV|OpenProject|provider seam)\b',
        caseSensitive: false,
      );

      for (final MapEntry(:key, :value) in releaseMemberCopy) {
        final copy = value as String;
        expect(
          copy,
          isNot(matches(forbiddenReleaseLanguage)),
          reason: '$key must not market unfinished release scope',
        );
        expect(
          copy,
          isNot(matches(forbiddenProviderSetupLanguage)),
          reason: '$key must not expose provider setup details to members',
        );
      }
    },
  );

  test('normal chat overview does not mount scaffold preview panels', () async {
    final chatScreen = await File(
      'lib/features/chat/presentation/chat_screen.dart',
    ).readAsString();

    expect(chatScreen, isNot(contains('WorkflowPreviewPanel')));
    expect(chatScreen, isNot(contains('workflowPreviewFacadeProvider')));
    expect(chatScreen, isNot(contains('_ChatContextCard')));
    expect(chatScreen, isNot(contains('_AgentChatGovernancePanel')));
    expect(chatScreen, isNot(contains('agentChatPreviewProvider')));
  });

  test(
    'Router exposes current member routes and keeps obsolete deck removed',
    () async {
      final routerSource = await File(
        'lib/core/router/app_router.dart',
      ).readAsString();
      final routesSource = await File(
        'lib/core/router/app_routes.dart',
      ).readAsString();
      final shellSource = await File(
        'lib/features/shell/presentation/app_shell.dart',
      ).readAsString();

      expect(routerSource, isNot(contains('onHiddenReleaseOneRoute')));
      expect(routerSource, isNot(contains('AppRoutes.deck')));
      _expectInOrder(routerSource, const [
        'path: AppRoutes.home',
        'path: AppRoutes.chat',
        'StatefulShellBranch(routes: [filesRoute()])',
        'path: AppRoutes.calendar',
        'path: AppRoutes.settings',
      ]);
      final files = filesRoute();
      expect(files.path, AppRoutes.files);
      expect(files.pageBuilder, isNotNull);
      expect(files.routes, hasLength(2));
      for (final location in files.routes.cast<GoRoute>()) {
        expect(location.pageBuilder, isNotNull);
        final folder = location.routes.single as GoRoute;
        expect(folder.path, AppRoutes.filesFolderRelative);
        expect(folder.pageBuilder, isNotNull);
      }
      expect(routerSource, contains('CalendarScreen'));
      expect(routerSource, isNot(contains('DeckScreen')));
      expect(routesSource, contains('/home'));
      expect(routesSource, contains('/calendar'));
      expect(routesSource, contains('/profile'));
      expect(routesSource, isNot(contains('/deck')));
      expect(
        File(
          'lib/features/calendar/presentation/calendar_screen.dart',
        ).existsSync(),
        isTrue,
      );
      expect(File('lib/features/deck').existsSync(), isFalse);

      _expectInOrder(shellSource, const [
        'l10n.navHome',
        'l10n.navChat',
        'l10n.navFiles',
        'l10n.navCalendar',
        'l10n.navSettings',
      ]);
      expect(shellSource, isNot(contains('navDeck')));
    },
  );
}

void _expectInOrder(String source, List<String> markers) {
  var cursor = -1;
  for (final marker in markers) {
    final index = source.indexOf(marker, cursor + 1);
    expect(index, isNonNegative, reason: '$marker exists after index $cursor');
    cursor = index;
  }
}
