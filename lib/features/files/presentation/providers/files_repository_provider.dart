import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/legacy_direct_nextcloud_files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

/// Enables the backend files facade path for MVP product files flows.
///
/// Backend facade mode is the default. Set
/// `--dart-define=WEAVE_USE_BACKEND_FILES_FACADE=false` only for the explicit
/// legacy fallback path that still talks directly to Nextcloud WebDAV.
final useBackendFilesFacadeProvider = Provider<bool>((ref) {
  return const bool.fromEnvironment(
    'WEAVE_USE_BACKEND_FILES_FACADE',
    defaultValue: true,
  );
});

final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  if (ref.watch(useBackendFilesFacadeProvider)) {
    return BackendFilesRepository(
      httpClient: ref.watch(weaveApiHttpClientProvider),
      serverConfigurationRepository: ref.watch(
        serverConfigurationRepositoryProvider,
      ),
      authSessionRepository: ref.watch(authSessionRepositoryProvider),
    );
  }

  return ref.watch(legacyDirectNextcloudFilesRepositoryProvider);
});
