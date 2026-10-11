//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardsWorkspaceApi {
  BoardsWorkspaceApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Complete a task without drag-and-drop in the Boards/Tasks workspace
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  Future<Response> completeTaskWithHttpInfo(
    String taskId,
  ) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/boards/tasks/{taskId}/complete'.replaceAll('{taskId}', taskId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Complete a task without drag-and-drop in the Boards/Tasks workspace
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  Future<TaskItem?> completeTask(
    String taskId,
  ) async {
    final response = await completeTaskWithHttpInfo(
      taskId,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'TaskItem',
      ) as TaskItem;
    }
    return null;
  }

  /// Create a task in the Boards/Tasks workspace with user-write authorization
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] boardId (required):
  ///
  /// * [BoardsCreateTaskRequest] boardsCreateTaskRequest (required):
  Future<Response> createTaskWithHttpInfo(
    String boardId,
    BoardsCreateTaskRequest boardsCreateTaskRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/boards/{boardId}/tasks'.replaceAll('{boardId}', boardId);

    // ignore: prefer_final_locals
    Object? postBody = boardsCreateTaskRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Create a task in the Boards/Tasks workspace with user-write authorization
  ///
  /// Parameters:
  ///
  /// * [String] boardId (required):
  ///
  /// * [BoardsCreateTaskRequest] boardsCreateTaskRequest (required):
  Future<TaskItem?> createTask(
    String boardId,
    BoardsCreateTaskRequest boardsCreateTaskRequest,
  ) async {
    final response = await createTaskWithHttpInfo(
      boardId,
      boardsCreateTaskRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'TaskItem',
      ) as TaskItem;
    }
    return null;
  }

  /// Link a workspace decision to a task in the Boards/Tasks workspace
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsLinkDecisionRequest] boardsLinkDecisionRequest (required):
  Future<Response> linkDecisionWithHttpInfo(
    String taskId,
    BoardsLinkDecisionRequest boardsLinkDecisionRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/boards/tasks/{taskId}/decision-links'
        .replaceAll('{taskId}', taskId);

    // ignore: prefer_final_locals
    Object? postBody = boardsLinkDecisionRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Link a workspace decision to a task in the Boards/Tasks workspace
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsLinkDecisionRequest] boardsLinkDecisionRequest (required):
  Future<TaskItem?> linkDecision(
    String taskId,
    BoardsLinkDecisionRequest boardsLinkDecisionRequest,
  ) async {
    final response = await linkDecisionWithHttpInfo(
      taskId,
      boardsLinkDecisionRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'TaskItem',
      ) as TaskItem;
    }
    return null;
  }

  /// Move a task without drag-and-drop in the Boards/Tasks workspace
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsMoveTaskRequest] boardsMoveTaskRequest (required):
  Future<Response> moveTaskWithHttpInfo(
    String taskId,
    BoardsMoveTaskRequest boardsMoveTaskRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/boards/tasks/{taskId}/move'.replaceAll('{taskId}', taskId);

    // ignore: prefer_final_locals
    Object? postBody = boardsMoveTaskRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Move a task without drag-and-drop in the Boards/Tasks workspace
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsMoveTaskRequest] boardsMoveTaskRequest (required):
  Future<TaskItem?> moveTask(
    String taskId,
    BoardsMoveTaskRequest boardsMoveTaskRequest,
  ) async {
    final response = await moveTaskWithHttpInfo(
      taskId,
      boardsMoveTaskRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'TaskItem',
      ) as TaskItem;
    }
    return null;
  }

  /// Update task status in the Boards/Tasks workspace
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsUpdateTaskStatusRequest] boardsUpdateTaskStatusRequest (required):
  Future<Response> updateTaskStatusWithHttpInfo(
    String taskId,
    BoardsUpdateTaskStatusRequest boardsUpdateTaskStatusRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/boards/tasks/{taskId}/status'.replaceAll('{taskId}', taskId);

    // ignore: prefer_final_locals
    Object? postBody = boardsUpdateTaskStatusRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Update task status in the Boards/Tasks workspace
  ///
  /// Parameters:
  ///
  /// * [String] taskId (required):
  ///
  /// * [BoardsUpdateTaskStatusRequest] boardsUpdateTaskStatusRequest (required):
  Future<TaskItem?> updateTaskStatus(
    String taskId,
    BoardsUpdateTaskStatusRequest boardsUpdateTaskStatusRequest,
  ) async {
    final response = await updateTaskStatusWithHttpInfo(
      taskId,
      boardsUpdateTaskStatusRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'TaskItem',
      ) as TaskItem;
    }
    return null;
  }

  /// Read the Boards/Tasks workspace snapshot
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> workspaceWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/boards/workspace';

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'GET',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Read the Boards/Tasks workspace snapshot
  Future<BoardsWorkspaceResponse?> workspace() async {
    final response = await workspaceWithHttpInfo();
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'BoardsWorkspaceResponse',
      ) as BoardsWorkspaceResponse;
    }
    return null;
  }
}
