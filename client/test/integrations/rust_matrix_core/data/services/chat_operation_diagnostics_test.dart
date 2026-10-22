import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/chat_operation_diagnostics.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

void main() {
  for (final (code, expected) in [
    ('M_WEAVE_E2EE_STORE', ChatFailureType.storage),
    ('M_WEAVE_MATRIX_BINDING_MISMATCH', ChatFailureType.storage),
    ('M_WEAVE_MATRIX_TRANSPORT_TIMEOUT', ChatFailureType.transport),
    ('M_WEAVE_MATRIX_TRANSPORT_CONNECTION', ChatFailureType.transport),
    ('M_WEAVE_MATRIX_PROTOCOL', ChatFailureType.protocol),
    ('M_WEAVE_E2EE_PEER_DEVICE_PENDING', ChatFailureType.peerDevicePending),
    ('M_UNKNOWN_TOKEN', ChatFailureType.sessionRequired),
    ('M_FORBIDDEN', ChatFailureType.accessDenied),
    ('M_WEAVE_MATRIX_HTTP_401', ChatFailureType.sessionRequired),
    ('M_WEAVE_MATRIX_HTTP_403', ChatFailureType.accessDenied),
    ('M_WEAVE_MATRIX_HTTP_503', ChatFailureType.serviceUnavailable),
    ('M_WEAVE_E2EE_TLS_ROOT', ChatFailureType.configuration),
    ('private unknown body bearer', ChatFailureType.protocol),
  ]) {
    test('preserves typed native class $code', () {
      final cause = RustMatrixCoreBridgeException(code);
      final result = classifyChatFailure(
        ChatFailure.protocol('safe operation', cause: cause),
      );
      expect(result.type, expected);
      expect(result.cause, same(cause));
    });
  }
  test('only evidenced transport failures use connection guidance', () {
    for (final cause in [
      TimeoutException('private'),
      http.ClientException('private'),
      const SocketException('private'),
    ]) {
      expect(classifyChatFailure(cause).type, ChatFailureType.transport);
    }
    expect(
      classifyChatFailure(StateError('private')).type,
      ChatFailureType.unknown,
    );
    expect(
      classifyChatFailure(const FormatException('private')).type,
      ChatFailureType.protocol,
    );
  });
  test('every phase emits one redacted record with a fresh correlation', () async {
    final original = debugPrint;
    final records = <String>[];
    debugPrint = (String? value, {int? wrapWidth}) => records.add(value ?? '');
    addTearDown(() => debugPrint = original);
    final correlations = <String>{};
    for (final stage in ChatStage.values) {
      await expectLater(
        ChatOperationDiagnostics.run<void>(
          ChatOperation.conversations,
          () async {
            ChatOperationDiagnostics.mark(stage);
            ChatOperationDiagnostics.status(503);
            throw ChatFailure.protocol(
              'private body https://secret/token',
              cause: StateError('private member'),
            );
          },
        ),
        throwsA(isA<ChatFailure>()),
      );
      expect(
        records.last,
        contains(
          'operation=conversations stage=${stage.name} type=protocol code=unavailable httpStatus=503',
        ),
      );
      final match = RegExp(
        r'correlation=([0-9a-f-]{36}) candidate=(unavailable|[0-9a-f]{40}) supportSafe=true$',
      ).firstMatch(records.last);
      expect(match, isNotNull);
      expect(correlations.add(match!.group(1)!), isTrue);
    }
    expect(records.length, ChatStage.values.length);
    expect(records.join(), isNot(contains('private')));
    expect(records.join(), isNot(contains('https:')));
  });
  test(
    'only the local persistence phase classifies filesystem failure as storage',
    () async {
      const error = FileSystemException('private', '/private/member');
      expect(classifyChatFailure(error).type, ChatFailureType.unknown);
      await expectLater(
        ChatOperationDiagnostics.run<void>(ChatOperation.connect, () async {
          ChatOperationDiagnostics.mark(ChatStage.bindingStorage);
          throw error;
        }),
        throwsA(
          isA<ChatFailure>().having(
            (value) => value.type,
            'type',
            ChatFailureType.storage,
          ),
        ),
      );
    },
  );
}
