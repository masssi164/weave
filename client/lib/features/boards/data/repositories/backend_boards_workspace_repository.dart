import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/boards/data/dtos/boards_openapi_mappers.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/repositories/boards_workspace_repository.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

class BackendBoardsWorkspaceRepository implements BoardsWorkspaceRepository {
  BackendBoardsWorkspaceRepository({
    required http.Client httpClient,
    required Uri apiBaseUrl,
    required String accessToken,
  }) : _api = user_api.BoardsWorkspaceApi(
         weaveUserApiClient(
           apiBaseUrl: apiBaseUrl,
           accessToken: accessToken,
           httpClient: httpClient,
         ),
       );

  final user_api.BoardsWorkspaceApi _api;

  @override
  Future<BoardWorkspace> loadWorkspace() async {
    try {
      final response = await _api.workspace().timeout(
        const Duration(seconds: 5),
      );
      if (response == null) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Boards workspace payload.',
        );
      }
      return response.toDomain();
    } on user_api.ApiException catch (error) {
      if (error.code == 503) return const BoardWorkspace.backendBlocked();
      throw AppFailure.unknown(
        'The Weave backend Boards workspace is not enabled right now.',
        cause: error.code,
      );
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach or decode the Weave backend Boards workspace.',
        cause: error,
      );
    }
  }

  Future<void> moveTask({
    required String taskId,
    required String targetColumnId,
    required int targetPosition,
  }) => _mutate(
    () => _api.moveTask(
      taskId,
      user_api.BoardsMoveTaskRequest(
        targetColumnId: targetColumnId,
        targetPosition: targetPosition,
      ),
    ),
  );

  Future<void> completeTask(String taskId) =>
      _mutate(() => _api.completeTask(taskId));

  Future<void> updateTaskStatus({
    required String taskId,
    required String status,
    String? targetColumnId,
  }) => _mutate(
    () => _api.updateTaskStatus(
      taskId,
      user_api.BoardsUpdateTaskStatusRequest(
        status: status,
        targetColumnId: targetColumnId,
      ),
    ),
  );

  Future<void> linkDecision({
    required String taskId,
    required String decisionRef,
  }) => _mutate(
    () => _api.linkDecision(
      taskId,
      user_api.BoardsLinkDecisionRequest(decisionRef: decisionRef),
    ),
  );

  Future<void> _mutate(Future<user_api.TaskItem?> Function() operation) async {
    try {
      if (await operation().timeout(const Duration(seconds: 5)) == null) {
        throw const AppFailure.unknown(
          'The Weave backend returned no Boards task after the action.',
        );
      }
    } on user_api.ApiException catch (error) {
      final code = _errorCode(error.message);
      if (error.code == 409 || code == 'boards-conflict') {
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
        cause: error.code,
      );
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend Boards workspace right now.',
        cause: error,
      );
    }
  }

  String? _errorCode(String? body) {
    try {
      final decoded = jsonDecode(body ?? '');
      return decoded is Map<String, dynamic>
          ? decoded['code'] as String?
          : null;
    } catch (_) {
      return null;
    }
  }
}
