import 'package:matrix/matrix.dart' as sdk;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Maps Matrix SDK protocol errors into [ChatFailure].
///
/// Used by all internal Matrix services. This helper is platform-neutral;
/// services should import this file instead of importing platform-specific
/// factory files.
ChatFailure mapMatrixServiceError(Object error, {required String fallback}) {
  if (error is ChatFailure) return error;

  if (error is sdk.MatrixException) {
    final message = error.errorMessage.trim();
    return ChatFailure.protocol(
      message.isEmpty ? fallback : message,
      cause: error,
    );
  }

  return ChatFailure.unknown(fallback, cause: error);
}
