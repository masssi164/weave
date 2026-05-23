import 'package:weave/features/auth/domain/entities/auth_configuration.dart';

class AuthSession {
  const AuthSession({
    required this.issuer,
    required this.clientId,
    required this.accessToken,
    required this.refreshToken,
    required this.idToken,
    required this.expiresAt,
    required this.tokenType,
    required this.scopes,
  });

  final Uri issuer;
  final String clientId;
  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final DateTime? expiresAt;
  final String? tokenType;
  final List<String> scopes;

  bool get hasRefreshToken => (refreshToken?.trim().isNotEmpty ?? false);

  bool get requiresRefresh {
    final expiresAt = this.expiresAt;
    if (expiresAt == null) {
      return false;
    }

    return !expiresAt.toUtc().isAfter(
      DateTime.now().toUtc().add(const Duration(seconds: 30)),
    );
  }

  bool matches(AuthConfiguration configuration) {
    return issuer.toString() == configuration.issuer.toString() &&
        clientId == configuration.clientId;
  }
}
