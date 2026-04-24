import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';

import 'live_oidc_test_driver.dart';
import 'test_config.dart';

class TestAuthTokens {
  const TestAuthTokens({
    required this.accessToken,
    required this.refreshToken,
    required this.idToken,
    required this.expiresAt,
    required this.tokenType,
    required this.scopes,
  });

  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final DateTime? expiresAt;
  final String? tokenType;
  final List<String> scopes;
}

class AuthHelper {
  AuthHelper();

  Future<String> signIn(TestConfig config) async {
    final tokens = await signInWithTokens(config);
    return tokens.accessToken;
  }

  Future<AuthSession> signInForAppSession(TestConfig config) async {
    final tokens = await signInWithTokens(config);

    return AuthSession(
      issuer: config.issuerUrl,
      clientId: config.clientId,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      idToken: tokens.idToken,
      expiresAt: tokens.expiresAt,
      tokenType: tokens.tokenType,
      scopes: tokens.scopes,
    );
  }

  Future<TestAuthTokens> signInWithTokens(TestConfig config) async {
    config.requireCredentials();

    final tokenBundle = await LiveOidcTestDriver(config: config)
        .authorizeAndExchangeCode(
          AuthConfiguration(
            issuer: config.issuerUrl,
            clientId: config.clientId,
          ),
        );

    return TestAuthTokens(
      accessToken: tokenBundle.accessToken,
      refreshToken: tokenBundle.refreshToken,
      idToken: tokenBundle.idToken,
      expiresAt: tokenBundle.expiresAt,
      tokenType: tokenBundle.tokenType,
      scopes: tokenBundle.scopes,
    );
  }
}
