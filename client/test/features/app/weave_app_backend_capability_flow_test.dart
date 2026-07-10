import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/agents/domain/entities/weaver_permission_mode.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/first_run_status_fixture.dart';
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
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {}

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
  Future<OrganizationManifestSnapshot> fetchOrganizationManifest({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    return OrganizationManifestSnapshot(
      manifestVersion: 'org-manifest-v1',
      organizationId: 'test',
      displayName: 'Test',
      organizationAuthUrl: Uri.parse(
        'https://auth.example.invalid/realms/weave',
      ),
      supportSafe: true,
      providerConfigurationExposed: false,
      diagnosticsExposed: false,
      whitelistingOwner: 'organization-admin-console',
      clientResponsibilities: const [],
      adminConsoleResponsibilities: const [],
      memberCapabilityStates: const {},
      capabilities: snapshot,
    );
  }

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

  @override
  Future<WorkspaceHomeSnapshot> fetchWorkspaceHome({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    return const WorkspaceHomeSnapshot(
      version: 1,
      readiness: WorkspaceCapabilityReadiness.ready,
      summary: 'Weave Home is ready for tests.',
      sections: [],
      actions: [],
      supportSafe: true,
    );
  }

  @override
  Future<MatrixE2eeDiagnostic> fetchMatrixE2eeDiagnostic({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    return const MatrixE2eeDiagnostic(
      e2eeEnabled: false,
      status: 'not_validated',
      serverReadableMessageContent: false,
      messageContentPolicy: 'encrypted_message_bodies_are_client_readable_only',
      agentParticipation:
          'blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented',
      connectorWritePolicy:
          'fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented',
    );
  }

  @override
  Future<ProviderStackSnapshot> fetchProviderStackStatus({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    return const ProviderStackSnapshot(
      releaseStatus: 'test',
      backendOwnedFacades: true,
      flutterDirectProviderCallsAllowed: false,
      supportSafe: true,
      providers: [],
    );
  }

  @override
  Future<DevopsProviderSummarySnapshot> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  }) async {
    return DevopsProviderSummarySnapshot(
      workspaceId: workspaceId,
      channelId: channelId,
      releaseStatus: 'test',
      readOnly: true,
      paidFeaturesRequired: false,
      supportSafe: true,
      providerReadiness: const [],
    );
  }

  @override
  Future<OfficeCapabilitiesSnapshot> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    return const OfficeCapabilitiesSnapshot(
      releaseStatus: 'test',
      enabled: false,
      configured: false,
      supportSafe: true,
      launchMode: 'disabled',
      defaultProvider: 'none',
      providerReadiness: [],
      supportedFileTypes: [],
      candidates: [],
      capabilities: OfficeCapabilityFlagsSnapshot(
        view: false,
        edit: false,
        comment: false,
        review: false,
        formFill: false,
      ),
      permissions: OfficePermissionModelSnapshot(
        canView: false,
        canEdit: false,
        canComment: false,
        canReview: false,
        canFillForms: false,
        reason: 'not-used',
      ),
      lockSessionReadiness: OfficeLockSessionReadinessSnapshot(
        documentLocks: 'disabled',
        sessionTokens: 'disabled',
        callbackVerification: 'disabled',
        supportSafe: true,
      ),
    );
  }

  @override
  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  }) async {
    throw UnimplementedError('Office launch is not used by this test fake.');
  }

  @override
  Future<WeaverPermissionMode> fetchWeaverPermissionMode({
    required Uri baseUrl,
    required String accessToken,
  }) async => WeaverPermissionMode.ask;

  @override
  Future<WeaverPermissionModeUpdate> updateWeaverPermissionMode({
    required Uri baseUrl,
    required String accessToken,
    required WeaverPermissionMode mode,
  }) async => WeaverPermissionModeUpdate(
    accepted: true,
    mode: mode,
    dangerous: mode.isDangerous,
    policyReason: 'test_fixture',
    runtimeProfileHash: 'test-profile',
  );
}

const _memberProfile = UserProfile(
  userId: 'member-1',
  username: 'member',
  email: 'member@example.test',
  emailVerified: true,
  displayName: 'Workspace Member',
  locale: 'en',
  timezone: 'Europe/Berlin',
  roles: ['member'],
  groups: ['workspace-default'],
);

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
            userProfileProvider.overrideWith((ref) async => _memberProfile),
            firstRunStatusProvider.overrideWith(
              (ref) async =>
                  FirstRunLoadResult.authenticated(buildTestFirstRunStatus()),
            ),
            weaveApiClientProvider.overrideWithValue(weaveApiClient),
          ],
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      await _continueFirstRunIfPresent(tester);

      expect(find.byType(NavigationBar), findsOneWidget);

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(weaveApiClient.callCount, 1);
      expect(
        weaveApiClient.lastBaseUrl,
        Uri.parse('https://api.home.internal/api'),
      );
      expect(weaveApiClient.lastAccessToken, 'backend-boundary-token');

      expect(find.text('Appearance'), findsOneWidget);
      expect(find.text('Language'), findsOneWidget);
      expect(find.text('Profile'), findsOneWidget);
      expect(find.text('Weave profile'), findsNothing);
      expect(find.text('Save profile'), findsNothing);
      expect(find.text('Shell modules'), findsOneWidget);
      expect(find.text('Workspace Readiness'), findsNothing);
      expect(
        find.text(
          'Shell access is ready, but one or more services still need attention.',
        ),
        findsNothing,
      );
      expect(find.text('Workspace setup is admin-only'), findsNothing);
      expect(find.text('Server Configuration'), findsNothing);
      expect(find.text('Provider stack readiness'), findsNothing);
      expect(find.text('AI agent capability governance'), findsNothing);
      expect(find.text('Readiness: Ready', findRichText: true), findsNothing);
      expect(find.text('Readiness: Blocked', findRichText: true), findsNothing);
      expect(
        find.text('Connection: Degraded', findRichText: true),
        findsNothing,
      );
      expect(
        find.text('Connection: Connected', findRichText: true),
        findsNothing,
      );
    },
  );
}

Future<void> _continueFirstRunIfPresent(WidgetTester tester) async {
  final continueButton = find.text('Continue to chat');
  if (continueButton.evaluate().isEmpty) {
    return;
  }

  await tester.ensureVisible(continueButton);
  await tester.pumpAndSettle();
  await tester.tap(continueButton);
  await tester.pumpAndSettle();
}
