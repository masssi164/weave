import 'dart:convert';
import 'dart:math';

import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

class FlutterAppAuthOidcClient implements OidcClient {
  FlutterAppAuthOidcClient({FlutterAppAuth? appAuth})
    : _appAuth = appAuth ?? const FlutterAppAuth();

  final FlutterAppAuth _appAuth;

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  ) async {
    try {
      final response = await _appAuth.authorizeAndExchangeCode(
        AuthorizationTokenRequest(
          configuration.clientId,
          oidcRedirectUri,
          issuer: configuration.issuer.toString(),
          scopes: oidcDefaultScopes,
          nonce: _generateNonce(),
          allowInsecureConnections: _usesInsecureIssuer(configuration),
        ),
      );

      return _tokenBundleFromResponse(
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        idToken: response.idToken,
        expiresAt: response.accessTokenExpirationDateTime,
        tokenType: response.tokenType,
        scopes: response.scopes,
      );
    } on FlutterAppAuthUserCancelledException catch (error) {
      throw AuthFailure.cancelled('Sign-in was cancelled.', cause: error);
    } on FlutterAppAuthPlatformException catch (error) {
      throw AuthFailure.protocol(
        _messageFromPlatformError(
          error,
          fallback: 'Unable to complete sign-in.',
        ),
        cause: error,
      );
    } catch (error) {
      throw AuthFailure.unknown('Unable to complete sign-in.', cause: error);
    }
  }

  @override
  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  }) async {
    try {
      final response = await _appAuth.token(
        TokenRequest(
          configuration.clientId,
          oidcRedirectUri,
          issuer: configuration.issuer.toString(),
          refreshToken: refreshToken,
          scopes: oidcDefaultScopes,
          allowInsecureConnections: _usesInsecureIssuer(configuration),
        ),
      );

      return _tokenBundleFromResponse(
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        idToken: response.idToken,
        expiresAt: response.accessTokenExpirationDateTime,
        tokenType: response.tokenType,
        scopes: response.scopes,
      );
    } on FlutterAppAuthPlatformException catch (error) {
      throw AuthFailure.protocol(
        _messageFromPlatformError(
          error,
          fallback: 'Unable to refresh the current session.',
        ),
        cause: error,
      );
    } catch (error) {
      throw AuthFailure.unknown(
        'Unable to refresh the current session.',
        cause: error,
      );
    }
  }

  @override
  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  }) async {
    try {
      await _appAuth.endSession(
        EndSessionRequest(
          idTokenHint: idTokenHint,
          postLogoutRedirectUrl: oidcPostLogoutRedirectUri,
          issuer: configuration.issuer.toString(),
          allowInsecureConnections: _usesInsecureIssuer(configuration),
        ),
      );
    } on FlutterAppAuthPlatformException catch (error) {
      throw AuthFailure.protocol(
        _messageFromPlatformError(
          error,
          fallback: 'Unable to log out cleanly.',
        ),
        cause: error,
      );
    } catch (error) {
      throw AuthFailure.unknown('Unable to log out cleanly.', cause: error);
    }
  }

  OidcTokenBundle _tokenBundleFromResponse({
    required String? accessToken,
    required String? refreshToken,
    required String? idToken,
    required DateTime? expiresAt,
    required String? tokenType,
    required List<String>? scopes,
  }) {
    if (accessToken == null || accessToken.isEmpty) {
      throw const AuthFailure.protocol(
        'The identity provider did not return an access token.',
      );
    }

    return OidcTokenBundle(
      accessToken: accessToken,
      refreshToken: refreshToken,
      idToken: idToken,
      expiresAt: expiresAt?.toUtc(),
      tokenType: tokenType,
      scopes: scopes ?? oidcDefaultScopes,
    );
  }

  String _generateNonce() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return base64UrlEncode(bytes).replaceAll('=', '');
  }

  String _messageFromPlatformError(
    FlutterAppAuthPlatformException error, {
    required String fallback,
  }) {
    final details = error.platformErrorDetails;
    final description = details.errorDescription?.trim();
    if (description != null && description.isNotEmpty) {
      return description;
    }

    final oauthError = details.error?.trim();
    if (oauthError != null && oauthError.isNotEmpty) {
      return oauthError;
    }

    final message = error.message?.trim();
    if (message != null && message.isNotEmpty) {
      return message;
    }

    return fallback;
  }

  bool _usesInsecureIssuer(AuthConfiguration configuration) {
    return configuration.issuer.scheme.toLowerCase() == 'http';
  }
}

final oidcClientProvider = Provider<OidcClient>(
  (ref) => FlutterAppAuthOidcClient(),
);
