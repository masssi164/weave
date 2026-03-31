import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';

class FakeChatRepository implements ChatRepository {
  FakeChatRepository({
    this.loadConversationsHandler,
    this.connectHandler,
    this.signOutHandler,
    this.clearSessionHandler,
  });

  Future<List<ChatConversation>> Function()? loadConversationsHandler;
  Future<void> Function()? connectHandler;
  Future<void> Function()? signOutHandler;
  Future<void> Function()? clearSessionHandler;

  int loadConversationsCalls = 0;
  int connectCalls = 0;
  int signOutCalls = 0;
  int clearSessionCalls = 0;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    loadConversationsCalls++;
    return loadConversationsHandler?.call() ?? const <ChatConversation>[];
  }

  @override
  Future<void> connect() async {
    connectCalls++;
    await connectHandler?.call();
  }

  @override
  Future<void> signOut() async {
    signOutCalls++;
    await signOutHandler?.call();
  }

  @override
  Future<void> clearSession() async {
    clearSessionCalls++;
    await clearSessionHandler?.call();
  }
}
