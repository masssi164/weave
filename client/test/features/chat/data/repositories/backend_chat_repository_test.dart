import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/dtos/chat_openapi_mappers.dart';
import 'package:weave/features/chat/data/repositories/backend_chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/domain/entities/openapi_feature_adapter.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeAuthSessionRepository implements AuthSessionRepository {
  _FakeAuthSessionRepository(this.state);

  AuthState state;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

void main() {
  late _FakeServerConfigurationRepository configurationRepository;
  late _FakeAuthSessionRepository authSessionRepository;

  BackendChatRepository repository(http.Client client) {
    return BackendChatRepository(
      serverConfigurationRepository: configurationRepository,
      authSessionRepository: authSessionRepository,
      httpClient: client,
    );
  }

  setUp(() {
    configurationRepository = _FakeServerConfigurationRepository(
      buildTestConfiguration(backendApiBaseUrl: 'https://api.weave.test/api'),
    );
    authSessionRepository = _FakeAuthSessionRepository(
      AuthState.authenticated(buildTestAuthSession(accessToken: 'chat-token')),
    );
  });

  test('lists conversations through generated OpenAPI DTO mapping', () async {
    late http.Request capturedRequest;
    final client = MockClient((request) async {
      capturedRequest = request;
      return http.Response(
        jsonEncode({
          'domain': 'chat',
          'source': 'weave-backend',
          'releaseStatus': 'canonical-domain-facade',
          'conversations': [
            {
              'id': 'chat:quiet',
              'contextId': 'workspace-default',
              'title': 'Quiet',
              'kind': 'channel',
              'lastMessageAt': '2026-05-01T09:00:00Z',
              'membership': {
                'principalRef': 'user:me',
                'state': 'joined',
                'role': 'member',
              },
              'historyPolicy': {
                'policyKey': 'workspace-default-history',
                'visibility': 'joined-members',
                'backendReadable': true,
                'encryptedProviderContentRedacted': true,
              },
              'attachmentPolicy': {
                'attachmentRefsSupported': true,
                'maxAttachmentRefs': 8,
                'rawProviderMediaUrlsExposed': false,
              },
              'availableActions': ['send-message'],
            },
            {
              'id': 'chat:recent',
              'contextId': 'workspace-default',
              'title': 'Recent',
              'kind': 'direct',
              'lastMessageAt': '2026-05-01T10:00:00Z',
              'membership': {
                'principalRef': 'user:me',
                'state': 'joined',
                'role': 'member',
              },
              'historyPolicy': {
                'policyKey': 'workspace-default-history',
                'visibility': 'joined-members',
                'backendReadable': true,
                'encryptedProviderContentRedacted': true,
              },
              'attachmentPolicy': {
                'attachmentRefsSupported': true,
                'maxAttachmentRefs': 8,
                'rawProviderMediaUrlsExposed': false,
              },
              'availableActions': ['send-message'],
            },
          ],
          'readiness': {
            'impactState': 'usable',
            'memberImpact': 'Weave Chat is available.',
            'diagnosticsRedacted': true,
            'grantedCapabilities': ['chat.message.read'],
          },
        }),
        200,
      );
    });

    final conversations = await repository(client).loadConversations();

    expect(
      capturedRequest.url.toString(),
      'https://api.weave.test/api/chat/conversations',
    );
    expect(capturedRequest.headers['authorization'], 'Bearer chat-token');
    expect(conversations.map((conversation) => conversation.id), [
      'chat:recent',
      'chat:quiet',
    ]);
    expect(conversations.first.isDirectMessage, isTrue);
  });

  test(
    'loads timeline and sends message through OpenAPI DTO mapping',
    () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        if (request.method == 'POST') {
          expect(jsonDecode(request.body), {
            'attachmentRefs': <Object>[],
            'text': 'Hello Weave',
          });
          return http.Response(
            jsonEncode({
              'id': 'message:2',
              'conversationId': 'chat:recent',
              'senderRef': 'user:me',
              'sentAt': '2026-05-01T10:01:00Z',
              'isMine': true,
              'text': 'Hello Weave',
              'attachmentRefs': <Object>[],
              'encryptedProviderContentRedacted': false,
              'deliveryEvidence': <String, Object>{},
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'conversationId': 'chat:recent',
            'readiness': {
              'impactState': 'usable',
              'memberImpact': 'Weave Chat is available.',
              'diagnosticsRedacted': true,
              'grantedCapabilities': ['chat.message.read'],
            },
            'messages': [
              {
                'id': 'message:1',
                'conversationId': 'chat:recent',
                'senderRef': 'user:alex',
                'sentAt': '2026-05-01T10:00:00Z',
                'isMine': false,
                'text': 'Ready',
                'attachmentRefs': <Object>[],
                'encryptedProviderContentRedacted': false,
                'deliveryEvidence': <String, Object>{},
              },
            ],
          }),
          200,
        );
      });
      final backend = repository(client);

      final timeline = await backend.loadRoomTimeline('chat:recent');
      await backend.sendMessage(roomId: 'chat:recent', message: 'Hello Weave');

      expect(timeline.roomId, 'chat:recent');
      expect(timeline.messages.single.senderId, 'user:alex');
      expect(requests.map((request) => request.method), ['GET', 'POST']);
    },
  );

  test('fails closed when Chat readiness is not available', () async {
    final client = MockClient(
      (_) async => http.Response(
        jsonEncode({
          'domain': 'chat',
          'memberState': 'misconfigured',
          'memberImpact': 'Ask an admin to finish Chat setup.',
          'supportSafe': true,
          'downstreamDiagnosticsExposedToMember': false,
        }),
        200,
      ),
    );

    await expectLater(
      repository(client).connect(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.message,
          'message',
          'Ask an admin to finish Chat setup.',
        ),
      ),
    );
  });

  test('maps server Chat readiness states into adapter states', () {
    expect(
      const openapi.ChatReadiness(
        memberState: 'ready',
        memberImpact: 'Ready.',
      ).toFeatureReadiness().state,
      OpenApiFeatureCapabilityState.available,
    );
    expect(
      const openapi.ChatReadiness(
        memberState: 'misconfigured',
        memberImpact: 'Ask an admin to finish Chat setup.',
      ).toFeatureReadiness().state,
      OpenApiFeatureCapabilityState.notConfigured,
    );
    expect(
      const openapi.ChatReadiness(
        memberState: 'disabled',
        memberImpact: 'Chat is disabled.',
      ).toFeatureReadiness().state,
      OpenApiFeatureCapabilityState.disabled,
    );
    expect(
      const openapi.ChatReadinessResponse(
        impactState: 'policy-blocked',
        memberImpact: 'Chat is blocked by policy.',
        grantedCapabilities: <String>[],
        diagnosticsRedacted: true,
      ).toFeatureReadiness().state,
      OpenApiFeatureCapabilityState.disabledByPolicy,
    );
  });

  test('fails closed when generated Chat payload misses required fields', () {
    final client = MockClient(
      (_) async => http.Response(
        jsonEncode({
          'domain': 'chat',
          'releaseStatus': 'canonical-domain-facade',
          'source': 'weave-backend',
          'readiness': {
            'impactState': 'usable',
            'memberImpact': 'Weave Chat is available.',
            'diagnosticsRedacted': true,
            'grantedCapabilities': ['chat.message.read'],
          },
        }),
        200,
      ),
    );

    expect(
      repository(client).loadConversations(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.message,
          'message',
          'The Weave Chat facade returned an invalid conversation list.',
        ),
      ),
    );
  });
}
