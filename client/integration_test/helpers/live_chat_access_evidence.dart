import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

bool isWorkspaceChatDeniedForEvidence(ChatFailure failure) {
  final cause = failure.cause;
  if (cause is! RustMatrixCoreBridgeException) {
    return false;
  }
  return (failure.type == ChatFailureType.configuration ||
          failure.type == ChatFailureType.protocol) &&
      (cause.code == 'M_FORBIDDEN' || cause.code == 'M_NOT_FOUND');
}
