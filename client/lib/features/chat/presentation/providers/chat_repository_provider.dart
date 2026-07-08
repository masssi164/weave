import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
import 'package:weave/features/chat/data/services/matrix_room_service.dart';
import 'package:weave/features/chat/data/services/matrix_session_service.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

/// Release chat flows use the Matrix Client-Server projection as the chat data plane.
///
/// `/api/chat/**` remains a transitional/control facade for readiness, migration
/// evidence, and generated DTO compatibility; normal message sync/send does not
/// treat OpenAPI/REST as the Chat data plane.
final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return MatrixChatRepository(
    sessionService: ref.watch(matrixSessionServiceProvider),
    conversationService: ref.watch(matrixConversationServiceProvider),
    roomService: ref.watch(matrixRoomServiceProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
