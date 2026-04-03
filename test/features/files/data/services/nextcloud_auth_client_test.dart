import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/files/data/services/nextcloud_auth_client.dart';
import 'package:weave/features/files/data/services/nextcloud_login_launcher.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

class _FakeNextcloudLoginLauncher implements NextcloudLoginLauncher {
  Uri? launchedUri;

  @override
  Future<void> launch(Uri loginUri) async {
    launchedUri = loginUri;
  }
}

void main() {
  group('NextcloudAuthClient', () {
    test(
      'createBearerSession resolves the user id over OCS bearer auth',
      () async {
        final client = MockClient((request) async {
          expect(request.headers['Authorization'], 'Bearer oidc-id-token');
          return http.Response(
            jsonEncode({
              'ocs': {
                'data': {'id': 'alice'},
              },
            }),
            200,
          );
        });
        final authClient = NextcloudAuthClient(
          httpClient: client,
          loginLauncher: _FakeNextcloudLoginLauncher(),
        );

        final session = await authClient.createBearerSession(
          configuredBaseUrl: Uri.parse('https://nextcloud.home.internal'),
          bearerToken: 'oidc-id-token',
          accountLabelHint: 'Alice Example',
        );

        expect(session.usesOidcBearer, isTrue);
        expect(session.baseUrl, Uri.parse('https://nextcloud.home.internal/'));
        expect(session.userId, 'alice');
        expect(session.accountLabel, 'Alice Example');
        expect(session.bearerToken, 'oidc-id-token');
      },
    );

    test(
      'connect completes the Login Flow v2 handshake and resolves the user id',
      () async {
        final launcher = _FakeNextcloudLoginLauncher();
        final client = MockClient((request) async {
          if (request.url.path.endsWith('/index.php/login/v2')) {
            return http.Response(
              jsonEncode({
                'poll': {
                  'token': 'poll-token',
                  'endpoint': 'https://nextcloud.home.internal/login/v2/poll',
                },
                'login': 'https://nextcloud.home.internal/login/v2/flow/abc',
              }),
              200,
            );
          }

          if (request.url.path.endsWith('/login/v2/poll')) {
            return http.Response(
              jsonEncode({
                'server': 'https://nextcloud.home.internal',
                'loginName': 'alice@example.com',
                'appPassword': 'app-password',
              }),
              200,
            );
          }

          if (request.url.path.endsWith('/ocs/v2.php/cloud/user')) {
            return http.Response(
              jsonEncode({
                'ocs': {
                  'data': {'id': 'alice'},
                },
              }),
              200,
            );
          }

          throw StateError(
            'Unexpected request: ${request.method} ${request.url}',
          );
        });
        final authClient = NextcloudAuthClient(
          httpClient: client,
          loginLauncher: launcher,
          pollInterval: Duration.zero,
          maxPollAttempts: 1,
        );

        final session = await authClient.connect(
          Uri.parse('https://nextcloud.home.internal'),
        );

        expect(
          launcher.launchedUri,
          Uri.parse('https://nextcloud.home.internal/login/v2/flow/abc'),
        );
        expect(session.usesAppPassword, isTrue);
        expect(session.baseUrl, Uri.parse('https://nextcloud.home.internal/'));
        expect(session.loginName, 'alice@example.com');
        expect(session.userId, 'alice');
        expect(session.appPassword, 'app-password');
      },
    );

    test(
      'connect rejects credentials returned for a different server',
      () async {
        final client = MockClient((request) async {
          if (request.url.path.endsWith('/index.php/login/v2')) {
            return http.Response(
              jsonEncode({
                'poll': {
                  'token': 'poll-token',
                  'endpoint': 'https://nextcloud.home.internal/login/v2/poll',
                },
                'login': 'https://nextcloud.home.internal/login/v2/flow/abc',
              }),
              200,
            );
          }

          if (request.url.path.endsWith('/login/v2/poll')) {
            return http.Response(
              jsonEncode({
                'server': 'https://other-nextcloud.home.internal',
                'loginName': 'alice@example.com',
                'appPassword': 'app-password',
              }),
              200,
            );
          }

          throw StateError(
            'Unexpected request: ${request.method} ${request.url}',
          );
        });
        final authClient = NextcloudAuthClient(
          httpClient: client,
          loginLauncher: _FakeNextcloudLoginLauncher(),
          pollInterval: Duration.zero,
          maxPollAttempts: 1,
        );

        await expectLater(
          authClient.connect(Uri.parse('https://nextcloud.home.internal')),
          throwsA(
            isA<FilesFailure>().having(
              (failure) => failure.type,
              'type',
              FilesFailureType.configuration,
            ),
          ),
        );
      },
    );
  });
}
