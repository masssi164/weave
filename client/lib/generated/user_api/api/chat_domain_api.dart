//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ChatDomainApi {
  ChatDomainApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Create a channel Decision Ledger record
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  ///
  /// * [DecisionLedgerCreateRequest] decisionLedgerCreateRequest (required):
  Future<Response> createDecisionWithHttpInfo(
    String conversationId,
    DecisionLedgerCreateRequest decisionLedgerCreateRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/chat/conversations/{conversationId}/decisions'
        .replaceAll('{conversationId}', conversationId);

    // ignore: prefer_final_locals
    Object? postBody = decisionLedgerCreateRequest;

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

  /// Create a channel Decision Ledger record
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  ///
  /// * [DecisionLedgerCreateRequest] decisionLedgerCreateRequest (required):
  Future<DecisionLedgerRecordResponse?> createDecision(
    String conversationId,
    DecisionLedgerCreateRequest decisionLedgerCreateRequest,
  ) async {
    final response = await createDecisionWithHttpInfo(
      conversationId,
      decisionLedgerCreateRequest,
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
        'DecisionLedgerRecordResponse',
      ) as DecisionLedgerRecordResponse;
    }
    return null;
  }

  /// Create a channel Meeting Capsule
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  ///
  /// * [MeetingCapsuleCreateRequest] meetingCapsuleCreateRequest (required):
  Future<Response> createMeetingCapsuleWithHttpInfo(
    String conversationId,
    MeetingCapsuleCreateRequest meetingCapsuleCreateRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/chat/conversations/{conversationId}/meeting-capsules'
        .replaceAll('{conversationId}', conversationId);

    // ignore: prefer_final_locals
    Object? postBody = meetingCapsuleCreateRequest;

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

  /// Create a channel Meeting Capsule
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  ///
  /// * [MeetingCapsuleCreateRequest] meetingCapsuleCreateRequest (required):
  Future<MeetingCapsuleResponse?> createMeetingCapsule(
    String conversationId,
    MeetingCapsuleCreateRequest meetingCapsuleCreateRequest,
  ) async {
    final response = await createMeetingCapsuleWithHttpInfo(
      conversationId,
      meetingCapsuleCreateRequest,
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
        'MeetingCapsuleResponse',
      ) as MeetingCapsuleResponse;
    }
    return null;
  }

  /// Read channel Decision Ledger records
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  Future<Response> decisionsWithHttpInfo(
    String conversationId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/chat/conversations/{conversationId}/decisions'
        .replaceAll('{conversationId}', conversationId);

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

  /// Read channel Decision Ledger records
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  Future<DecisionLedgerRecordsResponse?> decisions(
    String conversationId,
  ) async {
    final response = await decisionsWithHttpInfo(
      conversationId,
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
        'DecisionLedgerRecordsResponse',
      ) as DecisionLedgerRecordsResponse;
    }
    return null;
  }

  /// Read member-safe Weave Chat readiness
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getChatReadinessWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/chat/readiness';

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

  /// Read member-safe Weave Chat readiness
  Future<ChatReadiness?> getChatReadiness() async {
    final response = await getChatReadinessWithHttpInfo();
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
        'ChatReadiness',
      ) as ChatReadiness;
    }
    return null;
  }

  /// Read channel Meeting Capsules
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  Future<Response> meetingCapsulesWithHttpInfo(
    String conversationId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/chat/conversations/{conversationId}/meeting-capsules'
        .replaceAll('{conversationId}', conversationId);

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

  /// Read channel Meeting Capsules
  ///
  /// Parameters:
  ///
  /// * [String] conversationId (required):
  Future<MeetingCapsulesResponse?> meetingCapsules(
    String conversationId,
  ) async {
    final response = await meetingCapsulesWithHttpInfo(
      conversationId,
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
        'MeetingCapsulesResponse',
      ) as MeetingCapsulesResponse;
    }
    return null;
  }
}
