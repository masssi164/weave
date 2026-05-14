import 'package:weave/features/boards/domain/entities/task_board_event.dart';

enum BoardAdapterCapability {
  comments,
  attachments,
  assignments,
  labels,
  priorities,
  dueDates,
  nonDestructiveArchive,
  webhookEvents,
  incrementalSync,
  checklists,
  customFields,
}

enum BoardAdapterReadiness { spikeOnly, prototype, productionCandidate }

enum BoardAdapterErrorType {
  unauthorized,
  forbidden,
  notFound,
  conflict,
  rateLimited,
  offline,
  validation,
  providerUnavailable,
  unknown,
}

class BoardAdapterDescriptor {
  const BoardAdapterDescriptor({
    required this.provider,
    required this.displayName,
    required this.readiness,
    required this.capabilities,
    required this.limitations,
  });

  final BoardEventProvider provider;
  final String displayName;
  final BoardAdapterReadiness readiness;
  final Set<BoardAdapterCapability> capabilities;
  final List<String> limitations;

  bool supports(BoardAdapterCapability capability) =>
      capabilities.contains(capability);
}

class BoardAdapterPage<T> {
  const BoardAdapterPage({
    required this.items,
    this.nextCursor,
    this.etag,
    this.syncedAt,
  });

  final List<T> items;
  final String? nextCursor;
  final String? etag;
  final DateTime? syncedAt;
}

class BoardAdapterError implements Exception {
  const BoardAdapterError({
    required this.type,
    required this.message,
    this.retryAfter,
    this.providerRef,
  });

  final BoardAdapterErrorType type;
  final String message;
  final Duration? retryAfter;
  final BoardProviderRef? providerRef;

  @override
  String toString() => 'BoardAdapterError(type: $type, message: $message)';
}

class BoardAdapterSyncRequest {
  const BoardAdapterSyncRequest({
    required this.workspaceId,
    this.projectId,
    this.boardId,
    this.cursor,
    this.ifNoneMatch,
    this.since,
  });

  final String workspaceId;
  final String? projectId;
  final String? boardId;
  final String? cursor;
  final String? ifNoneMatch;
  final DateTime? since;
}

class BoardAdapterTaskPatch {
  const BoardAdapterTaskPatch({
    this.title,
    this.description,
    this.assigneeIds,
    this.labelIds,
    this.priority,
    this.dueAt,
    this.completed,
    this.archived,
  });

  final String? title;
  final String? description;
  final List<String>? assigneeIds;
  final List<String>? labelIds;
  final String? priority;
  final DateTime? dueAt;
  final bool? completed;
  final bool? archived;
}

abstract interface class BoardProviderAdapter {
  BoardAdapterDescriptor get descriptor;

  Future<BoardAdapterPage<BoardProviderRef>> listBoards(
    BoardAdapterSyncRequest request,
  );

  Future<BoardAdapterPage<BoardProviderRef>> listTasks(
    BoardAdapterSyncRequest request,
  );

  Future<BoardProviderRef> createTask({
    required BoardAdapterSyncRequest request,
    required String title,
    String? description,
  });

  Future<BoardProviderRef> updateTask({
    required BoardAdapterSyncRequest request,
    required String taskId,
    required BoardAdapterTaskPatch patch,
  });

  Future<BoardProviderRef> moveTask({
    required BoardAdapterSyncRequest request,
    required String taskId,
    required String targetColumnId,
    int? targetPosition,
  });

  Future<BoardAdapterPage<TaskBoardEvent>> syncEvents(
    BoardAdapterSyncRequest request,
  );
}

const vikunjaAdapterDescriptor = BoardAdapterDescriptor(
  provider: BoardEventProvider.vikunja,
  displayName: 'Vikunja',
  readiness: BoardAdapterReadiness.prototype,
  capabilities: {
    BoardAdapterCapability.comments,
    BoardAdapterCapability.attachments,
    BoardAdapterCapability.assignments,
    BoardAdapterCapability.labels,
    BoardAdapterCapability.priorities,
    BoardAdapterCapability.dueDates,
    BoardAdapterCapability.nonDestructiveArchive,
    BoardAdapterCapability.webhookEvents,
    BoardAdapterCapability.incrementalSync,
  },
  limitations: [
    'Webhook delivery is at-most-once, so adapters still need periodic reconciliation.',
    'Vikunja project/view/bucket vocabulary must stay behind provider refs.',
  ],
);

const openProjectBenchmarkDescriptor = BoardAdapterDescriptor(
  provider: BoardEventProvider.openProject,
  displayName: 'OpenProject benchmark',
  readiness: BoardAdapterReadiness.spikeOnly,
  capabilities: {
    BoardAdapterCapability.comments,
    BoardAdapterCapability.attachments,
    BoardAdapterCapability.assignments,
    BoardAdapterCapability.priorities,
    BoardAdapterCapability.dueDates,
    BoardAdapterCapability.customFields,
  },
  limitations: [
    'Benchmark-only until a later provider spike validates auth and sync.',
    'Action boards can mutate work package fields; basic boards do not.',
  ],
);

const nextcloudDeckBridgeDescriptor = BoardAdapterDescriptor(
  provider: BoardEventProvider.nextcloudDeck,
  displayName: 'Nextcloud Deck bridge',
  readiness: BoardAdapterReadiness.prototype,
  capabilities: {
    BoardAdapterCapability.comments,
    BoardAdapterCapability.attachments,
    BoardAdapterCapability.assignments,
    BoardAdapterCapability.labels,
    BoardAdapterCapability.dueDates,
    BoardAdapterCapability.nonDestructiveArchive,
    BoardAdapterCapability.incrementalSync,
  },
  limitations: [
    'Bridge/import candidate only; Deck board/stack/card names must not define Weave UI labels.',
    'No first-class webhook source found in the Deck API notes; use ETag/If-Modified-Since polling.',
  ],
);
