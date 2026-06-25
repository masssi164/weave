import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/backend_chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

/// Release chat flows always use the backend-owned product facade.
///
/// Direct Matrix clients may exist only in explicitly scoped legacy/debug tests;
/// normal member/release code must not instantiate them.
final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return BackendChatRepository(
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
    httpClient: ref.watch(weaveApiHttpClientProvider),
  );
});
