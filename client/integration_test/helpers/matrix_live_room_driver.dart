import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

const matrixMegolmV1Algorithm = 'm.megolm.v1.aes-sha2';

class MatrixLiveActorCredentials {
  const MatrixLiveActorCredentials({
    required this.accessToken,
    required this.deviceId,
  });

  final String accessToken;
  final String deviceId;
}

class MatrixLiveRoomProvisioning {
  const MatrixLiveRoomProvisioning({
    required this.roomId,
    required this.authorUserId,
    this.collaboratorUserId,
  });

  final String roomId;
  final String authorUserId;
  final String? collaboratorUserId;
}

/// Drives only real Matrix Client-Server facade routes used to arrange live
/// encrypted-room preconditions and clean up run-created messages.
class MatrixLiveRoomDriver {
  MatrixLiveRoomDriver({
    required this.client,
    required this.homeserver,
    this.deviceKeyConvergenceTimeout = const Duration(seconds: 45),
    this.deviceKeyPollInterval = const Duration(seconds: 1),
  });

  final http.Client client;
  final Uri homeserver;
  final Duration deviceKeyConvergenceTimeout;
  final Duration deviceKeyPollInterval;

  Future<String> registerWhoami(MatrixLiveActorCredentials actor) async {
    final response = await client.get(
      _uri(<String>['_matrix', 'client', 'v3', 'account', 'whoami']),
      headers: _headers(actor),
    );
    _requireSuccess(response, operation: 'whoami');
    final payload = _object(response.body, operation: 'whoami');
    final userId = payload['user_id'];
    if (userId is! String ||
        userId.isEmpty ||
        payload['device_id'] != actor.deviceId) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_IDENTITY_INVALID',
      );
    }
    return userId;
  }

  Future<MatrixLiveRoomProvisioning> createEncryptedRoom({
    required MatrixLiveActorCredentials author,
    required String roomName,
    MatrixLiveActorCredentials? collaborator,
    bool requireColdCollaboratorDevice = false,
    bool pruneStaleActorDevices = false,
  }) async {
    final authorUserId = await registerWhoami(author);
    final collaboratorUserId = collaborator == null
        ? null
        : await registerWhoami(collaborator);
    if (collaboratorUserId == authorUserId) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_ACTORS_NOT_DISTINCT',
      );
    }

    if (collaborator != null && collaboratorUserId != null) {
      // A newly opened Rust crypto profile can authenticate before its device
      // keys and one-time key material have converged through the northbound
      // facade. Prove both current devices first; otherwise stale-device
      // pruning can mistake that transient empty projection for a missing
      // authenticated device and fail before the bounded convergence wait.
      await requireMutualDeviceKeys(
        author: author,
        authorUserId: authorUserId,
        collaborator: collaborator,
        collaboratorUserId: collaboratorUserId,
      );
      if (pruneStaleActorDevices) {
        await retainOnlyCurrentDevice(actor: author, userId: authorUserId);
        await retainOnlyCurrentDevice(
          actor: collaborator,
          userId: collaboratorUserId,
        );
      }
      if (requireColdCollaboratorDevice || pruneStaleActorDevices) {
        await requireExactCurrentDevices(
          observer: author,
          targetUserId: collaboratorUserId,
          expectedDeviceIds: <String>{collaborator.deviceId},
        );
      }
      if (pruneStaleActorDevices) {
        await requireExactCurrentDevices(
          observer: collaborator,
          targetUserId: authorUserId,
          expectedDeviceIds: <String>{author.deviceId},
        );
      }
    }

    final createResponse = await client.post(
      _uri(<String>['_matrix', 'client', 'v3', 'createRoom']),
      headers: _jsonHeaders(author),
      body: jsonEncode(<String, Object>{
        'name': roomName,
        'preset': 'private_chat',
        if (collaboratorUserId != null) 'invite': <String>[collaboratorUserId],
        'initial_state': <Map<String, Object>>[
          <String, Object>{
            'type': 'm.room.encryption',
            'state_key': '',
            'content': <String, String>{'algorithm': matrixMegolmV1Algorithm},
          },
        ],
      }),
    );
    _requireSuccess(createResponse, operation: 'create-room');
    final roomId = _object(
      createResponse.body,
      operation: 'create-room',
    )['room_id'];
    if (roomId is! String || roomId.isEmpty) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_ROOM_INVALID',
      );
    }

    if (collaborator != null) {
      final joinResponse = await client.post(
        _uri(<String>['_matrix', 'client', 'v3', 'join', roomId]),
        headers: _jsonHeaders(collaborator),
        body: '{}',
      );
      _requireSuccess(joinResponse, operation: 'join-room');
      final joinedRoomId = _object(
        joinResponse.body,
        operation: 'join-room',
      )['room_id'];
      if (joinedRoomId != roomId) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_JOIN_INVALID',
        );
      }
    }

    final expectedJoinedUsers = <String>{
      authorUserId,
      if (collaboratorUserId != null) collaboratorUserId,
    };
    await requireExactJoinedMembers(
      actor: author,
      roomId: roomId,
      expectedUserIds: expectedJoinedUsers,
    );
    if (collaborator != null) {
      await requireExactJoinedMembers(
        actor: collaborator,
        roomId: roomId,
        expectedUserIds: expectedJoinedUsers,
      );
    }

    await _requireEncryptedState(author, roomId);
    if (collaborator != null) {
      await _requireEncryptedState(collaborator, roomId);
    }
    return MatrixLiveRoomProvisioning(
      roomId: roomId,
      authorUserId: authorUserId,
      collaboratorUserId: collaboratorUserId,
    );
  }

  Future<String> requireJoinedRoom({
    required MatrixLiveActorCredentials actor,
    required String conversationIdFragment,
  }) async {
    final response = await client.get(
      _uri(<String>['_matrix', 'client', 'v3', 'joined_rooms']),
      headers: _headers(actor),
    );
    _requireSuccess(response, operation: 'joined-rooms');
    final joinedRooms = _object(
      response.body,
      operation: 'joined-rooms',
    )['joined_rooms'];
    if (joinedRooms is! List) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_JOINED_ROOMS_INVALID',
      );
    }
    final matchingRooms = joinedRooms
        .whereType<String>()
        .where((roomId) => roomId.contains(conversationIdFragment))
        .toList(growable: false);
    if (matchingRooms.length != 1) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_JOINED_ROOM_NOT_UNIQUE',
      );
    }
    return matchingRooms.single;
  }

  Future<void> enableEncryptionOnJoinedRoom({
    required MatrixLiveActorCredentials actor,
    required String roomId,
  }) async {
    await _enableEncryption(actor: actor, roomId: roomId);
    await _requireEncryptedState(actor, roomId);
  }

  /// Waits until each established app-owned Matrix device can discover the
  /// other device and has uploaded usable Olm session material through the
  /// northbound Matrix facade. Encrypted room creation must not race either
  /// half of the SDK's initial key upload.
  Future<void> requireMutualDeviceKeys({
    required MatrixLiveActorCredentials author,
    required String authorUserId,
    required MatrixLiveActorCredentials collaborator,
    required String collaboratorUserId,
  }) async {
    await _requireDeviceKey(
      observer: author,
      targetUserId: collaboratorUserId,
      targetDeviceId: collaborator.deviceId,
    );
    await _requireDeviceKey(
      observer: collaborator,
      targetUserId: authorUserId,
      targetDeviceId: author.deviceId,
    );
    await _requireOneTimeKeyMaterial(actor: author);
    await _requireOneTimeKeyMaterial(actor: collaborator);
  }

  /// Fails unless the current northbound device projection contains exactly
  /// the expected app-owned devices. The first collaboration pass uses this
  /// as a cold-identity precondition so a warmed second pass cannot conceal a
  /// first-device room-key delivery defect.
  Future<void> requireExactCurrentDevices({
    required MatrixLiveActorCredentials observer,
    required String targetUserId,
    required Set<String> expectedDeviceIds,
  }) async {
    final observedDeviceIds = await _currentDeviceIds(
      observer: observer,
      targetUserId: targetUserId,
    );
    if (observedDeviceIds.length != expectedDeviceIds.length ||
        !observedDeviceIds.containsAll(expectedDeviceIds)) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_COLLABORATOR_NOT_COLD',
      );
    }
  }

  /// Removes stale devices belonging to a disposable isolated-run actor while
  /// preserving the device that owns the current authenticated app profile.
  ///
  /// This must only be called by isolated E2E setup. It prevents earlier test
  /// processes for the same disposable identity from remaining eligible for a
  /// Megolm room-key share and hiding whether the currently running peer
  /// received its envelope.
  Future<int> retainOnlyCurrentDevice({
    required MatrixLiveActorCredentials actor,
    required String userId,
  }) async {
    final deviceIds = await _currentDeviceIds(
      observer: actor,
      targetUserId: userId,
    );
    if (!deviceIds.contains(actor.deviceId)) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_CURRENT_DEVICE_MISSING',
      );
    }

    final staleDeviceIds =
        deviceIds
            .where((deviceId) => deviceId != actor.deviceId)
            .toList(growable: false)
          ..sort();
    for (final deviceId in staleDeviceIds) {
      final response = await client.delete(
        _uri(<String>['_matrix', 'client', 'v3', 'devices', deviceId]),
        headers: _jsonHeaders(actor),
        body: '{}',
      );
      _requireSuccess(response, operation: 'revoke-stale-device');
    }

    await requireExactCurrentDevices(
      observer: actor,
      targetUserId: userId,
      expectedDeviceIds: <String>{actor.deviceId},
    );
    return staleDeviceIds.length;
  }

  Future<Set<String>> _currentDeviceIds({
    required MatrixLiveActorCredentials observer,
    required String targetUserId,
  }) async {
    final response = await client.post(
      _uri(<String>['_matrix', 'client', 'v3', 'keys', 'query']),
      headers: _jsonHeaders(observer),
      body: jsonEncode(<String, Object>{
        'device_keys': <String, List<String>>{targetUserId: <String>[]},
      }),
    );
    _requireSuccess(response, operation: 'query-current-device-set');
    final payload = _object(
      response.body,
      operation: 'query-current-device-set',
    );
    final deviceKeys = payload['device_keys'];
    final userDevices = deviceKeys is Map ? deviceKeys[targetUserId] : null;
    if (userDevices is! Map) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_DEVICE_SET_INVALID',
      );
    }

    final deviceIds = <String>{};
    for (final entry in userDevices.entries) {
      final device = entry.value;
      final keys = device is Map ? device['keys'] : null;
      if (entry.key is! String ||
          device is! Map ||
          device['user_id'] != targetUserId ||
          device['device_id'] != entry.key ||
          keys is! Map ||
          keys.isEmpty) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_DEVICE_SET_INVALID',
        );
      }
      deviceIds.add(entry.key as String);
    }
    return deviceIds;
  }

  /// Proves that the canonical room-member projection is complete from the
  /// authenticated actor's perspective before native Megolm sharing begins.
  /// Only identities and membership states are inspected; raw provider payloads
  /// never enter support-safe failures.
  Future<void> requireExactJoinedMembers({
    required MatrixLiveActorCredentials actor,
    required String roomId,
    required Set<String> expectedUserIds,
  }) async {
    final response = await client.get(
      _uri(<String>['_matrix', 'client', 'v3', 'rooms', roomId, 'members']),
      headers: _headers(actor),
    );
    _requireSuccess(response, operation: 'room-members');
    final chunk = _object(response.body, operation: 'room-members')['chunk'];
    if (chunk is! List) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_ROOM_MEMBERS_INVALID',
      );
    }
    final joinedUserIds = <String>{};
    for (final event in chunk) {
      if (event is! Map ||
          event['type'] != 'm.room.member' ||
          event['state_key'] is! String ||
          event['content'] is! Map) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_ROOM_MEMBERS_INVALID',
        );
      }
      final content = event['content'] as Map;
      if (content['membership'] == 'join') {
        joinedUserIds.add(event['state_key'] as String);
      }
    }
    if (joinedUserIds.length != expectedUserIds.length ||
        !joinedUserIds.containsAll(expectedUserIds)) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_ROOM_MEMBERS_NOT_CONVERGED',
      );
    }
  }

  Future<void> _requireDeviceKey({
    required MatrixLiveActorCredentials observer,
    required String targetUserId,
    required String targetDeviceId,
  }) async {
    final deadline = DateTime.now().add(deviceKeyConvergenceTimeout);
    while (true) {
      final response = await client.post(
        _uri(<String>['_matrix', 'client', 'v3', 'keys', 'query']),
        headers: _jsonHeaders(observer),
        body: jsonEncode(<String, Object>{
          'device_keys': <String, List<String>>{
            targetUserId: <String>[targetDeviceId],
          },
        }),
      );
      _requireSuccess(response, operation: 'query-device-keys');
      final payload = _object(response.body, operation: 'query-device-keys');
      final deviceKeys = payload['device_keys'];
      if (deviceKeys is! Map) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_DEVICE_KEYS_INVALID',
        );
      }
      final userDevices = deviceKeys[targetUserId];
      final device = userDevices is Map ? userDevices[targetDeviceId] : null;
      final keys = device is Map ? device['keys'] : null;
      if (device is Map &&
          device['user_id'] == targetUserId &&
          device['device_id'] == targetDeviceId &&
          keys is Map &&
          keys.isNotEmpty) {
        return;
      }
      if (!DateTime.now().isBefore(deadline)) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_DEVICE_KEYS_NOT_CONVERGED',
        );
      }
      await Future<void>.delayed(deviceKeyPollInterval);
    }
  }

  Future<void> _requireOneTimeKeyMaterial({
    required MatrixLiveActorCredentials actor,
  }) async {
    final deadline = DateTime.now().add(deviceKeyConvergenceTimeout);
    while (true) {
      // An empty upload is the Matrix status query for the authenticated
      // device. It returns counts without claiming or exposing key material.
      final response = await client.post(
        _uri(<String>['_matrix', 'client', 'v3', 'keys', 'upload']),
        headers: _jsonHeaders(actor),
        body: '{}',
      );
      _requireSuccess(response, operation: 'query-one-time-key-counts');
      final payload = _object(
        response.body,
        operation: 'query-one-time-key-counts',
      );
      final counts = payload['one_time_key_counts'];
      final signedCurve25519 = counts is Map
          ? counts['signed_curve25519']
          : null;
      if (signedCurve25519 is num && signedCurve25519.toInt() > 0) {
        return;
      }
      if (!DateTime.now().isBefore(deadline)) {
        throw const MatrixLiveRoomDriverException(
          'M_WEAVE_LIVE_MATRIX_KEY_MATERIAL_NOT_CONVERGED',
        );
      }
      await Future<void>.delayed(deviceKeyPollInterval);
    }
  }

  Future<void> _enableEncryption({
    required MatrixLiveActorCredentials actor,
    required String roomId,
  }) async {
    final encryptionResponse = await client.put(
      _uri(<String>[
        '_matrix',
        'client',
        'v3',
        'rooms',
        roomId,
        'state',
        'm.room.encryption',
      ]),
      headers: _jsonHeaders(actor),
      body: jsonEncode(<String, String>{'algorithm': matrixMegolmV1Algorithm}),
    );
    _requireSuccess(encryptionResponse, operation: 'enable-encryption');
  }

  Future<int> redactEventsAndVerify({
    required MatrixLiveActorCredentials actor,
    required String roomId,
    required Set<String> eventIds,
  }) async {
    if (eventIds.isEmpty) {
      return 0;
    }
    var redactedCount = 0;
    final transactionSuffix = DateTime.now().toUtc().microsecondsSinceEpoch;
    for (final indexedEvent in eventIds.indexed) {
      final response = await client.put(
        _uri(<String>[
          '_matrix',
          'client',
          'v3',
          'rooms',
          roomId,
          'redact',
          indexedEvent.$2,
          'weave-e2e-redact-${indexedEvent.$1}-$transactionSuffix',
        ]),
        headers: _jsonHeaders(actor),
        body: jsonEncode(<String, String>{'reason': 'isolated-e2e-cleanup'}),
      );
      _requireSuccess(response, operation: 'redact-event');
      redactedCount += 1;
    }

    final deadline = DateTime.now().add(const Duration(seconds: 45));
    while (DateTime.now().isBefore(deadline)) {
      if (await _eventsAreRedacted(actor, roomId, eventIds)) {
        return redactedCount;
      }
      await Future<void>.delayed(const Duration(seconds: 1));
    }
    throw const MatrixLiveRoomDriverException(
      'M_WEAVE_LIVE_MATRIX_REDACTION_NOT_OBSERVED',
    );
  }

  Future<void> leaveRoom({
    required MatrixLiveActorCredentials actor,
    required String roomId,
  }) async {
    final response = await client.post(
      _uri(<String>['_matrix', 'client', 'v3', 'rooms', roomId, 'leave']),
      headers: _jsonHeaders(actor),
      body: '{}',
    );
    _requireSuccess(response, operation: 'leave-room');
  }

  Future<void> _requireEncryptedState(
    MatrixLiveActorCredentials actor,
    String roomId,
  ) async {
    final response = await client.get(
      _uri(<String>[
        '_matrix',
        'client',
        'v3',
        'rooms',
        roomId,
        'state',
        'm.room.encryption',
      ]),
      headers: _headers(actor),
    );
    _requireSuccess(response, operation: 'read-encryption');
    final payload = _object(response.body, operation: 'read-encryption');
    if (payload['algorithm'] != matrixMegolmV1Algorithm) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_ENCRYPTION_INVALID',
      );
    }
  }

  Future<bool> _eventsAreRedacted(
    MatrixLiveActorCredentials actor,
    String roomId,
    Set<String> eventIds,
  ) async {
    final response = await client.get(
      _uri(
        <String>['_matrix', 'client', 'v3', 'rooms', roomId, 'messages'],
        queryParameters: const <String, String>{'dir': 'b', 'limit': '100'},
      ),
      headers: _headers(actor),
    );
    _requireSuccess(response, operation: 'verify-redaction');
    final payload = _object(response.body, operation: 'verify-redaction');
    final chunk = payload['chunk'];
    if (chunk is! List) {
      throw const MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_TIMELINE_INVALID',
      );
    }
    final events = <String, Map<Object?, Object?>>{
      for (final event in chunk)
        if (event is Map && event['event_id'] is String)
          event['event_id'] as String: event,
    };
    return eventIds.every((eventId) {
      final event = events[eventId];
      return event != null &&
          event['type'] == 'm.room.encrypted' &&
          event['content'] is Map &&
          (event['content'] as Map).isEmpty;
    });
  }

  Uri _uri(List<String> pathSegments, {Map<String, String>? queryParameters}) {
    return homeserver.replace(
      pathSegments: pathSegments,
      queryParameters: queryParameters,
      fragment: null,
    );
  }

  Map<String, String> _headers(MatrixLiveActorCredentials actor) {
    return <String, String>{
      'Authorization': 'Bearer ${actor.accessToken}',
      'X-Weave-Matrix-Device-Id': actor.deviceId,
      'Accept': 'application/json',
    };
  }

  Map<String, String> _jsonHeaders(MatrixLiveActorCredentials actor) {
    return <String, String>{
      ..._headers(actor),
      'Content-Type': 'application/json',
    };
  }

  Map<String, dynamic> _object(String body, {required String operation}) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        return decoded;
      }
    } on FormatException {
      // The support-safe error below intentionally omits the raw response.
    }
    throw MatrixLiveRoomDriverException(
      'M_WEAVE_LIVE_MATRIX_${operation.toUpperCase().replaceAll('-', '_')}_INVALID',
    );
  }

  void _requireSuccess(http.Response response, {required String operation}) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw MatrixLiveRoomDriverException(
        'M_WEAVE_LIVE_MATRIX_${operation.toUpperCase().replaceAll('-', '_')}_HTTP_${response.statusCode}',
      );
    }
  }
}

class MatrixLiveRoomDriverException implements Exception {
  const MatrixLiveRoomDriverException(this.code);

  final String code;

  @override
  String toString() => code;
}
