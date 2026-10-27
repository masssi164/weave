import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'README design illustrations distinguish concepts from executable evidence',
    () async {
      final readme = await File('README.md').readAsString();
      final illustrations = _section(readme, '## Design illustrations');

      expect(illustrations, contains('earlier design concepts'));
      expect(illustrations, contains('one organization origin'));
      expect(illustrations, contains('OrgManifest'));
      expect(illustrations, contains('endpoint setup belongs to deployment'));
      expect(
        illustrations,
        contains(
          'executable product evidence comes from the native acceptance',
        ),
      );
      expect(illustrations, isNot(contains('active product-maturity')));

      final images = RegExp(
        r'<img src="([^"]+)" alt="([^"]+)" width="560">',
      ).allMatches(illustrations).toList();

      expect(images.map((image) => image.group(1)).toList(), <String>[
        '../docs/assets/marketing/01-setup-start.svg',
        '../docs/assets/marketing/02-review-service-endpoints.svg',
        '../docs/assets/marketing/03-chat-room.svg',
        '../docs/assets/marketing/04-files-documents.svg',
        '../docs/assets/marketing/05-settings.svg',
      ]);

      for (final image in images) {
        final assetPath = image.group(1)!;
        final altText = image.group(2)!;

        expect(
          File(assetPath).existsSync(),
          isTrue,
          reason: '$assetPath exists',
        );
        expect(altText.trim(), isNotEmpty, reason: '$assetPath has alt text');
        expect(altText.toLowerCase(), isNot(contains('preview')));
      }

      final lowerIllustrations = illustrations.toLowerCase();
      for (final gatedSurface in ['calendar', 'deck', 'boards', 'tasks']) {
        expect(lowerIllustrations, isNot(contains(gatedSurface)));
      }
    },
  );

  test('roadmap page keeps gated surfaces honest', () async {
    final roadmap = await File(
      '../docs/roadmap-and-guarded-surfaces.md',
    ).readAsString();
    final calendarRoadmap = _section(roadmap, '## Shared calendars');
    final boardsRoadmap = _section(roadmap, '## Boards/tasks');

    expect(roadmap.toLowerCase(), isNot(contains('preview')));
    expect(calendarRoadmap, contains('06-calendar-roadmap-readiness.svg'));
    expect(
      calendarRoadmap,
      contains('shared workspace, team, and channel scheduling'),
    );
    expect(
      calendarRoadmap,
      contains('Private personal calendar ingestion is not a product goal'),
    );

    expect(boardsRoadmap, contains('07-boards-feature-gate.svg'));
    expect(boardsRoadmap, contains('active Weave scope behind feature gates'));
    expect(boardsRoadmap, contains('provider-neutral'));
    expect(boardsRoadmap, contains('must not claim a live Vikunja'));
  });
}

String _section(String markdown, String heading) {
  final start = markdown.indexOf(heading);
  if (start == -1) {
    fail('Missing README heading: $heading');
  }

  int? nextHeading;
  for (final match in RegExp(
    r'^## ',
    multiLine: true,
  ).allMatches(markdown, start + heading.length)) {
    nextHeading = match.start;
    break;
  }

  return markdown.substring(start, nextHeading ?? markdown.length);
}
