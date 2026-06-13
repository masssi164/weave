import 'package:weave/features/chat/domain/entities/chat_message.dart';

enum DecisionEvidenceKind { decision, risk, openQuestion, evidence }

enum DecisionEvidenceStatus { active, resolved, archived }

enum DecisionEvidenceSourceType { chatMessage }

enum DecisionLedgerStatus { proposed, accepted, superseded, rejected }

enum DecisionLedgerReferenceType { chatMessage, file, meeting, task }

class DecisionEvidenceAuditMetadata {
  const DecisionEvidenceAuditMetadata({
    required this.provenanceSummary,
    required this.auditRefs,
    required this.exportPosture,
    required this.supportSafe,
  });

  final String provenanceSummary;
  final List<String> auditRefs;
  final String exportPosture;
  final bool supportSafe;

  bool get isSupportSafe =>
      supportSafe &&
      provenanceSummary.isNotEmpty &&
      exportPosture.isNotEmpty &&
      auditRefs.isNotEmpty &&
      auditRefs.every(
        (ref) => ref.startsWith('audit://') || ref.startsWith('evidence:'),
      );
}

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

class DecisionLedgerReference {
  const DecisionLedgerReference({
    required this.type,
    required this.ref,
    required this.label,
    required this.excerpt,
  });

  factory DecisionLedgerReference.fromEvidenceSource(
    DecisionEvidenceSource source,
  ) {
    return DecisionLedgerReference(
      type: DecisionLedgerReferenceType.chatMessage,
      ref: '${source.roomId}/${source.messageId}',
      label: 'Message from ${source.senderDisplayName}',
      excerpt: source.excerpt,
    );
  }

  final DecisionLedgerReferenceType type;
  final String ref;
  final String label;
  final String excerpt;

  bool get isSourceLinked => ref.isNotEmpty && label.isNotEmpty;
}

class ChannelDecisionRecord {
  const ChannelDecisionRecord({
    required this.id,
    required this.channelId,
    required this.title,
    required this.status,
    required this.authorLabel,
    required this.decidedAt,
    required this.references,
    required this.auditMetadata,
  });

  factory ChannelDecisionRecord.fromEvidenceRecord({
    required String channelId,
    required DecisionEvidenceRecord record,
    DecisionLedgerStatus status = DecisionLedgerStatus.proposed,
  }) {
    return ChannelDecisionRecord(
      id: record.id,
      channelId: channelId,
      title: record.title,
      status: status,
      authorLabel: record.ownerLabel,
      decidedAt: record.createdAt,
      references: [DecisionLedgerReference.fromEvidenceSource(record.source)],
      auditMetadata: DecisionEvidenceAuditMetadata(
        provenanceSummary:
            'Weave-owned provenance links this decision to explicit source references and actor labels.',
        auditRefs: [
          'audit://chat/decision-ledger/${Uri.encodeComponent(channelId)}',
          'evidence:${Uri.encodeComponent(channelId)}:decision-final-state',
        ],
        exportPosture:
            'Export decision records, source refs, and audit refs through the Weave decisions/evidence contract; raw provider secrets stay hidden.',
        supportSafe: true,
      ),
    );
  }

  final String id;
  final String channelId;
  final String title;
  final DecisionLedgerStatus status;
  final String authorLabel;
  final DateTime decidedAt;
  final List<DecisionLedgerReference> references;
  final DecisionEvidenceAuditMetadata auditMetadata;

  bool get hasLifecycleState => DecisionLedgerStatus.values.contains(status);

  bool get isSourceLinked =>
      references.isNotEmpty &&
      references.every((source) => source.isSourceLinked);

  bool get isReadable =>
      id.isNotEmpty &&
      channelId.isNotEmpty &&
      title.isNotEmpty &&
      authorLabel.isNotEmpty &&
      hasLifecycleState &&
      isSourceLinked &&
      auditMetadata.isSupportSafe;
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

  List<ChannelDecisionRecord> get decisionLedgerRecords =>
      recordsFor(DecisionEvidenceKind.decision)
          .map(
            (record) => ChannelDecisionRecord.fromEvidenceRecord(
              channelId: roomId,
              record: record,
            ),
          )
          .where((decision) => decision.isReadable)
          .toList(growable: false);

  int countFor(DecisionEvidenceKind kind) => recordsFor(kind).length;

  bool get hasRecords => records.isNotEmpty;

  bool get isExplicitAndSourceLinked =>
      !backgroundRoomReadingEnabled &&
      records.every((record) => record.isExplainable);

  bool get isDecisionLedgerMvpReady =>
      !backgroundRoomReadingEnabled &&
      decisionLedgerRecords.every((decision) => decision.isReadable);

  DecisionEvidenceAuditMetadata get auditMetadata {
    final ledger = decisionLedgerRecords;
    if (ledger.isNotEmpty) {
      return ledger.first.auditMetadata;
    }
    return DecisionEvidenceAuditMetadata(
      provenanceSummary:
          'Weave keeps decisions evidence explicit, support-safe, and source linked before export.',
      auditRefs: [
        'audit://chat/decision-ledger/${Uri.encodeComponent(roomId)}',
        'evidence:${Uri.encodeComponent(roomId)}:decision-final-state',
      ],
      exportPosture:
          'Export decision records, source refs, and audit refs through the Weave decisions/evidence contract; raw provider secrets stay hidden.',
      supportSafe: true,
    );
  }
}
