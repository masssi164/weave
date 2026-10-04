import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart' as http_testing;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_session_access.dart';

Map<String, Object?> _capabilities({
  bool enabled = true,
  String policyState = 'allowed',
  String readiness = 'ready',
  List<String> grants = const ['chat.read'],
}) {
  final disabled = {
    'enabled': false,
    'policyState': 'disabled',
    'readiness': 'unavailable',
    'grantedCapabilities': <String>[],
  };
  return {
    for (final key in [
      'adminControlPlane',
      'agentRuntimeControl',
      'boards',
      'calendar',
      'chat',
      'decisionsEvidence',
      'documentsCollaboration',
      'files',
      'manualsHelp',
      'meetingsCalls',
      'releaseEvidence',
      'shellAccess',
    ])
      key: key == 'chat'
          ? {
              'enabled': enabled,
              'policyState': policyState,
              'readiness': readiness,
              'grantedCapabilities': grants,
            }
          : disabled,
  };
}

void main() {
  final apiBase = Uri.parse('https://api.weave.test/api');
  final issuer = Uri.parse('https://auth.weave.test/realms/weave');

  Future<MatrixSessionAccess> authorize(
    http.Client client, {
    String expectedSubject = 'member-1',
  }) => GeneratedMatrixSessionAccess(httpClient: client).authorize(
    userApiBaseUrl: apiBase,
    weaveAccessToken: 'weave-only-token',
    expectedSubject: expectedSubject,
    expectedIssuer: issuer,
  );

  test(
    'checks current member and Chat grant with generated User operations',
    () async {
      final requestedPaths = <String>[];
      final client = http_testing.MockClient((request) async {
        requestedPaths.add(request.url.path);
        expect(request.url.origin, apiBase.origin);
        expect(request.headers['Authorization'], 'Bearer weave-only-token');
        return http.Response(
          jsonEncode(
            request.url.path == '/api/me'
                ? {
                    'subject': 'member-1',
                    'organizationId': 'organization-1',
                    'identityIssuer': issuer.toString(),
                  }
                : _capabilities(),
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      final access = await authorize(client);

      expect(access.organizationId, 'organization-1');
      expect(access.subject, 'member-1');
      expect(requestedPaths, ['/api/me', '/api/workspace/capabilities']);
    },
  );

  for (final (name, identity, chat) in [
    (
      'wrong member',
      {'subject': 'other', 'organizationId': 'organization-1'},
      _capabilities(),
    ),
    ('missing organization', {'subject': 'member-1'}, _capabilities()),
    (
      'revoked Chat grant',
      {'subject': 'member-1', 'organizationId': 'organization-1'},
      _capabilities(grants: const []),
    ),
    (
      'blocked Chat policy',
      {'subject': 'member-1', 'organizationId': 'organization-1'},
      _capabilities(policyState: 'policy_blocked'),
    ),
  ]) {
    test('fails closed for $name', () async {
      final client = http_testing.MockClient((request) async {
        return http.Response(
          jsonEncode(
            request.url.path == '/api/me'
                ? {...identity, 'identityIssuer': issuer.toString()}
                : chat,
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      await expectLater(authorize(client), throwsA(isA<ChatFailure>()));
    });
  }

  test(
    'does not authorize Chat when the Weave API rejects the session',
    () async {
      final client = http_testing.MockClient(
        (_) async => http.Response('', 401),
      );

      await expectLater(authorize(client), throwsA(isA<ChatFailure>()));
    },
  );
}
