import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

/// Release files flows always use the backend-owned product facade.
///
/// Direct provider clients may exist only in explicitly scoped admin/debug or
/// legacy tests; normal member/release code must not instantiate them.
final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  return BackendFilesRepository(
    httpClient: ref.watch(weaveApiHttpClientProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
  );
});
