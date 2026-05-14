import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/boards/data/normalizers/task_board_event_normalizer.dart';
import 'package:weave/features/boards/domain/adapters/board_provider_adapter.dart';
import 'package:weave/features/boards/domain/entities/task_board_event.dart';

void main() {
  group('VikunjaWebhookNormalizer', () {
    test('maps task.created into a provider-neutral support-safe event', () {
      final event = const VikunjaWebhookNormalizer().normalize(
        workspaceId: 'weave-home',
        payload: {
          'event_name': 'task.created',
          'time': '2026-05-14T12:00:00+02:00',
          'data': {
            'task': {
              'id': 42,
              'project_id': 7,
              'bucket_id': 3,
              'position': 100,
              'done': false,
            },
            'doer': {'id': 5, 'username': 'ada', 'name': 'Ada'},
          },
        },
      );

      expect(event.type, BoardEventType.taskCreated);
      expect(event.workspaceId, 'weave-home');
      expect(event.projectId, '7');
      expect(event.boardId, '7');
      expect(event.taskId, '42');
      expect(event.actorRef.displayName, 'Ada');
      expect(event.providerRef!.provider, BoardEventProvider.vikunja);
      expect(event.payload['target_column_id'], '3');
      expect(event.isSupportSafe, isTrue);
      expect(event.toJson()['type'], 'task.created');
    });

    test('maps completed Vikunja updates without storing raw task text', () {
      final event = const VikunjaWebhookNormalizer().normalize(
        workspaceId: 'weave-home',
        payload: {
          'event_name': 'task.updated',
          'time': '2026-05-14T10:30:00Z',
          'data': {
            'task': {
              'id': 43,
              'project_id': 7,
              'title': 'Secret task title',
              'description': 'Do not persist this body in event logs',
              'done': true,
            },
            'doer': {'id': 5, 'username': 'ada'},
          },
        },
      );

      expect(event.type, BoardEventType.taskCompleted);
      expect(event.payload.containsKey('title'), isFalse);
      expect(event.payload.containsKey('description'), isFalse);
      expect(event.payload['completed'], isTrue);
    });
  });

  group('DeckPollingEventNormalizer', () {
    test('maps card move snapshots with ETag-backed idempotency', () {
      final event = const DeckPollingEventNormalizer().normalizeCardSnapshot(
        workspaceId: 'weave-home',
        boardId: 'board-10',
        stackId: 'stack-2',
        change: DeckSnapshotChange.moved,
        card: {
          'id': 81,
          'ETag': 'bdb10fa2d2aeda092a2b6b469454dc90',
          'order': 200,
          'lastModified': 1778752800,
        },
      );

      expect(event.type, BoardEventType.taskMoved);
      expect(event.taskId, '81');
      expect(event.payload['target_column_id'], 'stack-2');
      expect(event.providerRef!.etag, 'bdb10fa2d2aeda092a2b6b469454dc90');
      expect(
        event.idempotencyKey,
        contains('nextcloudDeck:moved:board-10:stack-2:81:'),
      );
    });

    test('maps Deck done and archive states to semantic Weave events', () {
      final completed = const DeckPollingEventNormalizer()
          .normalizeCardSnapshot(
            workspaceId: 'weave-home',
            boardId: 'board-10',
            stackId: 'stack-2',
            change: DeckSnapshotChange.updated,
            card: {'id': 82, 'done': true},
          );
      final archived = const DeckPollingEventNormalizer().normalizeCardSnapshot(
        workspaceId: 'weave-home',
        boardId: 'board-10',
        stackId: 'stack-2',
        change: DeckSnapshotChange.updated,
        card: {'id': 83, 'archived': true},
      );

      expect(completed.type, BoardEventType.taskCompleted);
      expect(archived.type, BoardEventType.taskArchived);
    });
  });

  group('adapter descriptors', () {
    test(
      'declare explicit capabilities and limitations per provider spike',
      () {
        expect(
          vikunjaAdapterDescriptor.supports(
            BoardAdapterCapability.webhookEvents,
          ),
          isTrue,
        );
        expect(
          nextcloudDeckBridgeDescriptor.supports(
            BoardAdapterCapability.incrementalSync,
          ),
          isTrue,
        );
        expect(
          nextcloudDeckBridgeDescriptor.supports(
            BoardAdapterCapability.webhookEvents,
          ),
          isFalse,
        );
        expect(
          openProjectBenchmarkDescriptor.readiness,
          BoardAdapterReadiness.spikeOnly,
        );
        expect(
          openProjectBenchmarkDescriptor.limitations.join(' '),
          contains('Benchmark-only'),
        );
      },
    );
  });
}
