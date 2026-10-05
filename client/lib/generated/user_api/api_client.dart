//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ApiClient {
  ApiClient({
    this.basePath = 'http://localhost',
    this.authentication,
  });

  final String basePath;
  final Authentication? authentication;

  var _client = Client();
  final _defaultHeaderMap = <String, String>{};

  /// Returns the current HTTP [Client] instance to use in this class.
  ///
  /// The return value is guaranteed to never be null.
  Client get client => _client;

  /// Requests to use a new HTTP [Client] in this class.
  set client(Client newClient) {
    _client = newClient;
  }

  Map<String, String> get defaultHeaderMap => _defaultHeaderMap;

  void addDefaultHeader(String key, String value) {
    _defaultHeaderMap[key] = value;
  }

  // We don't use a Map<String, String> for queryParams.
  // If collectionFormat is 'multi', a key might appear multiple times.
  Future<Response> invokeAPI(
    String path,
    String method,
    List<QueryParam> queryParams,
    Object? body,
    Map<String, String> headerParams,
    Map<String, String> formParams,
    String? contentType,
  ) async {
    await authentication?.applyToParams(queryParams, headerParams);

    headerParams.addAll(_defaultHeaderMap);
    if (contentType != null) {
      headerParams['Content-Type'] = contentType;
    }

    final urlEncodedQueryParams = queryParams.map((param) => '$param');
    final queryString = urlEncodedQueryParams.isNotEmpty
        ? '?${urlEncodedQueryParams.join('&')}'
        : '';
    final uri = Uri.parse('$basePath$path$queryString');

    try {
      // Special case for uploading a single file which isn't a 'multipart/form-data'.
      if (body is MultipartFile &&
          (contentType == null ||
              !contentType.toLowerCase().startsWith('multipart/form-data'))) {
        final request = StreamedRequest(method, uri);
        request.headers.addAll(headerParams);
        request.contentLength = body.length;
        body.finalize().listen(
          request.sink.add,
          onDone: request.sink.close,
          // ignore: avoid_types_on_closure_parameters
          onError: (Object error, StackTrace trace) {
            request.sink.addError(error, trace);
            unawaited(request.sink.close());
          },
          cancelOnError: true,
        );
        final response = await _client.send(request);
        return Response.fromStream(response);
      }

      if (body is MultipartRequest) {
        final request = MultipartRequest(method, uri);
        request.fields.addAll(body.fields);
        request.files.addAll(body.files);
        request.headers.addAll(body.headers);
        request.headers.addAll(headerParams);
        final response = await _client.send(request);
        return Response.fromStream(response);
      }

      final msgBody = contentType == 'application/x-www-form-urlencoded'
          ? formParams
          : await serializeAsync(body);
      final nullableHeaderParams = headerParams.isEmpty ? null : headerParams;

      switch (method) {
        case 'POST':
          return await _client.post(
            uri,
            headers: nullableHeaderParams,
            body: msgBody,
          );
        case 'PUT':
          return await _client.put(
            uri,
            headers: nullableHeaderParams,
            body: msgBody,
          );
        case 'DELETE':
          return await _client.delete(
            uri,
            headers: nullableHeaderParams,
            body: msgBody,
          );
        case 'PATCH':
          return await _client.patch(
            uri,
            headers: nullableHeaderParams,
            body: msgBody,
          );
        case 'HEAD':
          return await _client.head(
            uri,
            headers: nullableHeaderParams,
          );
        case 'GET':
          return await _client.get(
            uri,
            headers: nullableHeaderParams,
          );
      }
    } on SocketException catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.badRequest,
        'Socket operation failed: $method $path',
        error,
        trace,
      );
    } on TlsException catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.badRequest,
        'TLS/SSL communication failed: $method $path',
        error,
        trace,
      );
    } on IOException catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.badRequest,
        'I/O operation failed: $method $path',
        error,
        trace,
      );
    } on ClientException catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.badRequest,
        'HTTP connection failed: $method $path',
        error,
        trace,
      );
    } on Exception catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.badRequest,
        'Exception occurred: $method $path',
        error,
        trace,
      );
    }

    throw ApiException(
      HttpStatus.badRequest,
      'Invalid HTTP operation: $method $path',
    );
  }

  Future<dynamic> deserializeAsync(
    String value,
    String targetType, {
    bool growable = false,
  }) async =>
      // ignore: deprecated_member_use_from_same_package
      deserialize(value, targetType, growable: growable);

  @Deprecated(
      'Scheduled for removal in OpenAPI Generator 6.x. Use deserializeAsync() instead.')
  dynamic deserialize(
    String value,
    String targetType, {
    bool growable = false,
  }) {
    // Remove all spaces. Necessary for regular expressions as well.
    targetType =
        targetType.replaceAll(' ', ''); // ignore: parameter_assignments

    // If the expected target type is String, nothing to do...
    return targetType == 'String'
        ? value
        : fromJson(json.decode(value), targetType, growable: growable);
  }

  // ignore: deprecated_member_use_from_same_package
  Future<String> serializeAsync(Object? value) async => serialize(value);

  @Deprecated(
      'Scheduled for removal in OpenAPI Generator 6.x. Use serializeAsync() instead.')
  String serialize(Object? value) => value == null ? '' : json.encode(value);

  /// Returns a native instance of an OpenAPI class matching the [specified type][targetType].
  static dynamic fromJson(
    dynamic value,
    String targetType, {
    bool growable = false,
  }) {
    try {
      switch (targetType) {
        case 'String':
          return value is String ? value : value.toString();
        case 'int':
          return value is int ? value : int.parse('$value');
        case 'double':
          return value is double ? value : double.parse('$value');
        case 'bool':
          if (value is bool) {
            return value;
          }
          final valueString = '$value'.toLowerCase();
          return valueString == 'true' || valueString == '1';
        case 'DateTime':
          return value is DateTime ? value : DateTime.tryParse(value);
        case 'ApiErrorResponse':
          return ApiErrorResponse.fromJson(value);
        case 'AuthenticatedUserResponse':
          return AuthenticatedUserResponse.fromJson(value);
        case 'Board':
          return Board.fromJson(value);
        case 'BoardColumn':
          return BoardColumn.fromJson(value);
        case 'BoardProviderCapabilities':
          return BoardProviderCapabilities.fromJson(value);
        case 'BoardsCreateTaskRequest':
          return BoardsCreateTaskRequest.fromJson(value);
        case 'BoardsLinkDecisionRequest':
          return BoardsLinkDecisionRequest.fromJson(value);
        case 'BoardsMoveTaskRequest':
          return BoardsMoveTaskRequest.fromJson(value);
        case 'BoardsSyncMetadataResponse':
          return BoardsSyncMetadataResponse.fromJson(value);
        case 'BoardsUpdateTaskStatusRequest':
          return BoardsUpdateTaskStatusRequest.fromJson(value);
        case 'BoardsWorkspaceResponse':
          return BoardsWorkspaceResponse.fromJson(value);
        case 'CalendarAccessModelResponse':
          return CalendarAccessModelResponse.fromJson(value);
        case 'CalendarClientSetupOptionResponse':
          return CalendarClientSetupOptionResponse.fromJson(value);
        case 'CalendarClientSetupResponse':
          return CalendarClientSetupResponse.fromJson(value);
        case 'CalendarCredentialReadinessResponse':
          return CalendarCredentialReadinessResponse.fromJson(value);
        case 'CalendarExternalEndpointsResponse':
          return CalendarExternalEndpointsResponse.fromJson(value);
        case 'CalendarNativeSyncOptionResponse':
          return CalendarNativeSyncOptionResponse.fromJson(value);
        case 'CalendarNativeSyncSetupResponse':
          return CalendarNativeSyncSetupResponse.fromJson(value);
        case 'CalendarScopeResponse':
          return CalendarScopeResponse.fromJson(value);
        case 'CalendarScopesResponse':
          return CalendarScopesResponse.fromJson(value);
        case 'CalendarSetupCredentialListResponse':
          return CalendarSetupCredentialListResponse.fromJson(value);
        case 'CalendarSetupCredentialRequest':
          return CalendarSetupCredentialRequest.fromJson(value);
        case 'CalendarSetupCredentialResponse':
          return CalendarSetupCredentialResponse.fromJson(value);
        case 'ChatHistoryPolicy':
          return ChatHistoryPolicy.fromJson(value);
        case 'ChatProviderMappingRecord':
          return ChatProviderMappingRecord.fromJson(value);
        case 'ChatReadiness':
          return ChatReadiness.fromJson(value);
        case 'ClientAccessCredentialLifecycleResponse':
          return ClientAccessCredentialLifecycleResponse.fromJson(value);
        case 'ClientAccessDiscoveryResponse':
          return ClientAccessDiscoveryResponse.fromJson(value);
        case 'ClientAccessProtocolSurfaceResponse':
          return ClientAccessProtocolSurfaceResponse.fromJson(value);
        case 'ConnectorBoundaryResponse':
          return ConnectorBoundaryResponse.fromJson(value);
        case 'ConnectorManifestValidationRequest':
          return ConnectorManifestValidationRequest.fromJson(value);
        case 'ConnectorManifestValidationResponse':
          return ConnectorManifestValidationResponse.fromJson(value);
        case 'DecisionLedgerCreateRequest':
          return DecisionLedgerCreateRequest.fromJson(value);
        case 'DecisionLedgerEvidencePostureResponse':
          return DecisionLedgerEvidencePostureResponse.fromJson(value);
        case 'DecisionLedgerRecordResponse':
          return DecisionLedgerRecordResponse.fromJson(value);
        case 'DecisionLedgerRecordsResponse':
          return DecisionLedgerRecordsResponse.fromJson(value);
        case 'DecisionLedgerReferenceRequest':
          return DecisionLedgerReferenceRequest.fromJson(value);
        case 'DecisionLedgerReferenceResponse':
          return DecisionLedgerReferenceResponse.fromJson(value);
        case 'DevopsIssueSummaryResponse':
          return DevopsIssueSummaryResponse.fromJson(value);
        case 'DevopsJobSummaryResponse':
          return DevopsJobSummaryResponse.fromJson(value);
        case 'DevopsMergeRequestSummaryResponse':
          return DevopsMergeRequestSummaryResponse.fromJson(value);
        case 'DevopsPipelineSummaryResponse':
          return DevopsPipelineSummaryResponse.fromJson(value);
        case 'DevopsReleaseSummaryResponse':
          return DevopsReleaseSummaryResponse.fromJson(value);
        case 'DevopsSummaryResponse':
          return DevopsSummaryResponse.fromJson(value);
        case 'DiagnosticCheck':
          return DiagnosticCheck.fromJson(value);
        case 'DiagnosticStatus':
          return DiagnosticStatus.fromJson(value);
        case 'DomainCapability':
          return DomainCapability.fromJson(value);
        case 'E2eeStatus':
          return E2eeStatus.fromJson(value);
        case 'FileNativeProviderOptionResponse':
          return FileNativeProviderOptionResponse.fromJson(value);
        case 'FileNativeProviderSetupResponse':
          return FileNativeProviderSetupResponse.fromJson(value);
        case 'FileSetupCredentialListResponse':
          return FileSetupCredentialListResponse.fromJson(value);
        case 'FileSetupCredentialRequest':
          return FileSetupCredentialRequest.fromJson(value);
        case 'FileSetupCredentialResponse':
          return FileSetupCredentialResponse.fromJson(value);
        case 'FilesUserCreateFolderRequest':
          return FilesUserCreateFolderRequest.fromJson(value);
        case 'FilesUserItemResponse':
          return FilesUserItemResponse.fromJson(value);
        case 'FilesUserListResponse':
          return FilesUserListResponse.fromJson(value);
        case 'GuestAccessContractResponse':
          return GuestAccessContractResponse.fromJson(value);
        case 'GuestInvitationRequest':
          return GuestInvitationRequest.fromJson(value);
        case 'HealthResponse':
          return HealthResponse.fromJson(value);
        case 'IdentitySessionReconcileResponse':
          return IdentitySessionReconcileResponse.fromJson(value);
        case 'LinkedSourceProjectResponse':
          return LinkedSourceProjectResponse.fromJson(value);
        case 'MatrixBackendBoundary':
          return MatrixBackendBoundary.fromJson(value);
        case 'MatrixStatus':
          return MatrixStatus.fromJson(value);
        case 'MeetingCapsuleCreateRequest':
          return MeetingCapsuleCreateRequest.fromJson(value);
        case 'MeetingCapsuleResponse':
          return MeetingCapsuleResponse.fromJson(value);
        case 'MeetingCapsulesResponse':
          return MeetingCapsulesResponse.fromJson(value);
        case 'ModuleSyncStatusResponse':
          return ModuleSyncStatusResponse.fromJson(value);
        case 'OfficeCapabilitiesResponse':
          return OfficeCapabilitiesResponse.fromJson(value);
        case 'OfficeCapabilityFlagsResponse':
          return OfficeCapabilityFlagsResponse.fromJson(value);
        case 'OfficeLaunchRequest':
          return OfficeLaunchRequest.fromJson(value);
        case 'OfficeLaunchResponse':
          return OfficeLaunchResponse.fromJson(value);
        case 'OfficeLockSessionReadinessResponse':
          return OfficeLockSessionReadinessResponse.fromJson(value);
        case 'OfficePermissionModelResponse':
          return OfficePermissionModelResponse.fromJson(value);
        case 'OfficeProviderCandidateResponse':
          return OfficeProviderCandidateResponse.fromJson(value);
        case 'Oidc':
          return Oidc.fromJson(value);
        case 'OrganizationManifestResponse':
          return OrganizationManifestResponse.fromJson(value);
        case 'PlatformConfigResponse':
          return PlatformConfigResponse.fromJson(value);
        case 'PlatformStatusResponse':
          return PlatformStatusResponse.fromJson(value);
        case 'ProductProfileResponse':
          return ProductProfileResponse.fromJson(value);
        case 'ProfileReadinessResponse':
          return ProfileReadinessResponse.fromJson(value);
        case 'Protocols':
          return Protocols.fromJson(value);
        case 'ProviderRef':
          return ProviderRef.fromJson(value);
        case 'ProviderStatusResponse':
          return ProviderStatusResponse.fromJson(value);
        case 'RecoveryAction':
          return RecoveryAction.fromJson(value);
        case 'SlackOAuthCallbackRequest':
          return SlackOAuthCallbackRequest.fromJson(value);
        case 'SlackOutboundMessageRequest':
          return SlackOutboundMessageRequest.fromJson(value);
        case 'SourceRepositoryResponse':
          return SourceRepositoryResponse.fromJson(value);
        case 'TaskItem':
          return TaskItem.fromJson(value);
        case 'UpdateProductProfileRequest':
          return UpdateProductProfileRequest.fromJson(value);
        case 'WeaveProject':
          return WeaveProject.fromJson(value);
        case 'WorkspaceCapabilitiesResponse':
          return WorkspaceCapabilitiesResponse.fromJson(value);
        case 'WorkspaceCapabilityPolicyResponse':
          return WorkspaceCapabilityPolicyResponse.fromJson(value);
        case 'WorkspaceCapabilityStatusResponse':
          return WorkspaceCapabilityStatusResponse.fromJson(value);
        case 'WorkspaceHomeActionResponse':
          return WorkspaceHomeActionResponse.fromJson(value);
        case 'WorkspaceHomeRecentActivityResponse':
          return WorkspaceHomeRecentActivityResponse.fromJson(value);
        case 'WorkspaceHomeResponse':
          return WorkspaceHomeResponse.fromJson(value);
        case 'WorkspaceHomeSectionResponse':
          return WorkspaceHomeSectionResponse.fromJson(value);
        case 'WorkspaceReleaseReadinessCheckResponse':
          return WorkspaceReleaseReadinessCheckResponse.fromJson(value);
        case 'WorkspaceReleaseReadinessResponse':
          return WorkspaceReleaseReadinessResponse.fromJson(value);
        default:
          dynamic match;
          if (value is List &&
              (match = _regList.firstMatch(targetType)?.group(1)) != null) {
            return value
                .map<dynamic>((dynamic v) => fromJson(
                      v,
                      match,
                      growable: growable,
                    ))
                .toList(growable: growable);
          }
          if (value is Set &&
              (match = _regSet.firstMatch(targetType)?.group(1)) != null) {
            return value
                .map<dynamic>((dynamic v) => fromJson(
                      v,
                      match,
                      growable: growable,
                    ))
                .toSet();
          }
          if (value is Map &&
              (match = _regMap.firstMatch(targetType)?.group(1)) != null) {
            return Map<String, dynamic>.fromIterables(
              value.keys.cast<String>(),
              value.values.map<dynamic>((dynamic v) => fromJson(
                    v,
                    match,
                    growable: growable,
                  )),
            );
          }
      }
    } on Exception catch (error, trace) {
      throw ApiException.withInner(
        HttpStatus.internalServerError,
        'Exception during deserialization.',
        error,
        trace,
      );
    }
    throw ApiException(
      HttpStatus.internalServerError,
      'Could not find a suitable class for deserialization',
    );
  }
}

