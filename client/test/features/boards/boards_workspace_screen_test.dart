import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/boards/presentation/boards_workspace_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/test_app.dart';

void main() {
  group('BoardsWorkspaceScreen', () {
    testWidgets('labels boards as dogfood provider-neutral workspace', (
      tester,
    ) async {
      _setCompactWorkspaceSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: _staticWorkspaceOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Boards workspace'), findsWidgets);
      expect(find.text('Dogfood boards/tasks workspace'), findsOneWidget);
      expect(find.text('Dogfood production'), findsWidgets);
      expect(find.text('Provider-neutral model'), findsOneWidget);
      expect(find.text('No drag required'), findsOneWidget);
      expect(find.text('Static fixture workspace'), findsOneWidget);
      expect(find.text('Adapter readiness check'), findsOneWidget);
      expect(find.text('Move menu instead of drag-only'), findsOneWidget);
    });

    testWidgets('renders backend-fed snapshots without provider identity', (
      tester,
    ) async {
      _setCompactWorkspaceSurface(tester);
      final requests = <http.Request>[];
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: [
            ..._backendWorkspaceOverrides((request) async {
              requests.add(request);
              if (request.method == 'POST') {
                return http.Response('{"id":"task-1"}', 200);
              }
              return http.Response(
                _backendWorkspacePayload,
                200,
                headers: {'content-type': 'application/json'},
              );
            }),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => _capabilities(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Launch board'), findsOneWidget);
      expect(find.text('Backend facade fed'), findsOneWidget);
      expect(find.textContaining('Provider:'), findsNothing);
      expect(find.textContaining('in-memory'), findsNothing);
      expect(find.text('Backend non-drag actions ready'), findsOneWidget);
      expect(find.text('Validate keyboard movement'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Board Launch board, 3 columns, 1 task.'),
        findsOneWidget,
      );

      await tester.tap(find.byIcon(Icons.more_vert).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mark done'));
      await tester.pumpAndSettle();

      expect(
        find.text('Task marked done through the backend facade.'),
        findsOneWidget,
      );
      expect(
        requests.any(
          (request) =>
              request.method == 'POST' &&
              request.url.toString() ==
                  'https://api.weave.test/api/boards/tasks/task-1/complete',
        ),
        isTrue,
      );
    });

    testWidgets(
      'shows blocked provider state when backend facade is disabled',
      (tester) async {
        _setCompactWorkspaceSurface(tester);
        await tester.pumpWidget(
          createTestApp(
            const BoardsWorkspaceScreen(),
            overrides: _backendWorkspaceOverrides(
              (_) async =>
                  http.Response('{"code":"boards-provider_unavailable"}', 503),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Boards unavailable'), findsOneWidget);
        expect(find.textContaining('Provider:'), findsNothing);
        expect(find.text('Backend non-drag actions blocked'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            'Board Boards backend facade unavailable, 0 columns, 0 tasks.',
          ),
          findsOneWidget,
        );
      },
    );

    testWidgets('fails closed when backend Boards capability is not ready', (
      tester,
    ) async {
      _setCompactWorkspaceSurface(tester);
      final requests = <http.Request>[];
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: [
            ..._backendWorkspaceOverrides((request) async {
              requests.add(request);
              return http.Response(_backendWorkspacePayload, 200);
            }),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async =>
                  _capabilities(boards: WorkspaceCapabilityReadiness.blocked),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Boards unavailable'), findsOneWidget);
      expect(find.text('Backend non-drag actions blocked'), findsOneWidget);
      expect(requests, isEmpty);
    });

    testWidgets(
      'fails closed when backend Boards context authorization denies',
      (tester) async {
        _setCompactWorkspaceSurface(tester);
        final requests = <http.Request>[];
        await tester.pumpWidget(
          createTestApp(
            const BoardsWorkspaceScreen(),
            overrides: [
              ..._backendWorkspaceOverrides((request) async {
                requests.add(request);
                return http.Response('{"code":"boards-forbidden"}', 403);
              }),
              weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
                (ref) async => _capabilities(),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Boards unavailable'), findsOneWidget);
        expect(find.text('Backend non-drag actions blocked'), findsOneWidget);
        expect(requests, hasLength(1));
      },
    );

    testWidgets('offers non-drag task actions with gated feedback', (
      tester,
    ) async {
      _setCompactWorkspaceSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: _staticWorkspaceOverrides,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.more_vert).first);
      await tester.pumpAndSettle();

      expect(find.text('Move to another column'), findsOneWidget);
      expect(find.text('Mark done'), findsOneWidget);
      expect(find.text('Mark blocked'), findsOneWidget);

      await tester.tap(find.text('Move to another column'));
      await tester.pumpAndSettle();

      expect(
        find.text('Connect to the workspace backend to apply task changes.'),
        findsOneWidget,
      );
    });

    testWidgets('exposes screen-reader summaries for board, columns, and tasks', (
      tester,
    ) async {
      _setCompactWorkspaceSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: _staticWorkspaceOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.bySemanticsLabel(
          'Dogfood boards/tasks workspace. Provider-neutral Weave model with keyboard and screen-reader alternatives; user task writes require the audited workspace backend.',
        ),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          'Board Release readiness board, 4 columns, 5 tasks.',
        ),
        findsOneWidget,
      );

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -1200));
      await tester.pumpAndSettle();

      expect(
        find.bySemanticsLabel('Column Blocked, status Blocked, 1 task.'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          'Task Runtime enablement spec. Column Blocked. Status Blocked. Assignee Cross-repo owner. Due Needs promotion spec. Priority High priority.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('meets tap-target accessibility guidelines', (tester) async {
      _setCompactWorkspaceSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: _staticWorkspaceOverrides,
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });

    testWidgets('keeps critical workspace copy reachable with large text', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(900, 1600);
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      await tester.pumpWidget(
        createTestApp(
          const BoardsWorkspaceScreen(),
          overrides: _staticWorkspaceOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Dogfood boards/tasks workspace'), findsOneWidget);

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -1800));
      await tester.pumpAndSettle();

      expect(find.text('Runtime enablement spec'), findsOneWidget);
      expect(find.byIcon(Icons.more_vert), findsWidgets);
    });
  });
}

final _staticWorkspaceOverrides = [
  weaveAuthenticatedSessionProvider.overrideWith((ref) async => null),
];

List<dynamic> _backendWorkspaceOverrides(
  Future<http.Response> Function(http.Request request) handler,
) => [
  weaveAuthenticatedSessionProvider.overrideWith(
    (ref) async => WeaveAuthenticatedSession(
      apiBaseUrl: Uri.parse('https://api.weave.test/api'),
      accessToken: 'token',
    ),
  ),
  weaveApiHttpClientProvider.overrideWithValue(MockClient(handler)),
];

WorkspaceCapabilitySnapshot _capabilities({
  WorkspaceCapabilityReadiness boards = WorkspaceCapabilityReadiness.ready,
}) => WorkspaceCapabilitySnapshot(
  shellAccess: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: boards,
  ),
);

void _setCompactWorkspaceSurface(WidgetTester tester) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(900, 1600);
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

const _backendWorkspacePayload = '''
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
      "columnId": "todo",
      "title": "Validate keyboard movement",
      "description": "No drag-only interactions.",
      "status": "open",
      "assigneeRefs": ["workspace:member"],
      "labelRefs": ["a11y"],
      "priority": "normal"
    }
  ]
}
''';
