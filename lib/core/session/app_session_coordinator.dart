import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

class AppSessionCoordinator {
  const AppSessionCoordinator(this.ref);

  final Ref ref;

  Future<void> signOut() async {
    final configuration = await ref
        .read(serverConfigurationRepositoryProvider)
        .loadConfiguration();

    if (configuration != null && configuration.hasCompleteAuthConfiguration) {
      await ref
          .read(authSessionRepositoryProvider)
          .signOut(
            AuthConfiguration(
              issuer: configuration.oidcIssuerUrl,
              clientId: configuration.oidcClientRegistration.clientId.trim(),
            ),
          );
    } else {
      await ref.read(authSessionRepositoryProvider).clearLocalSession();
    }

    await ref.read(chatRepositoryProvider).signOut();
    await ref.read(filesRepositoryProvider).disconnect();
    _invalidateMatrixSession();
  }

  Future<void> restartSetup() async {
    await ref.read(authSessionRepositoryProvider).clearLocalSession();
    await ref.read(chatRepositoryProvider).clearSession();
    await ref.read(filesRepositoryProvider).disconnect();
    await ref.read(serverConfigurationRepositoryProvider).clearConfiguration();
    _invalidateMatrixSession();
  }

  Future<void> handleConfigurationSaved(
    ServerConfigurationSaveResult result,
  ) async {
    if (result.authConfigurationChanged) {
      await ref.read(authSessionRepositoryProvider).clearLocalSession();
    }

    if (result.matrixHomeserverChanged) {
      await ref.read(chatRepositoryProvider).clearSession();
      _invalidateMatrixSession();
    }

    if (result.nextcloudBaseUrlChanged) {
      await ref.read(filesRepositoryProvider).disconnect();
    }
  }

  void _invalidateMatrixSession() {
    ref.read(matrixSessionInvalidationProvider.notifier).bump();
  }
}

final appSessionCoordinatorProvider = Provider<AppSessionCoordinator>(
  AppSessionCoordinator.new,
);
