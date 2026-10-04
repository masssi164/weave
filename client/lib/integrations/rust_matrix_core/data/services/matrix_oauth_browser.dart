import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

const matrixOAuthRedirectScheme = 'com.massimotter.weave.matrix';
const matrixOAuthRedirectUri = '$matrixOAuthRedirectScheme:/oauthredirect';

bool isMatrixOAuthRedirect(Uri uri) =>
    uri.scheme == matrixOAuthRedirectScheme &&
    uri.host.isEmpty &&
    uri.path == '/oauthredirect';

Uri validateMatrixOAuthRedirect(Uri uri, {required String state}) {
  final states = uri.queryParametersAll['state'];
  if (!isMatrixOAuthRedirect(uri) ||
      uri.hasFragment ||
      state.isEmpty ||
      states?.length != 1 ||
      states!.single != state) {
    throw const ChatFailure.protocol('M_WEAVE_MATRIX_OAUTH_REDIRECT');
  }
  return uri;
}

Uri validateMatrixOAuthAuthorizationUrl(
  Uri uri, {
  required String state,
  required bool allowInsecureAuthorization,
}) {
  final states = uri.queryParametersAll['state'];
  if (!uri.hasAuthority ||
      uri.host.isEmpty ||
      (uri.scheme != 'https' &&
          !(allowInsecureAuthorization && uri.scheme == 'http')) ||
      uri.userInfo.isNotEmpty ||
      uri.hasFragment ||
      state.isEmpty ||
      states?.length != 1 ||
      states!.single != state) {
    throw const ChatFailure.protocol('M_WEAVE_MATRIX_OAUTH_REQUEST');
  }
  return uri;
}

abstract interface class MatrixOAuthBrowser {
  Future<Uri> authorize(
    Uri authorizationUrl, {
    required String state,
    required bool allowInsecureAuthorization,
  });
}

class SystemMatrixOAuthBrowser implements MatrixOAuthBrowser {
  SystemMatrixOAuthBrowser({AppLinks? appLinks})
    : _appLinks = appLinks ?? AppLinks();

  final AppLinks _appLinks;

  @override
  Future<Uri> authorize(
    Uri authorizationUrl, {
    required String state,
    required bool allowInsecureAuthorization,
  }) async {
    validateMatrixOAuthAuthorizationUrl(
      authorizationUrl,
      state: state,
      allowInsecureAuthorization: allowInsecureAuthorization,
    );

    final callback = Completer<Uri>();
    final subscription = _appLinks.uriLinkStream.listen(
      (uri) {
        if (!isMatrixOAuthRedirect(uri) || callback.isCompleted) return;
        try {
          callback.complete(validateMatrixOAuthRedirect(uri, state: state));
        } on ChatFailure catch (failure) {
          callback.completeError(failure);
        }
      },
      onError: (Object error) {
        if (!callback.isCompleted) {
          callback.completeError(
            ChatFailure.protocol('M_WEAVE_MATRIX_OAUTH_CALLBACK', cause: error),
          );
        }
      },
    );
    try {
      final launched = await launchUrl(
        authorizationUrl,
        mode: LaunchMode.externalApplication,
      );
      if (!launched) {
        throw const ChatFailure.configuration('M_WEAVE_MATRIX_OAUTH_BROWSER');
      }
      return await callback.future.timeout(
        const Duration(minutes: 5),
        onTimeout: () =>
            throw const ChatFailure.cancelled('M_WEAVE_MATRIX_OAUTH_TIMEOUT'),
      );
    } finally {
      await subscription.cancel();
    }
  }
}
