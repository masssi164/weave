import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/boards/data/dtos/boards_openapi_mappers.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/repositories/boards_workspace_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

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
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Boards workspace payload.',
        );
      }
      return openapi.BoardsWorkspaceResponse.fromJson(decoded).toDomain();
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode Boards workspace data from the Weave backend.',
        cause: error,
      );
    }
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
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    final code = _errorCode(response.body);
    if (response.statusCode == 409 || code == 'boards-conflict') {
      throw AppFailure.validation(
        'Task changed somewhere else. Refresh the board and try the action again.',
        cause: code ?? 'boards-conflict',
      );
    }
    if (code == 'boards-unsupported_capability') {
      throw AppFailure.validation(
        'This board provider cannot apply that action yet. Use a supported move or ask an admin to check provider readiness.',
        cause: code,
      );
    }
    throw AppFailure.unknown(
      'The Weave backend did not accept the Boards workspace task action.',
      cause: response.statusCode,
    );
  }

  String? _errorCode(String body) {
    try {
      final decoded = jsonDecode(body);
      return decoded is Map<String, dynamic>
          ? decoded['code'] as String?
          : null;
    } catch (_) {
      return null;
    }
  }

  Uri _boardsWorkspaceUri() =>
      weaveApiUri(_apiBaseUrl, const ['api', 'boards', 'workspace']);

  Uri _taskMoveUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'move']);

  Uri _taskCompleteUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'complete']);

  Uri _taskStatusUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'status']);

  Uri _taskDecisionLinksUri(String taskId) =>
      _apiUri(['boards', 'tasks', taskId, 'decision-links']);

  Uri _apiUri(List<String> tailSegments) =>
      weaveApiUri(_apiBaseUrl, ['api', ...tailSegments]);
}
