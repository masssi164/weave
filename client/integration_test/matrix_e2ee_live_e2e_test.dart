import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import 'helpers/isolated_stack_scope.dart';
import 'helpers/live_oidc_auth_helper.dart';
import 'helpers/matrix_live_room_driver.dart';
import 'helpers/test_config.dart';
import 'helpers/test_http_overrides.dart';

const _deviceA = 'WEAVELIVEE2EEDEVICEA';
const _deviceB = 'WEAVELIVEE2EEDEVICEB';
const _deviceC = 'WEAVELIVEE2EEDEVICEC';
const _profileA = 'live-e2ee-profile-a';
const _profileB = 'live-e2ee-profile-b';
const _profileC = 'live-e2ee-profile-c';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  // This protocol-only test can inherit host macOS accessibility state. Keep
  // semantics coverage in the dedicated widget gates and avoid a host-owned
  // semantics handle leaking past integration-test teardown.
  binding.platformDispatcher.semanticsEnabledTestValue = false;
  HttpOverrides.global = TestHttpOverrides();

  final config = TestConfig.fromEnvironment();
  final skipTest = !config.hasLiveCredentials;

  testWidgets(
    'two devices encrypt, verify, recover, relaunch, and revoke',
    (tester) async {
      requireIsolatedStackScope();
      final httpClient = createTrustedTestHttpClient();
      const liveAuth = LiveOidcAuthHelper();
      const bridge = RustMatrixCoreBridge();
      final root = await Directory.systemTemp.createTemp(
        'weave-live-matrix-e2ee-',
      );
      final accessTokenA = await liveAuth.accessToken(config);
      final accessTokenB = await liveAuth.accessToken(config);
      final accessTokenC = await liveAuth.accessToken(config);
      final homeserver = config.matrixHomeserverUrl;
      final initializedProfiles = <String>[];
      final runEventIds = <String>{};
      String? provisionedRoomId;
      var scenarioPassed = false;
      Object? chatCleanupError;
      StackTrace? chatCleanupStackTrace;
      late String userId;

      Future<void> initialize(
        String profile,
        String device,
        String passphrase,
        String accessToken,
      ) async {
        final store = Directory('${root.path}/$profile');
        await store.create(recursive: true);
        await bridge.initializeClient(
          profileKey: profile,
          homeserverUrl: homeserver.toString(),
          userId: userId,
          deviceId: device,
          accessToken: accessToken,
          storePath: store.path,
          storePassphrase: passphrase,
        );
        initializedProfiles.add(profile);
        await bridge.syncClient(profileKey: profile);
      }

      try {
        userId = await _whoami(httpClient, homeserver, accessTokenA, _deviceA);
        expect(
          await _whoami(httpClient, homeserver, accessTokenB, _deviceB),
          userId,
        );
        expect(
          await _whoami(httpClient, homeserver, accessTokenC, _deviceC),
          userId,
        );
        provisionedRoomId =
            (await MatrixLiveRoomDriver(
                  client: httpClient,
                  homeserver: homeserver,
                ).createEncryptedRoom(
                  author: MatrixLiveActorCredentials(
                    accessToken: accessTokenA,
                    deviceId: _deviceA,
                  ),
                  roomName:
                      'Weave E2EE live '
                      '${DateTime.now().toUtc().microsecondsSinceEpoch}',
                ))
                .roomId;
        await _deleteCurrentRoomKeyBackup(
          httpClient,
          homeserver,
          accessTokenA,
          _deviceA,
          strict: true,
        );
        await initialize(
          _profileA,
          _deviceA,
          'live-e2ee-store-passphrase-device-a-0001',
          accessTokenA,
        );
        await initialize(
          _profileB,
          _deviceB,
          'live-e2ee-store-passphrase-device-b-0002',
          accessTokenB,
        );
        await _syncProfiles(bridge, <String>[_profileA, _profileB]);
        final authorRooms = await bridge.loadEncryptedRooms(
          profileKey: _profileA,
        );
        final collaboratorRooms = await bridge.loadEncryptedRooms(
          profileKey: _profileB,
        );
        final room = authorRooms.firstWhere(
          (value) => value.roomId == provisionedRoomId && value.encrypted,
          orElse: () => throw TestFailure(
            'The author device did not resolve its provisioned encrypted room.',
          ),
        );
        expect(
          collaboratorRooms.any(
            (value) => value.roomId == room.roomId && value.encrypted,
          ),
          isTrue,
          reason: 'The second device did not resolve the same encrypted room.',
        );
        final message =
            'weave-e2ee-${DateTime.now().toUtc().microsecondsSinceEpoch}';

        runEventIds.add(
          await bridge.sendEncryptedText(
            profileKey: _profileA,
            roomId: room.roomId,
            body: message,
          ),
        );
        await _syncProfiles(bridge, <String>[_profileA, _profileB]);
        final decryptedByA = await _waitForMessage(
          bridge,
          _profileA,
          room.roomId,
          message,
        );
        final rawTimeline = await _rawTimeline(
          httpClient,
          homeserver,
          accessTokenA,
          _deviceA,
          room.roomId,
        );
        expect(rawTimeline, contains('m.room.encrypted'), reason: rawTimeline);
        expect(rawTimeline, isNot(contains(message)));
        expect(decryptedByA.body, message);
        // MATRIX_E2EE_CIPHERTEXT_ONLY
        // ignore: avoid_print
        print(
          'MATRIX_E2EE_CIPHERTEXT_ONLY encrypted=true plaintextOnServer=false',
        );

        await bridge.startVerification(profileKey: _profileA);
        await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileB,
          <String>{'incomingRequest'},
        );
        await bridge.acceptVerification(profileKey: _profileB);
        await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileA,
          <String>{'chooseMethod'},
        );
        await bridge.startSas(profileKey: _profileA);
        await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileB,
          <String>{'waitingForOtherDevice', 'compareSas'},
        );
        await bridge.startSas(profileKey: _profileB);
        final sasA = await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileA,
          <String>{'compareSas'},
        );
        final sasB = await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileB,
          <String>{'compareSas'},
        );
        expect(sasA.sasNumbers, sasB.sasNumbers);
        expect(sasA.sasEmojis, hasLength(7));
        await bridge.confirmSas(profileKey: _profileA, matches: true);
        await _syncProfiles(bridge, <String>[_profileA, _profileB]);
        await bridge.confirmSas(profileKey: _profileB, matches: true);
        await _waitForVerificationPhase(
          bridge,
          <String>[_profileA, _profileB],
          _profileA,
          <String>{'done'},
        );
        // MATRIX_E2EE_TWO_DEVICE_VERIFIED
        // ignore: avoid_print
        print(
          'MATRIX_E2EE_TWO_DEVICE_VERIFIED sasMatched=true accessible=true',
        );

        final recoveryKey = await bridge.bootstrapRecovery(
          profileKey: _profileA,
        );
        expect(recoveryKey, isNotEmpty);
        expect(
          await _currentRoomKeyBackupCount(
            httpClient,
            homeserver,
            accessTokenA,
            _deviceA,
          ),
          greaterThan(0),
        );
        await initialize(
          _profileC,
          _deviceC,
          'live-e2ee-store-passphrase-device-c-0003',
          accessTokenC,
        );
        await bridge.recover(
          profileKey: _profileC,
          recoveryKeyOrPassphrase: recoveryKey,
        );
        await _syncProfiles(bridge, <String>[_profileA, _profileC]);
        final recovered = await _waitForMessage(
          bridge,
          _profileC,
          room.roomId,
          message,
        );
        expect(recovered.body, message);
        // MATRIX_E2EE_RECOVERY_RESTORED
        // ignore: avoid_print
        print('MATRIX_E2EE_RECOVERY_RESTORED historyDecrypted=true');

        await bridge.disposeClient(profileKey: _profileA);
        initializedProfiles.remove(_profileA);
        await initialize(
          _profileA,
          _deviceA,
          'live-e2ee-store-passphrase-device-a-0001',
          accessTokenA,
        );
        final relaunched = await _waitForMessage(
          bridge,
          _profileA,
          room.roomId,
          message,
        );
        expect(relaunched.body, message);
        // MATRIX_E2EE_IPHONE_RELAUNCH
        // ignore: avoid_print
        print(
          'MATRIX_E2EE_IPHONE_RELAUNCH profileStable=true sessionRestored=true',
        );

        final revoke = await httpClient.delete(
          homeserver.replace(
            pathSegments: <String>[
              '_matrix',
              'client',
              'v3',
              'devices',
              _deviceB,
            ],
          ),
          headers: _matrixHeaders(accessTokenA, _deviceA),
        );
        expect(revoke.statusCode, 200, reason: revoke.body);
        final revokedWhoami = await httpClient.get(
          homeserver.replace(
            pathSegments: const <String>[
              '_matrix',
              'client',
              'v3',
              'account',
              'whoami',
            ],
          ),
          headers: _matrixHeaders(accessTokenB, _deviceB),
        );
        expect(revokedWhoami.statusCode, 401, reason: revokedWhoami.body);
        expect(
          (jsonDecode(revokedWhoami.body) as Map<String, dynamic>)['errcode'],
          'M_UNKNOWN_TOKEN',
        );
        expect(
          (await _waitForMessage(bridge, _profileA, room.roomId, message)).body,
          message,
        );
        await expectLater(
          bridge.syncClient(profileKey: _profileB),
          throwsA(
            isA<RustMatrixCoreBridgeException>().having(
              (error) => error.code,
              'errcode',
              'M_UNKNOWN_TOKEN',
            ),
          ),
        );
        final postRevocationMessage =
            'weave-e2ee-after-revoke-'
            '${DateTime.now().toUtc().microsecondsSinceEpoch}';
        runEventIds.add(
          await bridge.sendEncryptedText(
            profileKey: _profileA,
            roomId: room.roomId,
            body: postRevocationMessage,
          ),
        );
        expect(
          (await _waitForMessage(
            bridge,
            _profileA,
            room.roomId,
            postRevocationMessage,
          )).body,
          postRevocationMessage,
        );
        // MATRIX_E2EE_LOST_DEVICE_REVOKED
        // ignore: avoid_print
        print(
          'MATRIX_E2EE_LOST_DEVICE_REVOKED '
          'denied=true remainingEncryptedSend=true',
        );
        scenarioPassed = true;
      } finally {
        final roomId = provisionedRoomId;
        if (roomId != null) {
          final driver = MatrixLiveRoomDriver(
            client: httpClient,
            homeserver: homeserver,
          );
          final actor = MatrixLiveActorCredentials(
            accessToken: accessTokenA,
            deviceId: _deviceA,
          );
          try {
            if (runEventIds.isNotEmpty) {
              final redactedCount = await driver.redactEventsAndVerify(
                actor: actor,
                roomId: roomId,
                eventIds: runEventIds,
              );
              if (redactedCount != runEventIds.length ||
                  (scenarioPassed && redactedCount != 2)) {
                throw const MatrixLiveRoomDriverException(
                  'M_WEAVE_LIVE_MATRIX_CLEANUP_INCOMPLETE',
                );
              }
            }
          } catch (error, stackTrace) {
            chatCleanupError = error;
            chatCleanupStackTrace = stackTrace;
          }
          try {
            await driver.leaveRoom(actor: actor, roomId: roomId);
          } catch (error, stackTrace) {
            chatCleanupError ??= error;
            chatCleanupStackTrace ??= stackTrace;
          }
        }
        for (final profile in initializedProfiles.toSet()) {
          await bridge.disposeClient(profileKey: profile);
        }
        await _deleteCurrentRoomKeyBackup(
          httpClient,
          homeserver,
          accessTokenA,
          _deviceA,
          strict: false,
        );
        httpClient.close();
        if (await root.exists()) {
          await root.delete(recursive: true);
        }
        if (scenarioPassed && chatCleanupError != null) {
          Error.throwWithStackTrace(
            chatCleanupError,
            chatCleanupStackTrace ?? StackTrace.current,
          );
        }
      }
    },
    skip: skipTest,
    semanticsEnabled: false,
  );
}

