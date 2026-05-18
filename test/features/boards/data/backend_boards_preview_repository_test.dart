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
      "releaseStatus": "active-feature-gated-preview",
      "source": "local-preview-backend-facade",
      "capabilities": {
        "provider": "in-memory",
        "enabled": true,
        "supported": ["accessible_non_drag_moves"],
        "unsupported": ["comments", "attachments"],
        "supportSafeSummary": "Local preview backend facade; no external provider secrets are exposed."
      },
      "boards": [
    {
      "id": "board-1",
      "name": "Launch board",
      "description": "Backend-fed feature-gated preview",
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
    expect(preview.isBackendFed, isTrue);
    expect(preview.canUseBackendNonDragActions, isTrue);
    expect(preview.capabilities.provider, 'in-memory');
    expect(preview.capabilities.supportSafeSummary, contains('no external'));
    expect(preview.columns, hasLength(3));
    expect(preview.taskCount, 2);
    expect(preview.columns[1].wipLimit, 3);
    expect(preview.columns[1].tasks.single.title, 'Validate keyboard movement');
    expect(preview.columns[1].tasks.single.status, BoardTaskStatus.inProgress);
    expect(preview.columns[2].tasks.single.status, BoardTaskStatus.done);
  });

  test(
    'returns a blocked preview state when backend runtime is disabled',
    () async {
      final repository = BackendBoardsPreviewRepository(
        httpClient: MockClient((request) async {
          expect(
            request.url.toString(),
            'https://api.weave.local/api/boards/preview',
          );
          return http.Response(
            '{"code":"boards-provider_unavailable","details":{"module":"boards"}}',
            503,
            headers: {'content-type': 'application/json'},
          );
        }),
        apiBaseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token',
      );

      final preview = await repository.loadPreview();

      expect(preview.isBackendBlocked, isTrue);
      expect(preview.canUseBackendNonDragActions, isFalse);
      expect(preview.columns, isEmpty);
      expect(preview.capabilities.provider, 'unavailable');
    },
  );

  test(
    'posts accessible non-drag move and complete actions to backend facade',
    () async {
      final seenRequests = <http.Request>[];
      final repository = BackendBoardsPreviewRepository(
        httpClient: MockClient((request) async {
          seenRequests.add(request);
          return http.Response('{"id":"task-1"}', 200);
        }),
        apiBaseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token',
      );

      await repository.moveTask(
        taskId: 'task-1',
        targetColumnId: 'done',
        targetPosition: 2,
      );
      await repository.completeTask('task-1');

      expect(seenRequests, hasLength(2));
      expect(
        seenRequests[0].url.toString(),
        'https://api.weave.local/api/boards/tasks/task-1/move',
      );
      expect(seenRequests[0].method, 'POST');
      expect(seenRequests[0].headers['Authorization'], 'Bearer token');
      expect(
        seenRequests[0].body,
        '{"targetColumnId":"done","targetPosition":2}',
      );
      expect(
        seenRequests[1].url.toString(),
        'https://api.weave.local/api/boards/tasks/task-1/complete',
      );
      expect(seenRequests[1].method, 'POST');
    },
  );
}
