import 'dart:convert';

import 'package:weave/integrations/rust_matrix_core/generated/frb_api.dart';
import 'package:weave/integrations/rust_matrix_core/generated/frb_generated.dart';

import 'rust_matrix_core_external_library.dart';

const _matrixLiveTestExtraRootEnabled = bool.fromEnvironment(
  'WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED',
  defaultValue: false,
);
const _matrixLiveTestExtraRootBase64 = String.fromEnvironment(
  'WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64',
  defaultValue: '',
);
const _maximumExtraRootCertificateBytes = 64 * 1024;

class RustMatrixCoreBridgeException implements Exception {
  const RustMatrixCoreBridgeException(this.code);

  final String code;

  @override
  String toString() => code;
}

String decodeMatrixLiveTestExtraRootCertificate({
  required bool enabled,
  required String encodedCertificate,
}) {
  if (!enabled) {
    return '';
  }

  try {
    final bytes = base64Decode(encodedCertificate.trim());
    if (bytes.isEmpty || bytes.length > _maximumExtraRootCertificateBytes) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_TLS_ROOT');
    }
    return utf8.decode(bytes);
  } on RustMatrixCoreBridgeException {
    rethrow;
  } on Object {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_TLS_ROOT');
  }
}

class RustMatrixCoreBridgeDescriptor {
  const RustMatrixCoreBridgeDescriptor({
    required this.protocolSurface,
    required this.oidcGatekeeper,
    required this.northboundHomeserverDependency,
    required this.rustProtocolCore,
    required this.serverJniBoundary,
    required this.flutterBridgeBoundary,
    required this.nativeLinked,
    required this.serverName,
    required this.supportedMatrixVersions,
    required this.supportedEndpoints,
  });

  factory RustMatrixCoreBridgeDescriptor.fromJson(Map<String, dynamic> json) {
    return RustMatrixCoreBridgeDescriptor(
      protocolSurface: _string(json['protocolSurface']),
      oidcGatekeeper: _string(json['oidcGatekeeper']),
      northboundHomeserverDependency:
          json['northboundHomeserverDependency'] == true,
      rustProtocolCore: _string(json['rustProtocolCore']),
      serverJniBoundary: _string(json['serverJniBoundary']),
      flutterBridgeBoundary: _string(json['flutterBridgeBoundary']),
      nativeLinked: json['nativeLinked'] == true,
      serverName: _string(json['serverName']),
      supportedMatrixVersions: _stringList(json['supportedMatrixVersions']),
      supportedEndpoints: _stringList(json['supportedEndpoints']),
    );
  }

  final String protocolSurface;
  final String oidcGatekeeper;
  final bool northboundHomeserverDependency;
  final String rustProtocolCore;
  final String serverJniBoundary;
  final String flutterBridgeBoundary;
  final bool nativeLinked;
  final String serverName;
  final List<String> supportedMatrixVersions;
  final List<String> supportedEndpoints;

  bool get isWeaveFacade =>
      protocolSurface == 'matrix-client-server-facade' &&
      oidcGatekeeper == 'spring-boot-resource-server' &&
      northboundHomeserverDependency == false &&
      nativeLinked;

  Map<String, Object?> toJson() => {
    'protocolSurface': protocolSurface,
    'oidcGatekeeper': oidcGatekeeper,
    'northboundHomeserverDependency': northboundHomeserverDependency,
    'rustProtocolCore': rustProtocolCore,
    'serverJniBoundary': serverJniBoundary,
    'flutterBridgeBoundary': flutterBridgeBoundary,
    'nativeLinked': nativeLinked,
    'serverName': serverName,
    'supportedMatrixVersions': supportedMatrixVersions,
    'supportedEndpoints': supportedEndpoints,
  };
}

class RustMatrixSyncProjection {
  const RustMatrixSyncProjection({
    required this.nextBatch,
    required this.rooms,
  });

  factory RustMatrixSyncProjection.fromJson(Map<String, dynamic> json) {
    return RustMatrixSyncProjection(
      nextBatch: _string(json['nextBatch']),
      rooms: _mapList(json['rooms'], RustMatrixRoomProjection.fromJson),
    );
  }

  final String nextBatch;
  final List<RustMatrixRoomProjection> rooms;
}

