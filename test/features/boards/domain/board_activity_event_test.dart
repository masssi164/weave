import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/boards/domain/entities/board_activity_event.dart';

void main() {
  group('BoardActivityEventTypeName', () {
    test('covers provider-neutral task and board activity event names', () {
      expect(
        BoardActivityEventType.values.map((type) => type.wireName),
        containsAll(<String>[
          'task.created',
          'task.updated',
          'task.completed',
          'task.moved',
          'assignment.changed',
          'label.changed',
          'priority.changed',
          'due_date.changed',
          'comment.added',
          'attachment.changed',
          'sync.conflict_detected',
        ]),
      );
    });

    test('keeps required envelope fields explicit', () {
      final event = BoardActivityEvent<TaskMovedPayload>(
        idempotencyKey: 'provider:workspace:event:task.moved',
        type: BoardActivityEventType.taskMoved,
        actor: const BoardActivityActorRef(id: 'user-1'),
        occurredAt: DateTime.utc(2026, 5, 14, 16),
        source: const BoardActivitySourceRef(
          workspaceId: 'workspace-1',
          projectId: 'project-1',
          boardId: 'board-1',
          taskId: 'task-1',
        ),
        providerRef: const BoardProviderRef(
          provider: 'provider-sample',
          externalId: 'external-task-1',
        ),
        payload: const TaskMovedPayload(
          fromColumnId: 'todo',
          toColumnId: 'doing',
        ),
      );

      expect(event.idempotencyKey, isNotEmpty);
      expect(event.actor.id, 'user-1');
      expect(event.source.workspaceId, 'workspace-1');
      expect(event.providerRef?.provider, 'provider-sample');
    });
  });
}
