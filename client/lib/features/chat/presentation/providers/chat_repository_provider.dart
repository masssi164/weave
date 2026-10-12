import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/native_matrix_chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';

/// Release chat flows use the native Rust/Matrix SDK for transport and E2EE.
/// Weave User API room bindings remain separate from Matrix message transport.
final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return NativeMatrixChatRepository(
    matrixCryptoSessionCoordinator: ref.watch(
      matrixCryptoSessionCoordinatorProvider,
    ),
  );
});
