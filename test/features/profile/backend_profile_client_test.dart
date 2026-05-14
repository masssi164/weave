import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';

class _RecordingHttpClient extends http.BaseClient {
  _RecordingHttpClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}

http.StreamedResponse _jsonResponse(Map<String, Object?> json) {
  return http.StreamedResponse(
    Stream.value(utf8.encode(jsonEncode(json))),
    200,
    headers: {'content-type': 'application/json'},
  );
}

void main() {
  group('BackendProfileClient', () {
    test('fetches /api/me through the backend identity facade', () async {
      late http.BaseRequest capturedRequest;
      final client = BackendProfileClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'userId': 'user-123',
            'username': 'alice',
            'email': 'alice@example.test',
            'emailVerified': true,
            'displayName': 'Alice Example',
            'locale': 'en',
            'timezone': 'Europe/Berlin',
            'roles': ['member'],
            'groups': ['workspace-default'],
          });
        }),
      );

      final profile = await client.fetchProfile(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
      );

      expect(capturedRequest.url.toString(), 'https://api.weave.local/api/me');
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(profile.displayName, 'Alice Example');
      expect(profile.roles, ['member']);
    });

    test('patches /api/profile through the backend profile facade', () async {
      late http.BaseRequest capturedRequest;
      final client = BackendProfileClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          expect(request, isA<http.Request>());
          expect(
            jsonDecode((request as http.Request).body),
            containsPair('displayName', 'Alice Updated'),
          );
          return _jsonResponse({
            'userId': 'user-123',
            'username': 'alice',
            'email': 'alice@example.test',
            'emailVerified': true,
            'displayName': 'Alice Updated',
            'locale': 'de',
            'timezone': 'Europe/Berlin',
            'roles': ['member'],
            'groups': ['workspace-default'],
          });
        }),
      );

      final profile = await client.updateProfile(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
        update: const UserProfileUpdate(
          displayName: 'Alice Updated',
          locale: 'de',
          timezone: 'Europe/Berlin',
        ),
      );

      expect(capturedRequest.method, 'PATCH');
      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/profile',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(profile.displayName, 'Alice Updated');
      expect(profile.locale, 'de');
    });
  });
}
