import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/files/data/services/nextcloud_dav_client.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

class _ThrowingHttpClient extends http.BaseClient {
  _ThrowingHttpClient(this.error);

  final Object error;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return Future<http.StreamedResponse>.error(error);
  }
}

void main() {
  group('NextcloudDavClient', () {
    test('listDirectory maps WebDAV responses into file entries', () async {
      final client = NextcloudDavClient(
        httpClient: MockClient((request) async {
          expect(request.method, 'PROPFIND');
          expect(request.headers['Authorization'], startsWith('Basic '));
          return http.Response(_multistatusResponse, 207);
        }),
      );

      final listing = await client.listDirectory(
        NextcloudSession.appPassword(
          baseUrl: Uri.parse('https://nextcloud.home.internal/'),
          loginName: 'alice@example.com',
          userId: 'alice',
          appPassword: 'app-password',
        ),
        '/',
      );

      expect(listing.path, '/');
      expect(listing.entries, hasLength(2));
      expect(listing.entries.first.name, 'Documents');
      expect(listing.entries.first.isDirectory, isTrue);
      expect(listing.entries.last.name, 'Notes.txt');
      expect(listing.entries.last.isDirectory, isFalse);
    });

    test('listDirectory supports OIDC bearer authorization', () async {
      final client = NextcloudDavClient(
        httpClient: MockClient((request) async {
          expect(request.headers['Authorization'], 'Bearer oidc-access-token');
          return http.Response(_multistatusResponse, 207);
        }),
      );

      final listing = await client.listDirectory(
        NextcloudSession.oidcBearer(
          baseUrl: Uri.parse('https://nextcloud.home.internal/'),
          userId: 'alice',
          bearerToken: 'oidc-access-token',
        ),
        '/',
      );

      expect(listing.entries, hasLength(2));
    });

    test('listDirectory accepts HTTP sessions for local dev stacks', () async {
      final client = NextcloudDavClient(
        httpClient: MockClient((request) async {
          expect(
            request.url,
            Uri.parse('http://files.home.internal/remote.php/dav/files/alice/'),
          );
          return http.Response(_multistatusResponse, 207);
        }),
      );

      final listing = await client.listDirectory(
        NextcloudSession.oidcBearer(
          baseUrl: Uri.parse('http://files.home.internal/'),
          userId: 'alice',
          bearerToken: 'oidc-access-token',
        ),
        '/',
      );

      expect(listing.entries, hasLength(2));
    });

    test('listDirectory surfaces invalid credentials from WebDAV', () async {
      final client = NextcloudDavClient(
        httpClient: MockClient((request) async => http.Response('', 401)),
      );

      await expectLater(
        client.listDirectory(
          NextcloudSession.appPassword(
            baseUrl: Uri.parse('https://nextcloud.home.internal/'),
            loginName: 'alice@example.com',
            userId: 'alice',
            appPassword: 'app-password',
          ),
          '/',
        ),
        throwsA(
          isA<NextcloudFailure>().having(
            (failure) => failure.type,
            'type',
            NextcloudFailureType.invalidCredentials,
          ),
        ),
      );
    });

    test(
      'listDirectory preserves typed Nextcloud failures from custom transports',
      () async {
        const failure = NextcloudFailure.sessionRequired(
          'Reconnect Nextcloud because the saved app password is incomplete.',
        );
        final client = NextcloudDavClient(
          httpClient: _ThrowingHttpClient(failure),
        );

        await expectLater(
          client.listDirectory(
            NextcloudSession.appPassword(
              baseUrl: Uri.parse('https://nextcloud.home.internal/'),
              loginName: 'alice@example.com',
              userId: 'alice',
              appPassword: 'app-password',
            ),
            '/',
          ),
          throwsA(
            isA<NextcloudFailure>()
                .having((value) => value.type, 'type', failure.type)
                .having((value) => value.message, 'message', failure.message),
          ),
        );
      },
    );
  });
}

const _multistatusResponse = '''<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:response>
    <d:href>/remote.php/dav/files/alice/</d:href>
    <d:propstat>
      <d:status>HTTP/1.1 200 OK</d:status>
      <d:prop>
        <d:displayname>alice</d:displayname>
        <d:resourcetype><d:collection /></d:resourcetype>
      </d:prop>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/alice/Documents/</d:href>
    <d:propstat>
      <d:status>HTTP/1.1 200 OK</d:status>
      <d:prop>
        <d:displayname>Documents</d:displayname>
        <d:resourcetype><d:collection /></d:resourcetype>
        <oc:fileid>10</oc:fileid>
      </d:prop>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/alice/Notes.txt</d:href>
    <d:propstat>
      <d:status>HTTP/1.1 200 OK</d:status>
      <d:prop>
        <d:displayname>Notes.txt</d:displayname>
        <d:getcontentlength>12</d:getcontentlength>
        <oc:fileid>11</oc:fileid>
      </d:prop>
    </d:propstat>
  </d:response>
</d:multistatus>
''';
