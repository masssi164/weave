import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

class _FakeFlutterAppAuth extends FlutterAppAuth {
  AuthorizationTokenRequest? authorizationRequest;
  TokenRequest? tokenRequest;
  EndSessionRequest? endSessionRequest;
  FlutterAppAuthPlatformException? tokenFailure;

  @override
  Future<AuthorizationTokenResponse> authorizeAndExchangeCode(
    AuthorizationTokenRequest request,
  ) async {
    authorizationRequest = request;
    return AuthorizationTokenResponse(
      'access-token',
      'refresh-token',
      null,
      'id-token',
      'Bearer',
      ['openid'],
      null,
      null,
    );
  }

  @override
  Future<TokenResponse> token(TokenRequest request) async {
    tokenRequest = request;
    final failure = tokenFailure;
    if (failure != null) {
      throw failure;
    }
    return TokenResponse(
      'access-token',
      'refresh-token',
      null,
      'id-token',
      'Bearer',
      ['openid'],
      null,
    );
  }

  @override
  Future<EndSessionResponse> endSession(EndSessionRequest request) async {
    endSessionRequest = request;
    return EndSessionResponse('state');
  }
}

void main() {
  group('FlutterAppAuthOidcClient', () {
    test(
      'enables insecure connections for HTTP issuer authorize/refresh/logout',
      () async {
        final appAuth = _FakeFlutterAppAuth();
        final client = FlutterAppAuthOidcClient(appAuth: appAuth);
        final configuration = AuthConfiguration(
          issuer: Uri(scheme: 'http', host: 'auth.home.internal'),
          clientId: 'weave-app',
        );

        await client.authorizeAndExchangeCode(configuration);
        await client.refresh(configuration, refreshToken: 'refresh-token');
        await client.endSession(configuration, idTokenHint: 'id-token');

        expect(appAuth.authorizationRequest?.allowInsecureConnections, isTrue);
        expect(appAuth.tokenRequest?.allowInsecureConnections, isTrue);
        expect(appAuth.endSessionRequest?.allowInsecureConnections, isTrue);
        expect(appAuth.authorizationRequest?.scopes, oidcDefaultScopes);
        expect(appAuth.tokenRequest?.scopes, oidcDefaultScopes);
      },
    );

    test('keeps secure defaults for HTTPS issuers', () async {
      final appAuth = _FakeFlutterAppAuth();
      final client = FlutterAppAuthOidcClient(appAuth: appAuth);
      final configuration = AuthConfiguration(
        issuer: Uri(scheme: 'https', host: 'auth.home.internal'),
        clientId: 'weave-app',
      );

      await client.authorizeAndExchangeCode(configuration);

      expect(appAuth.authorizationRequest?.allowInsecureConnections, isFalse);
    });

    test(
      'marks only invalid_grant refresh failures as session rejection',
      () async {
        final appAuth = _FakeFlutterAppAuth();
        final client = FlutterAppAuthOidcClient(appAuth: appAuth);
        final configuration = AuthConfiguration(
          issuer: Uri(scheme: 'https', host: 'auth.home.internal'),
          clientId: 'weave-app',
        );
        appAuth.tokenFailure = FlutterAppAuthPlatformException(
          code: 'token_failed',
          platformErrorDetails: FlutterAppAuthPlatformErrorDetails(
            error: FlutterAppAuthOAuthError.invalidGrant,
          ),
        );

        await expectLater(
          client.refresh(configuration, refreshToken: 'revoked-refresh-token'),
          throwsA(
            isA<AuthFailure>().having(
              (failure) => failure.invalidatesSavedSession,
              'invalidatesSavedSession',
              isTrue,
            ),
          ),
        );

        appAuth.tokenFailure = FlutterAppAuthPlatformException(
          code: 'network_failed',
          platformErrorDetails: FlutterAppAuthPlatformErrorDetails(),
        );
        await expectLater(
          client.refresh(
            configuration,
            refreshToken: 'retryable-refresh-token',
          ),
          throwsA(
            isA<AuthFailure>().having(
              (failure) => failure.invalidatesSavedSession,
              'invalidatesSavedSession',
              isFalse,
            ),
          ),
        );
      },
    );
  });
}
