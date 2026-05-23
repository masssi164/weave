import 'package:weave/features/boards/domain/entities/board_activity_event.dart';
import 'package:weave/features/boards/domain/services/board_activity_event_normalizer.dart';

/// Provider-shaped DTO used by future adapters or backend gateway responses.
///
/// It intentionally models only the stable seam that the app consumes. Raw
/// provider webhook bodies remain outside the Flutter presentation contract.
class ExternalBoardProviderRawEvent {
  const ExternalBoardProviderRawEvent({
    required this.provider,
    required this.eventId,
    required this.eventName,
    required this.occurredAt,
    required this.actorId,
    this.actorDisplayName,
    required this.workspaceId,
    this.projectId,
    this.boardId,
    this.taskId,
    this.externalId,
    this.externalUrl,
    this.providerSequence,
    this.syncCursor,
    this.version,
    this.etag,
    this.title,
    this.description,
    this.fromColumnId,
    this.toColumnId,
    this.fromPosition,
    this.toPosition,
    this.addedAssigneeIds = const [],
    this.removedAssigneeIds = const [],
    this.addedLabelIds = const [],
    this.removedLabelIds = const [],
    this.fromPriority,
    this.toPriority,
    this.fromDueAt,
    this.toDueAt,
    this.commentId,
    this.commentBodySnippet,
    this.attachmentId,
    this.attachmentChange,
    this.attachmentDisplayName,
    this.conflictId,
    this.conflictReason,
    this.localVersion,
    this.remoteVersion,
  });

  final String provider;
  final String eventId;
  final String eventName;
  final DateTime occurredAt;
  final String actorId;
  final String? actorDisplayName;
  final String workspaceId;
  final String? projectId;
  final String? boardId;
  final String? taskId;
  final String? externalId;
  final Uri? externalUrl;
  final int? providerSequence;
  final String? syncCursor;
  final String? version;
  final String? etag;
  final String? title;
  final String? description;
  final String? fromColumnId;
  final String? toColumnId;
  final int? fromPosition;
  final int? toPosition;
  final List<String> addedAssigneeIds;
  final List<String> removedAssigneeIds;
  final List<String> addedLabelIds;
  final List<String> removedLabelIds;
  final String? fromPriority;
  final String? toPriority;
  final DateTime? fromDueAt;
  final DateTime? toDueAt;
  final String? commentId;
  final String? commentBodySnippet;
  final String? attachmentId;
  final String? attachmentChange;
  final String? attachmentDisplayName;
  final String? conflictId;
  final String? conflictReason;
  final String? localVersion;
  final String? remoteVersion;
}

