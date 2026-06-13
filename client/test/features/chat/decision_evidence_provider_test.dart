import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/decision_evidence.dart';
import 'package:weave/features/chat/presentation/providers/decision_evidence_provider.dart';

void main() {
  test('room decision and evidence snapshots start empty and fail closed', () {
    final container = DecisionEvidenceControllerContainer();

    final snapshot = container.controller.snapshotForRoom(
      '!room:home.internal',
    );

    expect(snapshot.records, isEmpty);
    expect(snapshot.backgroundRoomReadingEnabled, isFalse);
    expect(snapshot.isExplicitAndSourceLinked, isTrue);
  });

  test('captures a message as an explicit source-linked decision record', () {
    final container = DecisionEvidenceControllerContainer();
    final message = ChatMessage(
      id: r'$message-1',
      senderId: '@alex:home.internal',
      senderDisplayName: 'Alex',
      sentAt: DateTime(2026, 5, 22, 10),
      isMine: false,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: ChatMessageContentType.text,
      text: 'Ship the admin diagnostics first, then polish the dashboard.',
    );

    final record = container.controller.captureMessage(
      roomId: '!room:home.internal',
      message: message,
      kind: DecisionEvidenceKind.decision,
      capturedAt: DateTime(2026, 5, 22, 10, 5),
      ownerLabel: 'You',
    );
    final snapshot = container.controller.snapshotForRoom(
      '!room:home.internal',
    );

    expect(record.kind, DecisionEvidenceKind.decision);
    expect(record.status, DecisionEvidenceStatus.active);
    expect(record.ownerLabel, 'You');
    expect(record.source.messageId, message.id);
    expect(record.source.senderDisplayName, 'Alex');
    expect(record.isExplainable, isTrue);
    expect(snapshot.records, [record]);
    expect(snapshot.countFor(DecisionEvidenceKind.decision), 1);
    expect(snapshot.countFor(DecisionEvidenceKind.risk), 0);
    expect(snapshot.backgroundRoomReadingEnabled, isFalse);
    expect(snapshot.isExplicitAndSourceLinked, isTrue);
  });

  test('creates a first-class decision ledger record with lifecycle state', () {
    final container = DecisionEvidenceControllerContainer();
    final message = ChatMessage(
      id: r'$decision-source',
      senderId: '@alex:home.internal',
      senderDisplayName: 'Alex',
      sentAt: DateTime(2026, 5, 22, 11),
      isMine: false,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: ChatMessageContentType.text,
      text: 'Use the governed channel workspace tabs for Sprint 4.',
    );

    final decision = container.controller.createDecisionFromMessage(
      roomId: '!room:home.internal',
      message: message,
      capturedAt: DateTime(2026, 5, 22, 11, 5),
      ownerLabel: 'You',
    );
    final snapshot = container.controller.snapshotForRoom(
      '!room:home.internal',
    );

    expect(decision.channelId, '!room:home.internal');
    expect(decision.status, DecisionLedgerStatus.proposed);
    expect(decision.authorLabel, 'You');
    expect(
      decision.references.single.type,
      DecisionLedgerReferenceType.chatMessage,
    );
    expect(decision.references.single.label, 'Message from Alex');
    expect(
      decision.auditMetadata.provenanceSummary,
      contains('Weave-owned provenance'),
    );
    expect(decision.auditMetadata.auditRefs, hasLength(2));
    expect(
      decision.auditMetadata.exportPosture,
      contains('raw provider secrets stay hidden'),
    );
    expect(decision.isReadable, isTrue);
    expect(snapshot.decisionLedgerRecords.single.id, decision.id);
    expect(snapshot.auditMetadata.supportSafe, isTrue);
    expect(snapshot.isDecisionLedgerMvpReady, isTrue);
    expect(snapshot.backgroundRoomReadingEnabled, isFalse);
  });

  test('filters unreadable decision ledger records from snapshots', () {
    final snapshot = RoomDecisionEvidenceSnapshot(
      roomId: '!room:home.internal',
      records: [
        DecisionEvidenceRecord(
          id: 'decision-evidence:bad',
          kind: DecisionEvidenceKind.decision,
          status: DecisionEvidenceStatus.active,
          title: 'Keep this off the ledger until it is source linked',
          ownerLabel: '',
          source: DecisionEvidenceSource(
            type: DecisionEvidenceSourceType.chatMessage,
            roomId: '',
            messageId: '',
            senderDisplayName: 'Alex',
            sentAt: DateTime(2026, 5, 22, 12),
            excerpt: 'Unsourced draft decision',
          ),
          createdAt: DateTime(2026, 5, 22, 12, 5),
        ),
      ],
      backgroundRoomReadingEnabled: false,
    );

    expect(snapshot.decisionLedgerRecords, isEmpty);
    expect(snapshot.isDecisionLedgerMvpReady, isTrue);
  });
}

class DecisionEvidenceControllerContainer {
  DecisionEvidenceControllerContainer()
    : container = ProviderContainer.test(overrides: const []);

  final ProviderContainer container;

  DecisionEvidenceController get controller =>
      container.read(decisionEvidenceProvider.notifier);
}
