import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/rust_matrix_core_chat_security_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';

/// Matrix E2EE device, verification, and recovery through the shared Rust core.
final chatSecurityRepositoryProvider = Provider<ChatSecurityRepository>((ref) {
  return RustMatrixCoreChatSecurityRepository(
    matrixCryptoSessionCoordinator: ref.watch(
      matrixCryptoSessionCoordinatorProvider,
    ),
  );
});
