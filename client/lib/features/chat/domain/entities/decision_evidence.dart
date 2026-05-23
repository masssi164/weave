import 'package:weave/features/chat/domain/entities/chat_message.dart';

enum DecisionEvidenceKind { decision, risk, openQuestion, evidence }

enum DecisionEvidenceStatus { active, resolved, archived }

enum DecisionEvidenceSourceType { chatMessage }

class DecisionEvidenceSource {
  const DecisionEvidenceSource({
    required this.type,
    required this.roomId,
    required this.messageId,
    required this.senderDisplayName,
    required this.sentAt,
    required this.excerpt,
  });

  factory DecisionEvidenceSource.fromMessage({
    required String roomId,
    required ChatMessage message,
  }) {
    return DecisionEvidenceSource(
      type: DecisionEvidenceSourceType.chatMessage,
      roomId: roomId,
      messageId: message.id,
      senderDisplayName: message.senderDisplayName,
      sentAt: message.sentAt,
      excerpt: switch (message.contentType) {
        ChatMessageContentType.text => (message.text ?? '').trim(),
        ChatMessageContentType.encrypted => 'Encrypted message',
        ChatMessageContentType.unsupported => 'Unsupported message',
      },
    );
  }

  final DecisionEvidenceSourceType type;
  final String roomId;
  final String messageId;
  final String senderDisplayName;
  final DateTime sentAt;
  final String excerpt;

  bool get isSourceLinked => roomId.isNotEmpty && messageId.isNotEmpty;
}

class DecisionEvidenceRecord {
  const DecisionEvidenceRecord({
    required this.id,
    required this.kind,
    required this.status,
    required this.title,
    required this.ownerLabel,
    required this.source,
    required this.createdAt,
  });

  factory DecisionEvidenceRecord.fromMessage({
    required String id,
    required DecisionEvidenceKind kind,
    required String roomId,
    required ChatMessage message,
    required DateTime createdAt,
    required String ownerLabel,
  }) {
    final source = DecisionEvidenceSource.fromMessage(
      roomId: roomId,
      message: message,
    );
    return DecisionEvidenceRecord(
      id: id,
      kind: kind,
      status: DecisionEvidenceStatus.active,
      title: _titleForSource(source),
      ownerLabel: ownerLabel,
      source: source,
      createdAt: createdAt,
    );
  }

  final String id;
  final DecisionEvidenceKind kind;
  final DecisionEvidenceStatus status;
  final String title;
  final String ownerLabel;
  final DecisionEvidenceSource source;
  final DateTime createdAt;

  bool get isExplainable => source.isSourceLinked && ownerLabel.isNotEmpty;

  static String _titleForSource(DecisionEvidenceSource source) {
    final normalized = source.excerpt.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (normalized.isEmpty) {
      return 'Captured chat message';
    }
    if (normalized.length <= 96) {
      return normalized;
    }
    return '${normalized.substring(0, 93)}…';
  }
}

class RoomDecisionEvidenceSnapshot {
  const RoomDecisionEvidenceSnapshot({
    required this.roomId,
    required this.records,
    required this.backgroundRoomReadingEnabled,
  });

  factory RoomDecisionEvidenceSnapshot.empty(String roomId) {
    return RoomDecisionEvidenceSnapshot(
      roomId: roomId,
      records: const <DecisionEvidenceRecord>[],
      backgroundRoomReadingEnabled: false,
    );
  }

  final String roomId;
  final List<DecisionEvidenceRecord> records;

  /// Must remain false until a governed, reviewable backend contract exists.
  final bool backgroundRoomReadingEnabled;

  Iterable<DecisionEvidenceRecord> recordsFor(DecisionEvidenceKind kind) =>
      records.where((record) => record.kind == kind);

  int countFor(DecisionEvidenceKind kind) => recordsFor(kind).length;

  bool get hasRecords => records.isNotEmpty;

  bool get isExplicitAndSourceLinked =>
      !backgroundRoomReadingEnabled &&
      records.every((record) => record.isExplainable);
}
