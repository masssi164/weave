//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class InteropApi {
  InteropApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Get support-safe interop gateway status
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getInteropStatusWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/status';

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

  /// Get support-safe interop gateway status
  Future<InteropStatusResponse?> getInteropStatus() async {
    final response = await getInteropStatusWithHttpInfo();
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
        'InteropStatusResponse',
      ) as InteropStatusResponse;
    }
    return null;
  }

  /// Convert a Slack text event into a canonical bridge event in sandbox mode
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] body (required):
  ///
  /// * [String] xSlackRequestTimestamp:
  ///
  /// * [String] xSlackSignature:
  Future<Response> slackEventWithHttpInfo(
    String body, {
    String? xSlackRequestTimestamp,
    String? xSlackSignature,
  }) async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/slack/events';

    // ignore: prefer_final_locals
    Object? postBody = body;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    if (xSlackRequestTimestamp != null) {
      headerParams[r'X-Slack-Request-Timestamp'] =
          parameterToString(xSlackRequestTimestamp);
    }
    if (xSlackSignature != null) {
      headerParams[r'X-Slack-Signature'] = parameterToString(xSlackSignature);
    }

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

  /// Convert a Slack text event into a canonical bridge event in sandbox mode
  ///
  /// Parameters:
  ///
  /// * [String] body (required):
  ///
  /// * [String] xSlackRequestTimestamp:
  ///
  /// * [String] xSlackSignature:
  Future<CanonicalBridgeEventResponse?> slackEvent(
    String body, {
    String? xSlackRequestTimestamp,
    String? xSlackSignature,
  }) async {
    final response = await slackEventWithHttpInfo(
      body,
      xSlackRequestTimestamp: xSlackRequestTimestamp,
      xSlackSignature: xSlackSignature,
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
        'CanonicalBridgeEventResponse',
      ) as CanonicalBridgeEventResponse;
    }
    return null;
  }

  /// Map a Weave text message to the Slack sandbox outbound adapter
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [SlackOutboundMessageRequest] slackOutboundMessageRequest (required):
  Future<Response> slackMessageWithHttpInfo(
    SlackOutboundMessageRequest slackOutboundMessageRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/slack/messages';

    // ignore: prefer_final_locals
    Object? postBody = slackOutboundMessageRequest;

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

  /// Map a Weave text message to the Slack sandbox outbound adapter
  ///
  /// Parameters:
  ///
  /// * [SlackOutboundMessageRequest] slackOutboundMessageRequest (required):
  Future<SlackOutboundMessageResponse?> slackMessage(
    SlackOutboundMessageRequest slackOutboundMessageRequest,
  ) async {
    final response = await slackMessageWithHttpInfo(
      slackOutboundMessageRequest,
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
        'SlackOutboundMessageResponse',
      ) as SlackOutboundMessageResponse;
    }
    return null;
  }

  /// Accept a Slack OAuth callback skeleton without token exchange
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [SlackOAuthCallbackRequest] slackOAuthCallbackRequest (required):
  Future<Response> slackOAuthCallbackWithHttpInfo(
    SlackOAuthCallbackRequest slackOAuthCallbackRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/slack/oauth/callback';

    // ignore: prefer_final_locals
    Object? postBody = slackOAuthCallbackRequest;

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

  /// Accept a Slack OAuth callback skeleton without token exchange
  ///
  /// Parameters:
  ///
  /// * [SlackOAuthCallbackRequest] slackOAuthCallbackRequest (required):
  Future<SlackOAuthCallbackResponse?> slackOAuthCallback(
    SlackOAuthCallbackRequest slackOAuthCallbackRequest,
  ) async {
    final response = await slackOAuthCallbackWithHttpInfo(
      slackOAuthCallbackRequest,
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
        'SlackOAuthCallbackResponse',
      ) as SlackOAuthCallbackResponse;
    }
    return null;
  }

  /// Get Slack one-channel on-ramp readiness
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> slackStatusWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/slack/status';

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

  /// Get Slack one-channel on-ramp readiness
  Future<SlackStatusResponse?> slackStatus() async {
    final response = await slackStatusWithHttpInfo();
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
        'SlackStatusResponse',
      ) as SlackStatusResponse;
    }
    return null;
  }

  /// Get the Teams gated bridge contract
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> teamsContractWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/interop/teams/contract';

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

  /// Get the Teams gated bridge contract
  Future<TeamsContractResponse?> teamsContract() async {
    final response = await teamsContractWithHttpInfo();
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
        'TeamsContractResponse',
      ) as TeamsContractResponse;
    }
    return null;
  }
}
