import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:intl/intl.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_auth_headers.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';
import 'package:weave/integrations/nextcloud/presentation/providers/nextcloud_provider.dart';
import 'package:xml/xml.dart';

class NextcloudDavClient {
  NextcloudDavClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;
  static final DateFormat _modifiedDateFormat = DateFormat(
    'EEE, dd MMM yyyy HH:mm:ss \'GMT\'',
    'en_US',
  );

  Future<DirectoryListing> listDirectory(
    NextcloudSession session,
    String path,
  ) async {
    _ensureHttpsSession(session);
    final normalizedPath = _normalizePath(path);
    final uri = _buildDirectoryUri(session, normalizedPath);
    final request = http.Request('PROPFIND', uri)
      ..headers.addAll({
        ...buildNextcloudAuthHeaders(session),
        'Depth': '1',
        'Content-Type': 'application/xml; charset=utf-8',
      })
      ..body = _propfindBody;

    late http.StreamedResponse response;
    try {
      response = await _httpClient.send(request);
    } on NextcloudFailure {
      rethrow;
    } catch (error) {
      throw NextcloudFailure.unknown(
        'Unable to load the Nextcloud directory.',
        cause: error,
      );
    }

    final body = await response.stream.bytesToString();
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const NextcloudFailure.invalidCredentials(
        'The saved Nextcloud credentials are no longer valid.',
      );
    }

    if (response.statusCode != 207) {
      throw NextcloudFailure.protocol(
        'Nextcloud returned an unexpected WebDAV status (${response.statusCode}).',
      );
    }

    final document = _parseXml(body);
    final davRootPath = _buildDirectoryUri(session, '/').path;
    final entries = <FileEntry>[];

    for (final responseElement in _elementsByLocalName(document, 'response')) {
      final href = _firstTextByLocalName(responseElement, 'href');
      if (href == null || href.isEmpty) {
        continue;
      }

      final entryPath = _relativePathFromHref(href, davRootPath);
      if (entryPath == normalizedPath) {
        continue;
      }

      final prop = _successfulProp(responseElement);
      if (prop == null) {
        continue;
      }

      final isDirectory = _isDirectory(prop);
      final fileId = (_firstTextByLocalName(prop, 'fileid') ?? entryPath)
          .trim();
      final name = _entryName(prop, entryPath, fallbackId: fileId);
      if (name.isEmpty) {
        continue;
      }

      entries.add(
        FileEntry(
          id: fileId,
          name: name,
          path: entryPath,
          isDirectory: isDirectory,
          modifiedAt: _parseModifiedAt(
            _firstTextByLocalName(prop, 'getlastmodified'),
          ),
          sizeInBytes: isDirectory
              ? null
              : int.tryParse(
                  _firstTextByLocalName(prop, 'getcontentlength') ?? '',
                ),
        ),
      );
    }

    entries.sort((left, right) {
      if (left.isDirectory != right.isDirectory) {
        return left.isDirectory ? -1 : 1;
      }
      return left.name.toLowerCase().compareTo(right.name.toLowerCase());
    });