Future<void> _deleteCurrentRoomKeyBackup(
  http.Client client,
  Uri homeserver,
  String accessToken,
  String deviceId, {
  required bool strict,
}) async {
  final current = await client.get(
    homeserver.replace(
      pathSegments: const <String>[
        '_matrix',
        'client',
        'v3',
        'room_keys',
        'version',
      ],
    ),
    headers: _matrixHeaders(accessToken, deviceId),
  );
  if (current.statusCode == 404) {
    return;
  }
  if (current.statusCode != 200) {
    if (strict) {
      throw TestFailure(
        'Room-key backup isolation failed with HTTP ${current.statusCode}.',
      );
    }
    return;
  }
  final version = (jsonDecode(current.body) as Map<String, dynamic>)['version'];
  if (version is! String || version.isEmpty) {
    if (strict) {
      throw TestFailure('Room-key backup isolation returned no version.');
    }
    return;
  }
  final deleted = await client.delete(
    homeserver.replace(
      pathSegments: <String>[
        '_matrix',
        'client',
        'v3',
        'room_keys',
        'version',
        version,
      ],
    ),
    headers: _matrixHeaders(accessToken, deviceId),
  );
  if (strict && deleted.statusCode != 200) {
    throw TestFailure(
      'Room-key backup cleanup failed with HTTP ${deleted.statusCode}.',
    );
  }
}

