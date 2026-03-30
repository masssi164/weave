import 'package:weave/features/auth/domain/entities/auth_session.dart';

AuthSession buildTestAuthSession({
  String issuer = 'https://auth.home.internal',
  String clientId = 'weave-mobile',
  String accessToken = 'access-token',
  String? refreshToken = 'refresh-token',
  String? idToken = 'id-token',
  DateTime? expiresAt,
  String? tokenType = 'Bearer',
  List<String> scopes = const ['openid', 'profile', 'email', 'offline_access'],
}) {
  return AuthSession(
    issuer: Uri.parse(issuer),
    clientId: clientId,
    accessToken: accessToken,
    refreshToken: refreshToken,
    idToken: idToken,
    expiresAt:
        expiresAt ?? DateTime.now().toUtc().add(const Duration(hours: 1)),
    tokenType: tokenType,
    scopes: scopes,
  );
}
