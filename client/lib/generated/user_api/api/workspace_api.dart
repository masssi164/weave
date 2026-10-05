//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceApi {
  WorkspaceApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Get workspace capability readiness
  ///
  /// Returns the backend-owned workspace capability snapshot consumed by the Weave client.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> capabilitiesWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/workspace/capabilities';

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

  /// Get workspace capability readiness
  ///
  /// Returns the backend-owned workspace capability snapshot consumed by the Weave client.
  Future<WorkspaceCapabilitiesResponse?> capabilities() async {
    final response = await capabilitiesWithHttpInfo();
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
        'WorkspaceCapabilitiesResponse',
      ) as WorkspaceCapabilitiesResponse;
    }
    return null;
  }

  /// Get workspace capability policy
  ///
  /// Returns an admin/operator support-safe snapshot of IDM role/group intake, profile mapping, deny-by-default posture, and Weaver-disabled-by-default policy state.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> capabilityPolicyWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/workspace/capability-policy';

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

  /// Get workspace capability policy
  ///
  /// Returns an admin/operator support-safe snapshot of IDM role/group intake, profile mapping, deny-by-default posture, and Weaver-disabled-by-default policy state.
  Future<WorkspaceCapabilityPolicyResponse?> capabilityPolicy() async {
    final response = await capabilityPolicyWithHttpInfo();
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
        'WorkspaceCapabilityPolicyResponse',
      ) as WorkspaceCapabilityPolicyResponse;
    }
    return null;
  }

  /// Get Weave Home daily-work snapshot
  ///
  /// Returns the backend-owned, support-safe daily work loop consumed by Weave Home.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> homeWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/workspace/home';

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

  /// Get Weave Home daily-work snapshot
  ///
  /// Returns the backend-owned, support-safe daily work loop consumed by Weave Home.
  Future<WorkspaceHomeResponse?> home() async {
    final response = await homeWithHttpInfo();
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
        'WorkspaceHomeResponse',
      ) as WorkspaceHomeResponse;
    }
    return null;
  }

  /// Get authenticated organization manifest
  ///
  /// Returns the support-safe org manifest consumed by Weave Client after org URL discovery and SSO. Provider setup, endpoint rotation, diagnostics, policy authoring, and whitelisting remain owned by the Organization/Admin Console.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> organizationManifestWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/organization/manifest';

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

  /// Get authenticated organization manifest
  ///
  /// Returns the support-safe org manifest consumed by Weave Client after org URL discovery and SSO. Provider setup, endpoint rotation, diagnostics, policy authoring, and whitelisting remain owned by the Organization/Admin Console.
  Future<OrganizationManifestResponse?> organizationManifest() async {
    final response = await organizationManifestWithHttpInfo();
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
        'OrganizationManifestResponse',
      ) as OrganizationManifestResponse;
    }
    return null;
  }

  /// Get workspace readiness
  ///
  /// Returns an operator-facing snapshot of the backend-owned core dependencies and remaining setup actions.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> releaseReadinessWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/workspace/release-readiness';

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

  /// Get workspace readiness
  ///
  /// Returns an operator-facing snapshot of the backend-owned core dependencies and remaining setup actions.
  Future<WorkspaceReleaseReadinessResponse?> releaseReadiness() async {
    final response = await releaseReadinessWithHttpInfo();
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
        'WorkspaceReleaseReadinessResponse',
      ) as WorkspaceReleaseReadinessResponse;
    }
    return null;
  }
}
