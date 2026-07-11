import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/agents/domain/entities/weaver_permission_mode.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
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
  _FakeOidcClient({this.authorizeHandler});

  Future<OidcTokenBundle> Function()? authorizeHandler;

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(configuration) async {
    final handler = authorizeHandler;
    if (handler == null) {
      throw UnimplementedError();
    }

    return handler();
  }

  @override
  Future<void> endSession(configuration, {required String idTokenHint}) async {}

  @override
  Future<OidcTokenBundle> refresh(
    configuration, {
    required String refreshToken,
  }) async {
    throw UnimplementedError();
  }
}

class _MutableFilesRepository implements FilesRepository {
  _MutableFilesRepository({
    required FilesConnectionState initialConnectionState,
    required Map<String, DirectoryListing> listings,
    FilesConnectionState? connectedState,
  }) : _connectionState = initialConnectionState,
       _listings = listings,
       _connectedState =
           connectedState ??
           FilesConnectionState.connected(
             baseUrl: Uri.parse('https://files.home.internal'),
             accountLabel: 'alice',
           );

  FilesConnectionState _connectionState;
  final Map<String, DirectoryListing> _listings;
  final FilesConnectionState _connectedState;
  int connectCalls = 0;
  int disconnectCalls = 0;
  final List<String> listedPaths = <String>[];

  @override
  Future<FilesConnectionState> connect() async {
    connectCalls++;
    _connectionState = _connectedState;
    return _connectionState;
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
    _connectionState = FilesConnectionState.disconnected(
      baseUrl: _connectionState.baseUrl,
    );
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    listedPaths.add(path);
    final listing = _listings[path];
    if (listing == null) {
      throw StateError('Missing fake listing for $path');
    }

    return listing;
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    onProgress?.call(request.sizeInBytes, request.sizeInBytes);
  }

  @override
  Future<FilesConnectionState> restoreConnection() async => _connectionState;
}

class _MutableChatRepository implements ChatRepository {
  _MutableChatRepository({required this.conversations});

  final List<ChatConversation> conversations;
  bool isConnected = false;
  int connectCalls = 0;
  int signOutCalls = 0;
  int clearSessionCalls = 0;
  int loadCalls = 0;

  @override
  Future<void> clearSession() async {
    clearSessionCalls++;
    isConnected = false;
  }

  @override
  Future<void> connect() async {
    connectCalls++;
    isConnected = true;
  }

  @override
  Future<List<ChatConversation>> loadConversations() async {
    loadCalls++;
    if (!isConnected) {
      throw const ChatFailure.sessionRequired('Matrix sign-in required.');
    }

    return conversations;
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    throw UnimplementedError();
  }

  @override
  Future<void> markRoomRead(String roomId) async {}

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {}

  @override
  Future<void> signOut() async {
    signOutCalls++;
    isConnected = false;
  }
}

class _StaticWeaveApiClient implements WeaveApiClient {
  const _StaticWeaveApiClient(this.snapshot);

