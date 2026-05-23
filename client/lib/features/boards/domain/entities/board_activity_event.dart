/// Provider-neutral activity emitted by feature-gated boards/tasks adapters.
///
/// This is an app-layer contract only: it normalizes workspace fixtures and
/// external-provider-shaped samples without claiming that a live provider is
/// connected until the provider facade is explicitly enabled.
enum BoardActivityEventType {
  taskCreated,
  taskUpdated,
  taskCompleted,
  taskMoved,
  assignmentChanged,
  labelChanged,
  priorityChanged,
  dueDateChanged,
  commentAdded,
  attachmentChanged,
  syncConflictDetected,
}

extension BoardActivityEventTypeName on BoardActivityEventType {
  String get wireName => switch (this) {
    BoardActivityEventType.taskCreated => 'task.created',
    BoardActivityEventType.taskUpdated => 'task.updated',
    BoardActivityEventType.taskCompleted => 'task.completed',
    BoardActivityEventType.taskMoved => 'task.moved',
    BoardActivityEventType.assignmentChanged => 'assignment.changed',
    BoardActivityEventType.labelChanged => 'label.changed',
    BoardActivityEventType.priorityChanged => 'priority.changed',
    BoardActivityEventType.dueDateChanged => 'due_date.changed',
    BoardActivityEventType.commentAdded => 'comment.added',
    BoardActivityEventType.attachmentChanged => 'attachment.changed',
    BoardActivityEventType.syncConflictDetected => 'sync.conflict_detected',
  };
}

enum BoardActivityActorKind { user, service, system, unknown }

enum BoardActivityRedactionLevel {
  /// Safe for screen-reader announcements and recent-activity summaries.
  userVisible,

  /// Safe for support/audit diagnostics, with provider payload details removed.
  supportSafe,

  /// Internal sync metadata; never display directly without review.
  internal,
}

class BoardActivityActorRef {
  const BoardActivityActorRef({
    required this.id,
    this.displayName,
    this.kind = BoardActivityActorKind.unknown,
  });

  final String id;
  final String? displayName;
  final BoardActivityActorKind kind;
}

class BoardActivitySourceRef {
  const BoardActivitySourceRef({
    required this.workspaceId,
    this.projectId,
    this.boardId,
    this.taskId,
  });

  final String workspaceId;
  final String? projectId;
  final String? boardId;
  final String? taskId;
}

class BoardProviderRef {
  const BoardProviderRef({
    required this.provider,
    required this.externalId,
    this.externalUrl,
    this.version,
    this.etag,
    this.lastSyncedAt,
  });

  final String provider;
  final String externalId;
  final Uri? externalUrl;
  final String? version;
  final String? etag;
  final DateTime? lastSyncedAt;
}

class BoardActivityOrdering {
  const BoardActivityOrdering({
    this.providerSequence,
    this.syncCursor,
    this.receivedAt,
  });

  final int? providerSequence;
  final String? syncCursor;
  final DateTime? receivedAt;
}

class BoardActivityEvent<TPayload extends BoardActivityPayload> {
  const BoardActivityEvent({
    required this.idempotencyKey,
    required this.type,
    required this.actor,
    required this.occurredAt,
    required this.source,
    required this.payload,
    this.providerRef,
    this.redactionLevel = BoardActivityRedactionLevel.supportSafe,
    this.ordering = const BoardActivityOrdering(),
  });

  final String idempotencyKey;
  final BoardActivityEventType type;
  final BoardActivityActorRef actor;
  final DateTime occurredAt;
  final BoardActivitySourceRef source;
  final BoardProviderRef? providerRef;
  final BoardActivityRedactionLevel redactionLevel;
  final BoardActivityOrdering ordering;
  final TPayload payload;
}

sealed class BoardActivityPayload {
  const BoardActivityPayload();
}

class TaskSnapshotPayload extends BoardActivityPayload {
  const TaskSnapshotPayload({
    required this.title,
    this.description,
    this.status,
    this.columnId,
    this.assigneeIds = const [],
    this.labelIds = const [],
    this.priority,
    this.dueAt,
  });

  final String title;
  final String? description;
  final String? status;
  final String? columnId;
  final List<String> assigneeIds;
  final List<String> labelIds;
  final String? priority;
  final DateTime? dueAt;
}

class TaskMovedPayload extends BoardActivityPayload {
  const TaskMovedPayload({
    this.fromColumnId,
    required this.toColumnId,
    this.fromPosition,
    this.toPosition,
  });

  final String? fromColumnId;
  final String toColumnId;
  final int? fromPosition;
  final int? toPosition;
}

class AssignmentChangedPayload extends BoardActivityPayload {
  const AssignmentChangedPayload({
    this.addedAssigneeIds = const [],
    this.removedAssigneeIds = const [],
  });

  final List<String> addedAssigneeIds;
  final List<String> removedAssigneeIds;
}

class LabelChangedPayload extends BoardActivityPayload {
  const LabelChangedPayload({
    this.addedLabelIds = const [],
    this.removedLabelIds = const [],
  });

  final List<String> addedLabelIds;
  final List<String> removedLabelIds;
}

class PriorityChangedPayload extends BoardActivityPayload {
  const PriorityChangedPayload({this.fromPriority, this.toPriority});

  final String? fromPriority;
  final String? toPriority;
}

class DueDateChangedPayload extends BoardActivityPayload {
  const DueDateChangedPayload({this.fromDueAt, this.toDueAt});

  final DateTime? fromDueAt;
  final DateTime? toDueAt;
}

class CommentAddedPayload extends BoardActivityPayload {
  const CommentAddedPayload({required this.commentId, this.bodyPreview});

  final String commentId;
  final String? bodyPreview;
}

class AttachmentChangedPayload extends BoardActivityPayload {
  const AttachmentChangedPayload({
    required this.attachmentId,
    required this.change,
    this.displayName,
  });

  final String attachmentId;
  final String change;
  final String? displayName;
}

class SyncConflictDetectedPayload extends BoardActivityPayload {
  const SyncConflictDetectedPayload({
    required this.conflictId,
    required this.reason,
    this.localVersion,
    this.remoteVersion,
  });

  final String conflictId;
  final String reason;
  final String? localVersion;
  final String? remoteVersion;
}
