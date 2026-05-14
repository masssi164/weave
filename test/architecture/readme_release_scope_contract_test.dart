import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'README Product screenshots stay limited to Release 1 surfaces',
    () async {
      final readme = await File('README.md').readAsString();
      final productScreenshots = _section(readme, '## Product screenshots');

      expect(productScreenshots, contains('Release 1 experience'));
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
      expect(productScreenshots.toLowerCase(), isNot(contains('calendar')));
      expect(productScreenshots.toLowerCase(), isNot(contains('boards')));
      expect(productScreenshots.toLowerCase(), isNot(contains('deck')));
    },
  );

  test('README preview screenshots are explicitly non-Release-1', () async {
    final readme = await File('README.md').readAsString();
    final previewSection = _section(
      readme,
      '## Future previews (not Release 1)',
    );

    expect(previewSection, contains('06-calendar-setup-readiness-preview.svg'));
    expect(previewSection, contains('07-boards-preview.svg'));
    expect(previewSection.toLowerCase(), contains('preview'));
    expect(previewSection.toLowerCase(), contains('not part of the release 1'));
    expect(previewSection, contains('hidden previews'));
    expect(previewSection, contains('post-Release-1'));
    expect(previewSection, contains('does not claim a live Vikunja'));
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
