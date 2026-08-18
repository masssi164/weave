import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/repositories/boards_workspace_repository.dart';

class StaticBoardsWorkspaceRepository implements BoardsWorkspaceRepository {
  const StaticBoardsWorkspaceRepository();

  @override
  Future<BoardWorkspace> loadWorkspace() async => _workspace;

  static const _workspace = BoardWorkspace(
    id: 'release-readiness-workspace',
    name: 'Release readiness board',
    description:
        'A provider-neutral Weave dogfood production for task planning. It is not wired to a live provider yet.',
    source: BoardWorkspaceSource.staticFixture,
    capabilities: BoardProviderWorkspaceCapabilities.staticWorkspace(),
    columns: [
      BoardColumnWorkspace(
        id: 'todo',
        name: 'To plan',
        semanticStatus: BoardTaskStatus.notStarted,
        tasks: [
          BoardTaskWorkspace(
            id: 'accessibility-review',
            title: 'Keyboard movement review',
            description:
                'Validate non-drag move actions, focus order, and screen-reader labels before enabling boards.',
            status: BoardTaskStatus.notStarted,
            assigneeLabel: 'Design systems',
            dueLabel: 'Dogfood milestone',
            labels: ['Accessibility', 'Design'],
            priorityLabel: 'High priority',
          ),
          BoardTaskWorkspace(
            id: 'provider-spike',
            title: 'Adapter readiness check',
            description:
                'Verify projects, tasks, labels, comments, and errors through the Weave board contract.',
            status: BoardTaskStatus.notStarted,
            assigneeLabel: 'Platform',
            dueLabel: 'Dogfood production',
            labels: ['Provider adapter', 'Spike'],
            priorityLabel: 'Medium priority',
          ),
        ],
      ),
      BoardColumnWorkspace(
        id: 'doing',
        name: 'In progress',
        semanticStatus: BoardTaskStatus.inProgress,
        wipLimit: 3,
        tasks: [
          BoardTaskWorkspace(
            id: 'domain-contract',
            title: 'Provider-neutral domain contract',
            description:
                'Keep boards, columns, tasks, labels, and provider refs in Weave-owned language.',
            status: BoardTaskStatus.inProgress,
            assigneeLabel: 'Product architecture',
            dueLabel: 'Drafted',
            labels: ['Domain model'],
            priorityLabel: 'High priority',
          ),
        ],
      ),
      BoardColumnWorkspace(
        id: 'blocked',
        name: 'Blocked',
        semanticStatus: BoardTaskStatus.blocked,
        tasks: [
          BoardTaskWorkspace(
            id: 'runtime-boundary',
            title: 'Runtime enablement spec',
            description:
                'Exact API routes, provider auth, export, backup, and smoke/E2E behavior must be specified first.',
            status: BoardTaskStatus.blocked,
            assigneeLabel: 'Cross-repo owner',
            dueLabel: 'Needs promotion spec',
            labels: ['Spec required'],
            priorityLabel: 'High priority',
          ),
        ],
      ),
      BoardColumnWorkspace(
        id: 'done',
        name: 'Done',
        semanticStatus: BoardTaskStatus.done,
        tasks: [
          BoardTaskWorkspace(
            id: 'release-boundary',
            title: 'Active scope documented',
            description:
                'README and specs state that boards/tasks are active v0.1 scope behind explicit provider, audit, and accessibility gates.',
            status: BoardTaskStatus.done,
            assigneeLabel: 'Documentation',
            dueLabel: 'Complete',
            labels: ['Roadmap honesty'],
            priorityLabel: 'Completed',
          ),
        ],
      ),
    ],
  );
}
