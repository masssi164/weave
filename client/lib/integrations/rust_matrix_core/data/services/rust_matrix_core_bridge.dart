import 'dart:convert';

import 'package:weave/integrations/rust_matrix_core/generated/frb_api.dart';
import 'package:weave/integrations/rust_matrix_core/generated/frb_generated.dart';

import 'rust_matrix_core_external_library.dart';

class RustMatrixCoreBridgeException implements Exception {
  const RustMatrixCoreBridgeException(this.code);

  final String code;

  @override
  String toString() => code;
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

class RustMatrixCoreBridge {
  const RustMatrixCoreBridge();

  static Future<void>? _initialization;

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
