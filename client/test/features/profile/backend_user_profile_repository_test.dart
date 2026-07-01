import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/profile/data/repositories/backend_user_profile_repository.dart';
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

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
  group('BackendUserProfileRepository', () {
    test('saves profile edits through PATCH /api/profile', () async {
      late http.BaseRequest capturedRequest;
      final repository = BackendUserProfileRepository(
        client: BackendProfileClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
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
        ),
        sessionResolver: () async => WeaveAuthenticatedSession(
          apiBaseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        ),
      );

      final profile = await repository.updateProfile(
        const UserProfileUpdate(
          displayName: 'Alice Updated',
          locale: 'de',
          timezone: 'Europe/Berlin',
        ),
      );

      expect(capturedRequest.method, 'PATCH');
      expect(
        capturedRequest.url.toString(),
        'https://api.weave.test/api/profile',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      final requestBody = jsonDecode((capturedRequest as http.Request).body);
      expect(requestBody, {
        'displayName': 'Alice Updated',
        'locale': 'de',
        'timezone': 'Europe/Berlin',
      });
      expect(requestBody, isNot(contains('avatar')));
      expect(requestBody, isNot(contains('profileVisibility')));
      expect(profile.displayName, 'Alice Updated');
      expect(profile.locale, 'de');
    });
  });
}