    return DirectoryListing(path: normalizedPath, entries: entries);
  }

  void _ensureHttpsSession(NextcloudSession session) {
    if (session.baseUrl.scheme.toLowerCase() != 'https') {
      throw const NextcloudFailure.configuration(
        'Use an HTTPS Nextcloud URL before browsing files.',
      );
    }
  }

  XmlDocument _parseXml(String raw) {
    try {
      return XmlDocument.parse(raw);
    } catch (error) {
      throw NextcloudFailure.protocol(
        'Nextcloud returned an invalid WebDAV response.',
        cause: error,
      );
    }
  }

  Iterable<XmlElement> _elementsByLocalName(XmlNode node, String localName) {
    return node.descendants.whereType<XmlElement>().where(
      (element) => element.name.local == localName,
    );
  }

  XmlElement? _successfulProp(XmlElement responseElement) {
    for (final propstat in _elementsByLocalName(responseElement, 'propstat')) {
      final status = _firstTextByLocalName(propstat, 'status') ?? '';
      if (!status.contains(' 200 ')) {
        continue;
      }

      for (final child in propstat.children.whereType<XmlElement>()) {
        if (child.name.local == 'prop') {
          return child;
        }
      }
    }

    return null;
  }

  bool _isDirectory(XmlElement prop) {
    for (final resourceType in _elementsByLocalName(prop, 'resourcetype')) {
      if (_elementsByLocalName(resourceType, 'collection').isNotEmpty) {
        return true;
      }
    }
    return false;
  }

  String? _firstTextByLocalName(XmlElement element, String localName) {
    for (final child in element.descendants.whereType<XmlElement>()) {
      if (child.name.local == localName) {
        return child.innerText;
      }
    }
    return null;
  }

  DateTime? _parseModifiedAt(String? raw) {
    if (raw == null || raw.trim().isEmpty) {
      return null;
    }

    try {
      return _modifiedDateFormat.parseUtc(raw.trim());
    } catch (_) {
      return null;
    }
  }

  String _relativePathFromHref(String href, String davRootPath) {
    final parsed = Uri.parse(href);
    final decodedPath = Uri.decodeFull(parsed.path);
    final rootPath = davRootPath.endsWith('/')
        ? davRootPath.substring(0, davRootPath.length - 1)
        : davRootPath;
    if (!decodedPath.startsWith(rootPath)) {
      throw const NextcloudFailure.protocol(
        'Nextcloud returned a WebDAV entry outside the configured account root.',
      );
    }

    final suffix = decodedPath.substring(rootPath.length);
    return _normalizePath(suffix);
  }

  String _basename(String path) {
    if (path == '/') {
      return '/';
    }
    final segments = path.split('/')..removeWhere((segment) => segment.isEmpty);
    return segments.isEmpty ? '/' : segments.last;
  }

  String _entryName(
    XmlElement prop,
    String entryPath, {
    required String fallbackId,
  }) {
    final displayName = (_firstTextByLocalName(prop, 'displayname') ?? '')
        .trim();
    if (displayName.isNotEmpty) {
      return displayName;
    }

    final fallbackName = _basename(entryPath).trim();
    if (fallbackName.isNotEmpty && fallbackName != '/') {
      return fallbackName;
    }

    return fallbackId;
  }

  String _normalizePath(String path) {
    final trimmed = path.trim();
    if (trimmed.isEmpty || trimmed == '/') {
      return '/';
    }

    var normalized = trimmed.startsWith('/') ? trimmed : '/$trimmed';
    if (normalized.length > 1 && normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }
    return normalized;
  }

  Uri _buildDirectoryUri(NextcloudSession session, String path) {
    final normalizedBaseUrl = normalizeNextcloudBaseUrl(session.baseUrl);
    final encodedUserId = Uri.encodeComponent(session.userId);
    final encodedSegments = _normalizePath(path)
        .split('/')
        .where((segment) => segment.isNotEmpty)
        .map(Uri.encodeComponent)
        .join('/');
    final relativePath = encodedSegments.isEmpty
        ? 'remote.php/dav/files/$encodedUserId/'
        : 'remote.php/dav/files/$encodedUserId/$encodedSegments';
    return normalizedBaseUrl.resolve(relativePath);
  }
}

const _propfindBody = '''<?xml version="1.0"?>
<d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:prop>
    <d:displayname />
    <d:getlastmodified />
    <d:getcontentlength />
    <d:resourcetype />
    <oc:fileid />
  </d:prop>
</d:propfind>
''';

final nextcloudDavClientProvider = Provider<NextcloudDavClient>((ref) {
  return NextcloudDavClient(httpClient: ref.watch(nextcloudHttpClientProvider));
});
