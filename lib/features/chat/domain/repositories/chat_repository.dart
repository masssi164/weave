import 'package:weave/features/chat/domain/entities/chat_message.dart';

abstract interface class ChatRepository {
  Future<List<ChatMessage>> loadMessages();
}
