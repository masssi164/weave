import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class MatrixChatRepository implements ChatRepository {
  const MatrixChatRepository({
    required MatrixClient client,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _client = client,
       _serverConfigurationRepository = serverConfigurationRepository;

  final MatrixClient _client;
  final ServerConfigurationRepository _serverConfigurationRepository;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    final homeserver = await _loadHomeserver();
    final rooms = await _client.loadConversations(homeserver: homeserver);

    return rooms
        .map(
          (room) => ChatConversation(
            id: room.id,
            title: room.title,
            previewType: switch (room.previewType) {
              MatrixRoomPreviewType.none => ChatConversationPreviewType.none,
              MatrixRoomPreviewType.text => ChatConversationPreviewType.text,
              MatrixRoomPreviewType.encrypted =>
                ChatConversationPreviewType.encrypted,
              MatrixRoomPreviewType.unsupported =>
                ChatConversationPreviewType.unsupported,
            },
            previewText: room.previewText,
            lastActivityAt: room.lastActivityAt,
            unreadCount: room.unreadCount,
            isInvite: room.isInvite,
            isDirectMessage: room.isDirectMessage,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<void> connect() async {
    final homeserver = await _loadHomeserver();
    await _client.connect(homeserver: homeserver);
  }

  @override
  Future<void> signOut() => _client.signOut();

  @override
  Future<void> clearSession() => _client.clearSession();

  Future<Uri> _loadHomeserver() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const ChatFailure.configuration(
        'Finish setup before opening Matrix chat.',
      );
    }

    return configuration.serviceEndpoints.matrixHomeserverUrl;
  }
}
