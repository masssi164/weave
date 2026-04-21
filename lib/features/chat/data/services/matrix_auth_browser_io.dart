import 'dart:async';
import 'dart:io';

import 'package:url_launcher/url_launcher.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser_stub.dart'
    as stub;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

class FlutterWebAuthMatrixAuthBrowser implements MatrixAuthBrowser {
  const FlutterWebAuthMatrixAuthBrowser();

  @override
  Future<Uri> authenticate({
    required Uri authorizationUri,
    required Uri redirectUri,
  }) async {
    if (redirectUri.scheme == 'http' && _isLoopbackHost(redirectUri.host)) {
      return _authenticateWithLoopbackRedirect(
        authorizationUri: authorizationUri,
        redirectUri: redirectUri,
      );
    }

    return const _StubMatrixAuthBrowser().authenticate(
      authorizationUri: authorizationUri,
      redirectUri: redirectUri,
    );
  }

  Future<Uri> _authenticateWithLoopbackRedirect({
    required Uri authorizationUri,
    required Uri redirectUri,
  }) async {
    HttpServer? server;
    try {
      server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        redirectUri.port,
      );
      final callbackFuture = server.first.timeout(const Duration(minutes: 2));
      final launched = await launchUrl(
        authorizationUri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched) {
        throw const ChatFailure.protocol(
          'Unable to open the browser for Matrix sign-in.',
        );
      }

      final request = await callbackFuture;
      final callbackUri = request.requestedUri;
      request.response
        ..statusCode = HttpStatus.ok
        ..headers.contentType = ContentType.html
        ..write(_loopbackCompletionPage);
      await request.response.close();
      return callbackUri;
    } on TimeoutException catch (error) {
      throw ChatFailure.cancelled(
        'Matrix sign-in did not return to the app in time.',
        cause: error,
      );
    } on ChatFailure {
      rethrow;
    } catch (error) {
      throw ChatFailure.unknown(
        'Unable to complete the Matrix browser sign-in flow.',
        cause: error,
      );
    } finally {
      await server?.close(force: true);
    }
  }

  bool _isLoopbackHost(String host) {
    return host == '127.0.0.1' || host == 'localhost';
  }
}

typedef _StubMatrixAuthBrowser = stub.FlutterWebAuthMatrixAuthBrowser;

MatrixAuthBrowser createMatrixAuthBrowser() {
  return const FlutterWebAuthMatrixAuthBrowser();
}

const _loopbackCompletionPage = '''
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>Weave sign-in complete</title>
  </head>
  <body>
    <script>window.close();</script>
    <p>You can return to Weave now.</p>
  </body>
</html>
''';
