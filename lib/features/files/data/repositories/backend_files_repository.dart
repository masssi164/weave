import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

/// Files repository backed by the Weave backend product facade.
///
/// Flutter owns the product UI and calls `weave-backend` only. The backend owns
/// all direct Nextcloud WebDAV/OCS access for the MVP files path.
class BackendFilesRepository implements FilesRepository {
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
    final response = await _send(
      () => _httpClient.get(
        _apiUri(context.baseUrl, const ['api', 'files'], query: {'path': path}),
        headers: _jsonHeaders(context.accessToken),
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

  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.post(
        _apiUri(context.baseUrl, const ['api', 'files', 'folders']),
        headers: _jsonHeaders(context.accessToken),
        body: jsonEncode({'parentPath': parentPath, 'name': name}),
      ),
      fallbackMessage: 'Unable to create the folder through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeEntry(_decodeObject(response.body));
  }

  Future<void> prepareDownload(String id) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.get(
        _apiUri(context.baseUrl, ['api', 'files', id, 'download']),
        headers: _jsonHeaders(context.accessToken),
      ),
      fallbackMessage: 'Unable to prepare the file download.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
  }

  Future<void> delete(String id) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.delete(
        _apiUri(context.baseUrl, ['api', 'files', id]),
        headers: _jsonHeaders(context.accessToken),
      ),
      fallbackMessage: 'Unable to delete the file through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
  }

  Future<_BackendFilesContext> _requireContext() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const FilesFailure.configuration(
        'Finish server setup before browsing files.',
      );
    }

    final authState = await _authSessionRepository.restoreSession(
      _authConfiguration(configuration),
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
    final json = _decodeObject(body);
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FilesFailure.protocol(
        'The Weave backend returned an invalid files listing.',
      );
    }
    return DirectoryListing(
      path: _readString(json, 'path', fallback: '/'),
      entries: rawItems
          .whereType<Map<String, dynamic>>()
          .map(_decodeEntry)
          .toList(growable: false),
    );
  }

  FileEntry _decodeEntry(Map<String, dynamic> json) {
    final type = _readString(json, 'type');
    return FileEntry(
      id: _readString(json, 'id'),
      name: _readString(json, 'name'),
      path: _readString(json, 'path'),
      isDirectory: type == 'folder' || type == 'directory',
      modifiedAt: _readDateTime(json['modifiedAt']),
      sizeInBytes: _readInt(json['size']),
    );
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

  String _readString(
    Map<String, dynamic> json,
    String key, {
    String? fallback,
  }) {
    final value = json[key];
    if (value is String && value.trim().isNotEmpty) {
      return value;
    }
    if (fallback != null) {
      return fallback;
    }
    throw FilesFailure.protocol(
      'The Weave backend returned a file item without $key.',
    );
  }

  int? _readInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return null;
  }

  DateTime? _readDateTime(Object? value) {
    if (value is! String || value.isEmpty) {
      return null;
    }
    return DateTime.tryParse(value);
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
  });

  final Uri baseUrl;
  final String accessToken;
}
