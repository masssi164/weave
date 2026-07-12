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
  MatrixLiveRoomDriver({required this.client, required this.homeserver});

  final http.Client client;
  final Uri homeserver;

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

    final createResponse = await client.post(
      _uri(<String>['_matrix', 'client', 'v3', 'createRoom']),
      headers: _jsonHeaders(author),
      body: jsonEncode(<String, Object>{
        'name': roomName,
        'preset': 'private_chat',
        if (collaboratorUserId != null) 'invite': <String>[collaboratorUserId],
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

    await _enableEncryption(actor: author, roomId: roomId);

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
