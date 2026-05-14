import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/boards/domain/entities/board_preview.dart';
import 'package:weave/features/boards/domain/repositories/boards_preview_repository.dart';

class BackendBoardsPreviewRepository implements BoardsPreviewRepository {
  BackendBoardsPreviewRepository({
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
  Future<BoardPreview> loadPreview() async {
    late http.Response response;
    try {
      response = await _httpClient
          .get(
            _boardsPreviewUri(),
            headers: {
              'Accept': 'application/json',
              'Authorization': 'Bearer $_accessToken',
            },
          )
          .timeout(const Duration(seconds: 5));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend Boards preview right now.',
        cause: error,
      );
    }

    if (response.statusCode != 200) {
      throw AppFailure.unknown(
        'The Weave backend Boards preview is not enabled right now.',
        cause: response.statusCode,
      );
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Boards preview payload.',
        );
      }
      return _parsePreview(payload);
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode Boards preview data from the Weave backend.',
        cause: error,
      );
    }
  }

  BoardPreview _parsePreview(Map<String, dynamic> payload) {
    final boards = _listOfMaps(payload['boards']);
    final tasks = _listOfMaps(payload['tasks']);
    if (boards.isEmpty) {
      throw const AppFailure.unknown(
        'The Weave backend returned no Boards preview board.',
      );
    }

    final board = boards.first;
    final columns = _listOfMaps(board['columns']);
    return BoardPreview(
      id: _string(board['id'], fallback: 'backend-board'),
      name: _string(board['name'], fallback: 'Boards preview'),
      description: _string(board['description']),
      columns: [
        for (final column in columns)
          BoardColumnPreview(
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
                BoardTaskPreview(
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

  Uri _boardsPreviewUri() {
    final baseSegments = _apiBaseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    final apiSegments = baseSegments.isNotEmpty && baseSegments.last == 'api'
        ? [...baseSegments, 'boards', 'preview']
        : [...baseSegments, 'api', 'boards', 'preview'];
    return _apiBaseUrl.replace(pathSegments: apiSegments);
  }
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
