import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/chat/data/repositories/stub_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';

part 'chat_provider.g.dart';

@Riverpod(keepAlive: true)
MatrixClient matrixClient(Ref ref) => const MatrixClient();

@Riverpod(keepAlive: true)
ChatRepository chatRepository(Ref ref) {
  final client = ref.watch(matrixClientProvider);
  return StubChatRepository(client: client);
}

@riverpod
class ChatNotifier extends _$ChatNotifier {
  @override
  Future<List<ChatMessage>> build() async {
    final repository = ref.watch(chatRepositoryProvider);
    return repository.loadMessages();
  }
}
