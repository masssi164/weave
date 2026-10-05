//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FilesApi {
  FilesApi([ApiClient? apiClient]) : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Create a revocable Files WebDAV setup credential and return its secret once
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [FileSetupCredentialRequest] fileSetupCredentialRequest (required):
  Future<Response> createFilesSetupCredentialWithHttpInfo(
    FileSetupCredentialRequest fileSetupCredentialRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/client-setup/credentials';

    // ignore: prefer_final_locals
    Object? postBody = fileSetupCredentialRequest;

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

  /// Create a revocable Files WebDAV setup credential and return its secret once
  ///
  /// Parameters:
  ///
  /// * [FileSetupCredentialRequest] fileSetupCredentialRequest (required):
  Future<FileSetupCredentialResponse?> createFilesSetupCredential(
    FileSetupCredentialRequest fileSetupCredentialRequest,
  ) async {
    final response = await createFilesSetupCredentialWithHttpInfo(
      fileSetupCredentialRequest,
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
        'FileSetupCredentialResponse',
      ) as FileSetupCredentialResponse;
    }
    return null;
  }

  /// Describe native Files provider setup
  ///
  /// Returns support-safe iOS File Provider and Android DocumentsProvider setup metadata backed only by Weave-owned file facade endpoints.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getFilesNativeProviderSetupWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/native-provider-setup';

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

  /// Describe native Files provider setup
  ///
  /// Returns support-safe iOS File Provider and Android DocumentsProvider setup metadata backed only by Weave-owned file facade endpoints.
  Future<FileNativeProviderSetupResponse?> getFilesNativeProviderSetup() async {
    final response = await getFilesNativeProviderSetupWithHttpInfo();
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
        'FileNativeProviderSetupResponse',
      ) as FileNativeProviderSetupResponse;
    }
    return null;
  }

  /// Get Files readiness
  ///
  /// Returns the member-safe, provider-neutral Files capability readiness derived from the canonical workspace capability snapshot.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getFilesReadinessWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/readiness';

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

  /// Get Files readiness
  ///
  /// Returns the member-safe, provider-neutral Files capability readiness derived from the canonical workspace capability snapshot.
  Future<WorkspaceCapabilityStatusResponse?> getFilesReadiness() async {
    final response = await getFilesReadinessWithHttpInfo();
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
        'WorkspaceCapabilityStatusResponse',
      ) as WorkspaceCapabilityStatusResponse;
    }
    return null;
  }

  /// List revocable Files WebDAV setup credential references
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getFilesSetupCredentialsWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/client-setup/credentials';

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

  /// List revocable Files WebDAV setup credential references
  Future<FileSetupCredentialListResponse?> getFilesSetupCredentials() async {
    final response = await getFilesSetupCredentialsWithHttpInfo();
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
        'FileSetupCredentialListResponse',
      ) as FileSetupCredentialListResponse;
    }
    return null;
  }

  /// Revoke a Files WebDAV setup credential reference
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] credentialId (required):
  Future<Response> revokeFilesSetupCredentialWithHttpInfo(
    String credentialId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/client-setup/credentials/{credentialId}'
        .replaceAll('{credentialId}', credentialId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'DELETE',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Revoke a Files WebDAV setup credential reference
  ///
  /// Parameters:
  ///
  /// * [String] credentialId (required):
  Future<FileSetupCredentialResponse?> revokeFilesSetupCredential(
    String credentialId,
  ) async {
    final response = await revokeFilesSetupCredentialWithHttpInfo(
      credentialId,
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
        'FileSetupCredentialResponse',
      ) as FileSetupCredentialResponse;
    }
    return null;
  }
}
