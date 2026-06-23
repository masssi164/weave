import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_security_repository.dart';
import 'package:weave/features/chat/data/services/matrix_security_service.dart';
import 'package:weave/features/chat/data/services/matrix_verification_service.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

/// Diagnostic-only Matrix E2EE/security seam.
///
/// Normal member chat/readiness flows must use backend-owned Chat and workspace
/// capability facades. This provider is kept only for explicit security
/// diagnostics until #895 replaces/fences the diagnostic with a backend API.
final chatSecurityRepositoryProvider = Provider<ChatSecurityRepository>((ref) {
  return MatrixChatSecurityRepository(
    securityService: ref.watch(matrixSecurityServiceProvider),
    verificationService: ref.watch(matrixVerificationServiceProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
