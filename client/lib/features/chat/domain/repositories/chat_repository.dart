import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';

abstract interface class ChatRepository {
  Future<List<ChatConversation>> loadConversations();

  Future<ChatConversation> createConversation({
    required String title,
    String? spaceRef,
  });

  Future<ChatRoomTimeline> loadRoomTimeline(String roomId);

  Future<void> sendMessage({required String roomId, required String message});

  Future<void> markRoomRead(String roomId);

  Future<void> connect();

  Future<void> signOut();

  Future<void> clearSession();
}

abstract interface class ChatParticipantRepository {
  Future<List<ChatParticipant>> searchMembers(String query, {String? spaceRef});
  Future<ChatConversation> createConversationWithParticipants({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  });
}

abstract interface class ChatInvitationRepository {
  Future<void> acceptInvitation(String roomId);
}
