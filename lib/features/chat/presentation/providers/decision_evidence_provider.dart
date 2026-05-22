import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/decision_evidence.dart';

final decisionEvidenceProvider =
    NotifierProvider<
      DecisionEvidenceController,
      Map<String, List<DecisionEvidenceRecord>>
    >(DecisionEvidenceController.new);

class DecisionEvidenceController
    extends Notifier<Map<String, List<DecisionEvidenceRecord>>> {
  @override
  Map<String, List<DecisionEvidenceRecord>> build() {
    return const <String, List<DecisionEvidenceRecord>>{};
  }

  RoomDecisionEvidenceSnapshot snapshotForRoom(String roomId) {
    return RoomDecisionEvidenceSnapshot(
      roomId: roomId,
      records: List<DecisionEvidenceRecord>.unmodifiable(
        state[roomId] ?? const <DecisionEvidenceRecord>[],
      ),
      backgroundRoomReadingEnabled: false,
    );
  }

  DecisionEvidenceRecord captureMessage({
    required String roomId,
    required ChatMessage message,
    required DecisionEvidenceKind kind,
    required DateTime capturedAt,
    String ownerLabel = 'You',
  }) {
    final record = DecisionEvidenceRecord.fromMessage(
      id: 'decision-evidence:${Uri.encodeComponent(roomId)}:${Uri.encodeComponent(message.id)}:${kind.name}:${capturedAt.microsecondsSinceEpoch}',
      kind: kind,
      roomId: roomId,
      message: message,
      createdAt: capturedAt,
      ownerLabel: ownerLabel,
    );
    final existing = state[roomId] ?? const <DecisionEvidenceRecord>[];
    state = <String, List<DecisionEvidenceRecord>>{
      ...state,
      roomId: <DecisionEvidenceRecord>[record, ...existing],
    };
    return record;
  }
}
