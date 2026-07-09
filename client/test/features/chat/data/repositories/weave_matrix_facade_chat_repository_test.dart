import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }

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
  AuthConfiguration? signOutConfiguration;

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
  Future<void> signOut(AuthConfiguration configuration) async {
    signOutConfiguration = configuration;
  }
}

void main() {
  late _FakeServerConfigurationRepository configurationRepository;
  late _FakeAuthSessionRepository authSessionRepository;

  WeaveMatrixFacadeChatRepository repository(http.Client client) {
    return WeaveMatrixFacadeChatRepository(
      serverConfigurationRepository: configurationRepository,
      authSessionRepository: authSessionRepository,
      httpClient: client,
    );
  }

  setUp(() {
    configurationRepository = _FakeServerConfigurationRepository(
      buildTestConfiguration(matrixHomeserverUrl: 'https://api.weave.test'),
    );
    authSessionRepository = _FakeAuthSessionRepository(
      AuthState.authenticated(
        buildTestAuthSession(accessToken: 'weave-oidc-token'),
      ),
    );
  });

  test('connect validates the OIDC-gated Rust Matrix facade', () async {
    // MATRIX_CONNECT_CONTRACT
    late http.Request capturedRequest;
    final client = MockClient((request) async {
      capturedRequest = request;
      return http.Response(
        jsonEncode({
          'versions': ['v1.18'],
          'weaveBoundary': 'northbound-matrix-client-server',
          'canonicalDomain': 'chat',
          'providerDataPlaneExposed': false,
          'matrixCore': {
            'protocolSurface': 'matrix-client-server-facade',
            'oidcGatekeeper': 'spring-boot-resource-server',
            'northboundHomeserverDependency': false,
            'rustProtocolCore': 'ruma-serde-serde_json-thiserror-tracing',
            'serverJniBoundary': 'server-jni-wrapper',
            'flutterBridgeBoundary': 'flutter-rust-bridge',
            'nativeLinked': true,
            'serverName': 'api.weave.test',
            'supportedMatrixVersions': ['v1.18'],
            'supportedEndpoints': ['GET /_matrix/client/v3/sync'],
          },
        }),
        200,
      );
    });

    await repository(client).connect();

    expect(
      capturedRequest.url.toString(),
      'https://api.weave.test/_matrix/client/versions',
    );
    expect(capturedRequest.headers['authorization'], 'Bearer weave-oidc-token');
  });

  test(
    'maps Matrix sync rooms from the Weave facade into chat entities',
    () async {
      // MATRIX_SPACES_ROOMS_CONTRACT
      final client = MockClient((request) async {
        expect(request.url.path, '/_matrix/client/v3/sync');
        return http.Response(
          jsonEncode({
            'next_batch': 'weave.s1.6e657874',
            'rooms': {
              'join': {
                '!quiet:api.weave.test': _room(
                  title: 'Quiet',
                  body: 'Earlier update',
                  timestamp: 1778244000000,
                  unreadCount: 0,
                ),
                '!general:api.weave.test': _room(
                  title: 'General',
                  body: 'Hello from the Matrix facade',
                  timestamp: 1778244300000,
                  unreadCount: 2,
                ),
              },
            },
            'matrixCore': {'flutterBridgeBoundary': 'flutter-rust-bridge'},
          }),
          200,
        );
      });

      final conversations = await repository(client).loadConversations();

      expect(conversations.map((room) => room.title), ['General', 'Quiet']);
      expect(conversations.first.id, '!general:api.weave.test');
      expect(conversations.first.previewType, ChatConversationPreviewType.text);
      expect(conversations.first.previewText, 'Hello from the Matrix facade');
      expect(conversations.first.unreadCount, 2);
      expect(conversations.first.isDirectMessage, isFalse);
    },
  );

  test('sends through Matrix facade and reads room messages', () async {
    // MATRIX_MESSAGE_CONTRACT
    final requests = <http.Request>[];
    final client = MockClient((request) async {
      requests.add(request);
      if (request.method == 'PUT') {
        expect(
          request.url.path,
          '/_matrix/client/v3/rooms/!general%3Aapi.weave.test/send/m.room.message/${request.url.pathSegments.last}',
        );
        expect(jsonDecode(request.body), {
          'msgtype': 'm.text',
          'body': 'hello through Matrix facade',
        });
        return http.Response(
          jsonEncode({'event_id': r'$sent:api.weave.test'}),
          200,
        );
      }
      expect(
        request.url.path,
        '/_matrix/client/v3/rooms/!general%3Aapi.weave.test/messages',
      );
      return http.Response(
        jsonEncode({
          'chunk': [
            {
              'type': 'm.room.message',
              'event_id': r'$sent:api.weave.test',
              'sender': '@user_alice:api.weave.test',
              'origin_server_ts': 1778244300000,
              'content': {
                'msgtype': 'm.text',
                'body': 'hello through Matrix facade',
              },
            },
          ],
        }),
        200,
      );
    });
    final chat = repository(client);

    await chat.sendMessage(
      roomId: '!general:api.weave.test',
      message: 'hello through Matrix facade',
    );
    final timeline = await chat.loadRoomTimeline('!general:api.weave.test');

    expect(requests.map((request) => request.method), ['PUT', 'GET']);
    expect(timeline.roomId, '!general:api.weave.test');
    expect(timeline.messages.single.contentType, ChatMessageContentType.text);
    expect(
      timeline.messages.single.deliveryState,
      ChatMessageDeliveryState.sent,
    );
    expect(timeline.messages.single.text, 'hello through Matrix facade');
    expect(timeline.messages.single.senderDisplayName, 'user alice');
  });

  test(
    'fails support-safely when the Matrix facade rejects a request',
    () async {
      final client = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'errcode': 'M_FORBIDDEN',
            'error': 'support-safe denial',
            'providerDataPlaneExposed': false,
          }),
          403,
        );
      });

      expect(
        repository(client).loadConversations(),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                isNot(contains('access_token')),
              )
              .having(
                (failure) => failure.message,
                'message',
                isNot(contains('homeserver')),
              ),
        ),
      );
    },
  );

  test('fails clearly when setup or session is missing', () async {
    final client = MockClient((_) async => http.Response('{}', 200));
    configurationRepository.configuration = null;

    await expectLater(
      repository(client).loadConversations(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.configuration,
        ),
      ),
    );

    configurationRepository.configuration = buildTestConfiguration();
    authSessionRepository.state = const AuthState.signedOut();

    await expectLater(
      repository(client).loadConversations(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.sessionRequired,
        ),
      ),
    );
  });
}

Map<String, Object?> _room({
  required String title,
  required String body,
  required int timestamp,
  int unreadCount = 0,
}) {
  return {
    'summary': {'m.joined_member_count': 1, 'm.invited_member_count': 0},
    'state': {
      'events': [
        {
          'type': 'm.room.name',
          'state_key': '',
          'event_id': r'$state:api.weave.test',
          'sender': '@weave:api.weave.test',
          'origin_server_ts': timestamp,
          'content': {'name': title},
        },
      ],
    },
    'timeline': {
      'events': [
        {
          'type': 'm.room.message',
          'event_id': r'$message:api.weave.test',
          'sender': '@user_alice:api.weave.test',
          'origin_server_ts': timestamp,
          'content': {'msgtype': 'm.text', 'body': body},
        },
      ],
    },
    'unread_notifications': {
      'notification_count': unreadCount,
      'highlight_count': 0,
    },
  };
}
