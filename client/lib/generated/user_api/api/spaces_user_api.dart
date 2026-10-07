//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SpacesUserApi {
  SpacesUserApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Read one current member Space
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] spaceRef (required):
  Future<Response> getSpaceWithHttpInfo(
    String spaceRef,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/spaces/{spaceRef}'.replaceAll('{spaceRef}', spaceRef);

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

  /// Read one current member Space
  ///
  /// Parameters:
  ///
  /// * [String] spaceRef (required):
  Future<SpaceUserResponse?> getSpace(
    String spaceRef,
  ) async {
    final response = await getSpaceWithHttpInfo(
      spaceRef,
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
        'SpaceUserResponse',
      ) as SpaceUserResponse;
    }
    return null;
  }

  /// List current member Spaces
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] afterSpaceRef:
  ///
  /// * [int] limit:
  Future<Response> listSpacesWithHttpInfo({
    String? afterSpaceRef,
    int? limit,
  }) async {
    // ignore: prefer_const_declarations
    final path = r'/api/spaces';

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    if (afterSpaceRef != null) {
      queryParams.addAll(_queryParams('', 'afterSpaceRef', afterSpaceRef));
    }
    if (limit != null) {
      queryParams.addAll(_queryParams('', 'limit', limit));
    }

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

  /// List current member Spaces
  ///
  /// Parameters:
  ///
  /// * [String] afterSpaceRef:
  ///
  /// * [int] limit:
  Future<SpaceUserListResponse?> listSpaces({
    String? afterSpaceRef,
    int? limit,
  }) async {
    final response = await listSpacesWithHttpInfo(
      afterSpaceRef: afterSpaceRef,
      limit: limit,
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
        'SpaceUserListResponse',
      ) as SpaceUserListResponse;
    }
    return null;
  }
}
