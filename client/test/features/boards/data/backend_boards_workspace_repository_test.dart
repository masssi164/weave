import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/boards/data/repositories/backend_boards_workspace_repository.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';

void main() {
  test('loads provider-neutral Boards workspace from backend facade', () async {
    final repository = BackendBoardsWorkspaceRepository(
      httpClient: MockClient((request) async {
        expect(
          request.url.toString(),
          'https://api.weave.local/api/boards/workspace',
        );
        expect(request.headers['Authorization'], 'Bearer token');
        return http.Response(
          '''
{
  "workspace": true,
      "releaseStatus": "active-dogfood-production",
      "source": "local-workspace-backend-facade",
      "capabilities": {
        "provider": "in-memory",
        "enabled": true,
        "supported": ["accessible_non_drag_moves"],
        "unsupported": ["comments", "attachments"],
        "supportSafeSummary": "Local workspace backend facade; no external provider secrets are exposed."
      },
      "boards": [
    {
      "id": "board-1",
      "name": "Launch board",
      "description": "Backend-fed feature-gated workspace",
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

    final workspace = await repository.loadWorkspace();

    expect(workspace.id, 'board-1');
    expect(workspace.name, 'Launch board');
    expect(workspace.isBackendFed, isTrue);
    expect(workspace.canUseBackendNonDragActions, isTrue);
    expect(workspace.capabilities.provider, 'in-memory');
    expect(workspace.capabilities.supportSafeSummary, contains('no external'));
    expect(workspace.columns, hasLength(3));
    expect(workspace.taskCount, 2);
    expect(workspace.columns[1].wipLimit, 3);
    expect(
      workspace.columns[1].tasks.single.title,
      'Validate keyboard movement',
    );
    expect(
      workspace.columns[1].tasks.single.status,
      BoardTaskStatus.inProgress,
    );
    expect(workspace.columns[2].tasks.single.status, BoardTaskStatus.done);
  });

  test(
    'returns a blocked workspace state when backend runtime is disabled',
    () async {
      final repository = BackendBoardsWorkspaceRepository(
        httpClient: MockClient((request) async {
          expect(
            request.url.toString(),
            'https://api.weave.local/api/boards/workspace',
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

      final workspace = await repository.loadWorkspace();

      expect(workspace.isBackendBlocked, isTrue);
      expect(workspace.canUseBackendNonDragActions, isFalse);
      expect(workspace.columns, isEmpty);
      expect(workspace.capabilities.provider, 'unavailable');
    },
  );

  test(
    'posts accessible non-drag move, status, decision-link, and complete actions to backend facade',
    () async {
      final seenRequests = <http.Request>[];
      final repository = BackendBoardsWorkspaceRepository(
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
      await repository.updateTaskStatus(
        taskId: 'task-1',
        status: 'blocked',
        targetColumnId: 'blocked',
      );
      await repository.linkDecision(
        taskId: 'task-1',
        decisionRef: 'decision:release-v0.1',
      );
      await repository.completeTask('task-1');

      expect(seenRequests, hasLength(4));
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
        'https://api.weave.local/api/boards/tasks/task-1/status',
      );
      expect(seenRequests[1].method, 'POST');
      expect(
        seenRequests[1].body,
        '{"status":"blocked","targetColumnId":"blocked"}',
      );
      expect(
        seenRequests[2].url.toString(),
        'https://api.weave.local/api/boards/tasks/task-1/decision-links',
      );
      expect(seenRequests[2].method, 'POST');
      expect(seenRequests[2].body, '{"decisionRef":"decision:release-v0.1"}');
      expect(
        seenRequests[3].url.toString(),
        'https://api.weave.local/api/boards/tasks/task-1/complete',
      );
      expect(seenRequests[3].method, 'POST');
    },
  );
}