class RustMatrixRoomProjection {
  const RustMatrixRoomProjection({
    required this.roomId,
    required this.title,
    required this.unreadCount,
    required this.messages,
  });

  factory RustMatrixRoomProjection.fromJson(Map<String, dynamic> json) {
    return RustMatrixRoomProjection(
      roomId: _string(json['roomId']),
      title: _string(json['title']),
      unreadCount: _integer(json['unreadCount']),
      messages: _mapList(
        json['messages'],
        RustMatrixMessageProjection.fromJson,
      ),
    );
  }

  final String roomId;
  final String title;
  final int unreadCount;
  final List<RustMatrixMessageProjection> messages;
}

class RustMatrixMessageProjection {
  const RustMatrixMessageProjection({
    required this.eventId,
    required this.sender,
    required this.originServerTimestamp,
    required this.body,
    required this.contentType,
  });

  factory RustMatrixMessageProjection.fromJson(Map<String, dynamic> json) {
    return RustMatrixMessageProjection(
      eventId: _string(json['eventId']),
      sender: _string(json['sender']),
      originServerTimestamp: _integer(json['originServerTs']),
      body: json['body'] is String ? json['body'] as String : null,
      contentType: _string(json['contentType']),
    );
  }

  final String eventId;
  final String sender;
  final int originServerTimestamp;
  final String? body;
  final String contentType;
}

class RustMatrixDecryptionDiagnostics {
  const RustMatrixDecryptionDiagnostics({
    required this.eventCount,
    required this.decryptedCount,
    required this.unableToDecryptCount,
    required this.plaintextCount,
    required this.reasonCounts,
    this.toDeviceDecryptedCount = 0,
    this.toDeviceDecryptedRoomKeyCount = 0,
    this.toDeviceDecryptedForwardedRoomKeyCount = 0,
    this.toDeviceDecryptedOtherCount = 0,
    this.toDeviceDecryptedUnknownTypeCount = 0,
    this.toDeviceUnableToDecryptCount = 0,
    this.toDevicePlaintextCount = 0,
    this.toDeviceInvalidCount = 0,
    this.toDeviceReasonCounts = const <String, int>{},
    this.joinedPeerCount = 0,
    this.authoritativeDeviceCount = 0,
    this.sdkDeviceCount = 0,
    this.sdkUsableDeviceCount = 0,
    this.sdkDeletedDeviceCount = 0,
    this.sdkBlacklistedDeviceCount = 0,
    this.sdkMissingCurve25519Count = 0,
    this.sdkMissingAuthoritativeDeviceCount = 0,
    this.sdkUnexpectedDeviceCount = 0,
    this.deviceQueryAttemptCount = 0,
    this.convergedPeerCount = 0,
    this.pendingPeerCount = 0,
    this.rejectedPeerCount = 0,
    this.blockedPeerCount = 0,
    this.invalidPeerCount = 0,
  });