class ExternalBoardActivityNormalizer
    implements BoardActivityEventNormalizer<ExternalBoardProviderRawEvent> {
  const ExternalBoardActivityNormalizer();

  @override
  Iterable<BoardActivityEvent<BoardActivityPayload>> normalize(
    ExternalBoardProviderRawEvent raw,
  ) sync* {
    final type = _typeFor(raw.eventName);
    if (type == null) {
      return;
    }

    yield BoardActivityEvent<BoardActivityPayload>(
      idempotencyKey: _idempotencyKey(raw, type),
      type: type,
      actor: BoardActivityActorRef(
        id: raw.actorId,
        displayName: raw.actorDisplayName,
        kind: BoardActivityActorKind.user,
      ),
      occurredAt: raw.occurredAt,
      source: BoardActivitySourceRef(
        workspaceId: raw.workspaceId,
        projectId: raw.projectId,
        boardId: raw.boardId,
        taskId: raw.taskId,
      ),
      providerRef: BoardProviderRef(
        provider: raw.provider,
        externalId: raw.externalId ?? raw.eventId,
        externalUrl: raw.externalUrl,
        version: raw.version,
        etag: raw.etag,
        lastSyncedAt: raw.occurredAt,
      ),
      redactionLevel: _redactionFor(type),
      ordering: BoardActivityOrdering(
        providerSequence: raw.providerSequence,
        syncCursor: raw.syncCursor,
        receivedAt: raw.occurredAt,
      ),
      payload: _payloadFor(raw, type),
    );
  }

  BoardActivityEventType? _typeFor(String eventName) => switch (eventName) {
    'task.created' || 'card.created' => BoardActivityEventType.taskCreated,
    'task.updated' || 'card.updated' => BoardActivityEventType.taskUpdated,
    'task.completed' ||
    'card.completed' => BoardActivityEventType.taskCompleted,
    'task.moved' || 'card.moved' => BoardActivityEventType.taskMoved,
    'assignment.changed' ||
    'assignee.changed' => BoardActivityEventType.assignmentChanged,
    'label.changed' || 'tag.changed' => BoardActivityEventType.labelChanged,
    'priority.changed' => BoardActivityEventType.priorityChanged,
    'due_date.changed' ||
    'due.changed' => BoardActivityEventType.dueDateChanged,
    'comment.added' => BoardActivityEventType.commentAdded,
    'attachment.changed' => BoardActivityEventType.attachmentChanged,
    'sync.conflict_detected' ||
    'conflict.detected' => BoardActivityEventType.syncConflictDetected,
    _ => null,
  };

  BoardActivityRedactionLevel _redactionFor(BoardActivityEventType type) {
    return switch (type) {
      BoardActivityEventType.syncConflictDetected =>
        BoardActivityRedactionLevel.supportSafe,
      BoardActivityEventType.commentAdded =>
        BoardActivityRedactionLevel.internal,
      _ => BoardActivityRedactionLevel.userVisible,
    };
  }

  BoardActivityPayload _payloadFor(
    ExternalBoardProviderRawEvent raw,
    BoardActivityEventType type,
  ) {
    return switch (type) {
      BoardActivityEventType.taskCreated ||
      BoardActivityEventType.taskUpdated ||
      BoardActivityEventType.taskCompleted => TaskSnapshotPayload(
        title: raw.title ?? raw.taskId ?? raw.externalId ?? 'Untitled task',
        description: raw.description,
      ),
      BoardActivityEventType.taskMoved => TaskMovedPayload(
        fromColumnId: raw.fromColumnId,
        toColumnId: raw.toColumnId ?? 'unknown',
        fromPosition: raw.fromPosition,
        toPosition: raw.toPosition,
      ),
      BoardActivityEventType.assignmentChanged => AssignmentChangedPayload(
        addedAssigneeIds: raw.addedAssigneeIds,
        removedAssigneeIds: raw.removedAssigneeIds,
      ),
      BoardActivityEventType.labelChanged => LabelChangedPayload(
        addedLabelIds: raw.addedLabelIds,
        removedLabelIds: raw.removedLabelIds,
      ),
      BoardActivityEventType.priorityChanged => PriorityChangedPayload(
        fromPriority: raw.fromPriority,
        toPriority: raw.toPriority,
      ),
      BoardActivityEventType.dueDateChanged => DueDateChangedPayload(
        fromDueAt: raw.fromDueAt,
        toDueAt: raw.toDueAt,
      ),
      BoardActivityEventType.commentAdded => CommentAddedPayload(
        commentId: raw.commentId ?? raw.eventId,
        bodySnippet: raw.commentBodySnippet,
      ),
      BoardActivityEventType.attachmentChanged => AttachmentChangedPayload(
        attachmentId: raw.attachmentId ?? raw.eventId,
        change: raw.attachmentChange ?? 'changed',
        displayName: raw.attachmentDisplayName,
      ),
      BoardActivityEventType.syncConflictDetected =>
        SyncConflictDetectedPayload(
          conflictId: raw.conflictId ?? raw.eventId,
          reason: raw.conflictReason ?? 'provider reported a sync conflict',
          localVersion: raw.localVersion,
          remoteVersion: raw.remoteVersion,
        ),
    };
  }

  String _idempotencyKey(
    ExternalBoardProviderRawEvent raw,
    BoardActivityEventType type,
  ) {
    return '${raw.provider}:${raw.workspaceId}:${raw.eventId}:${type.wireName}';
  }
}
