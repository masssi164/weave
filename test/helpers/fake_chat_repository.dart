import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';

class FakeChatRepository implements ChatRepository {
  FakeChatRepository({
    this.loadConversationsHandler,
    this.loadRoomTimelineHandler,
    this.sendMessageHandler,
    this.markRoomReadHandler,
    this.connectHandler,
    this.signOutHandler,
    this.clearSessionHandler,
  });

  Future<List<ChatConversation>> Function()? loadConversationsHandler;
  Future<ChatRoomTimeline> Function(String roomId)? loadRoomTimelineHandler;
  Future<void> Function({required String roomId, required String message})?
  sendMessageHandler;
  Future<void> Function(String roomId)? markRoomReadHandler;
  Future<void> Function()? connectHandler;
  Future<void> Function()? signOutHandler;
  Future<void> Function()? clearSessionHandler;

  int loadConversationsCalls = 0;
  int loadRoomTimelineCalls = 0;
  int sendMessageCalls = 0;
  int markRoomReadCalls = 0;
  int connectCalls = 0;
  int signOutCalls = 0;
  int clearSessionCalls = 0;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    loadConversationsCalls++;
    return loadConversationsHandler?.call() ?? const <ChatConversation>[];
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    loadRoomTimelineCalls++;
    final handler = loadRoomTimelineHandler;
    if (handler == null) {
      throw UnimplementedError('loadRoomTimelineHandler was not provided.');
    }
    return handler(roomId);
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    sendMessageCalls++;
    await sendMessageHandler?.call(roomId: roomId, message: message);
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    markRoomReadCalls++;
    await markRoomReadHandler?.call(roomId);
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