  factory RustMatrixDecryptionDiagnostics.fromJson(Map<String, dynamic> json) {
    final rawReasonCounts = json['reasonCounts'];
    return RustMatrixDecryptionDiagnostics(
      eventCount: _integer(json['eventCount']),
      decryptedCount: _integer(json['decryptedCount']),
      unableToDecryptCount: _integer(json['unableToDecryptCount']),
      plaintextCount: _integer(json['plaintextCount']),
      reasonCounts: rawReasonCounts is Map
          ? Map<String, int>.unmodifiable(
              rawReasonCounts.map(
                (key, value) => MapEntry(key.toString(), _integer(value)),
              ),
            )
          : const <String, int>{},
      toDeviceDecryptedCount: _integer(json['toDeviceDecryptedCount']),
      toDeviceDecryptedRoomKeyCount: _integer(
        json['toDeviceDecryptedRoomKeyCount'],
      ),
      toDeviceDecryptedForwardedRoomKeyCount: _integer(
        json['toDeviceDecryptedForwardedRoomKeyCount'],
      ),
      toDeviceDecryptedOtherCount: _integer(
        json['toDeviceDecryptedOtherCount'],
      ),
      toDeviceDecryptedUnknownTypeCount: _integer(
        json['toDeviceDecryptedUnknownTypeCount'],
      ),
      toDeviceUnableToDecryptCount: _integer(
        json['toDeviceUnableToDecryptCount'],
      ),
      toDevicePlaintextCount: _integer(json['toDevicePlaintextCount']),
      toDeviceInvalidCount: _integer(json['toDeviceInvalidCount']),
      toDeviceReasonCounts: json['toDeviceReasonCounts'] is Map
          ? Map<String, int>.unmodifiable(
              (json['toDeviceReasonCounts'] as Map).map(
                (key, value) => MapEntry(key.toString(), _integer(value)),
              ),
            )
          : const <String, int>{},
      joinedPeerCount: _integer(json['joinedPeerCount']),
      authoritativeDeviceCount: _integer(json['authoritativeDeviceCount']),
      sdkDeviceCount: _integer(json['sdkDeviceCount']),
      sdkUsableDeviceCount: _integer(json['sdkUsableDeviceCount']),
      sdkDeletedDeviceCount: _integer(json['sdkDeletedDeviceCount']),
      sdkBlacklistedDeviceCount: _integer(json['sdkBlacklistedDeviceCount']),
      sdkMissingCurve25519Count: _integer(json['sdkMissingCurve25519Count']),
      sdkMissingAuthoritativeDeviceCount: _integer(
        json['sdkMissingAuthoritativeDeviceCount'],
      ),
      sdkUnexpectedDeviceCount: _integer(json['sdkUnexpectedDeviceCount']),
      deviceQueryAttemptCount: _integer(json['deviceQueryAttemptCount']),
      convergedPeerCount: _integer(json['convergedPeerCount']),
      pendingPeerCount: _integer(json['pendingPeerCount']),
      rejectedPeerCount: _integer(json['rejectedPeerCount']),
      blockedPeerCount: _integer(json['blockedPeerCount']),
      invalidPeerCount: _integer(json['invalidPeerCount']),
    );
  }

  final int eventCount;
  final int decryptedCount;
  final int unableToDecryptCount;
  final int plaintextCount;
  final Map<String, int> reasonCounts;
  final int toDeviceDecryptedCount;
  final int toDeviceDecryptedRoomKeyCount;
  final int toDeviceDecryptedForwardedRoomKeyCount;
  final int toDeviceDecryptedOtherCount;
  final int toDeviceDecryptedUnknownTypeCount;
  final int toDeviceUnableToDecryptCount;
  final int toDevicePlaintextCount;
  final int toDeviceInvalidCount;
  final Map<String, int> toDeviceReasonCounts;
  final int joinedPeerCount;
  final int authoritativeDeviceCount;
  final int sdkDeviceCount;
  final int sdkUsableDeviceCount;
  final int sdkDeletedDeviceCount;
  final int sdkBlacklistedDeviceCount;
  final int sdkMissingCurve25519Count;
  final int sdkMissingAuthoritativeDeviceCount;
  final int sdkUnexpectedDeviceCount;
  final int deviceQueryAttemptCount;
  final int convergedPeerCount;
  final int pendingPeerCount;
  final int rejectedPeerCount;
  final int blockedPeerCount;
  final int invalidPeerCount;

  String get supportCode {
    if ((toDeviceReasonCounts['decryptionFailure'] ?? 0) > 0) {
      return 'M_WEAVE_E2EE_OLM_DECRYPTION_FAILURE';
    }
    if ((toDeviceReasonCounts['unverifiedSenderDevice'] ?? 0) > 0) {
      return 'M_WEAVE_E2EE_OLM_SENDER_NOT_TRUSTED';
    }
    if ((toDeviceReasonCounts['noOlmMachine'] ?? 0) > 0 ||
        (toDeviceReasonCounts['encryptionDisabled'] ?? 0) > 0) {
      return 'M_WEAVE_E2EE_OLM_UNAVAILABLE';
    }
    if (toDeviceInvalidCount > 0) {
      return 'M_WEAVE_E2EE_TO_DEVICE_INVALID';
    }
    if (rejectedPeerCount > 0) {
      return 'M_WEAVE_E2EE_PEER_DEVICE_REJECTED';
    }
    if (blockedPeerCount > 0) {
      return 'M_WEAVE_E2EE_PEER_DEVICE_BLOCKED';
    }
    if (invalidPeerCount > 0) {
      return 'M_WEAVE_E2EE_PEER_DEVICE_INVALID';
    }
    if (pendingPeerCount > 0) {
      return 'M_WEAVE_E2EE_PEER_DEVICE_PENDING';
    }
    if ((reasonCounts['missingMegolmSession'] ?? 0) > 0) {
      if (toDeviceDecryptedRoomKeyCount > 0 ||
          toDeviceDecryptedForwardedRoomKeyCount > 0) {
        return 'M_WEAVE_E2EE_ROOM_KEY_NOT_IMPORTED';
      }
      return 'M_WEAVE_E2EE_ROOM_KEY_NOT_RECEIVED';
    }
    if ((reasonCounts['mismatchedIdentityKeys'] ?? 0) > 0) {
      return 'M_WEAVE_E2EE_MISMATCHED_IDENTITY_KEYS';
    }
    if ((reasonCounts['senderIdentityNotTrusted'] ?? 0) > 0) {
      return 'M_WEAVE_E2EE_SENDER_NOT_TRUSTED';
    }
    if (unableToDecryptCount > 0) {
      return 'M_WEAVE_E2EE_UNABLE_TO_DECRYPT';
    }
    return 'M_WEAVE_E2EE_MESSAGE_NOT_OBSERVED';
  }
}

