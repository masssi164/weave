import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

/// Release chat flows use the Matrix Client-Server projection as the chat data plane.
///
/// `/api/chat/**` remains a control/product facade for readiness, migration,
/// decisions, meetings, and Weaver scout evidence; normal message sync/send
/// does not treat OpenAPI/REST or a direct Matrix SDK as the Chat data plane.
final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return WeaveMatrixFacadeChatRepository(
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
    httpClient: ref.watch(weaveApiHttpClientProvider),
  );
});
