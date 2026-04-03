import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/apply_server_configuration_changes.dart';
import 'package:weave/features/app/domain/use_cases/resolve_app_bootstrap.dart';
import 'package:weave/features/app/domain/use_cases/restart_workspace_setup.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/app/domain/use_cases/sign_out_workspace.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

final appAuthPortProvider = Provider<AppAuthPort>((ref) {
  return _RepositoryAppAuthPort(ref.watch(authSessionRepositoryProvider));
});

final chatSessionPortProvider = Provider<ChatSessionPort>((ref) {
  return _RiverpodChatSessionPort(ref);
});

final filesSessionPortProvider = Provider<FilesSessionPort>((ref) {
  return _RepositoryFilesSessionPort(ref.watch(filesRepositoryProvider));
});

final serverConfigurationPortProvider = Provider<ServerConfigurationPort>((
  ref,
) {
  return _RepositoryServerConfigurationPort(
    ref.watch(serverConfigurationRepositoryProvider),
  );
});

final resolveAppBootstrapProvider = Provider<ResolveAppBootstrap>((ref) {
  return ResolveAppBootstrap(
    authPort: ref.watch(appAuthPortProvider),
    serverConfigurationPort: ref.watch(serverConfigurationPortProvider),
  );
});

final signInWithOidcProvider = Provider<SignInWithOidc>((ref) {
  return SignInWithOidc(
    authPort: ref.watch(appAuthPortProvider),
    serverConfigurationPort: ref.watch(serverConfigurationPortProvider),
  );
});

final signOutWorkspaceProvider = Provider<SignOutWorkspace>((ref) {
  return SignOutWorkspace(
    authPort: ref.watch(appAuthPortProvider),
    chatSessionPort: ref.watch(chatSessionPortProvider),
    filesSessionPort: ref.watch(filesSessionPortProvider),
    serverConfigurationPort: ref.watch(serverConfigurationPortProvider),
  );
});

final restartWorkspaceSetupProvider = Provider<RestartWorkspaceSetup>((ref) {
  return RestartWorkspaceSetup(
    authPort: ref.watch(appAuthPortProvider),
    chatSessionPort: ref.watch(chatSessionPortProvider),
    filesSessionPort: ref.watch(filesSessionPortProvider),
    serverConfigurationPort: ref.watch(serverConfigurationPortProvider),
  );
});

final applyServerConfigurationChangesProvider =
    Provider<ApplyServerConfigurationChanges>((ref) {
      return ApplyServerConfigurationChanges(
        authPort: ref.watch(appAuthPortProvider),
        chatSessionPort: ref.watch(chatSessionPortProvider),
        filesSessionPort: ref.watch(filesSessionPortProvider),
      );
    });

class _RepositoryAppAuthPort implements AppAuthPort {
  const _RepositoryAppAuthPort(this._repository);

  final AuthSessionRepository _repository;

  @override
  Future<void> clearLocalSession() => _repository.clearLocalSession();

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) {
    return _repository.restoreSession(configuration);
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) {
    return _repository.signIn(configuration);
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) {
    return _repository.signOut(configuration);
  }
}

class _RiverpodChatSessionPort implements ChatSessionPort {
  _RiverpodChatSessionPort(this._ref);

  final Ref _ref;

  ChatRepository get _repository => _ref.read(chatRepositoryProvider);

  @override
  Future<void> clearSession() => _repository.clearSession();

  @override
  void invalidateActiveSession() {
    _ref.read(matrixSessionInvalidationProvider.notifier).bump();
  }

  @override
  Future<void> signOut() => _repository.signOut();
}

class _RepositoryFilesSessionPort implements FilesSessionPort {
  const _RepositoryFilesSessionPort(this._repository);

  final FilesRepository _repository;

  @override
  Future<void> disconnect() => _repository.disconnect();
}

class _RepositoryServerConfigurationPort implements ServerConfigurationPort {
  const _RepositoryServerConfigurationPort(this._repository);

  final ServerConfigurationRepository _repository;

  @override
  Future<void> clearConfiguration() => _repository.clearConfiguration();

  @override
  Future<ServerConfiguration?> loadConfiguration() {
    return _repository.loadConfiguration();
  }
}
