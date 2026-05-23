import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_security_repository.dart';
import 'package:weave/features/chat/data/services/matrix_security_service.dart';
import 'package:weave/features/chat/data/services/matrix_verification_service.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

final chatSecurityRepositoryProvider = Provider<ChatSecurityRepository>((ref) {
  return MatrixChatSecurityRepository(
    securityService: ref.watch(matrixSecurityServiceProvider),
    verificationService: ref.watch(matrixVerificationServiceProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
