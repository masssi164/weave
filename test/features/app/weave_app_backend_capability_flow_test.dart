import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.configuration});

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeOidcClient implements OidcClient {
  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(configuration) {
    throw UnimplementedError();
  }

  @override
  Future<void> endSession(configuration, {required String idTokenHint}) async {}

  @override
  Future<OidcTokenBundle> refresh(
    configuration, {
    required String refreshToken,
  }) {
    throw UnimplementedError();
  }
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

class _RecordingWeaveApiClient implements WeaveApiClient {
  _RecordingWeaveApiClient({required this.snapshot});

  final WorkspaceCapabilitySnapshot snapshot;
  Uri? lastBaseUrl;
  String? lastAccessToken;
  int callCount = 0;

  @override
  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    callCount++;
    lastBaseUrl = baseUrl;
    lastAccessToken = accessToken;
    return snapshot;
  }
}

void main() {
  testWidgets(
    'consumes backend capabilities and reflects merged readiness in settings',
    (tester) async {
      final weaveApiClient = _RecordingWeaveApiClient(
        snapshot: const WorkspaceCapabilitySnapshot(
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
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: buildTestConfiguration(),
              ),
            ),
            secureStoreProvider.overrideWithValue(
              InMemorySecureStore({
                authSessionStorageKey: AuthSessionDto.fromSession(
                  buildTestAuthSession(accessToken: 'backend-boundary-token'),
                ).encode(),
              }),
            ),
            oidcClientProvider.overrideWithValue(_FakeOidcClient()),
            chatRepositoryProvider.overrideWithValue(FakeChatRepository()),
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
            weaveApiClientProvider.overrideWithValue(weaveApiClient),
          ],
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(weaveApiClient.callCount, 1);
      expect(
        weaveApiClient.lastBaseUrl,
        Uri.parse('https://home.internal/api'),
      );
      expect(weaveApiClient.lastAccessToken, 'backend-boundary-token');

      expect(find.text('Workspace Readiness'), findsOneWidget);
      expect(
        find.text(
          'Shell access is ready, but one or more services still need attention.',
        ),
        findsOneWidget,
      );
      expect(find.text('Server Configuration'), findsOneWidget);
      expect(
        find.text('Readiness: Ready', findRichText: true),
        findsNWidgets(2),
      );
      expect(
        find.text('Readiness: Blocked', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text('Connection: Degraded', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text('Connection: Connected', findRichText: true),
        findsNWidgets(2),
      );
    },
  );
}
