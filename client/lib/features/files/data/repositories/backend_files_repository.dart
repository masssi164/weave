import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

/// Member Files through the generated, server-owned User HTTP contract.
///
/// Paths are for display and navigation only. Every request uses the opaque
/// Weave file ID returned by the server, never a provider path or DAV URL.
class BackendFilesRepository
    implements
        FilesRepository,
        FilesEntryMutationRepository,
        FilesRelocationRepository,
        FilesExportRepository {
  const BackendFilesRepository({
    required http.Client httpClient,
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
  }) : _httpClient = httpClient,
       _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository;

  static const accountLabel = 'Weave files';
  static const rootFileId = 'file:root';
  static const maxTransferBytes = 25 * 1024 * 1024;
  static final _opaqueFileId = RegExp(
    r'^file:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );

  final http.Client _httpClient;
  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;

  @override
  Future<FilesConnectionState> restoreConnection() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      return const FilesConnectionState.misconfigured(
        message: 'Finish server setup before browsing files.',
      );
    }
    final authConfiguration = _authConfiguration(configuration);
    final authState = await _authSessionRepository.restoreSession(
      authConfiguration,
    );
    if (!authState.isAuthenticated || authState.session == null) {
      return FilesConnectionState.disconnected(
        baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
        message: 'Sign in to Weave before browsing files.',
      );
    }
    return _connectionForReadiness(
      await _contextForSession(configuration, authState.session!),
    );
  }

  @override
  Future<FilesConnectionState> connect() async {
    return _connectionForReadiness(await _requireContext());
  }

  @override
  Future<void> disconnect() async {
    // Files uses the ordinary Weave member session.
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    final context = await _requireContext();
    final normalizedPath = _normalizePath(path);
    final listing = await _listAtPath(context, normalizedPath);
    return DirectoryListing(
      path: normalizedPath,
      parentFileId: listing.parentFileId,
      allowedActions: listing.allowedActions.toSet(),
      entries: listing.items
          .map(
            (item) => _entry(
              item,
              parentId: listing.parentFileId,
              parentPath: normalizedPath,
            ),
          )
          .toList(growable: false),
    );
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    _validateChildName(request.fileName);
    if (request.sizeInBytes < 0 || request.sizeInBytes > maxTransferBytes) {
      throw const FilesFailure.storage('Files uploads are limited to 25 MiB.');
    }
    final context = await _requireContext();
    final listing = await _listAtPath(context, _normalizePath(directoryPath));
    _requireAction(listing.allowedActions, 'upload');

    // Buffer this bounded transfer before sending. A file-picker stream cannot
    // be replayed after a 401 refresh, and a partial stream must never become
    // an apparently successful upload.
    final bytes = BytesBuilder(copy: false);
    var count = 0;
    try {
      await for (final chunk in request.byteStream) {
        count += chunk.length;
        if (count > maxTransferBytes || count > request.sizeInBytes) {
          throw const FilesFailure.protocol(
            'The selected file size changed during upload.',
          );
        }
        bytes.add(chunk);
      }
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(
        'Unable to read the selected file.',
        cause: error,
      );
    }
    if (count != request.sizeInBytes) {
      throw const FilesFailure.protocol(
        'The selected file size changed during upload.',
      );
    }
    final content = bytes.takeBytes();
    final key = _idempotencyKey();
    final result = await _invoke(
      context,
      (api) => api.uploadFilesItemContent(
        listing.parentFileId,
        request.fileName,
        '*',
        key,
        _uploadBody(content, onProgress: onProgress),
      ),
      fallbackMessage: 'Unable to upload the file through Weave Files.',
      confirmIdentity: true,
    );
    if (result == null) {
      throw const FilesFailure.protocol(
        'Weave Files returned no uploaded item.',
      );
    }
    _entry(
      result,
      parentId: listing.parentFileId,
      parentPath: _normalizePath(directoryPath),
    );
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) async {
    _validateChildName(name);
    final context = await _requireContext();
    final listing = await _listAtPath(context, _normalizePath(parentPath));
    _requireAction(listing.allowedActions, 'createFolder');
    final key = _idempotencyKey();
    final result = await _invoke(
      context,
      (api) => api.createFilesFolder(
        '*',
        key,
        user_api.FilesUserCreateFolderRequest(
          parentFileId: listing.parentFileId,
          name: name,
        ),
      ),
      fallbackMessage: 'Unable to create the folder through Weave Files.',
      confirmIdentity: true,
    );
    if (result == null) {
      throw const FilesFailure.protocol(
        'Weave Files returned no created folder.',
      );
    }
    final entry = _entry(
      result,
      parentId: listing.parentFileId,
      parentPath: _normalizePath(parentPath),
    );
    if (!entry.isDirectory) {
      throw const FilesFailure.protocol(
        'Weave Files returned an invalid folder.',
      );
    }
    return entry;
  }

  @override
  Future<FileDownload> downloadFile(FileEntry entry) async {
    if (entry.isDirectory ||
        !_opaqueFileId.hasMatch(entry.id) ||
        !entry.allows('download')) {
      throw const FilesFailure.unsupportedPlatform(
        'This file cannot be downloaded through Weave Files.',
      );
    }
    final context = await _requireContext();
    final response = await _invoke(context, (api) async {
      final response = await api.downloadFilesItemContentWithHttpInfo(entry.id);
      if (response.statusCode >= 400) {
        throw user_api.ApiException(response.statusCode, response.body);
      }
      return response;
    }, fallbackMessage: 'Unable to download the file through Weave Files.');
    if (response.statusCode != 200) {
      throw const FilesFailure.protocol(
        'Weave Files returned an unexpected download status.',
      );
    }
    _verifyDownload(response);
    return FileDownload(
      fileName: entry.name,
      bytes: Uint8List.fromList(response.bodyBytes),
    );
  }

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    throw const FilesFailure.unsupportedPlatform(
      'Delete is not available through the Weave Files User API yet.',
    );
  }

  @override
  Future<FileEntry> copyEntry(
    FileEntry source, {
    required String destinationPath,
    bool overwrite = false,
  }) async {
    throw const FilesFailure.unsupportedPlatform(
      'Copy is not available through the Weave Files User API yet.',
    );
  }

  @override
  Future<FileEntry> moveEntry(
    FileEntry source, {
    required String destinationPath,
    bool overwrite = false,
  }) async {
    throw const FilesFailure.unsupportedPlatform(
      'Move is not available through the Weave Files User API yet.',
    );
  }

  Future<user_api.FilesUserListResponse> _listAtPath(
    _BackendFilesContext context,
    String path,
  ) async {
    var listing = await _list(context, rootFileId);
    if (path == '/') return listing;
    var walkedPath = '';
    for (final segment in path.split('/').where((part) => part.isNotEmpty)) {
      walkedPath += '/$segment';
      final matches = listing.items.where(
        (item) =>
            item.name == segment &&
            item.displayPath == walkedPath &&
            item.kind == user_api.FilesUserItemResponseKindEnum.folder &&
            item.parentFileId == listing.parentFileId &&
            _opaqueFileId.hasMatch(item.fileId) &&
            item.allowedActions.contains('listChildren'),
      );
      if (matches.length != 1) {
        throw const FilesFailure.protocol(
          'This folder is no longer available in Weave Files.',
        );
      }
      listing = await _list(context, matches.single.fileId);
    }
    return listing;
  }

  Future<user_api.FilesUserListResponse> _list(
    _BackendFilesContext context,
    String parentId,
  ) async {
    if (parentId != rootFileId && !_opaqueFileId.hasMatch(parentId)) {
      throw const FilesFailure.protocol(
        'The Files folder reference is invalid.',
      );
    }
    final response = await _invoke(
      context,
      (api) => api.listFilesItems(parentId: parentId),
      fallbackMessage: 'Unable to load files from Weave Files.',
    );
    if (response == null || response.parentFileId != parentId) {
      throw const FilesFailure.protocol(
        'Weave Files returned an invalid directory listing.',
      );
    }
    return response;
  }

  FileEntry _entry(
    user_api.FilesUserItemResponse item, {
    required String parentId,
    required String parentPath,
  }) {
    final path = item.displayPath;
    final expectedPath = parentPath == '/'
        ? '/${item.name}'
        : '$parentPath/${item.name}';
    if (item.parentFileId != parentId ||
        !_opaqueFileId.hasMatch(item.fileId) ||
        path != expectedPath ||
        item.name.trim().isEmpty ||
        item.revision.isEmpty ||
        item.kind != user_api.FilesUserItemResponseKindEnum.file &&
            item.kind != user_api.FilesUserItemResponseKindEnum.folder) {
      throw const FilesFailure.protocol(
        'Weave Files returned an invalid item.',
      );
    }
    return FileEntry(
      id: item.fileId,
      name: item.name,
      path: path,
      isDirectory: item.kind == user_api.FilesUserItemResponseKindEnum.folder,
      modifiedAt: item.modifiedAt,
      sizeInBytes: item.kind == user_api.FilesUserItemResponseKindEnum.file
          ? item.size
          : null,
      revision: item.revision,
      allowedActions: item.allowedActions.toSet(),
    );
  }

  void _verifyDownload(http.Response response) {
    final bytes = response.bodyBytes;
    final length = int.tryParse(response.headers['content-length'] ?? '');
    final etag = response.headers['etag'];
    final contentType = response.headers['content-type'];
    final digestHeader = response.headers['content-digest'];
    final contentHash = sha256.convert(bytes);
    final match = digestHeader == null
        ? null
        : RegExp(
            r'^sha-256=:([A-Za-z0-9+/]+={0,2}):$',
          ).firstMatch(digestHeader);
    if (bytes.length > maxTransferBytes ||
        length != bytes.length ||
        etag == null ||
        !RegExp(r'^"[^"\r\n]+"$').hasMatch(etag) ||
        etag != '"sha256-$contentHash"' ||
        contentType == null ||
        contentType.trim().isEmpty ||
        match == null ||
        match.group(1) != base64Encode(contentHash.bytes)) {
      throw const FilesFailure.protocol(
        'Weave Files returned content that failed integrity validation.',
      );
    }
  }

  void _requireAction(List<String> actions, String action) {
    if (!actions.contains(action)) {
      throw const FilesFailure.unsupportedPlatform(
        'This action is not available for this Weave Files folder.',
      );
    }
  }

  void _validateChildName(String name) {
    if (name.isEmpty ||
        name != name.trim() ||
        name == '.' ||
        name == '..' ||
        name.contains('/') ||
        name.contains('\\') ||
        name.runes.any((rune) => rune < 0x20)) {
      throw const FilesFailure.protocol('The file name is not valid.');
    }
  }

  String _normalizePath(String path) {
    final normalized = path.trim().replaceAll(RegExp('/+'), '/');
    final withLeadingSlash = normalized.startsWith('/')
        ? normalized
        : '/$normalized';
    final result = withLeadingSlash.length > 1 && withLeadingSlash.endsWith('/')
        ? withLeadingSlash.substring(0, withLeadingSlash.length - 1)
        : withLeadingSlash;
    if (result.split('/').any((part) => part == '.' || part == '..')) {
      throw const FilesFailure.protocol('The Files path is invalid.');
    }
    return result;
  }

  String _idempotencyKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(24, (_) => random.nextInt(256));
    return base64UrlEncode(bytes).replaceAll('=', '');
  }

  http.MultipartFile _uploadBody(
    Uint8List content, {
    FileUploadProgressCallback? onProgress,
  }) {
    const chunkSize = 64 * 1024;
    var sent = 0;
    final chunks = <List<int>>[
      for (var offset = 0; offset < content.length; offset += chunkSize)
        Uint8List.sublistView(
          content,
          offset,
          min(offset + chunkSize, content.length),
        ),
    ];
    return http.MultipartFile(
      'body',
      Stream<List<int>>.fromIterable(chunks).map((chunk) {
        sent += chunk.length;
        onProgress?.call(sent, content.length);
        return chunk;
      }),
      content.length,
    );
  }

  Future<_BackendFilesContext> _requireContext() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const FilesFailure.configuration(
        'Finish server setup before browsing files.',
      );
    }
    final authConfiguration = _authConfiguration(configuration);
    final authState = await _authSessionRepository.restoreSession(
      authConfiguration,
    );
    final session = authState.session;
    if (!authState.isAuthenticated || session == null) {
      throw const FilesFailure.sessionRequired(
        'Sign in to Weave before browsing files.',
      );
    }
    return _contextForSession(configuration, session);
  }

  Future<_BackendFilesContext> _contextForSession(
    ServerConfiguration configuration,
    AuthSession session,
  ) async {
    final authConfiguration = _authConfiguration(configuration);
    if (!session.matches(authConfiguration)) {
      throw const FilesFailure.sessionRequired('WEAVE_FILES_SESSION_CHANGED');
    }
    final baseUrl = configuration.serviceEndpoints.backendApiBaseUrl;
    return _BackendFilesContext(
      baseUrl: baseUrl,
      accessToken: session.accessToken,
      authConfiguration: authConfiguration,
      identity: await _readIdentity(
        baseUrl,
        session.accessToken,
        authConfiguration,
      ),
    );
  }

  Future<_FilesRequestIdentity> _readIdentity(
    Uri baseUrl,
    String token,
    AuthConfiguration authConfiguration,
  ) async {
    try {
      final identity = await user_api.IdentityApi(
        weaveUserApiClient(
          apiBaseUrl: baseUrl,
          accessToken: token,
          httpClient: _httpClient,
        ),
      ).me().timeout(const Duration(seconds: 8));
      final subject = identity?.subject;
      final organizationId = identity?.organizationId;
      if (subject == null ||
          subject.isEmpty ||
          organizationId == null ||
          organizationId.isEmpty ||
          identity?.identityIssuer != authConfiguration.issuer.toString()) {
        throw const FilesFailure.sessionRequired(
          'WEAVE_FILES_IDENTITY_UNCONFIRMED',
        );
      }
      return _FilesRequestIdentity(subject, organizationId);
    } on FilesFailure {
      rethrow;
    } catch (_) {
      throw const FilesFailure.sessionRequired(
        'WEAVE_FILES_IDENTITY_UNCONFIRMED',
      );
    }
  }

  Future<void> _assertContextCurrent(_BackendFilesContext context) async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null ||
        configuration.serviceEndpoints.backendApiBaseUrl != context.baseUrl ||
        configuration.oidcIssuerUrl != context.authConfiguration.issuer ||
        configuration.oidcClientRegistration.clientId.trim() !=
            context.authConfiguration.clientId) {
      throw const FilesFailure.sessionRequired('WEAVE_FILES_SESSION_CHANGED');
    }
    final current = await _authSessionRepository.restoreSession(
      context.authConfiguration,
    );
    if (!current.isAuthenticated ||
        current.session?.accessToken != context.accessToken ||
        current.session?.matches(context.authConfiguration) != true) {
      throw const FilesFailure.sessionRequired('WEAVE_FILES_SESSION_CHANGED');
    }
  }

  Future<FilesConnectionState> _connectionForReadiness(
    _BackendFilesContext context,
  ) async {
    try {
      final readiness = await _invoke(
        context,
        (api) => user_api.FilesApi(api.apiClient).getFilesReadiness(),
        fallbackMessage: 'Files readiness could not be confirmed right now.',
      );
      final isReady =
          readiness?.enabled == true &&
          readiness?.policyState ==
              user_api
                  .WorkspaceCapabilityStatusResponsePolicyStateEnum
                  .allowed &&
          (readiness?.readiness ==
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .ready ||
              readiness?.readiness ==
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .degraded) &&
          readiness!.grantedCapabilities.contains('files.read');
      if (isReady) {
        return FilesConnectionState.connected(
          baseUrl: context.baseUrl,
          accountLabel: accountLabel,
        );
      }
      final impact = readiness?.memberImpact?.trim();
      return FilesConnectionState.unavailable(
        baseUrl: context.baseUrl,
        message: impact == null || impact.isEmpty
            ? 'Files access is not available for this Weave account.'
            : impact,
      );
    } on FilesFailure catch (failure) {
      if (failure.type == FilesFailureType.invalidCredentials ||
          failure.type == FilesFailureType.sessionRequired) {
        return FilesConnectionState.invalid(
          baseUrl: context.baseUrl,
          accountLabel: accountLabel,
          message: failure.message,
        );
      }
      return FilesConnectionState.unavailable(
        baseUrl: context.baseUrl,
        message: failure.message,
      );
    }
  }

  Future<T> _invoke<T>(
    _BackendFilesContext context,
    Future<T> Function(user_api.FilesUserApi) request, {
    required String fallbackMessage,
    bool confirmIdentity = false,
  }) async {
    Future<T> send(_BackendFilesContext selected) async {
      // Never replay a buffered upload or a root-scoped write under another
      // account, organization, or server selected while this request waited.
      await _assertContextCurrent(selected);
      if (confirmIdentity) {
        final identity = await _readIdentity(
          selected.baseUrl,
          selected.accessToken,
          selected.authConfiguration,
        );
        if (identity.subject != selected.identity.subject ||
            identity.organizationId != selected.identity.organizationId) {
          throw const FilesFailure.sessionRequired(
            'WEAVE_FILES_SESSION_CHANGED',
          );
        }
        await _assertContextCurrent(selected);
      }
      return request(
        user_api.FilesUserApi(
          weaveUserApiClient(
            apiBaseUrl: selected.baseUrl,
            accessToken: selected.accessToken,
            httpClient: _httpClient,
          ),
        ),
      ).timeout(const Duration(seconds: 20));
    }

    try {
      return await send(context);
    } on user_api.ApiException catch (error) {
      if (error.code == 401) {
        final refreshed = await _refreshContext(context);
        if (refreshed != null && refreshed.accessToken != context.accessToken) {
          // Advance only the bearer after the same server-confirmed identity
          // passed refresh admission. Later steps retain this request's scope.
          context.accessToken = refreshed.accessToken;
          try {
            return await send(context);
          } on user_api.ApiException catch (retryError) {
            throw _apiFailure(retryError);
          } catch (retryError) {
            throw FilesFailure.unknown(fallbackMessage, cause: retryError);
          }
        }
      }
      throw _apiFailure(error);
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(fallbackMessage, cause: error);
    }
  }

  FilesFailure _apiFailure(user_api.ApiException error) {
    final message = _errorMessage(error.message);
    if (error.code == 401) {
      return FilesFailure.invalidCredentials(
        message ?? 'Files access is not allowed for this Weave session.',
        cause: error.code,
      );
    }
    if (error.code == 403) {
      return FilesFailure.permissionDenied(
        message ??
            'This Weave member cannot access the requested Files resource.',
        cause: error.code,
      );
    }
    if (error.code == 400 ||
        error.code == 404 ||
        error.code == 409 ||
        error.code == 412 ||
        error.code == 423 ||
        error.code == 428) {
      return FilesFailure.protocol(
        message ?? 'The file operation conflicts with the current state.',
        cause: error.code,
      );
    }
    if (error.code == 413 || error.code == 507) {
      return FilesFailure.storage(
        message ?? 'The file cannot be stored with the current limits.',
        cause: error.code,
      );
    }
    if (error.code == 503) {
      return FilesFailure.configuration(
        message ?? 'Files need admin attention before members can use them.',
        cause: error.code,
      );
    }
    return FilesFailure.unknown(
      message ?? 'The Files request could not be completed right now.',
      cause: error.code,
    );
  }

  String? _errorMessage(String? body) {
    if (body == null || body.isEmpty) return null;
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        final impact = payload['memberImpact'];
        if (impact is String && impact.trim().isNotEmpty) return impact;
        final message = payload['message'];
        if (message is String && message.trim().isNotEmpty) return message;
      }
    } catch (_) {
      // Generated transport errors are not guaranteed to have a JSON body.
    }
    return null;
  }

  AuthConfiguration _authConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }

  Future<_BackendFilesContext?> _refreshContext(
    _BackendFilesContext context,
  ) async {
    try {
      await _assertContextCurrent(context);
      final authState = await _authSessionRepository.refreshSession(
        context.authConfiguration,
      );
      final session = authState.session;
      if (!authState.isAuthenticated ||
          session == null ||
          !session.matches(context.authConfiguration)) {
        return null;
      }
      final identity = await _readIdentity(
        context.baseUrl,
        session.accessToken,
        context.authConfiguration,
      );
      if (identity.subject != context.identity.subject ||
          identity.organizationId != context.identity.organizationId) {
        return null;
      }
      return _BackendFilesContext(
        baseUrl: context.baseUrl,
        accessToken: session.accessToken,
        authConfiguration: context.authConfiguration,
        identity: context.identity,
      );
    } catch (_) {
      return null;
    }
  }
}

class _BackendFilesContext {
  _BackendFilesContext({
    required this.baseUrl,
    required this.accessToken,
    required this.authConfiguration,
    required this.identity,
  });

  final Uri baseUrl;
  String accessToken;
  final AuthConfiguration authConfiguration;
  final _FilesRequestIdentity identity;
}

class _FilesRequestIdentity {
  const _FilesRequestIdentity(this.subject, this.organizationId);

  final String subject;
  final String organizationId;
}
