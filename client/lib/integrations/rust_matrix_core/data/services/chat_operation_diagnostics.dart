import 'dart:async';
import 'dart:math';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';

enum ChatOperation {
  conversations,
  invitation,
  search,
  create,
  timeline,
  send,
  read,
  connect,
  signOut,
  clearSession,
}

enum ChatStage {
  open,
  admission,
  bindingStorage,
  nativeActivation,
  sync,
  roomList,
  invitation,
  search,
  create,
  timeline,
  send,
  read,
  cleanup,
}

/// Per-action context carries only fixed stage names and bounded status codes.
/// The root zone prints before asynchronous log buffering can hide first-load errors.
class ChatOperationDiagnostics {
  ChatOperationDiagnostics._(this.operation) : correlation = _uuid();
  final ChatOperation operation;
  final String correlation;
  ChatStage stage = ChatStage.open;
  int? httpStatus;
  static final Object _zoneKey = Object();
  static ChatOperationDiagnostics? get current =>
      Zone.current[_zoneKey] as ChatOperationDiagnostics?;
  static void mark(ChatStage stage) {
    current?.stage = stage;
  }

  static void status(int value) {
    if (value >= 100 && value <= 599) current?.httpStatus = value;
  }

  static Future<T> run<T>(
    ChatOperation operation,
    Future<T> Function() action,
  ) {
    final context = ChatOperationDiagnostics._(operation);
    return runZoned(() async {
      try {
        return await action();
      } catch (error, stack) {
        final failure = classifyChatFailure(error);
        context._report(failure, error);
        Error.throwWithStackTrace(failure, stack);
      }
    }, zoneValues: {_zoneKey: context});
  }

  void _report(ChatFailure failure, Object original) {
    final cause = failure.cause;
    final code = original is RustMatrixCoreBridgeException
        ? original.code
        : cause is RustMatrixCoreBridgeException
        ? cause.code
        : failure.message;
    final statusMatch = RegExp(
      r'^M_WEAVE_MATRIX_HTTP_([1-5][0-9]{2})$',
    ).firstMatch(code);
    if (statusMatch != null) status(int.parse(statusMatch.group(1)!));
    const candidate = String.fromEnvironment('WEAVE_CANDIDATE_COMMIT');
    final safeCandidate = RegExp(r'^[0-9a-f]{40}$').hasMatch(candidate)
        ? candidate
        : 'unavailable';
    try {
      Zone.root.run(
        () => debugPrint(
          'WEAVE_NATIVE_CHAT_FAILURE operation=${operation.name} '
          'stage=${stage.name} type=${failure.type.name} code=${_safeCode(code)} '
          'httpStatus=${httpStatus ?? 'unavailable'} correlation=$correlation candidate=$safeCandidate supportSafe=true',
        ),
      );
    } catch (_) {
      /* Diagnostics never replace the original failure. */
    }
  }

