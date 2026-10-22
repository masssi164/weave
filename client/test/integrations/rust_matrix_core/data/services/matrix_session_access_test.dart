import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart' as http_testing;
import 'package:test/test.dart';
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
      'wrong identity issuer',
      {
        'subject': 'member-1',
        'organizationId': 'organization-1',
        'identityIssuer': 'https://other.invalid',
      },
      _capabilities(),
    ),
    (
      'unavailable Chat',
      {'subject': 'member-1', 'organizationId': 'organization-1'},
      _capabilities(readiness: 'unavailable'),
    ),
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
                ? {'identityIssuer': issuer.toString(), ...identity}
                : chat,
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      await expectLater(
        authorize(client),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'localized failure category',
                name == 'unavailable Chat'
                    ? ChatFailureType.serviceUnavailable
                    : name == 'revoked Chat grant' ||
                          name == 'blocked Chat policy'
                    ? ChatFailureType.accessDenied
                    : ChatFailureType.sessionRequired,
              )
              .having(
                (failure) => failure.message,
                'support-safe diagnostic code',
                name == 'unavailable Chat'
                    ? 'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED'
                    : name == 'revoked Chat grant' ||
                          name == 'blocked Chat policy'
                    ? 'M_WEAVE_MATRIX_ACCESS_DENIED'
                    : 'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
              ),
        ),
      );
    });
  }

  for (final path in ['/api/me', '/api/workspace/capabilities']) {
    for (final (name, status, error, expected) in [
      ('rejected session', 401, null, ChatFailureType.sessionRequired),
      ('denied access', 403, null, ChatFailureType.accessDenied),
      ('invalid response', 400, null, ChatFailureType.protocol),
      ('unavailable service', 503, null, ChatFailureType.serviceUnavailable),
      (
        'connection failure',
        null,
        http.ClientException('Private transport detail'),
        ChatFailureType.transport,
      ),
      (
        'request timeout',
        null,
        TimeoutException('Private timeout detail'),
        ChatFailureType.transport,
      ),
    ]) {
      test(
        'keeps the correct admission failure category for $name at $path',
        () async {
          final requests = <String>[];
          final client = http_testing.MockClient((request) async {
            requests.add(request.url.path);
            if (request.url.path == path) {
              if (error != null) throw error;
              return http.Response('Private provider detail', status!);
            }
            return http.Response(
              jsonEncode({
                'subject': 'member-1',
                'organizationId': 'organization-1',
                'identityIssuer': issuer.toString(),
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          });
          addTearDown(client.close);

          await expectLater(
            authorize(client),
            throwsA(
              isA<ChatFailure>()
                  .having(
                    (failure) => failure.type,
                    'recovery category',
                    expected,
                  )
                  .having(
                    (failure) => failure.message,
                    'support-safe diagnostic code',
                    'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
                  )
                  .having(
                    (failure) => failure.cause,
                    'typed cause or real HTTP status',
                    error == null ? status : same(error),
                  ),
            ),
          );
          expect(requests, [
            '/api/me',
            if (path != '/api/me') '/api/workspace/capabilities',
          ]);
        },
      );
    }
  }
}
