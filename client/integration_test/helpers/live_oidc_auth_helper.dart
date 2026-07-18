import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';

import 'live_oidc_test_driver.dart';
import 'test_config.dart';

/// Establishes live test sessions through OIDC authorization code + PKCE.
///
/// This helper is intended for protocol-oriented tests that need a session but
/// do not own an application provider graph. App-flow tests should call the
/// real [AuthSessionRepository] through their provider container instead.
class LiveOidcAuthHelper {
  const LiveOidcAuthHelper();

  Future<AuthSession> signIn(TestConfig config) async {
    config.requireCredentials();
    final configuration = AuthConfiguration(
      issuer: config.issuerUrl,
      clientId: config.clientId,
    );
    final tokens = await LiveOidcTestDriver(
      config: config,
    ).authorizeAndExchangeCode(configuration);

    return AuthSession(
      issuer: configuration.issuer,
      clientId: configuration.clientId,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      idToken: tokens.idToken,
      expiresAt: tokens.expiresAt,
      tokenType: tokens.tokenType,
      scopes: tokens.scopes,
    );
  }

  Future<String> accessToken(TestConfig config) async {
    return (await signIn(config)).accessToken;
  }
}