/// Primarily intended for use in an isolate.
class DeserializationMessage {
  const DeserializationMessage({
    required this.json,
    required this.targetType,
    this.growable = false,
  });

  /// The JSON value to deserialize.
  final String json;

  /// Target type to deserialize to.
  final String targetType;

  /// Whether to make deserialized lists or maps growable.
  final bool growable;
}

/// Primarily intended for use in an isolate.
Future<dynamic> decodeAsync(DeserializationMessage message) async {
  // Remove all spaces. Necessary for regular expressions as well.
  final targetType = message.targetType.replaceAll(' ', '');

  // If the expected target type is String, nothing to do...
  return targetType == 'String' ? message.json : json.decode(message.json);
}

/// Primarily intended for use in an isolate.
Future<dynamic> deserializeAsync(DeserializationMessage message) async {
  // Remove all spaces. Necessary for regular expressions as well.
  final targetType = message.targetType.replaceAll(' ', '');

  // If the expected target type is String, nothing to do...
  return targetType == 'String'
      ? message.json
      : ApiClient.fromJson(
          json.decode(message.json),
          targetType,
          growable: message.growable,
        );
}

/// Primarily intended for use in an isolate.
Future<String> serializeAsync(Object? value) async =>
    value == null ? '' : json.encode(value);
