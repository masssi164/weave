import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/repositories/boards_workspace_repository.dart';

class BackendBoardsWorkspaceRepository implements BoardsWorkspaceRepository {
  BackendBoardsWorkspaceRepository({
    required http.Client httpClient,
    required Uri apiBaseUrl,
    required String accessToken,
  }) : _httpClient = httpClient,
       _apiBaseUrl = apiBaseUrl,
       _accessToken = accessToken;

  final http.Client _httpClient;
  final Uri _apiBaseUrl;
  final String _accessToken;

  @override
  Future<BoardWorkspace> loadWorkspace() async {
    late http.Response response;
    try {
      response = await _httpClient
          .get(
            _boardsWorkspaceUri(),
            headers: {
              'Accept': 'application/json',
              'Authorization': 'Bearer $_accessToken',
            },
          )
          .timeout(const Duration(seconds: 5));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend Boards workspace right now.',
        cause: error,
      );
    }

    if (response.statusCode != 200) {
      if (response.statusCode == 503) {
        return const BoardWorkspace.backendBlocked();
      }
      throw AppFailure.unknown(
        'The Weave backend Boards workspace is not enabled right now.',
        cause: response.statusCode,
      );
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Boards workspace payload.',
        );
      }
      return _parseWorkspace(payload);
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode Boards workspace data from the Weave backend.',
        cause: error,
      );
    }
  }

  BoardWorkspace _parseWorkspace(Map<String, dynamic> payload) {
    final workspace = payload['workspace'];
    final releaseStatus = _string(payload['releaseStatus']);
    final source = _string(payload['source']);
    if (workspace != true ||
        releaseStatus != 'active-dogfood-production' ||
        source != 'local-workspace-backend-facade') {
      throw const AppFailure.unknown(
        'The Weave backend returned a Boards workspace outside the active dogfood-production facade.',
      );
    }

    final boards = _listOfMaps(payload['boards']);
    final tasks = _listOfMaps(payload['tasks']);
    if (boards.isEmpty) {
      throw const AppFailure.unknown(
        'The Weave backend returned no Boards workspace board.',
      );
    }

    final board = boards.first;
    final columns = _listOfMaps(board['columns']);
    return BoardWorkspace(
      id: _string(board['id'], fallback: 'backend-board'),
      name: _string(board['name'], fallback: 'Boards workspace'),
      description: _string(board['description']),
      source: BoardWorkspaceSource.backendFacade,
      releaseStatus: releaseStatus,
      capabilities: _capabilities(payload['capabilities']),
      columns: [
        for (final column in columns)
          BoardColumnWorkspace(
            id: _string(column['id'], fallback: 'backend-column'),
            name: _string(column['name'], fallback: 'Column'),
            semanticStatus: _columnStatus(_string(column['semanticStatus'])),
            wipLimit: column['wipLimit'] is int
                ? column['wipLimit'] as int
                : null,
            tasks: [
              for (final task in tasks.where(
                (task) => task['columnId'] == column['id'],
              ))
                BoardTaskWorkspace(
                  id: _string(task['id'], fallback: 'backend-task'),
                  title: _string(task['title'], fallback: 'Untitled task'),
                  description: _string(task['description']),
                  status: _taskStatus(
                    _string(task['status']),
                    _columnStatus(_string(column['semanticStatus'])),
                  ),
                  assigneeLabel: _labelList(
                    task['assigneeRefs'],
                    fallback: 'Unassigned',
                  ),
                  dueLabel: _string(task['dueAt'], fallback: 'No due date'),
                  labels: _stringList(task['labelRefs']),
                  priorityLabel: _string(task['priority'], fallback: 'normal'),
                ),
            ],
          ),
      ],
    );
  }

  Future<void> moveTask({
    required String taskId,
    required String targetColumnId,
    required int targetPosition,
  }) async {
    final response = await _postJson(
      _taskMoveUri(taskId),
      body: {
        'targetColumnId': targetColumnId,
        'targetPosition': targetPosition,
      },
    );
    _requireMutationSuccess(response);
  }

  Future<void> completeTask(String taskId) async {
    final response = await _postJson(_taskCompleteUri(taskId));
    _requireMutationSuccess(response);
  }

  Future<void> updateTaskStatus({
    required String taskId,
    required String status,
    String? targetColumnId,
  }) async {
    final response = await _postJson(
      _taskStatusUri(taskId),
      body: {
        'status': status,
        if (targetColumnId != null) 'targetColumnId': targetColumnId,
      },
    );
    _requireMutationSuccess(response);
  }

  Future<void> linkDecision({
    required String taskId,
    required String decisionRef,
  }) async {
    final response = await _postJson(
      _taskDecisionLinksUri(taskId),
      body: {'decisionRef': decisionRef},
    );
    _requireMutationSuccess(response);
  }

  Future<http.Response> _postJson(Uri uri, {Map<String, Object?>? body}) async {
    try {
      return await _httpClient
          .post(
            uri,
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $_accessToken',
            },
            body: body == null ? null : jsonEncode(body),
          )
          .timeout(const Duration(seconds: 5));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend Boards workspace right now.',
        cause: error,
      );
    }
  }

  void _requireMutationSuccess(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AppFailure.unknown(
        'The Weave backend did not accept the Boards workspace task action.',
        cause: response.statusCode,
      );
    }
  }

  Uri _boardsWorkspaceUri() {
    final baseSegments = _apiBaseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    final apiSegments = baseSegments.isNotEmpty && baseSegments.last == 'api'
        ? [...baseSegments, 'boards', 'workspace']
        : [...baseSegments, 'api', 'boards', 'workspace'];
    return _apiBaseUrl.replace(pathSegments: apiSegments);
  }

  Uri _taskMoveUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'move']);

  Uri _taskCompleteUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'complete']);

  Uri _taskStatusUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'status']);

  Uri _taskDecisionLinksUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'decision-links']);

  Uri _apiUri(List<String> tailSegments) {
    final baseSegments = _apiBaseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    final apiSegments = baseSegments.isNotEmpty && baseSegments.last == 'api'
        ? [...baseSegments, ...tailSegments]
        : [...baseSegments, 'api', ...tailSegments];
    return _apiBaseUrl.replace(pathSegments: apiSegments);
  }
}

