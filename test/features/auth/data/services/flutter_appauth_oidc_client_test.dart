import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

class _FakeFlutterAppAuth extends FlutterAppAuth {
  AuthorizationTokenRequest? authorizationRequest;
  TokenRequest? tokenRequest;
  EndSessionRequest? endSessionRequest;

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
      },
    );

    test(
      'requests the workspace API scope for authorize and refresh',
      () async {
        final appAuth = _FakeFlutterAppAuth();
        final client = FlutterAppAuthOidcClient(appAuth: appAuth);
        final configuration = AuthConfiguration(
          issuer: Uri(scheme: 'https', host: 'auth.home.internal'),
          clientId: 'weave-app',
        );

        await client.authorizeAndExchangeCode(configuration);
        await client.refresh(configuration, refreshToken: 'refresh-token');

        expect(
          appAuth.authorizationRequest?.scopes,
          contains(oidcWorkspaceScope),
        );
        expect(appAuth.tokenRequest?.scopes, contains(oidcWorkspaceScope));
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
  });
}
