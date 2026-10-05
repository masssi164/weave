//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FilesUserApi {
  FilesUserApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Create an empty Files folder at an absent name
  ///
  /// Requires createFolder in the parent's current allowedActions. The current release supports atomic root-folder creation; other parents fail closed until their identity can be bound atomically by the selected provider.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] ifNoneMatch (required):
  ///   Must be * for atomic absent-name creation.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character key for durable User HTTP intent.
  ///
  /// * [FilesUserCreateFolderRequest] filesUserCreateFolderRequest (required):
  Future<Response> createFilesFolderWithHttpInfo(
    String ifNoneMatch,
    String idempotencyKey,
    FilesUserCreateFolderRequest filesUserCreateFolderRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/items/folders';

    // ignore: prefer_final_locals
    Object? postBody = filesUserCreateFolderRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    headerParams[r'If-None-Match'] = parameterToString(ifNoneMatch);
    headerParams[r'Idempotency-Key'] = parameterToString(idempotencyKey);

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

  /// Create an empty Files folder at an absent name
  ///
  /// Requires createFolder in the parent's current allowedActions. The current release supports atomic root-folder creation; other parents fail closed until their identity can be bound atomically by the selected provider.
  ///
  /// Parameters:
  ///
  /// * [String] ifNoneMatch (required):
  ///   Must be * for atomic absent-name creation.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character key for durable User HTTP intent.
  ///
  /// * [FilesUserCreateFolderRequest] filesUserCreateFolderRequest (required):
  Future<FilesUserItemResponse?> createFilesFolder(
    String ifNoneMatch,
    String idempotencyKey,
    FilesUserCreateFolderRequest filesUserCreateFolderRequest,
  ) async {
    final response = await createFilesFolderWithHttpInfo(
      ifNoneMatch,
      idempotencyKey,
      filesUserCreateFolderRequest,
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
        'FilesUserItemResponse',
      ) as FilesUserItemResponse;
    }
    return null;
  }

  /// Download bounded binary Files content
  ///
  /// Returns at most 26214400 bytes (25 MiB) from an identity-bound conditional provider read. Requires download in the item's current allowedActions. Larger files fail with 413.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  ///
  /// * [String] ifNoneMatch:
  Future<Response> downloadFilesItemContentWithHttpInfo(
    String fileId, {
    String? ifNoneMatch,
  }) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/files/items/{fileId}/content'.replaceAll('{fileId}', fileId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    if (ifNoneMatch != null) {
      headerParams[r'If-None-Match'] = parameterToString(ifNoneMatch);
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

  /// Download bounded binary Files content
  ///
  /// Returns at most 26214400 bytes (25 MiB) from an identity-bound conditional provider read. Requires download in the item's current allowedActions. Larger files fail with 413.
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  ///
  /// * [String] ifNoneMatch:
  Future<MultipartFile?> downloadFilesItemContent(
    String fileId, {
    String? ifNoneMatch,
  }) async {
    final response = await downloadFilesItemContentWithHttpInfo(
      fileId,
      ifNoneMatch: ifNoneMatch,
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
        'MultipartFile',
      ) as MultipartFile;
    }
    return null;
  }

  /// Inspect a Weave Files item
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  Future<Response> getFilesItemWithHttpInfo(
    String fileId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/items/{fileId}'.replaceAll('{fileId}', fileId);

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

  /// Inspect a Weave Files item
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  Future<FilesUserItemResponse?> getFilesItem(
    String fileId,
  ) async {
    final response = await getFilesItemWithHttpInfo(
      fileId,
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
        'FilesUserItemResponse',
      ) as FilesUserItemResponse;
    }
    return null;
  }

  /// List Weave-authorized Files children
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] parentId:
  Future<Response> listFilesItemsWithHttpInfo({
    String? parentId,
  }) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/items';

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    if (parentId != null) {
      queryParams.addAll(_queryParams('', 'parentId', parentId));
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

  /// List Weave-authorized Files children
  ///
  /// Parameters:
  ///
  /// * [String] parentId:
  Future<FilesUserListResponse?> listFilesItems({
    String? parentId,
  }) async {
    final response = await listFilesItemsWithHttpInfo(
      parentId: parentId,
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
        'FilesUserListResponse',
      ) as FilesUserListResponse;
    }
    return null;
  }

  /// Replace bounded binary content against a strong validator
  ///
  /// Accepts at most 26214400 bytes (25 MiB). Requires updateContent in the item's current allowedActions and a provider that atomically enforces both identity and version. Unsupported providers fail closed; a content ETag is distinct from the item revision.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Strong ETag returned by the last content download.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character durable operation key.
  ///
  /// * [MultipartFile] body (required):
  ///
  /// * [String] mediaType:
  Future<Response> updateFilesItemContentWithHttpInfo(
    String fileId,
    String ifMatch,
    String idempotencyKey,
    MultipartFile body, {
    String? mediaType,
  }) async {
    // ignore: prefer_const_declarations
    final path =
        r'/api/files/items/{fileId}/content'.replaceAll('{fileId}', fileId);

    // ignore: prefer_final_locals
    Object? postBody = body;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    if (mediaType != null) {
      queryParams.addAll(_queryParams('', 'mediaType', mediaType));
    }

    headerParams[r'If-Match'] = parameterToString(ifMatch);
    headerParams[r'Idempotency-Key'] = parameterToString(idempotencyKey);

    const contentTypes = <String>['application/octet-stream'];

    return apiClient.invokeAPI(
      path,
      'PUT',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Replace bounded binary content against a strong validator
  ///
  /// Accepts at most 26214400 bytes (25 MiB). Requires updateContent in the item's current allowedActions and a provider that atomically enforces both identity and version. Unsupported providers fail closed; a content ETag is distinct from the item revision.
  ///
  /// Parameters:
  ///
  /// * [String] fileId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Strong ETag returned by the last content download.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character durable operation key.
  ///
  /// * [MultipartFile] body (required):
  ///
  /// * [String] mediaType:
  Future<FilesUserItemResponse?> updateFilesItemContent(
    String fileId,
    String ifMatch,
    String idempotencyKey,
    MultipartFile body, {
    String? mediaType,
  }) async {
    final response = await updateFilesItemContentWithHttpInfo(
      fileId,
      ifMatch,
      idempotencyKey,
      body,
      mediaType: mediaType,
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
        'FilesUserItemResponse',
      ) as FilesUserItemResponse;
    }
    return null;
  }

  /// Upload bounded binary content at an absent name
  ///
  /// Accepts at most 26214400 bytes (25 MiB), including an empty file. Requires upload in the parent's current allowedActions. The current release supports atomic creation in file:root; unsupported parent identity guarantees fail closed.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] parentId (required):
  ///
  /// * [String] name (required):
  ///
  /// * [String] ifNoneMatch (required):
  ///   Must be * for atomic absent-name creation.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character durable operation key.
  ///
  /// * [MultipartFile] body (required):
  ///
  /// * [String] mediaType:
  Future<Response> uploadFilesItemContentWithHttpInfo(
    String parentId,
    String name,
    String ifNoneMatch,
    String idempotencyKey,
    MultipartFile body, {
    String? mediaType,
  }) async {
    // ignore: prefer_const_declarations
    final path = r'/api/files/items/uploads';

    // ignore: prefer_final_locals
    Object? postBody = body;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    queryParams.addAll(_queryParams('', 'parentId', parentId));
    queryParams.addAll(_queryParams('', 'name', name));
    if (mediaType != null) {
      queryParams.addAll(_queryParams('', 'mediaType', mediaType));
    }

    headerParams[r'If-None-Match'] = parameterToString(ifNoneMatch);
    headerParams[r'Idempotency-Key'] = parameterToString(idempotencyKey);

    const contentTypes = <String>['application/octet-stream'];

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

  /// Upload bounded binary content at an absent name
  ///
  /// Accepts at most 26214400 bytes (25 MiB), including an empty file. Requires upload in the parent's current allowedActions. The current release supports atomic creation in file:root; unsupported parent identity guarantees fail closed.
  ///
  /// Parameters:
  ///
  /// * [String] parentId (required):
  ///
  /// * [String] name (required):
  ///
  /// * [String] ifNoneMatch (required):
  ///   Must be * for atomic absent-name creation.
  ///
  /// * [String] idempotencyKey (required):
  ///   16 to 128 character durable operation key.
  ///
  /// * [MultipartFile] body (required):
  ///
  /// * [String] mediaType:
  Future<FilesUserItemResponse?> uploadFilesItemContent(
    String parentId,
    String name,
    String ifNoneMatch,
    String idempotencyKey,
    MultipartFile body, {
    String? mediaType,
  }) async {
    final response = await uploadFilesItemContentWithHttpInfo(
      parentId,
      name,
      ifNoneMatch,
      idempotencyKey,
      body,
      mediaType: mediaType,
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
        'FilesUserItemResponse',
      ) as FilesUserItemResponse;
    }
    return null;
  }
}
