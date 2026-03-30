import 'package:weave/features/auth/domain/entities/auth_configuration.dart';

abstract interface class OidcClient {
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  );

  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  });

  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  });
}

class OidcTokenBundle {
  const OidcTokenBundle({
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
