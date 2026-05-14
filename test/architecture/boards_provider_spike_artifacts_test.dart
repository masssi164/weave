import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('provider spike artifacts stay concrete and post-Release-1', () async {
    final artifacts = await File(
      'docs/research/boards-provider-spike-artifacts.md',
    ).readAsString();

    expect(artifacts, contains('#119 Vikunja first adapter spike'));
    expect(artifacts, contains('#120 OpenProject accessibility benchmark'));
    expect(artifacts, contains('#121 Nextcloud Deck bridge spike'));
    expect(artifacts, contains('#123 Event normalizer artifact'));
    expect(artifacts, contains('/projects/{id}/webhooks'));
    expect(artifacts, contains('/api/v3/work_packages'));
    expect(artifacts, contains('/boards/{boardId}/stacks'));
    expect(artifacts, contains('TaskBoardEventNormalizer'));
    expect(artifacts, contains('No live instance, credentials, or provider secrets'));
    expect(artifacts, contains('hidden and disabled for Release 1'));
  });

  test('provider artifacts preserve accessible non-drag board contract', () async {
    final artifacts = await File(
      'docs/research/boards-provider-spike-artifacts.md',
    ).readAsString();

    expect(artifacts, contains('No task movement requires drag-and-drop'));
    expect(artifacts, contains('Screen readers get deterministic'));
    expect(artifacts, contains('Text at 200%'));
    expect(artifacts, contains('reorder'));
    expect(artifacts, contains('/tasks/{id}/position'));
  });
}
