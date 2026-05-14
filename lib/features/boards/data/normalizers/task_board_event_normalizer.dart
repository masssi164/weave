import 'package:weave/features/boards/domain/entities/task_board_event.dart';

class VikunjaWebhookNormalizer {
  const VikunjaWebhookNormalizer();

  TaskBoardEvent normalize({
    required Map<String, Object?> payload,
    required String workspaceId,
    String? projectId,
  }) {
    final eventName = _string(payload['event_name']) ?? 'task.updated';
    final data = _map(payload['data']);
    final task = _map(data['task']);
    final doer = _map(data['doer']);
    final occurredAt = _date(payload['time']) ?? DateTime.now().toUtc();
    final taskId = _id(task['id']);
    final project = projectId ?? _id(task['project_id']);
    final bucketId = _id(task['bucket_id']);

    return TaskBoardEvent(
      idempotencyKey: _idempotencyKey(
        provider: BoardEventProvider.vikunja,
        eventName: eventName,
        occurredAt: occurredAt,
        primaryId: taskId,
      ),
      type: _vikunjaType(eventName, task),
      actorRef: _actor(doer, BoardEventProvider.vikunja),
      occurredAt: occurredAt,
      workspaceId: workspaceId,
      projectId: project,
      boardId: project,
      taskId: taskId,
      providerRef: BoardProviderRef(
        provider: BoardEventProvider.vikunja,
        externalId: taskId ?? 'unknown-task',
        rawType: eventName,
      ),
      redactionLevel: BoardEventRedactionLevel.supportSafe,
      payload: {
        if (bucketId != null) 'target_column_id': bucketId,
        if (_id(task['position']) != null) 'position': _id(task['position']),
        if (_bool(task['done']) != null) 'completed': _bool(task['done']),
        if (_string(task['priority']) != null)
          'priority': _string(task['priority']),
        if (_string(task['due_date']) != null)
          'due_at': _string(task['due_date']),
      },
    );
  }

  static BoardEventType _vikunjaType(
    String eventName,
    Map<String, Object?> task,
  ) {
    if (eventName.contains('comment')) return BoardEventType.commentAdded;
    if (eventName.contains('attachment')) {
      return BoardEventType.attachmentChanged;
    }
    if (eventName.contains('assignee')) return BoardEventType.assignmentChanged;
    if (eventName.contains('label')) return BoardEventType.labelChanged;
    if (eventName == 'task.created') return BoardEventType.taskCreated;
    if (_bool(task['done']) == true) return BoardEventType.taskCompleted;
    if (eventName.contains('moved') || task.containsKey('bucket_id')) {
      return BoardEventType.taskMoved;
    }
    return BoardEventType.taskUpdated;
  }
}

class DeckPollingEventNormalizer {
  const DeckPollingEventNormalizer();

  TaskBoardEvent normalizeCardSnapshot({
    required Map<String, Object?> card,
    required String workspaceId,
    required String boardId,
    required String stackId,
    required DeckSnapshotChange change,
    DateTime? observedAt,
  }) {
    final cardId = _id(card['id']) ?? 'unknown-card';
    final archived = _bool(card['archived']) ?? false;
    final done = _bool(card['done']) ?? false;
    final etag = _string(card['ETag']) ?? _string(card['etag']);
    final changedAt =
        _date(card['lastModified']) ?? observedAt ?? DateTime.now().toUtc();

    return TaskBoardEvent(
      idempotencyKey: _idempotencyKey(
        provider: BoardEventProvider.nextcloudDeck,
        eventName: change.name,
        occurredAt: changedAt,
        primaryId: '$boardId:$stackId:$cardId:${etag ?? 'no-etag'}',
      ),
      type: _deckType(change, archived: archived, done: done),
      actorRef: const BoardActorRef.system(),
      occurredAt: changedAt,
      workspaceId: workspaceId,
      boardId: boardId,
      taskId: cardId,
      providerRef: BoardProviderRef(
        provider: BoardEventProvider.nextcloudDeck,
        externalId: cardId,
        etag: etag,
        rawType: 'card',
      ),
      redactionLevel: BoardEventRedactionLevel.supportSafe,
      payload: {
        'target_column_id': stackId,
        if (_id(card['order']) != null) 'position': _id(card['order']),
        if (archived) 'archived': true,
        if (done) 'completed': true,
        if (_string(card['duedate']) != null)
          'due_at': _string(card['duedate']),
      },
    );
  }

  static BoardEventType _deckType(
    DeckSnapshotChange change, {
    required bool archived,
    required bool done,
  }) {
    if (archived) return BoardEventType.taskArchived;
    if (done) return BoardEventType.taskCompleted;
    return switch (change) {
      DeckSnapshotChange.created => BoardEventType.taskCreated,
      DeckSnapshotChange.updated => BoardEventType.taskUpdated,
      DeckSnapshotChange.moved => BoardEventType.taskMoved,
      DeckSnapshotChange.deleted => BoardEventType.taskArchived,
      DeckSnapshotChange.conflict => BoardEventType.syncConflictDetected,
    };
  }
}

enum DeckSnapshotChange { created, updated, moved, deleted, conflict }

String _idempotencyKey({
  required BoardEventProvider provider,
  required String eventName,
  required DateTime occurredAt,
  required String? primaryId,
}) {
  final instant = occurredAt.toUtc().toIso8601String();
  return '${provider.name}:$eventName:${primaryId ?? 'unknown'}:$instant';
}

BoardActorRef _actor(Map<String, Object?> json, BoardEventProvider provider) {
  final id = _id(json['id']) ?? _string(json['username']) ?? 'unknown-actor';
  return BoardActorRef(
    id: id,
    displayName: _string(json['name']) ?? _string(json['username']),
    providerRef: BoardProviderRef(
      provider: provider,
      externalId: id,
      rawType: 'user',
    ),
  );
}

Map<String, Object?> _map(Object? value) {
  if (value is Map<String, Object?>) return value;
  if (value is Map) return Map<String, Object?>.from(value);
  return const <String, Object?>{};
}

String? _string(Object? value) => value?.toString();

String? _id(Object? value) {
  final raw = _string(value);
  if (raw == null || raw.isEmpty || raw == 'null') return null;
  return raw;
}

bool? _bool(Object? value) {
  if (value is bool) return value;
  if (value is num) return value != 0;
  if (value is String) {
    if (value.toLowerCase() == 'true') return true;
    if (value.toLowerCase() == 'false') return false;
  }
  return null;
}

DateTime? _date(Object? value) {
  if (value is DateTime) return value.toUtc();
  if (value is int) {
    final isSeconds = value < 100000000000;
    return DateTime.fromMillisecondsSinceEpoch(
      isSeconds ? value * 1000 : value,
      isUtc: true,
    );
  }
  final raw = _string(value);
  if (raw == null || raw.isEmpty) return null;
  return DateTime.tryParse(raw)?.toUtc();
}
