import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import '../../integration_test/helpers/matrix_live_room_driver.dart';

const _author = MatrixLiveActorCredentials(
  accessToken: 'author-access-token',
  deviceId: 'WEAVEAUTHORDEVICE',
);
const _collaborator = MatrixLiveActorCredentials(
  accessToken: 'collaborator-access-token',
  deviceId: 'WEAVECOLLABORATORDEVICE',
);
const _roomId = '!room-e2e:api.weave.test';

void main() {
  test('registers actors, creates, encrypts, and joins in order', () async {
    final requests = <http.Request>[];
    final responses = <http.Response>[
      _jsonResponse(<String, Object>{
        'user_id': '@author:api.weave.test',
        'device_id': _author.deviceId,
      }),
      _jsonResponse(<String, Object>{
        'user_id': '@collaborator:api.weave.test',
        'device_id': _collaborator.deviceId,
      }),
      _deviceKeysResponse(
        userId: '@collaborator:api.weave.test',
        deviceId: _collaborator.deviceId,
      ),
      _deviceKeysResponse(
        userId: '@author:api.weave.test',
        deviceId: _author.deviceId,
      ),
      _jsonResponse(<String, Object>{'room_id': _roomId}),
      _jsonResponse(<String, Object>{'event_id': r'$encryption'}),
      _jsonResponse(<String, Object>{'room_id': _roomId}),
      _jsonResponse(<String, Object>{'algorithm': matrixMegolmV1Algorithm}),
      _jsonResponse(<String, Object>{'algorithm': matrixMegolmV1Algorithm}),
    ];
    final client = MockClient((request) async {
      requests.add(request);
      return responses[requests.length - 1];
    });

    final provisioned =
        await MatrixLiveRoomDriver(
          client: client,
          homeserver: Uri.parse('https://api.weave.test'),
        ).createEncryptedRoom(
          author: _author,
          collaborator: _collaborator,
          roomName: 'unique encrypted room',
        );

    expect(provisioned.roomId, _roomId);
    expect(provisioned.authorUserId, '@author:api.weave.test');
    expect(provisioned.collaboratorUserId, '@collaborator:api.weave.test');
    expect(requests.map((request) => request.method), <String>[
      'GET',
      'GET',
      'POST',
      'POST',
      'POST',
      'PUT',
      'POST',
      'GET',
      'GET',
    ]);
    expect(requests[2].url.path, '/_matrix/client/v3/keys/query');
    expect(jsonDecode(requests[2].body), <String, Object>{
      'device_keys': <String, List<String>>{
        '@collaborator:api.weave.test': <String>[_collaborator.deviceId],
      },
    });
    expect(requests[3].url.path, '/_matrix/client/v3/keys/query');
    expect(
      requests[3].headers['X-Weave-Matrix-Device-Id'],
      _collaborator.deviceId,
    );
    expect(requests[4].url.path, '/_matrix/client/v3/createRoom');
    expect(jsonDecode(requests[4].body), <String, Object>{
      'name': 'unique encrypted room',
      'preset': 'private_chat',
      'invite': <String>['@collaborator:api.weave.test'],
    });
    expect(
      requests[5].url.path,
      '/_matrix/client/v3/rooms/!room-e2e:api.weave.test/state/m.room.encryption',
    );
    expect(
      requests[6].url.path,
      '/_matrix/client/v3/join/!room-e2e:api.weave.test',
    );
    expect(
      requests[6].headers['X-Weave-Matrix-Device-Id'],
      _collaborator.deviceId,
    );
  });

  test(
    'waits for delayed mutual device-key discovery in both directions',
    () async {
      final requests = <http.Request>[];
      var collaboratorQueries = 0;
      final client = MockClient((request) async {
        requests.add(request);
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final requested = body['device_keys'] as Map<String, dynamic>;
        final userId = requested.keys.single;
        if (userId == '@collaborator:api.weave.test') {
          collaboratorQueries += 1;
          if (collaboratorQueries == 1) {
            return _jsonResponse(<String, Object>{
              'device_keys': <String, Object>{},
            });
          }
          return _deviceKeysResponse(
            userId: userId,
            deviceId: _collaborator.deviceId,
          );
        }
        return _deviceKeysResponse(userId: userId, deviceId: _author.deviceId);
      });

      await MatrixLiveRoomDriver(
        client: client,
        homeserver: Uri.parse('https://api.weave.test'),
        deviceKeyConvergenceTimeout: const Duration(seconds: 1),
        deviceKeyPollInterval: Duration.zero,
      ).requireMutualDeviceKeys(
        author: _author,
        authorUserId: '@author:api.weave.test',
        collaborator: _collaborator,
        collaboratorUserId: '@collaborator:api.weave.test',
      );

      expect(requests, hasLength(3));
      expect(
        requests.map((request) => request.url.path),
        everyElement('/_matrix/client/v3/keys/query'),
      );
      expect(
        requests.map((request) => request.headers['Authorization']),
        <String>[
          'Bearer ${_author.accessToken}',
          'Bearer ${_author.accessToken}',
          'Bearer ${_collaborator.accessToken}',
        ],
      );
    },
  );

  test('device-key timeout exposes no provider payload', () {
    const secretBody = 'raw-device-key-secret';
    final driver = MatrixLiveRoomDriver(
      client: MockClient(
        (request) async => _jsonResponse(<String, Object>{
          'device_keys': <String, Object>{},
          'provider_debug': secretBody,
        }),
      ),
      homeserver: Uri.parse('https://api.weave.test'),
      deviceKeyConvergenceTimeout: Duration.zero,
      deviceKeyPollInterval: Duration.zero,
    );

    expect(
      driver.requireMutualDeviceKeys(
        author: _author,
        authorUserId: '@author:api.weave.test',
        collaborator: _collaborator,
        collaboratorUserId: '@collaborator:api.weave.test',
      ),
      throwsA(
        isA<MatrixLiveRoomDriverException>()
            .having(
              (error) => error.code,
              'code',
              'M_WEAVE_LIVE_MATRIX_DEVICE_KEYS_NOT_CONVERGED',
            )
            .having(
              (error) => error.toString(),
              'support-safe text',
              isNot(contains(secretBody)),
            ),
      ),
    );
  });

  test('redacts both events, verifies empty content, then leaves', () async {
    final requests = <http.Request>[];
    final client = MockClient((request) async {
      requests.add(request);
      if (requests.length <= 2) {
        return _jsonResponse(<String, Object>{'event_id': r'$redaction'});
      }
      if (requests.length == 3) {
        return _jsonResponse(<String, Object>{
          'chunk': <Map<String, Object>>[
            <String, Object>{
              'event_id': r'$event-a:api.weave.test',
              'type': 'm.room.encrypted',
              'content': <String, Object>{},
            },
            <String, Object>{
              'event_id': r'$event-b:api.weave.test',
              'type': 'm.room.encrypted',
              'content': <String, Object>{},
            },
          ],
        });
      }
      return _jsonResponse(<String, Object>{});
    });
    final driver = MatrixLiveRoomDriver(
      client: client,
      homeserver: Uri.parse('https://api.weave.test'),
    );

    final redactedCount = await driver.redactEventsAndVerify(
      actor: _author,
      roomId: _roomId,
      eventIds: const <String>{
        r'$event-a:api.weave.test',
        r'$event-b:api.weave.test',
      },
    );
    await driver.leaveRoom(actor: _author, roomId: _roomId);

    expect(redactedCount, 2);
    expect(requests.map((request) => request.method), <String>[
      'PUT',
      'PUT',
      'GET',
      'POST',
    ]);
    expect(requests[0].url.path, contains('/redact/'));
    expect(requests[1].url.path, contains('/redact/'));
    expect(
      requests[3].url.path,
      '/_matrix/client/v3/rooms/!room-e2e:api.weave.test/leave',
    );
  });

  test('finds and encrypts the exact joined canonical room', () async {
    var requestIndex = 0;
    final client = MockClient((request) async {
      requestIndex += 1;
      return switch (requestIndex) {
        1 => _jsonResponse(<String, Object>{
          'joined_rooms': <String>[
            '!channel-general:api.weave.test',
            '!other:api.weave.test',
          ],
        }),
        2 => _jsonResponse(<String, Object>{'event_id': r'$encryption'}),
        _ => _jsonResponse(<String, Object>{
          'algorithm': matrixMegolmV1Algorithm,
        }),
      };
    });
    final driver = MatrixLiveRoomDriver(
      client: client,
      homeserver: Uri.parse('https://api.weave.test'),
    );

    final roomId = await driver.requireJoinedRoom(
      actor: _author,
      conversationIdFragment: 'channel-general',
    );
    await driver.enableEncryptionOnJoinedRoom(actor: _author, roomId: roomId);

    expect(roomId, '!channel-general:api.weave.test');
    expect(requestIndex, 3);
  });

  test('non-2xx and malformed responses expose only support-safe codes', () {
    const secretBody =
        'Authorization: Bearer raw-token; room=!secret:api.weave.test';
    final nonSuccessDriver = MatrixLiveRoomDriver(
      client: MockClient((request) async => http.Response(secretBody, 503)),
      homeserver: Uri.parse('https://api.weave.test'),
    );
    final malformedDriver = MatrixLiveRoomDriver(
      client: MockClient((request) async => http.Response(secretBody, 200)),
      homeserver: Uri.parse('https://api.weave.test'),
    );

    expect(
      nonSuccessDriver.registerWhoami(_author),
      throwsA(
        isA<MatrixLiveRoomDriverException>()
            .having(
              (error) => error.code,
              'code',
              'M_WEAVE_LIVE_MATRIX_WHOAMI_HTTP_503',
            )
            .having(
              (error) => error.toString(),
              'support-safe text',
              isNot(contains('raw-token')),
            ),
      ),
    );
    expect(
      malformedDriver.registerWhoami(_author),
      throwsA(
        isA<MatrixLiveRoomDriverException>()
            .having(
              (error) => error.code,
              'code',
              'M_WEAVE_LIVE_MATRIX_WHOAMI_INVALID',
            )
            .having(
              (error) => error.toString(),
              'support-safe text',
              isNot(contains('raw-token')),
            ),
      ),
    );
  });
}

http.Response _jsonResponse(Map<String, Object> body) {
  return http.Response(
    jsonEncode(body),
    200,
    headers: const <String, String>{'content-type': 'application/json'},
  );
}

http.Response _deviceKeysResponse({
  required String userId,
  required String deviceId,
}) {
  return _jsonResponse(<String, Object>{
    'device_keys': <String, Object>{
      userId: <String, Object>{
        deviceId: <String, Object>{
          'user_id': userId,
          'device_id': deviceId,
          'keys': <String, String>{'ed25519:$deviceId': 'public-key'},
        },
      },
    },
  });
}
