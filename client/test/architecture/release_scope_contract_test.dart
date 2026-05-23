import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'Router keeps feature-gated calendar and boards surfaces out of default navigation',
    () async {
      final routerSource = await File(
        'lib/core/router/app_router.dart',
      ).readAsString();
      final shellSource = await File(
        'lib/features/shell/presentation/app_shell.dart',
      ).readAsString();

      expect(routerSource, contains('onHiddenReleaseOneRoute'));
      expect(
        routerSource,
        contains('state.matchedLocation == AppRoutes.calendar'),
      );
      expect(routerSource, contains('state.matchedLocation == AppRoutes.deck'));
      expect(routerSource, contains('return AppRoutes.chat'));

      expect(routerSource, isNot(contains('CalendarScreen')));
      expect(routerSource, isNot(contains('DeckScreen')));
      expect(routerSource, isNot(contains('path: AppRoutes.calendar')));
      expect(routerSource, isNot(contains('path: AppRoutes.deck')));

      expect(shellSource, contains('l10n.navChat'));
      expect(shellSource, contains('l10n.navFiles'));
      expect(shellSource, contains('l10n.navSettings'));
      expect(shellSource, isNot(contains('navCalendar')));
      expect(shellSource, isNot(contains('navDeck')));
    },
  );

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
