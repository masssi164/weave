import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';

class StubChatRepository implements ChatRepository {
  const StubChatRepository({required MatrixClient client}) : _client = client;

  final MatrixClient _client;

  @override
  Future<List<ChatMessage>> loadMessages() async {
    final _ = _client;
    return const [];
  }
}