  static String _uuid() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((v) => v.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}

const _allowedCodes = {
  'M_UNKNOWN_TOKEN',
  'M_MISSING_TOKEN',
  'M_FORBIDDEN',
  'M_NOT_FOUND',
  'M_INVALID_PARAM',
  'M_WEAVE_CHAT_JOIN_REQUIRED',
  'M_WEAVE_E2EE_PEER_DEVICE_PENDING',
  'M_WEAVE_E2EE_SEND',
  'M_WEAVE_E2EE_TIMELINE',
  'M_WEAVE_E2EE_SYNC',
  'M_WEAVE_E2EE_STORE',
  'M_WEAVE_E2EE_TLS_ROOT',
  'M_WEAVE_E2EE_NOT_INITIALIZED',
  'M_WEAVE_E2EE_UNAVAILABLE',
  'M_WEAVE_CHAT_SETUP_REQUIRED',
  'M_WEAVE_CHAT_CREATE_ROOM_INVALID',
  'M_WEAVE_MATRIX_SESSION_EXPIRED',
  'M_WEAVE_MATRIX_MEMBER_SESSION',
  'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
  'M_WEAVE_MATRIX_ENDPOINT_UNCONFIRMED',
  'M_WEAVE_MATRIX_SESSION_CHANGED',
  'M_WEAVE_MATRIX_ACCESS_DENIED',
  'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
  'M_WEAVE_MATRIX_BINDING_INVALID',
  'M_WEAVE_MATRIX_BINDING_MISMATCH',
  'M_WEAVE_MATRIX_STORE_OWNER_MISMATCH',
  'M_WEAVE_MATRIX_STORE_LOCKED',
  'M_WEAVE_MATRIX_DEVICE_PROOF_INVALID',
  'M_WEAVE_MATRIX_RETIRED_GRANT_CLEAR_FAILED',
  'M_WEAVE_MATRIX_CONFIGURATION',
  'M_WEAVE_MATRIX_PROTOCOL',
  'M_WEAVE_MATRIX_BUILD',
  'M_WEAVE_MATRIX_TRANSPORT_TIMEOUT',
  'M_WEAVE_MATRIX_TRANSPORT_CONNECTION',
  'M_WEAVE_MATRIX_TRANSPORT_BODY',
  'M_WEAVE_MATRIX_DEVICE_RECOVERY',
  'M_WEAVE_MATRIX_SESSION_MISMATCH',
};
String _safeCode(String code) =>
    _allowedCodes.contains(code) ||
        RegExp(r'^M_WEAVE_MATRIX_HTTP_([1-5][0-9]{2})$').hasMatch(code)
    ? code
    : 'unavailable';

/// Classify by structured native codes or typed transport exceptions only.
ChatFailure classifyChatFailure(Object error) {
  final existing = error is ChatFailure ? error : null;
  final cause = existing?.cause ?? error;
  final code = cause is RustMatrixCoreBridgeException
      ? cause.code
      : existing?.message;
  ChatFailureType? type;
  if (code != null) {
    if (isMatrixSessionExpiredCode(code) ||
        code == 'M_WEAVE_MATRIX_MEMBER_SESSION' ||
        code == 'M_WEAVE_E2EE_NOT_INITIALIZED') {
      type = ChatFailureType.sessionRequired;
    } else if (code == 'M_FORBIDDEN' ||
        code == 'M_WEAVE_MATRIX_ACCESS_DENIED') {
      type = ChatFailureType.accessDenied;
    } else if (code == 'M_WEAVE_E2EE_STORE' ||
        code == 'M_WEAVE_MATRIX_BINDING_INVALID' ||
        code == 'M_WEAVE_MATRIX_BINDING_MISMATCH' ||
        code == 'M_WEAVE_MATRIX_STORE_OWNER_MISMATCH' ||
        code == 'M_WEAVE_MATRIX_STORE_LOCKED' ||
        code == 'M_WEAVE_MATRIX_DEVICE_PROOF_INVALID' ||
        code == 'M_WEAVE_MATRIX_RETIRED_GRANT_CLEAR_FAILED') {
      type = ChatFailureType.storage;
    } else if (code == 'M_WEAVE_MATRIX_TRANSPORT_TIMEOUT' ||
        code == 'M_WEAVE_MATRIX_TRANSPORT_CONNECTION' ||
        code == 'M_WEAVE_MATRIX_TRANSPORT_BODY') {
      type = ChatFailureType.transport;
    } else if (code == 'M_WEAVE_MATRIX_CONFIGURATION' ||
        code == 'M_WEAVE_E2EE_TLS_ROOT') {
      type = ChatFailureType.configuration;
    } else if (code == 'M_WEAVE_E2EE_PEER_DEVICE_PENDING') {
      type = ChatFailureType.peerDevicePending;
    } else {
      final match = RegExp(
        r'^M_WEAVE_MATRIX_HTTP_([1-5][0-9]{2})$',
      ).firstMatch(code);
      final status = match == null ? null : int.parse(match.group(1)!);
      if (status == 401) {
        type = ChatFailureType.sessionRequired;
      } else if (status == 403) {
        type = ChatFailureType.accessDenied;
      } else if (status != null && status >= 500) {
        type = ChatFailureType.serviceUnavailable;
      }
    }
  }
  if (type == null &&
      (cause is TimeoutException ||
          cause is http.ClientException ||
          cause is SocketException)) {
    type = ChatFailureType.transport;
  }
  if (type == null && cause is TlsException) {
    type = ChatFailureType.configuration;
  }
  if (type == null &&
      (cause is FileSystemException || cause is PlatformException) &&
      ChatOperationDiagnostics.current?.stage == ChatStage.bindingStorage) {
    type = ChatFailureType.storage;
  }
  if (type == null && cause is FormatException) {
    type = ChatFailureType.protocol;
  }
  if (type == null && existing != null) return existing;
  type ??= error is RustMatrixCoreBridgeException
      ? ChatFailureType.protocol
      : ChatFailureType.unknown;
  if (existing?.type == type) return existing!;
  return ChatFailure(
    type: type,
    message:
        existing?.message ??
        (code == null ? 'M_WEAVE_CHAT_OPERATION_FAILED' : _safeCode(code)),
    cause: cause,
  );
}
