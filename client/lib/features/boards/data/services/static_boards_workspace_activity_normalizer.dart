import 'package:weave/features/boards/domain/entities/board_activity_event.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/services/board_activity_event_normalizer.dart';

class StaticBoardsWorkspaceRawEvent {
  const StaticBoardsWorkspaceRawEvent.taskFixtureCreated({
    required this.occurredAt,
    required this.workspaceId,
    required this.projectId,
    required this.board,
    required this.column,
    required this.task,
  }) : type = StaticBoardsWorkspaceRawEventType.taskFixtureCreated,
       fromColumnId = null,
       toColumnId = null,
       fromPosition = null,
       toPosition = null;

  const StaticBoardsWorkspaceRawEvent.taskFixtureMoved({
    required this.occurredAt,
    required this.workspaceId,
    required this.projectId,
    required this.board,
    required this.task,
    required this.fromColumnId,
    required this.toColumnId,
    this.fromPosition,
    this.toPosition,
  }) : type = StaticBoardsWorkspaceRawEventType.taskFixtureMoved,
       column = null;

  final StaticBoardsWorkspaceRawEventType type;
  final DateTime occurredAt;
  final String workspaceId;
  final String projectId;
  final BoardWorkspace board;
  final BoardColumnWorkspace? column;
  final BoardTaskWorkspace task;
  final String? fromColumnId;
  final String? toColumnId;
  final int? fromPosition;
  final int? toPosition;
}

enum StaticBoardsWorkspaceRawEventType { taskFixtureCreated, taskFixtureMoved }

class StaticBoardsWorkspaceActivityNormalizer
    implements BoardActivityEventNormalizer<StaticBoardsWorkspaceRawEvent> {
  const StaticBoardsWorkspaceActivityNormalizer();

  static const providerName = 'static-workspace';
  static const actor = BoardActivityActorRef(
    id: 'boards-workspace-fixture',
    displayName: 'Boards workspace fixture',
    kind: BoardActivityActorKind.system,
  );

  @override
  Iterable<BoardActivityEvent<BoardActivityPayload>> normalize(
    StaticBoardsWorkspaceRawEvent raw,
  ) sync* {
    final source = BoardActivitySourceRef(
      workspaceId: raw.workspaceId,
      projectId: raw.projectId,
      boardId: raw.board.id,
      taskId: raw.task.id,
    );
    final providerRef = BoardProviderRef(
      provider: providerName,
      externalId: '${raw.board.id}:${raw.task.id}',
    );

    switch (raw.type) {
      case StaticBoardsWorkspaceRawEventType.taskFixtureCreated:
        yield BoardActivityEvent<TaskSnapshotPayload>(
          idempotencyKey: _key(raw, 'task.created'),
          type: BoardActivityEventType.taskCreated,
          actor: actor,
          occurredAt: raw.occurredAt,
          source: source,
          providerRef: providerRef,
          redactionLevel: BoardActivityRedactionLevel.userVisible,
          payload: TaskSnapshotPayload(
            title: raw.task.title,
            description: raw.task.description,
            status: raw.task.status.name,
            columnId: raw.column?.id,
            priority: raw.task.priorityLabel,
          ),
        );
      case StaticBoardsWorkspaceRawEventType.taskFixtureMoved:
        yield BoardActivityEvent<TaskMovedPayload>(
          idempotencyKey: _key(raw, 'task.moved'),
          type: BoardActivityEventType.taskMoved,
          actor: actor,
          occurredAt: raw.occurredAt,
          source: source,
          providerRef: providerRef,
          redactionLevel: BoardActivityRedactionLevel.userVisible,
          payload: TaskMovedPayload(
            fromColumnId: raw.fromColumnId,
            toColumnId: raw.toColumnId ?? 'unknown',
            fromPosition: raw.fromPosition,
            toPosition: raw.toPosition,
          ),
        );
    }
  }

  String _key(StaticBoardsWorkspaceRawEvent raw, String eventType) {
    final timestamp = raw.occurredAt.toUtc().toIso8601String();
    return '$providerName:${raw.workspaceId}:${raw.board.id}:${raw.task.id}:$eventType:$timestamp';
  }
}
