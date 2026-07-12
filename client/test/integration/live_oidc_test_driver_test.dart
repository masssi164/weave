import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/helpers/live_oidc_test_driver.dart';
import '../../integration_test/helpers/test_config.dart';

void main() {
  test(
    'browser-like PKCE auth supports separate username and password steps',
    () async {
      const username = 'live-author';
      const password = 'live-password';
      final submissions = <String, Map<String, String>>{};
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final subscription = server.listen((request) async {
        switch (request.uri.path) {
          case '/authorize':
            await _writeHtml(request.response, '''
            <form action="/username" method="post">
              <input name="username" type="text">
              <input name="login" type="submit" value="Sign In">
            </form>
            ''');
          case '/username':
            submissions['username'] = await _readForm(request);
            await _writeHtml(request.response, '''
            <form action="/password" method="post">
              <input name="password" type="password">
              <input name="login" type="submit" value="Sign In">
            </form>
            ''');
          case '/password':
            submissions['password'] = await _readForm(request);
            request.response.statusCode = HttpStatus.found;
            request.response.headers.set(
              HttpHeaders.locationHeader,
              'com.massimotter.weave:/oauthredirect?code=test-code&state=test-state',
            );
            await request.response.close();
          default:
            request.response.statusCode = HttpStatus.notFound;
            await request.response.close();
        }
      });
      addTearDown(() async {
        await subscription.cancel();
        await server.close(force: true);
      });
      final origin = Uri.parse('http://127.0.0.1:${server.port}');
      final driver = LiveOidcTestDriver(
        config: TestConfig(
          baseUrl: origin,
          username: username,
          password: password,
          issuerUrl: origin,
          clientId: 'weave-app',
          matrixHomeserverUrl: origin,
          nextcloudBaseUrl: origin,
          backendApiBaseUrl: origin,
          offlineContractOnly: false,
        ),
      );

      final redirected = await driver.authenticate(
        authorizationUri: origin.replace(path: '/authorize'),
        redirectUri: Uri.parse('com.massimotter.weave:/oauthredirect'),
      );

      expect(redirected.queryParameters['code'], 'test-code');
      expect(submissions['username'], <String, String>{
        'username': username,
        'login': 'Sign In',
      });
      expect(submissions['password'], <String, String>{
        'password': password,
        'login': 'Sign In',
      });
    },
  );
}

Future<Map<String, String>> _readForm(HttpRequest request) async {
  final body = await utf8.decoder.bind(request).join();
  return Uri.splitQueryString(body);
}

Future<void> _writeHtml(HttpResponse response, String html) async {
  response.headers.contentType = ContentType.html;
  response.write(html);
  await response.close();
}
