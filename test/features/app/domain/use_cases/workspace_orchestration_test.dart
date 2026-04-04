import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/files_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/ports/workspace_invalidation_port.dart';
import 'package:weave/features/app/domain/use_cases/apply_server_configuration_changes.dart';
import 'package:weave/features/app/domain/use_cases/restart_workspace_setup.dart';
import 'package:weave/features/app/domain/use_cases/sign_out_workspace.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeAppAuthPort implements AppAuthPort {
  int clearLocalSessionCalls = 0;
  int signOutCalls = 0;
  AuthConfiguration? lastSignOutConfiguration;

  @override
  Future<void> clearLocalSession() async {
    clearLocalSessionCalls++;
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    signOutCalls++;
    lastSignOutConfiguration = configuration;
  }
}

class _FakeChatSessionPort implements ChatSessionPort {
  int signOutCalls = 0;
  int clearSessionCalls = 0;

  @override
  Future<void> clearSession() async {
    clearSessionCalls++;
  }

  @override
  Future<void> signOut() async {
    signOutCalls++;
  }
}

class _FakeFilesSessionPort implements FilesSessionPort {
  int disconnectCalls = 0;

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
  }
}

class _FakeServerConfigurationPort implements ServerConfigurationPort {
  _FakeServerConfigurationPort({this.configuration});

  ServerConfiguration? configuration;
  int clearConfigurationCalls = 0;

  @override
  Future<void> clearConfiguration() async {
    clearConfigurationCalls++;
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;
}

class _FakeWorkspaceInvalidationPort implements WorkspaceInvalidationPort {
  final List<IntegrationInvalidation> invalidations =
      <IntegrationInvalidation>[];

  @override
  void invalidate({
    required WorkspaceIntegration integration,
    required IntegrationInvalidationReason reason,
  }) {
    final sequence =
        invalidations
            .where((entry) => entry.integration == integration)
            .length +
        1;
    invalidations.add(
      IntegrationInvalidation(
        integration: integration,
        reason: reason,
        sequence: sequence,
      ),
    );
  }

