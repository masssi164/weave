import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

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
  AuthHelper({http.Client? httpClient}) : _httpClient = httpClient;

  final http.Client? _httpClient;

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

    final ownsClient = _httpClient == null;
    final client = _httpClient ?? http.Client();
    try {
      final discovery = await _readDiscovery(client, config.issuerUrl);
      final tokenEndpoint = _readUri(discovery, 'token_endpoint');

      final response = await client.post(
        tokenEndpoint,
        headers: const <String, String>{
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: <String, String>{
          'grant_type': 'password',
          'client_id': config.clientId,
          'username': config.username,
          'password': config.password,
          'scope': oidcDefaultScopes.join(' '),
        },
      );

      if (response.statusCode != 200) {
        throw StateError(
          'OIDC sign-in failed for issuer ${config.issuerUrl} '
          'with HTTP ${response.statusCode}: ${_responseSummary(response.body)}',
        );
      }

      final payload = _decodeObject(response.body, 'OIDC token response');
      final accessToken = payload['access_token'];
      if (accessToken is! String || accessToken.isEmpty) {
        throw StateError('OIDC sign-in did not return an access token.');
      }

      final expiresIn = payload['expires_in'];
      return TestAuthTokens(
        accessToken: accessToken,
        refreshToken: payload['refresh_token'] as String?,
        idToken: payload['id_token'] as String?,
        expiresAt: expiresIn is int
            ? DateTime.now().toUtc().add(Duration(seconds: expiresIn))
            : null,
        tokenType: payload['token_type'] as String?,
        scopes: _scopesFrom(payload['scope']),
      );
    } finally {
      if (ownsClient) {
        client.close();
      }
    }
  }

  Future<Map<String, dynamic>> _readDiscovery(
    http.Client client,
    Uri issuer,
  ) async {
    final response = await client.get(
      _discoveryUri(issuer),
      headers: const <String, String>{'Accept': 'application/json'},
    );

    if (response.statusCode != 200) {
      throw StateError(
        'Unable to read OIDC discovery document for $issuer '
        '(HTTP ${response.statusCode}): ${_responseSummary(response.body)}',
      );
    }

    return _decodeObject(response.body, 'OIDC discovery document');
  }

  Uri _discoveryUri(Uri issuer) {
    return issuer.replace(
      pathSegments: [
        ...issuer.pathSegments.where((segment) => segment.isNotEmpty),
        '.well-known',
        'openid-configuration',
      ],
    );
  }

  Uri _readUri(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.isEmpty) {
      throw StateError('OIDC discovery document is missing "$key".');
    }

    final parsed = Uri.tryParse(value);
    if (parsed == null || !parsed.isAbsolute) {
      throw StateError('OIDC discovery value "$key" is not an absolute URL.');
    }

    return parsed;
  }

  Map<String, dynamic> _decodeObject(String body, String label) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('$label was not a JSON object.');
    }

    return decoded;
  }

  List<String> _scopesFrom(Object? value) {
    if (value is String && value.trim().isNotEmpty) {
      return value.split(' ').where((scope) => scope.isNotEmpty).toList();
    }

    return oidcDefaultScopes;
  }

  String _responseSummary(String body) {
    final trimmed = body.trim();
    if (trimmed.isEmpty) {
      return '<empty response body>';
    }

    return trimmed.length <= 240 ? trimmed : '${trimmed.substring(0, 240)}...';
  }
}
