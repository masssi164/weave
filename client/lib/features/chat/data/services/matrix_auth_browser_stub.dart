import 'package:flutter/services.dart';
import 'package:flutter_web_auth_2/flutter_web_auth_2.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

class FlutterWebAuthMatrixAuthBrowser implements MatrixAuthBrowser {
  const FlutterWebAuthMatrixAuthBrowser();

  @override
  Future<Uri> authenticate({
    required Uri authorizationUri,
    required Uri redirectUri,
  }) async {
    try {
      final result = await FlutterWebAuth2.authenticate(
        url: authorizationUri.toString(),
        callbackUrlScheme: redirectUri.scheme,
        options: redirectUri.scheme == 'https'
            ? FlutterWebAuth2Options(
                httpsHost: redirectUri.host,
                httpsPath: redirectUri.path,
              )
            : const FlutterWebAuth2Options(),
      );
      return Uri.parse(result);
    } on PlatformException catch (error) {
      if (error.code == 'CANCELED') {
        throw ChatFailure.cancelled(
          'Matrix sign-in was cancelled before it completed.',
          cause: error,
        );
      }

      final message = error.message?.trim();
      throw ChatFailure.protocol(
        message == null || message.isEmpty
            ? 'Unable to complete the Matrix browser sign-in flow.'
            : message,
        cause: error,
      );
    } catch (error) {
      throw ChatFailure.unknown(
        'Unable to complete the Matrix browser sign-in flow.',
        cause: error,
      );
    }
  }
}

MatrixAuthBrowser createMatrixAuthBrowser() {
  return const FlutterWebAuthMatrixAuthBrowser();
}