  IntegrationInvalidationReason? lastReasonFor(
    WorkspaceIntegration integration,
  ) {
    for (final invalidation in invalidations.reversed) {
      if (invalidation.integration == integration) {
        return invalidation.reason;
      }
    }

    return null;
  }
}

void main() {
  group('SignOutWorkspace', () {
    test('signs out auth, chat, and files with session invalidation', () async {
      final authPort = _FakeAppAuthPort();
      final chatSessionPort = _FakeChatSessionPort();
      final filesSessionPort = _FakeFilesSessionPort();
      final workspaceInvalidationPort = _FakeWorkspaceInvalidationPort();
      final useCase = SignOutWorkspace(
        authPort: authPort,
        chatSessionPort: chatSessionPort,
        filesSessionPort: filesSessionPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(clientId: ' weave-mobile '),
        ),
        workspaceInvalidationPort: workspaceInvalidationPort,
      );

      await useCase.call();

      expect(authPort.signOutCalls, 1);
      expect(authPort.lastSignOutConfiguration?.clientId, 'weave-mobile');
      expect(chatSessionPort.signOutCalls, 1);
      expect(filesSessionPort.disconnectCalls, 1);
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.appAuth),
        IntegrationInvalidationReason.explicitSignOut,
      );
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.matrix),
        IntegrationInvalidationReason.explicitSignOut,
      );
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.nextcloud),
        IntegrationInvalidationReason.explicitSignOut,
      );
    });

    test('clears local auth when no complete configuration is saved', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = SignOutWorkspace(
        authPort: authPort,
        chatSessionPort: _FakeChatSessionPort(),
        filesSessionPort: _FakeFilesSessionPort(),
        serverConfigurationPort: _FakeServerConfigurationPort(),
        workspaceInvalidationPort: _FakeWorkspaceInvalidationPort(),
      );

      await useCase.call();

      expect(authPort.clearLocalSessionCalls, 1);
      expect(authPort.signOutCalls, 0);
    });
  });

  group('RestartWorkspaceSetup', () {
    test(
      'clears saved sessions, configuration, and chat invalidation',
      () async {
        final authPort = _FakeAppAuthPort();
        final chatSessionPort = _FakeChatSessionPort();
        final filesSessionPort = _FakeFilesSessionPort();
        final serverConfigurationPort = _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(),
        );
        final workspaceInvalidationPort = _FakeWorkspaceInvalidationPort();
        final useCase = RestartWorkspaceSetup(
          authPort: authPort,
          chatSessionPort: chatSessionPort,
          filesSessionPort: filesSessionPort,
          serverConfigurationPort: serverConfigurationPort,
          workspaceInvalidationPort: workspaceInvalidationPort,
        );

        await useCase.call();

        expect(authPort.clearLocalSessionCalls, 1);
        expect(chatSessionPort.clearSessionCalls, 1);
        expect(filesSessionPort.disconnectCalls, 1);
        expect(serverConfigurationPort.clearConfigurationCalls, 1);
        expect(
          workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.appAuth),
          IntegrationInvalidationReason.restartSetup,
        );
        expect(
          workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.matrix),
          IntegrationInvalidationReason.restartSetup,
        );
        expect(
          workspaceInvalidationPort.lastReasonFor(
            WorkspaceIntegration.nextcloud,
          ),
          IntegrationInvalidationReason.restartSetup,
        );
      },
    );
  });

  group('ApplyServerConfigurationChanges', () {
    test('clears only Matrix state when the homeserver changes', () async {
      final authPort = _FakeAppAuthPort();
      final chatSessionPort = _FakeChatSessionPort();
      final filesSessionPort = _FakeFilesSessionPort();
      final workspaceInvalidationPort = _FakeWorkspaceInvalidationPort();
      final useCase = ApplyServerConfigurationChanges(
        authPort: authPort,
        chatSessionPort: chatSessionPort,
        filesSessionPort: filesSessionPort,
        workspaceInvalidationPort: workspaceInvalidationPort,
      );

      await useCase.call(
        ServerConfigurationSaveResult(
          configuration: buildTestConfiguration(),
          authConfigurationChanged: false,
          matrixHomeserverChanged: true,
          nextcloudBaseUrlChanged: false,
        ),
      );

      expect(authPort.clearLocalSessionCalls, 0);
      expect(chatSessionPort.clearSessionCalls, 1);
      expect(filesSessionPort.disconnectCalls, 0);
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.matrix),
        IntegrationInvalidationReason.matrixHomeserverChanged,
      );
    });

    test('clears only Nextcloud when the base URL changes', () async {
      final authPort = _FakeAppAuthPort();
      final chatSessionPort = _FakeChatSessionPort();
      final filesSessionPort = _FakeFilesSessionPort();
      final workspaceInvalidationPort = _FakeWorkspaceInvalidationPort();
      final useCase = ApplyServerConfigurationChanges(
        authPort: authPort,
        chatSessionPort: chatSessionPort,
        filesSessionPort: filesSessionPort,
        workspaceInvalidationPort: workspaceInvalidationPort,
      );

      await useCase.call(
        ServerConfigurationSaveResult(
          configuration: buildTestConfiguration(),
          authConfigurationChanged: false,
          matrixHomeserverChanged: false,
          nextcloudBaseUrlChanged: true,
        ),
      );

      expect(authPort.clearLocalSessionCalls, 0);
      expect(chatSessionPort.clearSessionCalls, 0);
      expect(filesSessionPort.disconnectCalls, 1);
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.nextcloud),
        IntegrationInvalidationReason.nextcloudBaseUrlChanged,
      );
    });

    test('records app auth invalidation when auth settings change', () async {
      final authPort = _FakeAppAuthPort();
      final workspaceInvalidationPort = _FakeWorkspaceInvalidationPort();
      final useCase = ApplyServerConfigurationChanges(
        authPort: authPort,
        chatSessionPort: _FakeChatSessionPort(),
        filesSessionPort: _FakeFilesSessionPort(),
        workspaceInvalidationPort: workspaceInvalidationPort,
      );

      await useCase.call(
        ServerConfigurationSaveResult(
          configuration: buildTestConfiguration(),
          authConfigurationChanged: true,
          matrixHomeserverChanged: false,
          nextcloudBaseUrlChanged: false,
        ),
      );

      expect(authPort.clearLocalSessionCalls, 1);
      expect(
        workspaceInvalidationPort.lastReasonFor(WorkspaceIntegration.appAuth),
        IntegrationInvalidationReason.authConfigurationChanged,
      );
    });
  });
}
