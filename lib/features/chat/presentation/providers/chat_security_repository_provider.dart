import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_security_repository.dart';
import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';

final chatSecurityRepositoryProvider = Provider<ChatSecurityRepository>((ref) {
  return MatrixChatSecurityRepository(
    client: ref.watch(matrixClientProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
