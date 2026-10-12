//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProfileApi {
  ProfileApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Get the authenticated product profile
  ///
  /// Returns the product-owned profile facade for the authenticated caller.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getProductProfileWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/profile';

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

  /// Get the authenticated product profile
  ///
  /// Returns the product-owned profile facade for the authenticated caller.
  Future<ProductProfileResponse?> getProductProfile() async {
    final response = await getProductProfileWithHttpInfo();
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
        'ProductProfileResponse',
      ) as ProductProfileResponse;
    }
    return null;
  }

  /// Get product profile facade readiness
  ///
  /// Returns support-safe readiness for the backend-owned profile facade without exposing provider credentials or upstream URLs.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getProductProfileReadinessWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/profile/readiness';

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

  /// Get product profile facade readiness
  ///
  /// Returns support-safe readiness for the backend-owned profile facade without exposing provider credentials or upstream URLs.
  Future<ProfileReadinessResponse?> getProductProfileReadiness() async {
    final response = await getProductProfileReadinessWithHttpInfo();
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
        'ProfileReadinessResponse',
      ) as ProfileReadinessResponse;
    }
    return null;
  }

  /// Get product profile module sync status
  ///
  /// Returns frontend-safe Matrix and Nextcloud profile synchronization state for the authenticated caller.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getProductProfileSyncStatusWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/profile/sync-status';

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

  /// Get product profile module sync status
  ///
  /// Returns frontend-safe Matrix and Nextcloud profile synchronization state for the authenticated caller.
  Future<ModuleSyncStatusResponse?> getProductProfileSyncStatus() async {
    final response = await getProductProfileSyncStatusWithHttpInfo();
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
        'ModuleSyncStatusResponse',
      ) as ModuleSyncStatusResponse;
    }
    return null;
  }

  /// Update the authenticated product profile
  ///
  /// Partially updates mutable product profile fields and returns the updated profile snapshot.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [UpdateProductProfileRequest] updateProductProfileRequest (required):
  Future<Response> updateProductProfileWithHttpInfo(
    UpdateProductProfileRequest updateProductProfileRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/profile';

    // ignore: prefer_final_locals
    Object? postBody = updateProductProfileRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'PATCH',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Update the authenticated product profile
  ///
  /// Partially updates mutable product profile fields and returns the updated profile snapshot.
  ///
  /// Parameters:
  ///
  /// * [UpdateProductProfileRequest] updateProductProfileRequest (required):
  Future<ProductProfileResponse?> updateProductProfile(
    UpdateProductProfileRequest updateProductProfileRequest,
  ) async {
    final response = await updateProductProfileWithHttpInfo(
      updateProductProfileRequest,
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
        'ProductProfileResponse',
      ) as ProductProfileResponse;
    }
    return null;
  }
}
