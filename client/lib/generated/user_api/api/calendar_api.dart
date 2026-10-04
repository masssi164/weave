//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarApi {
  CalendarApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Describe fail-closed private calendar access policy
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> accessPolicyWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/access-policy';

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

  /// Describe fail-closed private calendar access policy
  Future<void> accessPolicy() async {
    final response = await accessPolicyWithHttpInfo();
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
  }

  /// Download a signed Apple Calendar setup profile
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> appleMobileConfigProfileWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/client-setup/apple.mobileconfig';

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

  /// Download a signed Apple Calendar setup profile
  Future<void> appleMobileConfigProfile() async {
    final response = await appleMobileConfigProfileWithHttpInfo();
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
  }

  /// Describe native calendar client setup options
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> clientSetupWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/client-setup';

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

  /// Describe native calendar client setup options
  Future<CalendarClientSetupResponse?> clientSetup() async {
    final response = await clientSetupWithHttpInfo();
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
        'CalendarClientSetupResponse',
      ) as CalendarClientSetupResponse;
    }
    return null;
  }

  /// Create a revocable calendar setup credential and return its secret once
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [CalendarSetupCredentialRequest] calendarSetupCredentialRequest (required):
  Future<Response> createSetupCredentialWithHttpInfo(
    CalendarSetupCredentialRequest calendarSetupCredentialRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/client-setup/credentials';

    // ignore: prefer_final_locals
    Object? postBody = calendarSetupCredentialRequest;

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

  /// Create a revocable calendar setup credential and return its secret once
  ///
  /// Parameters:
  ///
  /// * [CalendarSetupCredentialRequest] calendarSetupCredentialRequest (required):
  Future<CalendarSetupCredentialResponse?> createSetupCredential(
    CalendarSetupCredentialRequest calendarSetupCredentialRequest,
  ) async {
    final response = await createSetupCredentialWithHttpInfo(
      calendarSetupCredentialRequest,
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
        'CalendarSetupCredentialResponse',
      ) as CalendarSetupCredentialResponse;
    }
    return null;
  }

  /// Describe native Calendar setup and sync boundary
  ///
  /// Returns support-safe iOS Calendar profile and Android Account/SyncAdapter setup metadata backed only by Weave-owned calendar facade endpoints.
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> getCalendarNativeSyncSetupWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/native-sync-setup';

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

  /// Describe native Calendar setup and sync boundary
  ///
  /// Returns support-safe iOS Calendar profile and Android Account/SyncAdapter setup metadata backed only by Weave-owned calendar facade endpoints.
  Future<CalendarNativeSyncSetupResponse?> getCalendarNativeSyncSetup() async {
    final response = await getCalendarNativeSyncSetupWithHttpInfo();
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
        'CalendarNativeSyncSetupResponse',
      ) as CalendarNativeSyncSetupResponse;
    }
    return null;
  }

  /// Revoke a calendar setup credential reference
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] credentialId (required):
  Future<Response> revokeSetupCredentialWithHttpInfo(
    String credentialId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/client-setup/credentials/{credentialId}'
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

  /// Revoke a calendar setup credential reference
  ///
  /// Parameters:
  ///
  /// * [String] credentialId (required):
  Future<CalendarSetupCredentialResponse?> revokeSetupCredential(
    String credentialId,
  ) async {
    final response = await revokeSetupCredentialWithHttpInfo(
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
        'CalendarSetupCredentialResponse',
      ) as CalendarSetupCredentialResponse;
    }
    return null;
  }

  /// List visible workspace, team, and channel calendar scopes
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> scopesWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/scopes';

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

  /// List visible workspace, team, and channel calendar scopes
  Future<CalendarScopesResponse?> scopes() async {
    final response = await scopesWithHttpInfo();
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
        'CalendarScopesResponse',
      ) as CalendarScopesResponse;
    }
    return null;
  }

  /// List revocable calendar setup credential references
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> setupCredentialsWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/client-setup/credentials';

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

  /// List revocable calendar setup credential references
  Future<CalendarSetupCredentialListResponse?> setupCredentials() async {
    final response = await setupCredentialsWithHttpInfo();
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
        'CalendarSetupCredentialListResponse',
      ) as CalendarSetupCredentialListResponse;
    }
    return null;
  }
}
