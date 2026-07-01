import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/data/dtos/files_openapi_mappers.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

/// Files repository backed by the Weave backend product facade.
///
/// Flutter owns the product UI and calls `weave-backend` only. The backend owns
/// all direct Nextcloud WebDAV/OCS access for the MVP files path.
class BackendFilesRepository
    implements
        FilesRepository,
        FilesEntryMutationRepository,
        FilesExportRepository {
  const BackendFilesRepository({
    required http.Client httpClient,
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
  }) : _httpClient = httpClient,
       _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository;

  static const accountLabel = 'Weave files';

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

    final authState = await _authSessionRepository.restoreSession(
      _authConfiguration(configuration),
    );
    if (!authState.isAuthenticated || authState.session == null) {
      return FilesConnectionState.disconnected(
        baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
        message: 'Sign in to Weave before browsing files.',
      );
    }

    return FilesConnectionState.connected(
      baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
      accountLabel: accountLabel,
    );
  }

  @override
  Future<FilesConnectionState> connect() async {
    final context = await _requireContext();
    return FilesConnectionState.connected(
      baseUrl: context.baseUrl,
      accountLabel: accountLabel,
    );
  }

  @override
  Future<void> disconnect() async {
    // The backend-facade path does not own a separate local Nextcloud session.
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, const ['api', 'files'], query: {'path': path}),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to load files from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeListing(response.body);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    final context = await _requireContext();
    final multipart =
        http.MultipartRequest(
            'POST',
            _apiUri(
              context.baseUrl,
              const ['api', 'files', 'upload'],
              query: {'parentPath': directoryPath},
            ),
          )
          ..headers.addAll({
            'Accept': 'application/json',
            'Authorization': 'Bearer ${context.accessToken}',
          });

    var uploadedBytes = 0;
    final stream = request.byteStream.transform(
      StreamTransformer<List<int>, List<int>>.fromHandlers(
        handleData: (chunk, sink) {
          uploadedBytes += chunk.length;
          onProgress?.call(uploadedBytes, request.sizeInBytes);
          sink.add(chunk);
        },
      ),
    );
    multipart.files.add(
      http.MultipartFile(
        'file',
        stream,
        request.sizeInBytes,
        filename: request.fileName,
      ),
    );

    final streamedResponse = await _sendStream(
      () => _httpClient.send(multipart),
      fallbackMessage: 'Unable to upload the file through the Weave backend.',
    );
    final response = await http.Response.fromStream(streamedResponse);
    _ensureSuccess(response, successCodes: const {200});
    onProgress?.call(request.sizeInBytes, request.sizeInBytes);
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) async {
    final context = await _requireContext();
    final request = openapi.CreateFolderRequest(
      parentPath: parentPath,
      name: name,
    );
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.post(
        _apiUri(context.baseUrl, const ['api', 'files', 'folders']),
        headers: _jsonHeaders(accessToken),
        body: jsonEncode(request.toJson()),
      ),
      fallbackMessage: 'Unable to create the folder through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeEntry(_decodeObject(response.body));
  }

  Future<void> prepareDownload(String id) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, ['api', 'files', id, 'download']),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to prepare the file download.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
  }

  @override
  Future<FileDownload> downloadFile(FileEntry entry) async {
    if (entry.isDirectory) {
      throw const FilesFailure.configuration(
        'Folders cannot be exported as a single file yet.',
      );
    }

    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, ['api', 'files', entry.id, 'download']),
        headers: {'Authorization': 'Bearer $accessToken'},
      ),
      fallbackMessage: 'Unable to download the file through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return FileDownload(
      fileName: _downloadFileName(response.headers) ?? entry.name,
      bytes: Uint8List.fromList(response.bodyBytes),
    );
  }

  String? _downloadFileName(Map<String, String> headers) {
    final disposition = headers['content-disposition'];
    if (disposition == null) {
      return null;
    }
    final filenameStar = RegExp(
      r"filename\*=UTF-8''([^;]+)",
      caseSensitive: false,
    ).firstMatch(disposition);
    if (filenameStar != null) {
      return Uri.decodeComponent(filenameStar.group(1)!);
    }
    final filename = RegExp(
      r'filename="?([^";]+)"?',
      caseSensitive: false,
    ).firstMatch(disposition);
    return filename?.group(1);
  }

  Future<void> delete(String id) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.delete(
        _apiUri(context.baseUrl, ['api', 'files', id]),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to delete the file through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
  }

  @override
  Future<void> deleteEntry(FileEntry entry) => delete(entry.id);

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

    return _BackendFilesContext(
      baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
      accessToken: session.accessToken,
      authConfiguration: authConfiguration,
    );
  }

  AuthConfiguration _authConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }

  Future<http.Response> _send(
    Future<http.Response> Function() request, {
    required String fallbackMessage,
  }) async {
    try {
      return await request().timeout(const Duration(seconds: 20));
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(fallbackMessage, cause: error);
    }
  }

  Future<http.StreamedResponse> _sendStream(
    Future<http.StreamedResponse> Function() request, {
    required String fallbackMessage,
  }) async {
    try {
      return await request().timeout(const Duration(seconds: 60));
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(fallbackMessage, cause: error);
    }
  }

  Future<http.Response> _sendAuthenticated(
    _BackendFilesContext context,
    Future<http.Response> Function(String accessToken) request, {
    required String fallbackMessage,
  }) async {
    final response = await _send(
      () => request(context.accessToken),
      fallbackMessage: fallbackMessage,
    );
    if (response.statusCode != 401) {
      return response;
    }

    final refreshedContext = await _refreshContext(context);
    if (refreshedContext == null ||
        refreshedContext.accessToken == context.accessToken) {
      return response;
    }

    return _send(
      () => request(refreshedContext.accessToken),
      fallbackMessage: fallbackMessage,
    );
  }

  Future<_BackendFilesContext?> _refreshContext(
    _BackendFilesContext context,
  ) async {
    try {
      final authState = await _authSessionRepository.refreshSession(
        context.authConfiguration,
      );
      final session = authState.session;
      if (!authState.isAuthenticated || session == null) {
        return null;
      }
      return _BackendFilesContext(
        baseUrl: context.baseUrl,
        accessToken: session.accessToken,
        authConfiguration: context.authConfiguration,
      );
    } catch (_) {
      return null;
    }
  }

  void _ensureSuccess(
    http.Response response, {
    required Set<int> successCodes,
  }) {
    if (successCodes.contains(response.statusCode)) {
      return;
    }

    final message = _errorMessage(response.body);
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw FilesFailure.invalidCredentials(
        message ?? 'The Weave backend rejected the current session.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 400 || response.statusCode == 404) {
      throw FilesFailure.protocol(
        message ?? 'The Weave backend rejected the files request.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 409) {
      throw FilesFailure.protocol(
        message ??
            'The file operation conflicts with the current backend state.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 413 || response.statusCode == 507) {
      throw FilesFailure.storage(
        message ??
            'There is not enough storage available to complete this file operation.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 503) {
      throw FilesFailure.configuration(
        message ?? 'The Weave backend files facade is unavailable.',
        cause: response.statusCode,
      );
    }

    throw FilesFailure.unknown(
      message ?? 'The Weave backend failed the files request.',
      cause: response.statusCode,
    );
  }

  DirectoryListing _decodeListing(String body) {
    try {
      return openapi.FileListResponse.fromJson(
        _decodeObject(body),
      ).toDomainListing();
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw const FilesFailure.protocol(
        'The Weave backend returned an invalid files listing.',
      );
    }
  }

  FileEntry _decodeEntry(Map<String, dynamic> json) {
    return openapi.FileItemResponse.fromJson(json).toDomainEntry();
  }

  Map<String, dynamic> _decodeObject(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        return payload;
      }
    } catch (_) {
      // Fall through to protocol failure below.
    }
    throw const FilesFailure.protocol(
      'The Weave backend returned an invalid files payload.',
    );
  }

  String? _errorMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        final message = payload['message'];
        if (message is String && message.trim().isNotEmpty) {
          return message;
        }
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  Map<String, String> _jsonHeaders(String accessToken) {
    return {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $accessToken',
    };
  }

  Uri _apiUri(
    Uri baseUrl,
    List<String> pathSegments, {
    Map<String, String>? query,
  }) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, pathSegments),
      queryParameters: query,
    );
  }

  List<String> _apiPath(Uri baseUrl, List<String> pathSegments) {
    final baseSegments = baseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (baseSegments.isNotEmpty &&
        pathSegments.isNotEmpty &&
        baseSegments.last == 'api' &&
        pathSegments.first == 'api') {
      return [...baseSegments, ...pathSegments.skip(1)];
    }

    return [...baseSegments, ...pathSegments];
  }
}

class _BackendFilesContext {
  const _BackendFilesContext({
    required this.baseUrl,
    required this.accessToken,
    required this.authConfiguration,
  });

  final Uri baseUrl;
  final String accessToken;
  final AuthConfiguration authConfiguration;
}
