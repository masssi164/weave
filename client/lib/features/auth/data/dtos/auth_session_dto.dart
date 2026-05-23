import 'dart:convert';

import 'package:weave/features/auth/domain/entities/auth_session.dart';

class AuthSessionDto {
  const AuthSessionDto({
    required this.issuer,
    required this.clientId,
    required this.accessToken,
    required this.refreshToken,
    required this.idToken,
    required this.expiresAt,
    required this.tokenType,
    required this.scopes,
  });

  factory AuthSessionDto.fromSession(AuthSession session) {
    return AuthSessionDto(
      issuer: session.issuer.toString(),
      clientId: session.clientId,
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      idToken: session.idToken,
      expiresAt: session.expiresAt?.toUtc().toIso8601String(),
      tokenType: session.tokenType,
      scopes: session.scopes,
    );
  }

  factory AuthSessionDto.fromJson(Map<String, dynamic> json) {
    return AuthSessionDto(
      issuer: json['issuer'] as String,
      clientId: json['clientId'] as String,
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String?,
      idToken: json['idToken'] as String?,
      expiresAt: json['expiresAt'] as String?,
      tokenType: json['tokenType'] as String?,
      scopes: (json['scopes'] as List<dynamic>? ?? const <dynamic>[])
          .cast<String>(),
    );
  }

  final String issuer;
  final String clientId;
  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final String? expiresAt;
  final String? tokenType;
  final List<String> scopes;

  AuthSession toSession() {
    return AuthSession(
      issuer: Uri.parse(issuer),
      clientId: clientId,
      accessToken: accessToken,
      refreshToken: refreshToken,
      idToken: idToken,
      expiresAt: expiresAt == null ? null : DateTime.parse(expiresAt!),
      tokenType: tokenType,
      scopes: scopes,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'issuer': issuer,
      'clientId': clientId,
      'accessToken': accessToken,
      'refreshToken': refreshToken,
      'idToken': idToken,
      'expiresAt': expiresAt,
      'tokenType': tokenType,
      'scopes': scopes,
    };
  }

  String encode() => jsonEncode(toJson());

  static AuthSessionDto decode(String raw) {
    return AuthSessionDto.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }
}
