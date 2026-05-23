import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/boards/data/repositories/static_boards_preview_repository.dart';
import 'package:weave/features/boards/data/services/external_board_activity_normalizer.dart';
import 'package:weave/features/boards/data/services/static_boards_preview_activity_normalizer.dart';
import 'package:weave/features/boards/domain/entities/board_activity_event.dart';

void main() {
  group('StaticBoardsPreviewActivityNormalizer', () {
    test(
      'maps workspace fixture activity without claiming a live provider',
      () async {
        final preview = await const StaticBoardsPreviewRepository()
            .loadPreview();
        final task = preview.columns.first.tasks.first;
        final raw = StaticBoardsPreviewRawEvent.taskFixtureMoved(
          occurredAt: DateTime.utc(2026, 5, 14, 12),
          workspaceId: 'workspace-default',
          projectId: 'project-default',
          board: preview,
          task: task,
          fromColumnId: 'todo',
          toColumnId: 'doing',
          fromPosition: 0,
          toPosition: 1,
        );

        final events = const StaticBoardsPreviewActivityNormalizer()
            .normalize(raw)
            .toList();

        expect(events, hasLength(1));
        final event = events.single;
        expect(event.type, BoardActivityEventType.taskMoved);
        expect(event.providerRef?.provider, 'static-workspace');
        expect(event.actor.kind, BoardActivityActorKind.system);
        expect(event.source.workspaceId, 'workspace-default');
        expect(event.idempotencyKey, contains('static-workspace'));
        expect(event.payload, isA<TaskMovedPayload>());
        expect((event.payload as TaskMovedPayload).toColumnId, 'doing');
      },
    );
  });

  group('ExternalBoardActivityNormalizer', () {
    test('maps an external-provider-shaped card move into task.moved', () {
      final raw = ExternalBoardProviderRawEvent(
        provider: 'vikunja-spike-sample',
        eventId: 'evt-42',
        eventName: 'card.moved',
        occurredAt: DateTime.utc(2026, 5, 14, 13),
        actorId: 'provider-user-7',
        actorDisplayName: 'Provider User',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        boardId: 'board-1',
        taskId: 'task-1',
        externalId: 'vikunja-task-99',
        providerSequence: 42,
        syncCursor: 'cursor-42',
        fromColumnId: 'backlog',
        toColumnId: 'doing',
        fromPosition: 3,
        toPosition: 1,
      );

      final event = const ExternalBoardActivityNormalizer()
          .normalize(raw)
          .single;

      expect(
        event.idempotencyKey,
        'vikunja-spike-sample:workspace-1:evt-42:task.moved',
      );
      expect(event.type, BoardActivityEventType.taskMoved);
      expect(event.actor.displayName, 'Provider User');
      expect(event.providerRef?.externalId, 'vikunja-task-99');
      expect(event.ordering.providerSequence, 42);
      expect(event.ordering.syncCursor, 'cursor-42');
      expect(event.payload, isA<TaskMovedPayload>());
      expect((event.payload as TaskMovedPayload).fromColumnId, 'backlog');
    });

    test(
      'maps assignment, label, priority, due-date, comment, attachment, and conflict samples',
      () {
        const normalizer = ExternalBoardActivityNormalizer();
        final occurredAt = DateTime.utc(2026, 5, 14, 14);
        final samples = <ExternalBoardProviderRawEvent>[
          ExternalBoardProviderRawEvent(
            provider: 'openproject-shaped-sample',
            eventId: 'assignment-1',
            eventName: 'assignment.changed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            addedAssigneeIds: const ['user-2'],
          ),
          ExternalBoardProviderRawEvent(
            provider: 'openproject-shaped-sample',
            eventId: 'label-1',
            eventName: 'tag.changed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            addedLabelIds: const ['accessibility'],
          ),
          ExternalBoardProviderRawEvent(
            provider: 'openproject-shaped-sample',
            eventId: 'priority-1',
            eventName: 'priority.changed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            fromPriority: 'medium',
            toPriority: 'high',
          ),
          ExternalBoardProviderRawEvent(
            provider: 'openproject-shaped-sample',
            eventId: 'due-1',
            eventName: 'due.changed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            toDueAt: DateTime.utc(2026, 6),
          ),
          ExternalBoardProviderRawEvent(
            provider: 'openproject-shaped-sample',
            eventId: 'completed-1',
            eventName: 'task.completed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            title: 'Keyboard movement review',
          ),
          ExternalBoardProviderRawEvent(
            provider: 'deck-shaped-sample',
            eventId: 'comment-1',
            eventName: 'comment.added',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            commentId: 'comment-1',
            commentBodyPreview: 'Sanitized workspace evidence',
          ),
          ExternalBoardProviderRawEvent(
            provider: 'deck-shaped-sample',
            eventId: 'attachment-1',
            eventName: 'attachment.changed',
            occurredAt: occurredAt,
            actorId: 'user-1',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            attachmentId: 'file-1',
            attachmentChange: 'added',
          ),
          ExternalBoardProviderRawEvent(
            provider: 'sync-gateway-shaped-sample',
            eventId: 'conflict-1',
            eventName: 'conflict.detected',
            occurredAt: occurredAt,
            actorId: 'sync-service',
            workspaceId: 'workspace-1',
            taskId: 'task-1',
            conflictReason: 'remote update won optimistic sync race',
          ),
        ];

        final events = samples.expand(normalizer.normalize).toList();

        expect(events.map((event) => event.type), <BoardActivityEventType>[
          BoardActivityEventType.assignmentChanged,
          BoardActivityEventType.labelChanged,
          BoardActivityEventType.priorityChanged,
          BoardActivityEventType.dueDateChanged,
          BoardActivityEventType.taskCompleted,
          BoardActivityEventType.commentAdded,
          BoardActivityEventType.attachmentChanged,
          BoardActivityEventType.syncConflictDetected,
        ]);
        expect(events[5].redactionLevel, BoardActivityRedactionLevel.internal);
        expect(events.last.payload, isA<SyncConflictDetectedPayload>());
      },
    );

    test(
      'drops unknown provider-shaped events until a mapping is reviewed',
      () {
        final raw = ExternalBoardProviderRawEvent(
          provider: 'unknown-provider-sample',
          eventId: 'evt-unknown',
          eventName: 'provider.private_event',
          occurredAt: DateTime.utc(2026, 5, 14, 15),
          actorId: 'user-1',
          workspaceId: 'workspace-1',
        );

        expect(const ExternalBoardActivityNormalizer().normalize(raw), isEmpty);
      },
    );
  });
}
