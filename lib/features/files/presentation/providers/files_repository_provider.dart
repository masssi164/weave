import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/data/repositories/secure_nextcloud_session_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_auth_client.dart';
import 'package:weave/features/files/data/services/nextcloud_client.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  return NextcloudFilesRepository(
    authClient: ref.watch(nextcloudAuthClientProvider),
    client: ref.watch(nextcloudClientProvider),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
    sessionRepository: ref.watch(nextcloudSessionRepositoryProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
  );
});
