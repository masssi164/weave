import 'package:weave/features/boards/domain/entities/board_activity_event.dart';
import 'package:weave/features/boards/domain/entities/board_preview.dart';
import 'package:weave/features/boards/domain/services/board_activity_event_normalizer.dart';

class StaticBoardsPreviewRawEvent {
  const StaticBoardsPreviewRawEvent.taskFixtureCreated({
    required this.occurredAt,
    required this.workspaceId,
    required this.projectId,
    required this.board,
    required this.column,
    required this.task,
  }) : type = StaticBoardsPreviewRawEventType.taskFixtureCreated,
       fromColumnId = null,
       toColumnId = null,
       fromPosition = null,
       toPosition = null;

  const StaticBoardsPreviewRawEvent.taskFixtureMoved({
    required this.occurredAt,
    required this.workspaceId,
    required this.projectId,
    required this.board,
    required this.task,
    required this.fromColumnId,
    required this.toColumnId,
    this.fromPosition,
    this.toPosition,
  }) : type = StaticBoardsPreviewRawEventType.taskFixtureMoved,
       column = null;

  final StaticBoardsPreviewRawEventType type;
  final DateTime occurredAt;
  final String workspaceId;
  final String projectId;
  final BoardPreview board;
  final BoardColumnPreview? column;
  final BoardTaskPreview task;
  final String? fromColumnId;
  final String? toColumnId;
  final int? fromPosition;
  final int? toPosition;
}

enum StaticBoardsPreviewRawEventType { taskFixtureCreated, taskFixtureMoved }

class StaticBoardsPreviewActivityNormalizer
    implements BoardActivityEventNormalizer<StaticBoardsPreviewRawEvent> {
  const StaticBoardsPreviewActivityNormalizer();

  static const providerName = 'static-preview';
  static const actor = BoardActivityActorRef(
    id: 'boards-preview-fixture',
    displayName: 'Boards preview fixture',
    kind: BoardActivityActorKind.system,
  );

  @override
  Iterable<BoardActivityEvent<BoardActivityPayload>> normalize(
    StaticBoardsPreviewRawEvent raw,
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
      case StaticBoardsPreviewRawEventType.taskFixtureCreated:
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
      case StaticBoardsPreviewRawEventType.taskFixtureMoved:
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

  String _key(StaticBoardsPreviewRawEvent raw, String eventType) {
    final timestamp = raw.occurredAt.toUtc().toIso8601String();
    return '$providerName:${raw.workspaceId}:${raw.board.id}:${raw.task.id}:$eventType:$timestamp';
  }
}
