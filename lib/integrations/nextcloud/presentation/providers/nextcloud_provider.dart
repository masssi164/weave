import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/nextcloud/data/repositories/secure_nextcloud_session_repository.dart';
import 'package:weave/integrations/nextcloud/data/services/default_nextcloud_connection_service.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_auth_client.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_dav_access_validator.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_login_launcher.dart';
import 'package:weave/integrations/nextcloud/domain/repositories/nextcloud_session_repository.dart';
import 'package:weave/integrations/nextcloud/domain/services/nextcloud_connection_service.dart';

final nextcloudHttpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final nextcloudLoginLauncherProvider = Provider<NextcloudLoginLauncher>((ref) {
  return const UrlLauncherNextcloudLoginLauncher();
});

final nextcloudAuthClientProvider = Provider<NextcloudAuthClient>((ref) {
  return NextcloudAuthClient(
    httpClient: ref.watch(nextcloudHttpClientProvider),
    loginLauncher: ref.watch(nextcloudLoginLauncherProvider),
  );
});

final nextcloudSessionRepositoryProvider = Provider<NextcloudSessionRepository>(
  (ref) {
    return SecureNextcloudSessionRepository(
      secureStore: ref.watch(secureStoreProvider),
    );
  },
);

final nextcloudDavAccessValidatorProvider =
    Provider<NextcloudDavAccessValidator>((ref) {
      return HttpNextcloudDavAccessValidator(
        httpClient: ref.watch(nextcloudHttpClientProvider),
      );
    });

final nextcloudConnectionServiceProvider = Provider<NextcloudConnectionService>(
  (ref) {
    return DefaultNextcloudConnectionService(
      authClient: ref.watch(nextcloudAuthClientProvider),
      davAccessValidator: ref.watch(nextcloudDavAccessValidatorProvider),
      authSessionRepository: ref.watch(authSessionRepositoryProvider),
      sessionRepository: ref.watch(nextcloudSessionRepositoryProvider),
      serverConfigurationRepository: ref.watch(
        serverConfigurationRepositoryProvider,
      ),
    );
  },
);
