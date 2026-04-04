import 'package:http/http.dart' as http;
import 'package:weave/integrations/nextcloud/data/services/nextcloud_auth_headers.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

abstract interface class NextcloudDavAccessValidator {
  Future<void> validateRootAccess(NextcloudSession session);
}

class HttpNextcloudDavAccessValidator implements NextcloudDavAccessValidator {
  HttpNextcloudDavAccessValidator({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  @override
  Future<void> validateRootAccess(NextcloudSession session) async {
    _ensureHttpsSession(session);
    final request =
        http.Request(
            'PROPFIND',
            normalizeNextcloudBaseUrl(session.baseUrl).resolve(
              'remote.php/dav/files/${Uri.encodeComponent(session.userId)}/',
            ),
          )
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
        'Unable to validate Nextcloud WebDAV access.',
        cause: error,
      );
    }

    await response.stream.drain<void>();
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
  }

  void _ensureHttpsSession(NextcloudSession session) {
    if (session.baseUrl.scheme.toLowerCase() != 'https') {
      throw const NextcloudFailure.configuration(
        'Use an HTTPS Nextcloud URL before validating WebDAV access.',
      );
    }
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