class RustMatrixEncryptedRoom {
  const RustMatrixEncryptedRoom({
    required this.roomId,
    required this.title,
    required this.unreadCount,
    required this.encrypted,
  });

  factory RustMatrixEncryptedRoom.fromJson(Map<String, dynamic> json) {
    return RustMatrixEncryptedRoom(
      roomId: _string(json['roomId']),
      title: _string(json['title']),
      unreadCount: _integer(json['unreadCount']),
      encrypted: json['encrypted'] == true,
    );
  }

  final String roomId;
  final String title;
  final int unreadCount;
  final bool encrypted;
}

class RustMatrixVerificationProjection {
  const RustMatrixVerificationProjection({
    required this.phase,
    required this.sasNumbers,
    required this.sasEmojis,
  });

  factory RustMatrixVerificationProjection.fromJson(Map<String, dynamic> json) {
    return RustMatrixVerificationProjection(
      phase: _string(json['phase']),
      sasNumbers: json['sasNumbers'] is List
          ? (json['sasNumbers'] as List)
                .whereType<num>()
                .map((value) => value.toInt())
                .toList(growable: false)
          : const <int>[],
      sasEmojis: _mapList(json['sasEmojis'], (emoji) => emoji),
    );
  }

  final String phase;
  final List<int> sasNumbers;
  final List<Map<String, dynamic>> sasEmojis;
}

class RustMatrixSecurityProjection {
  const RustMatrixSecurityProjection({
    required this.signedIn,
    required this.recoveryState,
    required this.crossSigningReady,
    required this.deviceVerified,
    required this.accountVerified,
    required this.encryptedRoomCount,
    required this.verification,
  });

  factory RustMatrixSecurityProjection.fromJson(Map<String, dynamic> json) {
    final verification = json['verification'];
    return RustMatrixSecurityProjection(
      signedIn: json['signedIn'] == true,
      recoveryState: _string(json['recoveryState']),
      crossSigningReady: json['crossSigningReady'] == true,
      deviceVerified: json['deviceVerified'] == true,
      accountVerified: json['accountVerified'] == true,
      encryptedRoomCount: _integer(json['encryptedRoomCount']),
      verification: RustMatrixVerificationProjection.fromJson(
        verification is Map
            ? Map<String, dynamic>.from(verification)
            : const <String, dynamic>{},
      ),
    );
  }

  final bool signedIn;
  final String recoveryState;
  final bool crossSigningReady;
  final bool deviceVerified;
  final bool accountVerified;
  final int encryptedRoomCount;
  final RustMatrixVerificationProjection verification;
}

class RustMatrixCoreBridge {
  const RustMatrixCoreBridge();

  static Future<void>? _initialization;

  Future<void> initializeClient({
    required String profileKey,
    required String homeserverUrl,
    required String userId,
    required String deviceId,
    required String accessToken,
    required String storePath,
    required String storePassphrase,
    String? extraRootCertificatePem,
  }) async {
    final resolvedExtraRootCertificatePem =
        extraRootCertificatePem ?? _loadExtraRootCertificatePem();
    await _native(
      () => initializeMatrixClient(
        profileKey: profileKey,
        homeserverUrl: homeserverUrl,
        userId: userId,
        deviceId: deviceId,
        accessToken: accessToken,
        storePath: storePath,
        storePassphrase: storePassphrase,
        extraRootCertificatePem: resolvedExtraRootCertificatePem,
      ),
    );
  }

