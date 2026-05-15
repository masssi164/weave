import 'package:weave/features/boards/domain/entities/board_preview.dart';
import 'package:weave/features/boards/domain/repositories/boards_preview_repository.dart';

class StaticBoardsPreviewRepository implements BoardsPreviewRepository {
  const StaticBoardsPreviewRepository();

  @override
  Future<BoardPreview> loadPreview() async => _preview;

  static const _preview = BoardPreview(
    id: 'release-readiness-preview',
    name: 'Release readiness board',
    description:
        'A provider-neutral Weave active preview for task planning. It is not wired to a live provider yet.',
    columns: [
      BoardColumnPreview(
        id: 'todo',
        name: 'To plan',
        semanticStatus: BoardTaskStatus.notStarted,
        tasks: [
          BoardTaskPreview(
            id: 'accessibility-review',
            title: 'Keyboard movement review',
            description:
                'Validate non-drag move actions, focus order, and screen-reader labels before enabling boards.',
            status: BoardTaskStatus.notStarted,
            assigneeLabel: 'Design systems',
            dueLabel: 'Future milestone',
            labels: ['Accessibility', 'Design'],
            priorityLabel: 'High priority',
          ),
          BoardTaskPreview(
            id: 'provider-spike',
            title: 'Vikunja adapter spike',
            description:
                'Map Vikunja projects, tasks, labels, comments, and errors into the Weave board model.',
            status: BoardTaskStatus.notStarted,
            assigneeLabel: 'Platform',
            dueLabel: 'Active preview',
            labels: ['Provider adapter', 'Spike'],
            priorityLabel: 'Medium priority',
          ),
        ],
      ),
      BoardColumnPreview(
        id: 'doing',
        name: 'In progress',
        semanticStatus: BoardTaskStatus.inProgress,
        wipLimit: 3,
        tasks: [
          BoardTaskPreview(
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
      BoardColumnPreview(
        id: 'blocked',
        name: 'Blocked',
        semanticStatus: BoardTaskStatus.blocked,
        tasks: [
          BoardTaskPreview(
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
      BoardColumnPreview(
        id: 'done',
        name: 'Done',
        semanticStatus: BoardTaskStatus.done,
        tasks: [
          BoardTaskPreview(
            id: 'release-boundary',
            title: 'Active scope documented',
            description:
                'README and specs state that boards/tasks are active scope behind explicit provider and accessibility gates.',
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
