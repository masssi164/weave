//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ConnectorsApi {
  ConnectorsApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Get internal connector runtime boundary
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> boundaryWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/connectors/boundary';

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

  /// Get internal connector runtime boundary
  Future<ConnectorBoundaryResponse?> boundary() async {
    final response = await boundaryWithHttpInfo();
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
        'ConnectorBoundaryResponse',
      ) as ConnectorBoundaryResponse;
    }
    return null;
  }

  /// Validate an internal connector manifest without accepting secret values
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [ConnectorManifestValidationRequest] connectorManifestValidationRequest (required):
  Future<Response> validateWithHttpInfo(
    ConnectorManifestValidationRequest connectorManifestValidationRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/connectors/manifest/validate';

    // ignore: prefer_final_locals
    Object? postBody = connectorManifestValidationRequest;

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

  /// Validate an internal connector manifest without accepting secret values
  ///
  /// Parameters:
  ///
  /// * [ConnectorManifestValidationRequest] connectorManifestValidationRequest (required):
  Future<ConnectorManifestValidationResponse?> validate(
    ConnectorManifestValidationRequest connectorManifestValidationRequest,
  ) async {
    final response = await validateWithHttpInfo(
      connectorManifestValidationRequest,
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
        'ConnectorManifestValidationResponse',
      ) as ConnectorManifestValidationResponse;
    }
    return null;
  }
}
