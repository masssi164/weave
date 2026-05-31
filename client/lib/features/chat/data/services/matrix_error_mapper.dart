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
    return ChatFailure.protocol(
      _supportSafeMatrixMessage(error, fallback),
      cause: error,
    );
  }

  return ChatFailure.unknown(fallback, cause: error);
}

String _supportSafeMatrixMessage(sdk.MatrixException error, String fallback) {
  final code = error.error.name;
  return switch (code) {
    'M_FORBIDDEN' => 'Chat is not allowed for this room or account.',
    'M_NOT_FOUND' => 'That chat room is no longer available.',
    'M_LIMIT_EXCEEDED' => 'Chat is temporarily rate limited. Try again later.',
    'M_UNAUTHORIZED' ||
    'M_UNKNOWN_TOKEN' => 'Chat needs you to reconnect before continuing.',
    _ => fallback,
  };
}