BoardProviderWorkspaceCapabilities _capabilities(Object? value) {
  if (value is! Map) {
    return const BoardProviderWorkspaceCapabilities.blocked();
  }
  final json = value.cast<String, dynamic>();
  return BoardProviderWorkspaceCapabilities(
    provider: _string(json['provider'], fallback: 'unknown'),
    enabled: json['enabled'] == true,
    supported: _stringList(json['supported']),
    unsupported: _stringList(json['unsupported']),
    supportSafeSummary: _string(
      json['supportSafeSummary'],
      fallback: 'Backend Boards workspace capabilities were not described.',
    ),
  );
}

List<Map<String, dynamic>> _listOfMaps(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<Map>()
      .map((item) => item.cast<String, dynamic>())
      .toList(growable: false);
}

String _string(Object? value, {String fallback = ''}) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  return fallback;
}

List<String> _stringList(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<String>()
      .map((item) => item.trim())
      .where((item) => item.isNotEmpty)
      .toList(growable: false);
}

String _labelList(Object? value, {required String fallback}) {
  final values = _stringList(value);
  return values.isEmpty ? fallback : values.join(', ');
}

BoardTaskStatus _columnStatus(String value) {
  return switch (value) {
    'in_progress' => BoardTaskStatus.inProgress,
    'blocked' => BoardTaskStatus.blocked,
    'done' || 'completed' => BoardTaskStatus.done,
    _ => BoardTaskStatus.notStarted,
  };
}

BoardTaskStatus _taskStatus(String value, BoardTaskStatus columnFallback) {
  return switch (value) {
    'blocked' => BoardTaskStatus.blocked,
    'completed' || 'done' => BoardTaskStatus.done,
    'open' => columnFallback,
    _ => columnFallback,
  };
}
