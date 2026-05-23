enum ContextGraphNodeKind { room, file, task, decision, evidenceArtifact }

enum ContextGraphEdgeKind {
  belongsTo,
  attachedTo,
  discussedIn,
  evidencedBy,
  linkedTo,
}

enum ContextGraphScope {
  currentRoom,
  selectedFiles,
  linkedTasks,
  recentDecisions,
}

class ContextGraphEvidence {
  const ContextGraphEvidence({
    required this.id,
    required this.label,
    required this.scope,
    required this.sourceDescription,
  });

  final String id;
  final String label;
  final ContextGraphScope scope;
  final String sourceDescription;
}

class ContextGraphItem {
  const ContextGraphItem({
    required this.id,
    required this.kind,
    required this.scope,
    required this.title,
    required this.description,
    required this.includedInPreview,
    required this.evidence,
  });

  final String id;
  final ContextGraphNodeKind kind;
  final ContextGraphScope scope;
  final String title;
  final String description;
  final bool includedInPreview;
  final List<ContextGraphEvidence> evidence;

  bool get isExplainable => evidence.isNotEmpty;
}

class ContextGraphEdge {
  const ContextGraphEdge({
    required this.sourceItemId,
    required this.targetItemId,
    required this.kind,
    required this.evidenceIds,
  });

  final String sourceItemId;
  final String targetItemId;
  final ContextGraphEdgeKind kind;
  final List<String> evidenceIds;
}

class ContextPackPreview {
  const ContextPackPreview({
    required this.id,
    required this.items,
    required this.edges,
    required this.agentUseEnabled,
    required this.backgroundRoomReadingEnabled,
  });

  final String id;
  final List<ContextGraphItem> items;
  final List<ContextGraphEdge> edges;

  /// Agent use remains disabled until consent, audit, and runtime policy gates
  /// are connected by backend-owned contracts.
  final bool agentUseEnabled;

  /// Must stay false for this preview slice: context is scoped to the room and
  /// explicit selections, not hidden continuous room reading.
  final bool backgroundRoomReadingEnabled;

  Iterable<ContextGraphItem> get includedItems =>
      items.where((item) => item.includedInPreview);

  Iterable<ContextGraphItem> get availableItems =>
      items.where((item) => !item.includedInPreview);

  bool get isExplainable => items.every((item) => item.isExplainable);

  bool get isFailClosedForAgentUse =>
      !agentUseEnabled && !backgroundRoomReadingEnabled && isExplainable;
}
