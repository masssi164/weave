import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../helpers/fake_chat_security_repository.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeAppBootstrap extends AppBootstrap {
  _FakeAppBootstrap(this._state);

  final BootstrapState _state;

  @override
  Future<BootstrapState> build() async => _state;
}

class _FakeFilesRepository implements FilesRepository {
  _FakeFilesRepository({required this.connectionState});

  final FilesConnectionState connectionState;

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    return DirectoryListing(path: path, entries: const []);
  }

  @override
  Future<FilesConnectionState> restoreConnection() async => connectionState;
}

void main() {
  group('workspace connection providers', () {
    test('maps ready bootstrap into connected shell access', () async {
      final container = ProviderContainer.test(
        overrides: [
          appBootstrapProvider.overrideWith(
            () => _FakeAppBootstrap(const BootstrapState.ready()),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(appBootstrapProvider.future);
      final state = container.read(appAuthIntegrationConnectionProvider);

      expect(state.requireValue.status, IntegrationConnectionStatus.connected);
      expect(
        state.requireValue.recoveryRequirement,
        IntegrationRecoveryRequirement.none,
      );
    });

    test(
      'maps Matrix security attention into a degraded integration',
      () async {
        final container = ProviderContainer.test(
          overrides: [
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(
                loadSecurityStateHandler: ({bool refresh = false}) async {
                  return const ChatSecurityState(
                    isMatrixSignedIn: true,
                    bootstrapState: ChatSecurityBootstrapState.recoveryRequired,
                    accountVerificationState:
                        ChatAccountVerificationState.verified,
                    deviceVerificationState:
                        ChatDeviceVerificationState.verified,
                    keyBackupState: ChatKeyBackupState.recoveryRequired,
                    roomEncryptionReadiness:
                        ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
                    secretStorageReady: false,
                    crossSigningReady: true,
                    hasEncryptedConversations: true,
                    verificationSession: ChatVerificationSession.none(),
                  );
                },
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        final state = await container.read(
          matrixIntegrationConnectionProvider.future,
        );

        expect(state.status, IntegrationConnectionStatus.degraded);
        expect(
          state.recoveryRequirement,
          IntegrationRecoveryRequirement.completeSetup,
        );
      },
    );

    test('maps invalid Nextcloud credentials into reauthentication', () async {
      final container = ProviderContainer.test(
        overrides: [
          savedServerConfigurationProvider.overrideWith(
            (ref) async => buildTestConfiguration(),
          ),
          filesRepositoryProvider.overrideWithValue(
            _FakeFilesRepository(
              connectionState: FilesConnectionState.invalid(
                baseUrl: Uri.parse('https://nextcloud.home.internal'),
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final state = await container.read(
        nextcloudIntegrationConnectionProvider.future,
      );

      expect(
        state.status,
        IntegrationConnectionStatus.requiresReauthentication,
      );
      expect(
        state.recoveryRequirement,
        IntegrationRecoveryRequirement.reauthenticate,
      );
    });

    test(
      'keeps shell access ready while service readiness stays degraded',
      () async {
        final container = ProviderContainer.test(
          overrides: [
            appBootstrapProvider.overrideWith(
              () => _FakeAppBootstrap(const BootstrapState.ready()),
            ),
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(
                loadSecurityStateHandler: ({bool refresh = false}) async {
                  return const ChatSecurityState(
                    isMatrixSignedIn: true,
                    bootstrapState:
                        ChatSecurityBootstrapState.partiallyInitialized,
                    accountVerificationState:
                        ChatAccountVerificationState.verificationRequired,
                    deviceVerificationState:
                        ChatDeviceVerificationState.unverified,
                    keyBackupState: ChatKeyBackupState.missing,
                    roomEncryptionReadiness:
                        ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
                    secretStorageReady: false,
                    crossSigningReady: false,
                    hasEncryptedConversations: true,
                    verificationSession: ChatVerificationSession.none(),
                  );
                },
              ),
            ),
            filesRepositoryProvider.overrideWithValue(
              _FakeFilesRepository(
                connectionState: FilesConnectionState.connected(
                  baseUrl: Uri.parse('https://nextcloud.home.internal'),
                  accountLabel: 'alice',
                ),
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(appBootstrapProvider.future);
        await container.read(matrixIntegrationConnectionProvider.future);
        await container.read(nextcloudIntegrationConnectionProvider.future);

        final workspace = container.read(workspaceConnectionStateProvider);
        final capabilities = container.read(
          workspaceCapabilitySnapshotProvider,
        );

        expect(workspace.requireValue.shellAccessReady, isTrue);
        expect(
          workspace.requireValue.status,
          IntegrationConnectionStatus.degraded,
        );
        expect(
          capabilities.requireValue.shellAccess.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
        expect(
          capabilities.requireValue.chat.readiness,
          WorkspaceCapabilityReadiness.degraded,
        );
        expect(
          capabilities.requireValue.files.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
      },
    );
  });
}
