import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/rust_matrix_core_chat_security_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

/// Diagnostic-only Matrix E2EE/security seam through the Rust core boundary.
///
/// Normal member chat/readiness flows must use backend-owned Chat and workspace
/// capability facades. Direct Matrix SDK crypto is intentionally absent; E2EE
/// actions fail closed until the flutter_rust_bridge implementation owns device
/// verification and recovery.
final chatSecurityRepositoryProvider = Provider<ChatSecurityRepository>((ref) {
  return RustMatrixCoreChatSecurityRepository(
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
