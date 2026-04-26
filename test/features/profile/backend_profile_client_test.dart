import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/profile/data/services/backend_profile_client.dart';

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
  });
}
