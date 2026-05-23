enum BoardTaskStatus { notStarted, inProgress, blocked, done }

enum BoardWorkspaceSource { staticFixture, backendFacade, backendBlocked }

class BoardProviderWorkspaceCapabilities {
  const BoardProviderWorkspaceCapabilities({
    required this.provider,
    required this.enabled,
    required this.supported,
    required this.unsupported,
    required this.supportSafeSummary,
  });

  const BoardProviderWorkspaceCapabilities.staticWorkspace()
    : provider = 'none',
      enabled = false,
      supported = const ['accessible_non_drag_moves'],
      unsupported = const [
        'comments',
        'attachments',
        'non_destructive_archive',
        'webhook_events',
        'incremental_sync',
        'checklists',
        'custom_fields',
      ],
      supportSafeSummary =
          'Static provider-neutral workspace fixture; no backend provider runtime is connected.';

  const BoardProviderWorkspaceCapabilities.blocked()
    : provider = 'unavailable',
      enabled = false,
      supported = const [],
      unsupported = const [
        'comments',
        'attachments',
        'non_destructive_archive',
        'webhook_events',
        'incremental_sync',
        'checklists',
        'custom_fields',
        'accessible_non_drag_moves',
      ],
      supportSafeSummary =
          'Backend Boards workspace runtime is disabled or unavailable.';

  final String provider;
  final bool enabled;
  final List<String> supported;
  final List<String> unsupported;
  final String supportSafeSummary;

  bool get supportsAccessibleNonDragMoves =>
      supported.contains('accessible_non_drag_moves') &&
      !unsupported.contains('accessible_non_drag_moves');
}

class BoardWorkspace {
  const BoardWorkspace({
    required this.id,
    required this.name,
    required this.description,
    required this.columns,
    this.source = BoardWorkspaceSource.staticFixture,
    this.releaseStatus = 'active-dogfood-production',
    this.capabilities =
        const BoardProviderWorkspaceCapabilities.staticWorkspace(),
  });

  const BoardWorkspace.backendBlocked()
    : id = 'boards-backend-blocked',
      name = 'Boards backend facade unavailable',
      description =
          'The authenticated backend facade is not enabled for this workspace. Boards remain feature-gated and no provider task data is shown.',
      columns = const [],
      source = BoardWorkspaceSource.backendBlocked,
      releaseStatus = 'active-dogfood-production',
      capabilities = const BoardProviderWorkspaceCapabilities.blocked();

  final String id;
  final String name;
  final String description;
  final List<BoardColumnWorkspace> columns;
  final BoardWorkspaceSource source;
  final String releaseStatus;
  final BoardProviderWorkspaceCapabilities capabilities;

  int get taskCount =>
      columns.fold<int>(0, (total, column) => total + column.tasks.length);

  bool get isBackendFed => source == BoardWorkspaceSource.backendFacade;

  bool get isBackendBlocked => source == BoardWorkspaceSource.backendBlocked;

  bool get canUseBackendNonDragActions =>
      isBackendFed &&
      capabilities.enabled &&
      capabilities.supportsAccessibleNonDragMoves;
}

class BoardColumnWorkspace {
  const BoardColumnWorkspace({
    required this.id,
    required this.name,
    required this.semanticStatus,
    required this.tasks,
    this.wipLimit,
  });

  final String id;
  final String name;
  final BoardTaskStatus semanticStatus;
  final List<BoardTaskWorkspace> tasks;
  final int? wipLimit;
}

class BoardTaskWorkspace {
  const BoardTaskWorkspace({
    required this.id,
    required this.title,
    required this.description,
    required this.status,
    required this.assigneeLabel,
    required this.dueLabel,
    required this.labels,
    required this.priorityLabel,
  });

  final String id;
  final String title;
  final String description;
  final BoardTaskStatus status;
  final String assigneeLabel;
  final String dueLabel;
  final List<String> labels;
  final String priorityLabel;
}