  String _loadExtraRootCertificatePem() =>
      decodeMatrixLiveTestExtraRootCertificate(
        enabled: _matrixLiveTestExtraRootEnabled,
        encodedCertificate: _matrixLiveTestExtraRootBase64,
      );

  Future<void> syncClient({required String profileKey}) async {
    await _native(() => syncMatrixClient(profileKey: profileKey));
  }

  Future<List<RustMatrixEncryptedRoom>> loadEncryptedRooms({
    required String profileKey,
  }) async {
    final result = await _native(() => matrixRooms(profileKey: profileKey));
    return _mapList(result['rooms'], RustMatrixEncryptedRoom.fromJson);
  }

  Future<RustMatrixEncryptedRoom> createEncryptedRoom({
    required String profileKey,
    required String title,
  }) async {
    return RustMatrixEncryptedRoom.fromJson(
      await _native(
        () => matrixCreateEncryptedRoom(profileKey: profileKey, title: title),
      ),
    );
  }

  Future<List<RustMatrixMessageProjection>> loadEncryptedRoomMessages({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) async {
    final result = await _native(
      () => matrixRoomMessages(
        profileKey: profileKey,
        roomId: roomId,
        limit: limit,
      ),
    );
    return _mapList(result['messages'], RustMatrixMessageProjection.fromJson);
  }

  Future<RustMatrixDecryptionDiagnostics> loadDecryptionDiagnostics({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) async {
    final result = await _native(
      () => matrixRoomMessages(
        profileKey: profileKey,
        roomId: roomId,
        limit: limit,
      ),
    );
    final diagnostics = result['decryption'];
    return RustMatrixDecryptionDiagnostics.fromJson(
      diagnostics is Map
          ? Map<String, dynamic>.from(diagnostics)
          : const <String, dynamic>{},
    );
  }

  Future<String> sendEncryptedText({
    required String profileKey,
    required String roomId,
    required String body,
  }) async {
    final result = await _native(
      () => matrixSendText(profileKey: profileKey, roomId: roomId, body: body),
    );
    return _string(result['eventId']);
  }

  Future<void> markRead({
    required String profileKey,
    required String roomId,
    required String eventId,
  }) async {
    await _native(
      () => matrixMarkRead(
        profileKey: profileKey,
        roomId: roomId,
        eventId: eventId,
      ),
    );
  }

  Future<RustMatrixSecurityProjection> loadSecurityState({
    required String profileKey,
  }) async {
    return RustMatrixSecurityProjection.fromJson(
      await _native(() => matrixSecurityState(profileKey: profileKey)),
    );
  }

  Future<String> bootstrapRecovery({
    required String profileKey,
    String passphrase = '',
  }) async {
    final result = await _native(
      () => matrixBootstrapRecovery(
        profileKey: profileKey,
        passphrase: passphrase,
      ),
    );
    return _string(result['recoveryKey']);
  }

  Future<void> recover({
    required String profileKey,
    required String recoveryKeyOrPassphrase,
  }) async {
    await _native(
      () => matrixRecover(
        profileKey: profileKey,
        recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
      ),
    );
  }

  Future<RustMatrixVerificationProjection> startVerification({
    required String profileKey,
  }) => _verification(() => matrixStartVerification(profileKey: profileKey));

  Future<RustMatrixVerificationProjection> acceptVerification({
    required String profileKey,
  }) => _verification(() => matrixAcceptVerification(profileKey: profileKey));

  Future<RustMatrixVerificationProjection> startSas({
    required String profileKey,
  }) => _verification(() => matrixStartSas(profileKey: profileKey));

  Future<RustMatrixVerificationProjection> confirmSas({
    required String profileKey,
    required bool matches,
  }) => _verification(
    () => matrixConfirmSas(profileKey: profileKey, matches: matches),
  );

  Future<RustMatrixVerificationProjection> cancelVerification({
    required String profileKey,
  }) => _verification(() => matrixCancelVerification(profileKey: profileKey));

  Future<void> dismissVerification({required String profileKey}) async {
    await _native(() => matrixDismissVerification(profileKey: profileKey));
  }

  Future<void> disposeClient({required String profileKey}) async {
    await _native(() => disposeMatrixClient(profileKey: profileKey));
  }

  Future<RustMatrixCoreBridgeDescriptor> descriptor({
    String serverName = 'api.weave.test',
  }) async {
    final payload = await _project(
      operation: 'descriptor',
      input: const <String, Object?>{},
      serverName: serverName,
    );
    return RustMatrixCoreBridgeDescriptor.fromJson(payload);
  }

  Future<void> validateVersions({
    required String responseJson,
    required String serverName,
  }) async {
    final result = await _projectRaw(
      operation: 'parse-versions',
      inputJson: responseJson,
      serverName: serverName,
    );
    if (result['compatible'] != true) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_CORE_ERROR');
    }
  }

  Future<RustMatrixSyncProjection> parseSync({
    required String responseJson,
    required String serverName,
  }) async {
    return RustMatrixSyncProjection.fromJson(
      await _projectRaw(
        operation: 'parse-sync',
        inputJson: responseJson,
        serverName: serverName,
      ),
    );
  }

  Future<List<RustMatrixMessageProjection>> parseMessages({
    required String responseJson,
    required String serverName,
  }) async {
    final result = await _projectRaw(
      operation: 'parse-messages',
      inputJson: responseJson,
      serverName: serverName,
    );
    return _mapList(result['messages'], RustMatrixMessageProjection.fromJson);
  }

  Future<String> parseWhoamiUserId({
    required String responseJson,
    required String serverName,
  }) async {
    final result = await _projectRaw(
      operation: 'parse-whoami',
      inputJson: responseJson,
      serverName: serverName,
    );
    final userId = _string(result['userId']);
    if (userId.isEmpty) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_CORE_ERROR');
    }
    return userId;
  }

