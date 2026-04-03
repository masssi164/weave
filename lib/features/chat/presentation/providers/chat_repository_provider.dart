import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

class MatrixSessionInvalidation extends Notifier<int> {
  @override
  int build() => 0;

  void bump() {
    state++;
  }
}

final matrixSessionInvalidationProvider =
    NotifierProvider<MatrixSessionInvalidation, int>(
      MatrixSessionInvalidation.new,
    );

final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return MatrixChatRepository(
    client: ref.watch(matrixClientProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
