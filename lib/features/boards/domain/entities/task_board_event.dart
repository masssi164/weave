/// Provider-neutral event model for future Weave boards/tasks adapters.
///
/// This is post-Release-1 planning code. It keeps provider payloads behind a
/// support-safe event envelope so future notifications, recent activity,
/// diagnostics, and agent workflows do not depend on Vikunja, OpenProject, or
/// Nextcloud Deck shapes directly.
enum BoardEventProvider { vikunja, openProject, nextcloudDeck, unknown }

enum BoardEventType {
  taskCreated,
  taskUpdated,
  taskCompleted,
  taskArchived,
  taskMoved,
  assignmentChanged,
  labelChanged,
  priorityChanged,
  dueDateChanged,
  commentAdded,
  attachmentChanged,
  syncConflictDetected,
}

enum BoardEventRedactionLevel {
  /// Event can be shown in support diagnostics without raw user text.
  supportSafe,

  /// Event metadata is safe, but payload intentionally includes user-authored
  /// content such as task titles or comment excerpts.
  containsUserContent,

  /// Provider payload contained credentials or other secrets and must never be
  /// persisted or shown outside secure connector internals.
  containsSecret,
}

class BoardProviderRef {
  const BoardProviderRef({
    required this.provider,
    required this.externalId,
    this.externalUrl,
    this.version,
    this.etag,
    this.rawType,
  });

  final BoardEventProvider provider;
  final String externalId;
  final Uri? externalUrl;
  final String? version;
  final String? etag;
  final String? rawType;

  Map<String, Object?> toJson() => {
    'provider': provider.name,
    'external_id': externalId,
    if (externalUrl != null) 'external_url': externalUrl.toString(),
    if (version != null) 'version': version,
    if (etag != null) 'etag': etag,
    if (rawType != null) 'raw_type': rawType,
  };
}

class BoardActorRef {
  const BoardActorRef({
    required this.id,
    this.displayName,
    this.providerRef,
    this.isSystem = false,
  });

  const BoardActorRef.system()
    : id = 'system',
      displayName = 'System',
      providerRef = null,
      isSystem = true;

  final String id;
  final String? displayName;
  final BoardProviderRef? providerRef;
  final bool isSystem;

  Map<String, Object?> toJson() => {
    'id': id,
    if (displayName != null) 'display_name': displayName,
    if (providerRef != null) 'provider_ref': providerRef!.toJson(),
    'is_system': isSystem,
  };
}

class TaskBoardEvent {
  const TaskBoardEvent({
    required this.idempotencyKey,
    required this.type,
    required this.actorRef,
    required this.occurredAt,
    required this.workspaceId,
    this.projectId,
    this.boardId,
    this.taskId,
    this.providerRef,
    this.redactionLevel = BoardEventRedactionLevel.supportSafe,
    this.payload = const <String, Object?>{},
  });

  final String idempotencyKey;
  final BoardEventType type;
  final BoardActorRef actorRef;
  final DateTime occurredAt;
  final String workspaceId;
  final String? projectId;
  final String? boardId;
  final String? taskId;
  final BoardProviderRef? providerRef;
  final BoardEventRedactionLevel redactionLevel;

  /// Provider-neutral, redacted details. Do not store raw provider responses,
  /// credentials, full comments, or long task descriptions here.
  final Map<String, Object?> payload;

  bool get isSupportSafe =>
      redactionLevel == BoardEventRedactionLevel.supportSafe;

  Map<String, Object?> toJson() => {
    'idempotency_key': idempotencyKey,
    'type': eventTypeWireName(type),
    'actor_ref': actorRef.toJson(),
    'occurred_at': occurredAt.toUtc().toIso8601String(),
    'workspace_id': workspaceId,
    if (projectId != null) 'project_id': projectId,
    if (boardId != null) 'board_id': boardId,
    if (taskId != null) 'task_id': taskId,
    if (providerRef != null) 'provider_ref': providerRef!.toJson(),
    'redaction_level': redactionLevel.name,
    if (payload.isNotEmpty) 'payload': payload,
  };
}

String eventTypeWireName(BoardEventType type) => switch (type) {
  BoardEventType.taskCreated => 'task.created',
  BoardEventType.taskUpdated => 'task.updated',
  BoardEventType.taskCompleted => 'task.completed',
  BoardEventType.taskArchived => 'task.archived',
  BoardEventType.taskMoved => 'task.moved',
  BoardEventType.assignmentChanged => 'assignment.changed',
  BoardEventType.labelChanged => 'label.changed',
  BoardEventType.priorityChanged => 'priority.changed',
  BoardEventType.dueDateChanged => 'due_date.changed',
  BoardEventType.commentAdded => 'comment.added',
  BoardEventType.attachmentChanged => 'attachment.changed',
  BoardEventType.syncConflictDetected => 'sync.conflict_detected',
};