  final WorkspaceCapabilitySnapshot snapshot;

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

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    binding.platformDispatcher.localeTestValue = const Locale('en');
  });

  tearDown(binding.platformDispatcher.clearLocaleTestValue);

  group('Release golden paths', () {
    testWidgets('sign-in journey enters the product shell directly', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      final configurationRepository = _FakeServerConfigurationRepository(
        configuration: buildTestConfiguration(),
      );
      final oidcClient = _FakeOidcClient(
        authorizeHandler: () async => OidcTokenBundle(
          accessToken: 'fresh-access-token',
          refreshToken: 'fresh-refresh-token',
          idToken: 'fresh-id-token',
          expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
          tokenType: 'Bearer',
          scopes: const ['openid', 'profile', 'email'],
        ),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => configurationRepository,
            ),
            secureStoreProvider.overrideWithValue(secureStore),
            oidcClientProvider.overrideWithValue(oidcClient),
            chatRepositoryProvider.overrideWithValue(
              _MutableChatRepository(conversations: const <ChatConversation>[]),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
            ),
            filesRepositoryProvider.overrideWithValue(
              _MutableFilesRepository(
                initialConnectionState:
                    const FilesConnectionState.disconnected(),
                listings: const <String, DirectoryListing>{
                  '/': DirectoryListing(path: '/', entries: <FileEntry>[]),
                },
              ),
            ),
            weaveApiClientProvider.overrideWithValue(
              const _StaticWeaveApiClient(
                WorkspaceCapabilitySnapshot(
                  shellAccess: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.shellAccess,
                    readiness: WorkspaceCapabilityReadiness.ready,
                  ),
                  chat: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.chat,
                    readiness: WorkspaceCapabilityReadiness.blocked,
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
            ),
          ],
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Sign In'), findsAtLeastNWidgets(1));

      final signInButton = find.widgetWithText(FilledButton, 'Sign In');
      await tester.scrollUntilVisible(signInButton, 300);
      await tester.tap(signInButton);
      await tester.pumpAndSettle();

      final container = ProviderScope.containerOf(
        tester.element(find.byType(WeaveApp)),
      );
      expect(
        container.read(appBootstrapProvider).requireValue.phase,
        BootstrapPhase.ready,
      );
      expect(
        await secureStore.read(authSessionStorageKey),
        contains('fresh-access-token'),
      );

      await _continueFirstRunIfPresent(tester);

      expect(find.byType(NavigationBar), findsOneWidget);
    });

    testWidgets(
      'files-first journey connects Weave Files, browses folders, and keeps chat reachable',
      (tester) async {
        final secureStore = InMemorySecureStore({
          authSessionStorageKey: AuthSessionDto.fromSession(
            buildTestAuthSession(),
          ).encode(),
        });
        final filesRepository = _MutableFilesRepository(
          initialConnectionState: FilesConnectionState.disconnected(
            baseUrl: Uri.parse('https://files.home.internal'),
          ),
          listings: <String, DirectoryListing>{
            '/': const DirectoryListing(
              path: '/',
              entries: <FileEntry>[
                FileEntry(
                  id: 'projects',
                  name: 'Projects',
                  path: '/Projects',
                  isDirectory: true,
                ),
              ],
            ),
            '/Projects': const DirectoryListing(
              path: '/Projects',
              entries: <FileEntry>[
                FileEntry(
                  id: 'roadmap',
                  name: 'roadmap.md',
                  path: '/Projects/roadmap.md',
                  isDirectory: false,
                  sizeInBytes: 2048,
                ),
              ],
            ),
          },
        );
        final chatRepository = _MutableChatRepository(
          conversations: const <ChatConversation>[
            ChatConversation(
              id: '!weave:home.internal',
              title: 'Weave Core',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Golden path looks healthy.',
              unreadCount: 2,
              isInvite: false,
              isDirectMessage: false,
            ),
          ],
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  configuration: buildTestConfiguration(),
                ),
              ),
              secureStoreProvider.overrideWithValue(secureStore),
              oidcClientProvider.overrideWithValue(_FakeOidcClient()),
              chatRepositoryProvider.overrideWithValue(chatRepository),
              chatSecurityRepositoryProvider.overrideWithValue(
                FakeChatSecurityRepository(),
              ),
              userProfileProvider.overrideWith((ref) async => null),
              filesRepositoryProvider.overrideWithValue(filesRepository),
              weaveApiClientProvider.overrideWithValue(
                const _StaticWeaveApiClient(
                  WorkspaceCapabilitySnapshot(
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
              ),
            ],
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        await _continueFirstRunIfPresent(tester);

        expect(chatRepository.connectCalls, 1);
        expect(find.text('Weave Core'), findsWidgets);

        await tester.tap(find.byIcon(Icons.folder_outlined));
        await tester.pumpAndSettle();

        expect(find.text('Connect Files'), findsWidgets);

        await tester.tap(find.text('Connect Files').first);
        await tester.pumpAndSettle();

        expect(filesRepository.connectCalls, 1);
        expect(find.text('Projects'), findsWidgets);

        await tester.drag(
          find.byType(CustomScrollView).last,
          const Offset(0, -120),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('Projects').last);
        await tester.pumpAndSettle();

        expect(
          filesRepository.listedPaths,
          containsAllInOrder(<String>['/', '/Projects']),
        );
        expect(find.widgetWithText(ListTile, 'roadmap.md'), findsOneWidget);

        await tester.tap(find.text('Chat'));
        await tester.pumpAndSettle();

        expect(find.text('Weave Core'), findsWidgets);
        expect(find.text('Golden path looks healthy.'), findsOneWidget);
      },
    );

    testWidgets('settings sign-out returns the app to sign-in', (tester) async {
      final secureStore = InMemorySecureStore({
        authSessionStorageKey: AuthSessionDto.fromSession(
          buildTestAuthSession(),
        ).encode(),
      });
      final filesRepository = _MutableFilesRepository(
        initialConnectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const <String, DirectoryListing>{
          '/': DirectoryListing(path: '/', entries: <FileEntry>[]),
        },
      );
      final chatRepository = _MutableChatRepository(conversations: const []);
      chatRepository.isConnected = true;

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: buildTestConfiguration(),
              ),
            ),
            secureStoreProvider.overrideWithValue(secureStore),
            oidcClientProvider.overrideWithValue(_FakeOidcClient()),
            chatRepositoryProvider.overrideWithValue(chatRepository),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
            ),
            userProfileProvider.overrideWith((ref) async => null),
            filesRepositoryProvider.overrideWithValue(filesRepository),
            weaveApiClientProvider.overrideWithValue(
              const _StaticWeaveApiClient(
                WorkspaceCapabilitySnapshot(
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
            ),
          ],
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      await _continueFirstRunIfPresent(tester);

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      final container = ProviderScope.containerOf(
        tester.element(find.byType(WeaveApp)),
      );
      await container.read(authFlowControllerProvider.notifier).signOut();
      await tester.pumpAndSettle();

      expect(find.text('Sign In'), findsAtLeastNWidgets(1));
      expect(await secureStore.read(authSessionStorageKey), isNull);
      expect(chatRepository.signOutCalls, 1);
      expect(filesRepository.disconnectCalls, 1);
    });

    testWidgets(
      'changed Nextcloud server marks files as needing recovery without dropping shell access',
      (tester) async {
        final secureStore = InMemorySecureStore({
          authSessionStorageKey: AuthSessionDto.fromSession(
            buildTestAuthSession(),
          ).encode(),
        });
        final configurationRepository = _FakeServerConfigurationRepository(
          configuration: buildTestConfiguration(),
        );
        final filesRepository = _MutableFilesRepository(
          initialConnectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
          listings: const <String, DirectoryListing>{
            '/': DirectoryListing(path: '/', entries: <FileEntry>[]),
          },
        );
        final chatRepository = _MutableChatRepository(conversations: const []);
        chatRepository.isConnected = true;

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => configurationRepository,
              ),
              secureStoreProvider.overrideWithValue(secureStore),
              oidcClientProvider.overrideWithValue(_FakeOidcClient()),
              chatRepositoryProvider.overrideWithValue(chatRepository),
              chatSecurityRepositoryProvider.overrideWithValue(
                FakeChatSecurityRepository(),
              ),
              userProfileProvider.overrideWith((ref) async => null),
              filesRepositoryProvider.overrideWithValue(filesRepository),
              weaveApiClientProvider.overrideWithValue(
                const _StaticWeaveApiClient(
                  WorkspaceCapabilitySnapshot(
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
              ),
            ],
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        await _continueFirstRunIfPresent(tester);

        await tester.tap(find.byIcon(Icons.settings_outlined));
        await tester.pumpAndSettle();

        final container = ProviderScope.containerOf(
          tester.element(find.byType(WeaveApp)),
        );
        final updatedConfiguration = buildTestConfiguration(
          nextcloudBaseUrl: 'https://files-2.home.internal',
        );
        await configurationRepository.saveConfiguration(updatedConfiguration);
        await container
            .read(authFlowControllerProvider.notifier)
            .handleConfigurationSaved(
              ServerConfigurationSaveResult(
                configuration: updatedConfiguration,
                authConfigurationChanged: false,
                matrixHomeserverChanged: false,
                nextcloudBaseUrlChanged: true,
                backendApiBaseUrlChanged: false,
              ),
            );
        await tester.pumpAndSettle();

        final workspace = container
            .read(workspaceConnectionStateProvider)
            .requireValue;
        final capabilities = container
            .read(workspaceCapabilitySnapshotProvider)
            .requireValue;

        expect(workspace.shellAccessReady, isTrue);
        expect(
          workspace.files.status,
          IntegrationConnectionStatus.disconnected,
        );
        expect(
          workspace.files.lastInvalidation?.reason,
          IntegrationInvalidationReason.filesConfigurationChanged,
        );
        expect(
          capabilities.files.readiness,
          WorkspaceCapabilityReadiness.blocked,
        );
        expect(filesRepository.disconnectCalls, 1);
      },
    );
  });
}

Future<void> _continueFirstRunIfPresent(WidgetTester tester) async {
  final continueButton = find.text('Continue to chat');
  if (continueButton.evaluate().isEmpty) {
    return;
  }

  expect(find.text('Your Weave workspace is ready'), findsOneWidget);
  await tester.ensureVisible(continueButton);
  await tester.pumpAndSettle();
  await tester.tap(continueButton);
  await tester.pumpAndSettle();
}