  Future<String> serializeTextMessage({
    required String body,
    required String serverName,
  }) async {
    final result = await _project(
      operation: 'serialize-send',
      input: <String, Object?>{'body': body},
      serverName: serverName,
    );
    return jsonEncode(result);
  }

  Future<Map<String, dynamic>> _project({
    required String operation,
    required Map<String, Object?> input,
    required String serverName,
  }) {
    return _projectRaw(
      operation: operation,
      inputJson: jsonEncode(input),
      serverName: serverName,
    );
  }

  Future<Map<String, dynamic>> _projectRaw({
    required String operation,
    required String inputJson,
    required String serverName,
  }) async {
    await _ensureInitialized();
    final output = await projectMatrixJson(
      operation: operation,
      inputJson: inputJson,
      serverName: serverName,
    );
    final decoded = jsonDecode(output);
    if (decoded is! Map) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_CORE_ERROR');
    }
    final result = Map<String, dynamic>.from(decoded);
    if (result['errcode'] case final String errcode) {
      throw RustMatrixCoreBridgeException(errcode);
    }
    return result;
  }

  Future<RustMatrixVerificationProjection> _verification(
    Future<String> Function() operation,
  ) async {
    return RustMatrixVerificationProjection.fromJson(await _native(operation));
  }

  Future<Map<String, dynamic>> _native(
    Future<String> Function() operation,
  ) async {
    await _ensureInitialized();
    final output = await operation();
    final decoded = jsonDecode(output);
    if (decoded is! Map) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SERIALIZATION');
    }
    final result = Map<String, dynamic>.from(decoded);
    if (result['errcode'] case final String errcode) {
      throw RustMatrixCoreBridgeException(errcode);
    }
    return result;
  }

  Future<void> _ensureInitialized() {
    return _initialization ??= _initialize();
  }

  Future<void> _initialize() async {
    await RustLib.init(
      externalLibrary: await loadRustMatrixCoreDevelopmentLibrary(),
    );
  }
}

String _string(Object? value) => value is String ? value : '';

int _integer(Object? value) => value is num ? value.toInt() : 0;

List<String> _stringList(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value.whereType<String>().toList(growable: false);
}

List<T> _mapList<T>(Object? value, T Function(Map<String, dynamic>) mapper) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<Map>()
      .map((item) => mapper(Map<String, dynamic>.from(item)))
      .toList(growable: false);
}
