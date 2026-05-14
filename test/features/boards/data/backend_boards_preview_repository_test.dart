import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/boards/data/repositories/backend_boards_preview_repository.dart';
import 'package:weave/features/boards/domain/entities/board_preview.dart';

void main() {
  test('loads provider-neutral Boards preview from backend facade', () async {
    final repository = BackendBoardsPreviewRepository(
      httpClient: MockClient((request) async {
        expect(
          request.url.toString(),
          'https://api.weave.local/api/boards/preview',
        );
        expect(request.headers['Authorization'], 'Bearer token');
        return http.Response(
          '''
{
  "preview": true,
  "releaseStatus": "post-release-hidden-preview",
  "source": "local-preview-backend-facade",
  "boards": [
    {
      "id": "board-1",
      "name": "Launch board",
      "description": "Backend-fed hidden preview",
      "columns": [
        {"id": "todo", "name": "To do", "semanticStatus": "not_started"},
        {"id": "doing", "name": "Doing", "semanticStatus": "in_progress", "wipLimit": 3},
        {"id": "done", "name": "Done", "semanticStatus": "done"}
      ]
    }
  ],
  "tasks": [
    {
      "id": "task-1",
      "boardId": "board-1",
      "columnId": "doing",
      "title": "Validate keyboard movement",
      "description": "No drag-only interactions.",
      "status": "open",
      "assigneeRefs": ["workspace:member"],
      "labelRefs": ["a11y"],
      "priority": "normal"
    },
    {
      "id": "task-2",
      "boardId": "board-1",
      "columnId": "done",
      "title": "Normalize events",
      "description": "Support-safe payloads.",
      "status": "completed",
      "assigneeRefs": [],
      "labelRefs": [],
      "priority": "high"
    }
  ]
}
''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
      apiBaseUrl: Uri.parse('https://api.weave.local/api'),
      accessToken: 'token',
    );

    final preview = await repository.loadPreview();

    expect(preview.id, 'board-1');
    expect(preview.name, 'Launch board');
    expect(preview.columns, hasLength(3));
    expect(preview.taskCount, 2);
    expect(preview.columns[1].wipLimit, 3);
    expect(preview.columns[1].tasks.single.title, 'Validate keyboard movement');
    expect(preview.columns[1].tasks.single.status, BoardTaskStatus.inProgress);
    expect(preview.columns[2].tasks.single.status, BoardTaskStatus.done);
  });
}
