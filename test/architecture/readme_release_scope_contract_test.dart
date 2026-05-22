import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'README Product screenshots describe the active maturity track honestly',
    () async {
      final readme = await File('README.md').readAsString();
      final productScreenshots = _section(readme, '## Product screenshots');

      expect(productScreenshots, contains('active product-maturity'));
      expect(productScreenshots, contains('01-setup-start.svg'));
      expect(productScreenshots, contains('02-review-service-endpoints.svg'));
      expect(productScreenshots, contains('03-chat-room.svg'));
      expect(productScreenshots, contains('04-files-documents.svg'));
      expect(productScreenshots, contains('05-settings.svg'));
      expect(
        productScreenshots,
        isNot(contains('06-calendar-setup-readiness-preview.svg')),
      );
      expect(productScreenshots, isNot(contains('07-boards-preview.svg')));
    },
  );

  test('roadmap page keeps gated surfaces honest', () async {
    final roadmap = await File(
      'docs/roadmap-and-guarded-surfaces.md',
    ).readAsString();
    final calendarRoadmap = _section(roadmap, '## Teams-like calendar');
    final boardsRoadmap = _section(roadmap, '## Boards/tasks');

    expect(calendarRoadmap, contains('06-calendar-roadmap-readiness.svg'));
    expect(calendarRoadmap.toLowerCase(), isNot(contains('preview')));
    expect(
      calendarRoadmap,
      contains('workspace/org, team, and channel scheduling'),
    );
    expect(
      calendarRoadmap,
      contains('Private personal calendar ingestion is not a product goal'),
    );

    expect(boardsRoadmap, contains('07-boards-feature-gate.svg'));
    expect(boardsRoadmap.toLowerCase(), isNot(contains('preview')));
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

String _subsection(String markdown, String heading) {
  final start = markdown.indexOf(heading);
  if (start == -1) {
    fail('Missing README heading: $heading');
  }

  int? nextHeading;
  for (final match in RegExp(
    r'^### ',
    multiLine: true,
  ).allMatches(markdown, start + heading.length)) {
    nextHeading = match.start;
    break;
  }

  return markdown.substring(start, nextHeading ?? markdown.length);
}