Future<int> _currentRoomKeyBackupCount(
  http.Client client,
  Uri homeserver,
  String accessToken,
  String deviceId,
) async {
  final current = await client.get(
    homeserver.replace(
      pathSegments: const <String>[
        '_matrix',
        'client',
        'v3',
        'room_keys',
        'version',
      ],
    ),
    headers: _matrixHeaders(accessToken, deviceId),
  );
  if (current.statusCode != 200) {
    throw TestFailure(
      'Room-key backup evidence failed with HTTP ${current.statusCode}.',
    );
  }
  final count = (jsonDecode(current.body) as Map<String, dynamic>)['count'];
  if (count is! num) {
    throw TestFailure('Room-key backup evidence returned no count.');
  }
  return count.toInt();
}

Future<String> _whoami(
  http.Client client,
  Uri homeserver,
  String accessToken,
  String deviceId,
) async {
  final response = await client.get(
    homeserver.replace(
      pathSegments: const <String>[
        '_matrix',
        'client',
        'v3',
        'account',
        'whoami',
      ],
    ),
    headers: _matrixHeaders(accessToken, deviceId),
  );
  expect(response.statusCode, 200, reason: response.body);
  final payload = jsonDecode(response.body) as Map<String, dynamic>;
  expect(payload['device_id'], deviceId);
  return payload['user_id'] as String;
}

