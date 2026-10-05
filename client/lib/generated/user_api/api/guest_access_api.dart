//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class GuestAccessApi {
  GuestAccessApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Get guest identity and policy contract
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> contractWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/guest/access-contract';

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

  /// Get guest identity and policy contract
  Future<GuestAccessContractResponse?> contract() async {
    final response = await contractWithHttpInfo();
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
        'GuestAccessContractResponse',
      ) as GuestAccessContractResponse;
    }
    return null;
  }

  /// Create a guest invitation when guest access is enabled
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [GuestInvitationRequest] guestInvitationRequest (required):
  Future<Response> inviteWithHttpInfo(
    GuestInvitationRequest guestInvitationRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/guest/invitations';

    // ignore: prefer_final_locals
    Object? postBody = guestInvitationRequest;

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

  /// Create a guest invitation when guest access is enabled
  ///
  /// Parameters:
  ///
  /// * [GuestInvitationRequest] guestInvitationRequest (required):
  Future<void> invite(
    GuestInvitationRequest guestInvitationRequest,
  ) async {
    final response = await inviteWithHttpInfo(
      guestInvitationRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
  }
}
