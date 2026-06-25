import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

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
          'chatHomeAiMetricReady',
          'chatHomeAiMetricDisabled',
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
          'chatAiChatsSectionTitle',
          'chatAiChatsSectionDescription',
          'chatAiChatsSectionEmpty',
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

  test('Router removes obsolete calendar and deck member routes', () async {
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
    expect(routerSource, isNot(contains('AppRoutes.calendar')));
    expect(routerSource, isNot(contains('AppRoutes.deck')));
    expect(routerSource, isNot(contains('CalendarScreen')));
    expect(routerSource, isNot(contains('DeckScreen')));
    expect(routesSource, isNot(contains('/calendar')));
    expect(routesSource, isNot(contains('/deck')));
    expect(
      File(
        'lib/features/calendar/presentation/calendar_screen.dart',
      ).existsSync(),
      isFalse,
    );
    expect(File('lib/features/deck').existsSync(), isFalse);

    expect(shellSource, contains('l10n.navChat'));
    expect(shellSource, contains('l10n.navFiles'));
    expect(shellSource, contains('l10n.navSettings'));
    expect(shellSource, isNot(contains('navCalendar')));
    expect(shellSource, isNot(contains('navDeck')));
  });

  test(
    'README screenshots keep current product surfaces separate from roadmap surfaces',
    () async {
      final readme = await File('README.md').readAsString();
      final screenshotSection = _section(readme, '## Product screenshots');

      final imageMatches = RegExp(
        r'<img src="([^"]+)" alt="([^"]+)" width="560">',
      ).allMatches(screenshotSection).toList();

      expect(imageMatches.map((match) => match.group(1)).toList(), <String>[
        '../docs/assets/marketing/01-setup-start.svg',
        '../docs/assets/marketing/02-review-service-endpoints.svg',
        '../docs/assets/marketing/03-chat-room.svg',
        '../docs/assets/marketing/04-files-documents.svg',
        '../docs/assets/marketing/05-settings.svg',
      ]);

      for (final match in imageMatches) {
        final assetPath = match.group(1)!;
        final altText = match.group(2)!;

        expect(
          File(assetPath).existsSync(),
          isTrue,
          reason: '$assetPath exists',
        );
        expect(altText.trim(), isNotEmpty, reason: '$assetPath has alt text');
        expect(altText.toLowerCase(), isNot(contains('preview')));
      }

      final lowerSection = screenshotSection.toLowerCase();
      expect(lowerSection, isNot(contains('calendar')));
      expect(lowerSection, isNot(contains('deck')));
      expect(lowerSection, isNot(contains('boards')));
      expect(lowerSection, isNot(contains('tasks')));

      final roadmap = await File(
        '../docs/roadmap-and-guarded-surfaces.md',
      ).readAsString();
      final lowerRoadmap = roadmap.toLowerCase();
      expect(lowerRoadmap, contains('06-calendar-roadmap-readiness.svg'));
      expect(lowerRoadmap, contains('07-boards-feature-gate.svg'));
      expect(lowerRoadmap, contains('must not claim a live vikunja'));
      expect(lowerRoadmap, isNot(contains('preview')));
    },
  );
}

String _section(String markdown, String heading) {
  final start = markdown.indexOf(heading);
  expect(start, isNonNegative, reason: '$heading section exists');

  final nextHeading = RegExp(
    r'\n## ',
  ).firstMatch(markdown.substring(start + 1));
  if (nextHeading == null) {
    return markdown.substring(start);
  }

  return markdown.substring(start, start + 1 + nextHeading.start);
}
