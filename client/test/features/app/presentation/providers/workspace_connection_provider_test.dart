import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

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
  int restoreConnectionCalls = 0;

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    return DirectoryListing(path: path, entries: const []);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {}

  @override
  Future<FilesConnectionState> restoreConnection() async {
    restoreConnectionCalls++;
    return connectionState;
  }
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

    test('propagates backend capability errors instead of local fallback', () {
      final error = StateError('Backend capabilities failed.');
      final container = ProviderContainer.test(
        overrides: [
          appBootstrapProvider.overrideWith(
            () => _FakeAppBootstrap(const BootstrapState.ready()),
          ),
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) => throw error,
          ),
        ],
      );
      addTearDown(container.dispose);

      final state = container.read(workspaceConnectionStateProvider);

      expect(state.hasError, isTrue);
      expect(state.error, same(error));
    });

    test(
      'keeps shell access ready when backend facade services are ready',
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
                  baseUrl: Uri.parse('https://files.home.internal'),
                  accountLabel: 'alice',
                ),
              ),
            ),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => const WorkspaceCapabilitySnapshot(
                shellAccess: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.shellAccess,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                chat: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.chat,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                files: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.files,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                calendar: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.calendar,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
                boards: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.boards,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(appBootstrapProvider.future);
        await container.read(
          weaveApiWorkspaceCapabilitySnapshotProvider.future,
        );
        final workspace = container.read(workspaceConnectionStateProvider);
        final capabilities = container.read(
          workspaceCapabilitySnapshotProvider,
        );

        expect(workspace.requireValue.shellAccessReady, isTrue);
        expect(
          workspace.requireValue.status,
          IntegrationConnectionStatus.connected,
        );
        expect(
          capabilities.requireValue.shellAccess.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
        expect(
          capabilities.requireValue.chat.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
        expect(
          capabilities.requireValue.files.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
      },
    );

    test(
      'prefers backend capability readiness without restoring provider sessions',
      () async {
        final filesRepository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
        );
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
            filesRepositoryProvider.overrideWithValue(filesRepository),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => const WorkspaceCapabilitySnapshot(
                shellAccess: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.shellAccess,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                chat: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.chat,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                files: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.files,
                  readiness: WorkspaceCapabilityReadiness.blocked,
                ),
                calendar: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.calendar,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
                boards: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.boards,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(appBootstrapProvider.future);
        await container.read(
          weaveApiWorkspaceCapabilitySnapshotProvider.future,
        );

        final capabilities = container.read(
          workspaceCapabilitySnapshotProvider,
        );

        expect(
          capabilities.requireValue.chat.readiness,
          WorkspaceCapabilityReadiness.ready,
        );
        expect(
          capabilities.requireValue.files.readiness,
          WorkspaceCapabilityReadiness.blocked,
        );
        expect(filesRepository.restoreConnectionCalls, 0);
      },
    );

    test(
      'workspace connection uses backend readiness without restoring provider sessions',
      () async {
        final filesRepository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
        );
        final container = ProviderContainer.test(
          overrides: [
            appBootstrapProvider.overrideWith(
              () => _FakeAppBootstrap(const BootstrapState.ready()),
            ),
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(),
            ),
            filesRepositoryProvider.overrideWithValue(filesRepository),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => const WorkspaceCapabilitySnapshot(
                shellAccess: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.shellAccess,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                chat: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.chat,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                files: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.files,
                  readiness: WorkspaceCapabilityReadiness.degraded,
                ),
                calendar: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.calendar,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
                boards: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.boards,
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        await container.read(appBootstrapProvider.future);
        await container.read(
          weaveApiWorkspaceCapabilitySnapshotProvider.future,
        );

        final workspace = container.read(workspaceConnectionStateProvider);

        expect(
          workspace.requireValue.chat.status,
          IntegrationConnectionStatus.connected,
        );
        expect(
          workspace.requireValue.files.status,
          IntegrationConnectionStatus.connected,
        );
        expect(filesRepository.restoreConnectionCalls, 0);
      },
    );
  });
}
