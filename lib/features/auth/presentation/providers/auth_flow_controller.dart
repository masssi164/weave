import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/session/app_session_coordinator.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

class AuthFlowState {
  const AuthFlowState({required this.isBusy, this.failure});

  const AuthFlowState.idle() : this(isBusy: false);

  final bool isBusy;
  final AuthFailure? failure;

  AuthFlowState copyWith({
    bool? isBusy,
    AuthFailure? failure,
    bool clearFailure = false,
  }) {
    return AuthFlowState(
      isBusy: isBusy ?? this.isBusy,
      failure: clearFailure ? null : (failure ?? this.failure),
    );
  }
}

class AuthFlowController extends Notifier<AuthFlowState> {
  @override
  AuthFlowState build() => const AuthFlowState.idle();

  Future<void> signIn() async {
    final configuration = await _loadConfigurationForAuth();
    if (configuration == null) {
      state = state.copyWith(
        failure: const AuthFailure.configuration(
          'Finish server setup before signing in.',
        ),
      );
      return;
    }

    if (!_isSupportedPlatform) {
      state = state.copyWith(
        failure: const AuthFailure.unsupportedPlatform(
          'Interactive sign-in is currently supported on Android, iOS, and macOS.',
        ),
      );
      return;
    }

    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref
          .read(authSessionRepositoryProvider)
          .signIn(_toAuthConfiguration(configuration));
      await ref.read(appBootstrapProvider.notifier).retry();
      state = state.copyWith(isBusy: false, clearFailure: true);
    } on AuthFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.unknown(
          'Unable to sign in right now.',
          cause: error,
        ),
      );
    }
  }

  Future<void> signOut() async {
    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref.read(appSessionCoordinatorProvider).signOut();
      await ref.read(appBootstrapProvider.notifier).retry();
      state = state.copyWith(isBusy: false, clearFailure: true);
    } on AuthFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } on ChatFailure catch (failure) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.storage(failure.message, cause: failure.cause),
      );
    } on AppFailure catch (failure) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.storage(failure.message, cause: failure.cause),
      );
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.unknown(
          'Unable to sign out right now.',
          cause: error,
        ),
      );
    }
  }

  Future<void> handleConfigurationSaved(
    ServerConfigurationSaveResult result,
  ) async {
    ref.invalidate(savedServerConfigurationProvider);

    if (!result.authConfigurationChanged &&
        !result.matrixHomeserverChanged &&
        !result.nextcloudBaseUrlChanged) {
      return;
    }

    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref
          .read(appSessionCoordinatorProvider)
          .handleConfigurationSaved(result);
      await ref.read(appBootstrapProvider.notifier).retry();
      state = state.copyWith(isBusy: false, clearFailure: true);
    } on AuthFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } on ChatFailure catch (failure) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.storage(failure.message, cause: failure.cause),
      );
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.unknown(
          'Unable to apply the updated sign-in settings.',
          cause: error,
        ),
      );
    }
  }

  Future<void> restartSetup() async {
    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref.read(appSessionCoordinatorProvider).restartSetup();
      ref.invalidate(savedServerConfigurationProvider);
      await ref.read(appBootstrapProvider.notifier).retry();
      state = state.copyWith(isBusy: false, clearFailure: true);
    } on AuthFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } on ChatFailure catch (failure) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.storage(failure.message, cause: failure.cause),
      );
    } on AppFailure catch (failure) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.storage(failure.message, cause: failure.cause),
      );
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: AuthFailure.unknown(
          'Unable to return to setup right now.',
          cause: error,
        ),
      );
    }
  }

  Future<ServerConfiguration?> _loadConfigurationForAuth() async {
    final configuration = await ref
        .read(serverConfigurationRepositoryProvider)
        .loadConfiguration();
    if (configuration == null || !configuration.hasCompleteAuthConfiguration) {
      return null;
    }

    return configuration;
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }

  bool get _isSupportedPlatform {
    if (kIsWeb) {
      return false;
    }

    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
  }
}

final authFlowControllerProvider =
    NotifierProvider<AuthFlowController, AuthFlowState>(AuthFlowController.new);
