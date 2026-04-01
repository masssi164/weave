import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

abstract interface class ChatRepository {
  Future<List<ChatConversation>> loadConversations();

  Future<void> connect();

  Future<void> signOut();

  Future<void> clearSession();
}