Future<String> _rawTimeline(
  http.Client client,
  Uri homeserver,
  String accessToken,
  String deviceId,
  String roomId,
) async {
  final response = await client.get(
    homeserver.replace(
      pathSegments: <String>[
        '_matrix',
        'client',
        'v3',
        'rooms',
        roomId,
        'messages',
      ],
      queryParameters: const <String, String>{'limit': '100'},
    ),
    headers: _matrixHeaders(accessToken, deviceId),
  );
  expect(response.statusCode, 200, reason: response.body);
  return response.body;
}

Future<RustMatrixMessageProjection> _waitForMessage(
  RustMatrixCoreBridge bridge,
  String profileKey,
  String roomId,
  String body,
) async {
  for (var attempt = 0; attempt < 20; attempt += 1) {
    await bridge.syncClient(profileKey: profileKey);
    final messages = await bridge.loadEncryptedRoomMessages(
      profileKey: profileKey,
      roomId: roomId,
    );
    for (final message in messages) {
      if (message.body == body) {
        return message;
      }
    }
    await Future<void>.delayed(const Duration(milliseconds: 250));
  }
  throw TestFailure('Encrypted message did not become decryptable.');
}

Future<RustMatrixVerificationProjection> _waitForVerificationPhase(
  RustMatrixCoreBridge bridge,
  List<String> profilesToSync,
  String inspectedProfile,
  Set<String> phases,
) async {
  for (var attempt = 0; attempt < 30; attempt += 1) {
    await _syncProfiles(bridge, profilesToSync);
    final state = await bridge.loadSecurityState(profileKey: inspectedProfile);
    if (phases.contains(state.verification.phase)) {
      return state.verification;
    }
    await Future<void>.delayed(const Duration(milliseconds: 250));
  }
  throw TestFailure(
    'Matrix verification did not reach ${phases.join(' or ')}.',
  );
}

Future<void> _syncProfiles(
  RustMatrixCoreBridge bridge,
  List<String> profiles,
) async {
  for (final profile in profiles) {
    await bridge.syncClient(profileKey: profile);
  }
}

Map<String, String> _matrixHeaders(String accessToken, String deviceId) {
  return <String, String>{
    'Authorization': 'Bearer $accessToken',
    'X-Weave-Matrix-Device-Id': deviceId,
    'Accept': 'application/json',
  };
}
