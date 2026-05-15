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

  test('README preview screenshots keep active gates explicit', () async {
    final readme = await File('README.md').readAsString();
    final calendarPreview = _subsection(
      readme,
      '### Guarded active preview: Teams-like calendar',
    );
    final boardsPreview = _subsection(
      readme,
      '### Active preview: boards/tasks',
    );

    expect(
      calendarPreview,
      contains('06-calendar-setup-readiness-preview.svg'),
    );
    expect(calendarPreview.toLowerCase(), contains('preview'));
    expect(
      calendarPreview,
      contains('workspace/org, team, and channel calendars'),
    );
    expect(
      calendarPreview,
      contains('private personal calendar ingestion is out of scope'),
    );

    expect(boardsPreview, contains('07-boards-preview.svg'));
    expect(boardsPreview.toLowerCase(), contains('preview'));
    expect(boardsPreview, contains('active Weave scope behind feature gates'));
    expect(boardsPreview, contains('provider-neutral'));
    expect(boardsPreview, contains('does not claim a live Vikunja'));
    expect(boardsPreview, contains('Deck'));
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
