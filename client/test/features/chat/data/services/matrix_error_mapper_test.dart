import 'package:matrix/matrix.dart' as sdk;
import 'package:test/test.dart';
import 'package:weave/features/chat/data/services/matrix_error_mapper.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

void main() {
  group('mapMatrixServiceError', () {
    test('redacts raw homeserver error text for known Matrix failures', () {
      final failure = mapMatrixServiceError(
        sdk.MatrixException.fromJson({
          'errcode': 'M_FORBIDDEN',
          'error':
              'Forbidden for https://matrix.example.invalid/_matrix/client token=secret',
        }),
        fallback: 'Chat action failed.',
      );

      expect(failure.type, ChatFailureType.protocol);
      expect(failure.message, 'Chat is not allowed for this room or account.');
      expect(failure.message, isNot(contains('matrix.example.invalid')));
      expect(failure.message, isNot(contains('secret')));
    });

    test(
      'uses fallback for unknown Matrix failures instead of raw provider text',
      () {
        final failure = mapMatrixServiceError(
          sdk.MatrixException.fromJson({
            'errcode': 'M_UNKNOWN',
            'error': 'Homeserver raw room id !secret:example.invalid failed',
          }),
          fallback: 'Chat could not complete the request.',
        );

        expect(failure.type, ChatFailureType.protocol);
        expect(failure.message, 'Chat could not complete the request.');
        expect(failure.message, isNot(contains('!secret')));
      },
    );
  });
}
