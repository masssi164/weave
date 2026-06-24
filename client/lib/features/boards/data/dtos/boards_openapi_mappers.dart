import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension BoardsWorkspaceOpenApiMapper on openapi.BoardsWorkspaceResponse {
  BoardWorkspace toDomain() {
    final release = _requiredString(releaseStatus, 'Boards release status');
    final facadeSource = _requiredString(source, 'Boards workspace source');
    if (workspace != true ||
        release != 'active-dogfood-production' ||
        !_isKnownWorkspaceFacadeSource(facadeSource)) {
      throw const AppFailure.unknown(
        'The Weave backend returned a Boards workspace outside the active dogfood-production facade.',
      );
    }

    final board = _firstBoard(boards);
    final boardColumns = board.columns ?? const [];
    final taskItems = tasks ?? const [];
    return BoardWorkspace(
      id: _optionalString(board.id) ?? 'backend-board',
      name: _optionalString(board.name) ?? 'Boards workspace',
      description: _optionalString(board.description) ?? '',
      source: BoardWorkspaceSource.backendFacade,
      releaseStatus: release,
      capabilities:
          capabilities?.toDomain() ??
          const BoardProviderWorkspaceCapabilities.blocked(),
      columns: [
        for (final column in boardColumns)
          column.toDomain(
            tasks: taskItems
                .where((task) => task.columnId == column.id)
                .toList(growable: false),
          ),
      ],
    );
  }
}

extension BoardColumnOpenApiMapper on openapi.BoardColumn {
  BoardColumnWorkspace toDomain({required List<openapi.TaskItem> tasks}) {
    final status = _columnStatus(semanticStatus);
    return BoardColumnWorkspace(
      id: _optionalString(id) ?? 'backend-column',
      name: _optionalString(name) ?? 'Column',
      semanticStatus: status,
      wipLimit: wipLimit,
      tasks: [for (final task in tasks) task.toDomain(columnFallback: status)],
    );
  }
}

extension TaskItemOpenApiMapper on openapi.TaskItem {
  BoardTaskWorkspace toDomain({required BoardTaskStatus columnFallback}) {
    return BoardTaskWorkspace(
      id: _optionalString(id) ?? 'backend-task',
      title: _optionalString(title) ?? 'Untitled task',
      description: _optionalString(description) ?? '',
      status: _taskStatus(status, columnFallback),
      assigneeLabel: _labelList(assigneeRefs, fallback: 'Unassigned'),
      dueLabel: _optionalString(dueAt) ?? 'No due date',
      labels: _stringList(labelRefs),
      priorityLabel: _optionalString(priority) ?? 'normal',
    );
  }
}

extension BoardProviderCapabilitiesOpenApiMapper
    on openapi.BoardProviderCapabilities {
  BoardProviderWorkspaceCapabilities toDomain() {
    return BoardProviderWorkspaceCapabilities(
      provider: _optionalString(provider) ?? 'unknown',
      enabled: enabled == true,
      supported: _stringList(supported),
      unsupported: _stringList(unsupported),
      supportSafeSummary:
          _optionalString(supportSafeSummary) ??
          'Backend Boards workspace capabilities were not described.',
    );
  }
}

openapi.Board _firstBoard(List<openapi.Board>? boards) {
  final first = boards == null || boards.isEmpty ? null : boards.first;
  if (first == null) {
    throw const AppFailure.unknown(
      'The Weave backend returned no Boards workspace board.',
    );
  }
  return first;
}

bool _isKnownWorkspaceFacadeSource(String source) {
  return const {
    'local-workspace-backend-facade',
    'openproject-workspace-sync-backend-facade',
  }.contains(source);
}

String _requiredString(String? value, String label) {
  final trimmed = _optionalString(value);
  if (trimmed != null) {
    return trimmed;
  }
  throw AppFailure.unknown('The Weave backend returned $label as empty.');
}

String? _optionalString(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

List<String> _stringList(List<String>? value) {
  return value
          ?.map((item) => item.trim())
          .where((item) => item.isNotEmpty)
          .toList(growable: false) ??
      const [];
}

String _labelList(List<String>? value, {required String fallback}) {
  final values = _stringList(value);
  return values.isEmpty ? fallback : values.join(', ');
}

BoardTaskStatus _columnStatus(String? value) {
  return switch (value) {
    'in_progress' => BoardTaskStatus.inProgress,
    'blocked' => BoardTaskStatus.blocked,
    'done' || 'completed' => BoardTaskStatus.done,
    _ => BoardTaskStatus.notStarted,
  };
}

BoardTaskStatus _taskStatus(String? value, BoardTaskStatus columnFallback) {
  return switch (value) {
    'blocked' => BoardTaskStatus.blocked,
    'completed' || 'done' => BoardTaskStatus.done,
    'open' => columnFallback,
    _ => columnFallback,
  };
}
