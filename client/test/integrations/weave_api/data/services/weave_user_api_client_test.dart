import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart' as http_testing;
import 'package:http/http.dart' as http;
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

void main() {
  test(
    'binds the generated User operation to the authenticated API origin',
    () async {
      final httpClient = http_testing.MockClient((request) async {
        expect(request.url.toString(), 'https://api.weave.test/api/me');
        expect(request.headers['Authorization'], 'Bearer member-token');
        return http.Response(
          '{"userId":"account-1","username":"alice","roles":["member"]}',
          200,
          headers: {'content-type': 'application/json'},
        );
      });
      final client = weaveUserApiClient(
        apiBaseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'member-token',
        httpClient: httpClient,
      );

      final response = await user_api.IdentityApi(client).me();

      expect(response?.userId, 'account-1');
      expect(response?.roles, ['member']);
    },
  );

  test('rejects a non-API base path before making a request', () {
    expect(
      () => weaveUserApiClient(
        apiBaseUrl: Uri.parse('https://api.weave.test/admin'),
        accessToken: 'member-token',
        httpClient: http_testing.MockClient(
          (_) async => http.Response('', 500),
        ),
      ),
      throwsArgumentError,
    );
  });

  test('generated enum map preserves typed capability states', () {
    final manifest = user_api.OrganizationManifestResponse.fromJson({
      'memberCapabilityStates': {'files': 'available'},
    });

    expect(
      manifest?.memberCapabilityStates['files'],
      user_api.OrganizationManifestResponseMemberCapabilityStatesEnum.available,
    );
    expect(
      () => user_api.OrganizationManifestResponse.fromJson({
        'memberCapabilityStates': {'files': 'unknown'},
      }),
      throwsFormatException,
    );
  });

  test('generated profile PATCH omits unset optional preferences', () {
    final request = user_api.UpdateProductProfileRequest(
      displayName: 'Alice Updated',
    );

    expect(request.toJson(), containsPair('displayName', 'Alice Updated'));
    expect(request.toJson(), isNot(contains('accessibilityPreferences')));
  });
}
