import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
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
import 'package:xml/xml.dart';

/// Files repository backed by the Weave backend product facade.
///
/// Flutter owns the product UI and calls `weave-backend` only. The backend owns
/// all direct provider access. File list/read data-plane operations use the
/// Weave WebDAV projection; OpenAPI remains the control plane for discovery,
/// setup, readiness, revoke, grants, and generated models. File writes use the
/// same Weave WebDAV projection with fail-closed precondition handling.
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
    // The backend-facade path does not own a separate local provider session.
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(context, (accessToken) async {
      final request = http.Request('PROPFIND', _davUri(context.baseUrl, path))
        ..headers.addAll(_webdavHeaders(accessToken, depth: '1'));
      return http.Response.fromStream(await _httpClient.send(request));
    }, fallbackMessage: 'Unable to load files from the Weave backend.');
    _ensureSuccess(response, successCodes: const {207});
    return _decodeWebDavListing(path, response.body);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    final context = await _requireContext();
    final uploadPath = _childPath(directoryPath, request.fileName);
    final bytes = await _collectUploadBytes(request, onProgress);
    final response = await _sendAuthenticated(
      context,
      (accessToken) async {
        final httpRequest =
            http.Request('PUT', _davUri(context.baseUrl, uploadPath))
              ..headers.addAll({
                ..._webdavHeaders(accessToken),
                'Content-Type': 'application/octet-stream',
                'If-None-Match': '*',
              })
              ..bodyBytes = bytes;
        return http.Response.fromStream(await _httpClient.send(httpRequest));
      },
      fallbackMessage: 'Unable to upload the file through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {201, 204});
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) async {
    final context = await _requireContext();
    final folderPath = _childPath(parentPath, name);
    final response = await _sendAuthenticated(
      context,
      (accessToken) async {
        final request =
            http.Request('MKCOL', _davUri(context.baseUrl, folderPath))
              ..headers.addAll({
                ..._webdavHeaders(accessToken),
                'If-None-Match': '*',
              });
        return http.Response.fromStream(await _httpClient.send(request));
      },
      fallbackMessage: 'Unable to create the folder through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {201});
    final createdPath = _pathFromLocation(response.headers) ?? folderPath;
    return FileEntry(
      id: createdPath,
      name: _fallbackNameFromPath(createdPath),
      path: createdPath,
      isDirectory: true,
    );
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
        _davUri(context.baseUrl, entry.path),
        headers: {'Accept': '*/*', 'Authorization': 'Bearer $accessToken'},
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

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) async {
        final request = http.Request(
          'DELETE',
          _davUri(context.baseUrl, entry.path),
        )..headers.addAll(_webdavHeaders(accessToken));
        return http.Response.fromStream(await _httpClient.send(request));
      },
      fallbackMessage: 'Unable to delete the file through the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {204});
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
        message ?? 'Files access is not allowed for this workspace session.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 400 || response.statusCode == 404) {
      throw FilesFailure.protocol(
        message ?? 'The files request could not be completed.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 409 ||
        response.statusCode == 412 ||
        response.statusCode == 423) {
      throw FilesFailure.protocol(
        message ??
            'The file operation conflicts with the current workspace state.',
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
        message ??
            'Files need admin attention before members can use them reliably.',
        cause: response.statusCode,
      );
    }

    throw FilesFailure.unknown(
      message ?? 'The files request could not be completed right now.',
      cause: response.statusCode,
    );
  }

  DirectoryListing _decodeWebDavListing(String requestedPath, String body) {
    try {
      final normalizedRequestedPath = _normalizeFilesPath(requestedPath);
      final document = XmlDocument.parse(body);
      final entries = <FileEntry>[];
      for (final response in document.descendants.whereType<XmlElement>()) {
        if (response.name.local != 'response') {
          continue;
        }
        final href = _firstElementText(response, 'href');
        if (href == null || href.isEmpty) {
          continue;
        }
        final path = _pathFromDavHref(href);
        if (path == normalizedRequestedPath) {
          continue;
        }
        final displayName =
            _firstElementText(response, 'displayname') ??
            _fallbackNameFromPath(path);
        final isDirectory = response.descendants.whereType<XmlElement>().any(
          (element) => element.name.local == 'collection',
        );
        final size = int.tryParse(
          _firstElementText(response, 'getcontentlength') ?? '',
        );
        final modifiedAt = _parseHttpDate(
          _firstElementText(response, 'getlastmodified'),
        );
        entries.add(
          FileEntry(
            id: path,
            name: displayName,
            path: path,
            isDirectory: isDirectory,
            modifiedAt: modifiedAt,
            sizeInBytes: isDirectory ? null : size,
          ),
        );
      }
      return DirectoryListing(path: normalizedRequestedPath, entries: entries);
    } catch (error) {
      throw const FilesFailure.protocol(
        'The Weave backend returned an invalid WebDAV files listing.',
      );
    }
  }

  String? _errorMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        final memberImpact = payload['memberImpact'];
        if (memberImpact is String && memberImpact.trim().isNotEmpty) {
          return memberImpact;
        }
        final message = payload['message'];
        if (message is String && message.trim().isNotEmpty) {
          return message;
        }
      }
    } catch (_) {
      final description = RegExp(
        r'<[^:>]*:?responsedescription>([^<]+)</[^:>]*:?responsedescription>',
        caseSensitive: false,
      ).firstMatch(body);
      return description == null
          ? null
          : _decodeXmlText(description.group(1) ?? '');
    }
    return null;
  }

  Map<String, String> _webdavHeaders(String accessToken, {String? depth}) {
    return {
      'Accept': 'application/xml',
      if (depth != null) 'Depth': depth,
      'Authorization': 'Bearer $accessToken',
    };
  }

  Uri _davUri(Uri baseUrl, String path) {
    final baseSegments = baseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: true);
    if (baseSegments.isNotEmpty && baseSegments.last == 'api') {
      baseSegments.removeLast();
    }
    final pathSegments = _normalizeFilesPath(
      path,
    ).split('/').where((segment) => segment.isNotEmpty).toList(growable: false);
    return baseUrl.replace(
      pathSegments: [...baseSegments, 'dav', 'files', ...pathSegments],
      queryParameters: null,
    );
  }

  Future<Uint8List> _collectUploadBytes(
    FileUploadRequest request,
    FileUploadProgressCallback? onProgress,
  ) async {
    final builder = BytesBuilder(copy: false);
    var uploaded = 0;
    await for (final chunk in request.byteStream) {
      builder.add(chunk);
      uploaded += chunk.length;
      onProgress?.call(uploaded, request.sizeInBytes);
    }
    return builder.takeBytes();
  }

  String _childPath(String parentPath, String childName) {
    final normalizedParent = _normalizeFilesPath(parentPath);
    final safeName = childName.trim();
    if (safeName.isEmpty || safeName.contains('/')) {
      throw const FilesFailure.protocol(
        'The file name is not valid for the Weave Files facade.',
      );
    }
    if (normalizedParent == '/') {
      return '/$safeName';
    }
    return '$normalizedParent/$safeName';
  }

  String _normalizeFilesPath(String path) {
    final collapsed = path.trim().replaceAll(RegExp('/+'), '/');
    if (collapsed.isEmpty || collapsed == '/') {
      return '/';
    }
    final withLeadingSlash = collapsed.startsWith('/')
        ? collapsed
        : '/$collapsed';
    return withLeadingSlash.endsWith('/') && withLeadingSlash.length > 1
        ? withLeadingSlash.substring(0, withLeadingSlash.length - 1)
        : withLeadingSlash;
  }

  String _pathFromDavHref(String href) {
    final rawPath = Uri.parse(href).path;
    final decoded = Uri.decodeComponent(rawPath);
    const marker = '/dav/files';
    final markerIndex = decoded.indexOf(marker);
    final suffix = markerIndex < 0
        ? decoded
        : decoded.substring(markerIndex + marker.length);
    return _normalizeFilesPath(suffix);
  }

  String? _pathFromLocation(Map<String, String> headers) {
    final location = headers['location'];
    if (location == null || location.trim().isEmpty) {
      return null;
    }
    return _pathFromDavHref(location);
  }

  String? _firstElementText(XmlElement parent, String localName) {
    for (final element in parent.descendants.whereType<XmlElement>()) {
      if (element.name.local == localName) {
        final text = element.innerText.trim();
        return text.isEmpty ? null : text;
      }
    }
    return null;
  }

  String _fallbackNameFromPath(String path) {
    if (path == '/') {
      return 'Files';
    }
    return path.substring(path.lastIndexOf('/') + 1);
  }

  DateTime? _parseHttpDate(String? value) {
    if (value == null || value.trim().isEmpty) {
      return null;
    }
    final match = RegExp(
      r'^[A-Za-z]{3},\s+(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+GMT$',
    ).firstMatch(value);
    if (match == null) {
      return null;
    }
    final month = const {
      'Jan': 1,
      'Feb': 2,
      'Mar': 3,
      'Apr': 4,
      'May': 5,
      'Jun': 6,
      'Jul': 7,
      'Aug': 8,
      'Sep': 9,
      'Oct': 10,
      'Nov': 11,
      'Dec': 12,
    }[match.group(2)];
    if (month == null) {
      return null;
    }
    return DateTime.utc(
      int.parse(match.group(3)!),
      month,
      int.parse(match.group(1)!),
      int.parse(match.group(4)!),
      int.parse(match.group(5)!),
      int.parse(match.group(6)!),
    );
  }

  String _decodeXmlText(String value) {
    return value
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&apos;', "'")
        .replaceAll('&amp;', '&');
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
