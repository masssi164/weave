import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';
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
    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref
          .read(signInWithOidcProvider)
          .call(isInteractiveSignInSupported: _isSupportedPlatform);
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
      await ref.read(signOutWorkspaceProvider).call();
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

    if (!result.hasSessionImpact) {
      return;
    }

    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await ref.read(applyServerConfigurationChangesProvider).call(result);
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
      await ref.read(restartWorkspaceSetupProvider).call();
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
