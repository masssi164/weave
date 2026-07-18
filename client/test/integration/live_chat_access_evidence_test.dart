import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../integration_test/helpers/live_chat_access_evidence.dart';

void main() {
  test('accepts explicit forbidden during Chat setup as denial evidence', () {
    expect(
      isWorkspaceChatDeniedForEvidence(
        const ChatFailure.configuration(
          'Chat unavailable.',
          cause: RustMatrixCoreBridgeException('M_FORBIDDEN'),
        ),
      ),
      isTrue,
    );
  });

  test('accepts forbidden or concealed target Chat access', () {
    for (final code in <String>['M_FORBIDDEN', 'M_NOT_FOUND']) {
      expect(
        isWorkspaceChatDeniedForEvidence(
          ChatFailure.protocol(
            'Chat target unavailable.',
            cause: RustMatrixCoreBridgeException(code),
          ),
        ),
        isTrue,
      );
    }
  });

  test('rejects authentication, provider, and unstructured failures', () {
    for (final code in <String>[
      'M_UNAUTHORIZED',
      'M_UNAVAILABLE',
      'M_LIMIT_EXCEEDED',
    ]) {
      expect(
        isWorkspaceChatDeniedForEvidence(
          ChatFailure.protocol(
            'Chat failed.',
            cause: RustMatrixCoreBridgeException(code),
          ),
        ),
        isFalse,
      );
    }
    expect(
      isWorkspaceChatDeniedForEvidence(
        const ChatFailure.protocol('Chat failed.', cause: 'M_FORBIDDEN'),
      ),
      isFalse,
    );
  });
}
