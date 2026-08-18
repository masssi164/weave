import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../helpers/in_memory_stores.dart';
import '../helpers/fake_identity_session_port.dart';

void main() {
  group('current release auth/files golden paths', () {
    testWidgets(
      'organization access, sign-in, ready shell, files browsing, chat send, and sign-out/re-auth',
      (tester) async {
        final authRepository = _ScenarioAuthSessionRepository();
        final serverConfigurationRepository =
            _MemoryServerConfigurationRepository();
        final filesRepository = _ScenarioFilesRepository(
          serverConfigurationRepository,
        );
        final discoveryClient = AppStartDiscoveryClient(
          httpClient: MockClient((request) async {
            expect(request.url.path, '/api/platform/config');
            return http.Response(
              jsonEncode({
                'schemaVersion': 1,
                'organizationOrigin': 'https://weave.test',
                'controlPlaneBaseUrl': 'https://api.weave.test/api',
                'oidc': {
                  'issuer': 'https://auth.weave.test/realms/weave',
                  'clientId': 'weave-app',
                },
                'protocols': {
                  'matrixClientServerBaseUrl': 'https://api.weave.test',
                  'filesWebDavBaseUrl': 'https://api.weave.test/dav/files',
                  'calendarCalDavBaseUrl': 'https://api.weave.test/caldav',
                },
                'releasePosture': 'dogfood',
                'domains': [
                  for (final domain in [
                    'identity',
                    'chat',
                    'files',
                    'calendar',
                    'boards',
                    'health',
                  ])
                    {
                      'domain': domain,
                      'state': 'available',
                      'capabilities': <String>[],
                    },
                ],
                'recoveryActions': <Object>[],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }),
        );

        tester.binding.platformDispatcher.localeTestValue = const Locale('en');
        addTearDown(tester.binding.platformDispatcher.clearLocaleTestValue);
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = const Size(1440, 2400);
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final container = ProviderContainer.test(
          overrides: [
            authSessionRepositoryProvider.overrideWithValue(authRepository),
            identitySessionPortProvider.overrideWithValue(
              FakeIdentitySessionPort(),
            ),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
            secureStoreProvider.overrideWithValue(InMemorySecureStore()),
            serverConfigurationRepositoryProvider.overrideWithValue(
              serverConfigurationRepository,
            ),
            discoverOrganizationAccessProvider.overrideWithValue(
              DiscoverOrganizationAccess(
                repository: serverConfigurationRepository,
                discoveryClient: discoveryClient,
              ),
            ),
            filesRepositoryProvider.overrideWithValue(filesRepository),
            chatRepositoryProvider.overrideWithValue(_ScenarioChatRepository()),
            chatSecurityRepositoryProvider.overrideWithValue(
              _SignedOutChatSecurityRepository(),
            ),
            userProfileProvider.overrideWith((ref) async => _ownerProfile),
            workspaceConnectionStateProvider.overrideWithValue(
              _workspaceConnectionState(),
            ),
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              _workspaceCapabilitySnapshot(),
            ),
            weaveBackendConnectionStateProvider.overrideWithValue(
              WeaveBackendConnectionState.connected,
            ),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        expect(
          container.read(appBootstrapProvider).requireValue.phase,
          BootstrapPhase.needsSetup,
        );
        expect(find.text('Organization access'), findsOneWidget);

        await tester.enterText(
          _textFieldWithLabel('Server URI, invitation link, or QR payload'),
          'https://weave.test',
        );
        await tester.tap(find.text('Continue to organization'));
        await tester.pumpAndSettle();

        expect(find.text('Workspace ready for sign-in'), findsOneWidget);
        expect(find.text('https://matrix.weave.test'), findsNothing);
        expect(find.text('https://files.weave.test'), findsNothing);
        expect(find.text('Sign In'), findsWidgets);

        await tester.tap(find.widgetWithText(AccessibleButton, 'Sign In'));
        await tester.pumpAndSettle();

        expect(
          container.read(appBootstrapProvider).requireValue.phase,
          BootstrapPhase.ready,
        );
        expect(find.text('Weave Home'), findsWidgets);

        await tester.tap(_navigationDestination('Chat'));
        await tester.pumpAndSettle();

        final releaseRoomTile = find.widgetWithText(ListTile, 'Release Room');
        expect(releaseRoomTile, findsWidgets);

        await tester.tap(releaseRoomTile.first);
        await tester.pumpAndSettle();

        expect(find.text('Golden path ready'), findsOneWidget);
        await tester.enterText(find.byType(TextField), 'Looks shippable');
        await tester.tap(find.text('Send'));
        await tester.pumpAndSettle();

        expect(find.text('Looks shippable'), findsOneWidget);

        await tester.tap(_navigationDestination('Files'));
        await tester.pumpAndSettle();

        expect(find.text('Connect Files'), findsWidgets);
        await tester.tap(find.text('Connect Files').first);
        await tester.pumpAndSettle();

        expect(find.widgetWithText(ListTile, 'Documents'), findsOneWidget);
        expect(find.widgetWithText(ListTile, 'Readme.md'), findsOneWidget);

        await tester.tap(find.widgetWithText(ListTile, 'Documents'));
        await tester.pumpAndSettle();

        expect(find.text('/Documents'), findsOneWidget);
        expect(find.widgetWithText(ListTile, 'Plans'), findsOneWidget);
        expect(find.widgetWithText(ListTile, 'spec.pdf'), findsOneWidget);

        await tester.tap(find.text('New folder'));
        await tester.pumpAndSettle();
        await tester.enterText(_textFieldWithLabel('Folder name'), 'Archive');
        await tester.pumpAndSettle();
        await tester.tap(find.text('Create'));
        await tester.pumpAndSettle();

        expect(find.widgetWithText(ListTile, 'Archive'), findsOneWidget);
        expect(find.text('Created folder Archive.'), findsOneWidget);
        expect(filesRepository.createdFolders, contains('/Documents/Archive'));

        await tester.tap(find.byTooltip('Delete spec.pdf'));
        await tester.pumpAndSettle();
        expect(find.text('Delete spec.pdf?'), findsOneWidget);
        await tester.tap(find.text('Delete').last);
        await tester.pumpAndSettle();

        expect(find.widgetWithText(ListTile, 'spec.pdf'), findsNothing);
        expect(find.text('Deleted spec.pdf.'), findsOneWidget);
        expect(filesRepository.deletedEntries, contains('spec'));

        await tester.tap(find.text('Up'));
        await tester.pumpAndSettle();

        await tester.tap(_navigationDestination('Settings'));
        await tester.pumpAndSettle();

        expect(find.text('Sign Out'), findsWidgets);
        await container.read(authFlowControllerProvider.notifier).signOut();
        await tester.pumpAndSettle();

        expect(
          container.read(appBootstrapProvider).requireValue.phase,
          BootstrapPhase.needsSignIn,
        );
        expect(authRepository.signOutCalls, 1);
        expect(filesRepository.disconnectCalls, 1);

        await tester.tap(find.widgetWithText(AccessibleButton, 'Sign In'));
        await tester.pumpAndSettle();
        container.read(appRouterProvider).go(AppRoutes.workspaceHealth);
        await tester.pumpAndSettle();
        expect(_textFieldWithLabel('OIDC Issuer URL'), findsNothing);
        expect(_textFieldWithLabel('Nextcloud Base URL'), findsNothing);

        await tester.tap(_navigationDestination('Files'));
        await tester.pumpAndSettle();

        expect(find.text('Connect Files'), findsWidgets);
        expect(
          filesRepository.lastConfiguredBaseUrl.toString(),
          'https://api.weave.test/dav/files',
        );

        await tester.tap(find.text('Connect Files').first);
        await tester.pumpAndSettle();

        expect(find.widgetWithText(ListTile, 'Documents'), findsOneWidget);
        expect(filesRepository.connectCalls, 2);
      },
    );
  });
}

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

Finder _navigationDestination(String label) {
  return find.widgetWithText(NavigationDestination, label);
}

const _ownerProfile = UserProfile(
  userId: 'release-owner',
  username: 'alex',
  email: 'alex@example.test',
  emailVerified: true,
  displayName: 'Alex Doe',
  locale: 'en',
  timezone: 'Europe/Berlin',
  roles: <String>['owner'],
  groups: <String>['workspace-default'],
);

AsyncValue<WorkspaceConnectionState> _workspaceConnectionState() {
  return const AsyncData(
    WorkspaceConnectionState(
      appAuth: IntegrationConnectionState(
        integration: WorkspaceIntegration.appAuth,
        status: IntegrationConnectionStatus.connected,
      ),
      chat: IntegrationConnectionState(
        integration: WorkspaceIntegration.chat,
        status: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      ),
      files: IntegrationConnectionState(
        integration: WorkspaceIntegration.files,
        status: IntegrationConnectionStatus.connected,
      ),
    ),
  );
}

AsyncValue<WorkspaceCapabilitySnapshot> _workspaceCapabilitySnapshot() {
  return const AsyncData(
    WorkspaceCapabilitySnapshot(
      shellAccess: WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      chat: WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.degraded,
        connectionStatus: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      ),
      files: WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
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
}

class _MemoryServerConfigurationRepository
    implements ServerConfigurationRepository {
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

class _ScenarioAuthSessionRepository implements AuthSessionRepository {
  AuthSession? session;
  int signOutCalls = 0;

  @override
  Future<void> clearLocalSession() async {
    session = null;
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    return session == null
        ? const AuthState.signedOut()
        : AuthState.authenticated(session!);
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    return session == null
        ? const AuthState.signedOut()
        : AuthState.authenticated(session!);
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    session = AuthSession(
      issuer: configuration.issuer,
      clientId: configuration.clientId,
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      idToken: 'id-token',
      expiresAt: DateTime.utc(2030),
      tokenType: 'Bearer',
      scopes: const ['openid', 'profile', 'email', 'offline_access'],
    );
    return AuthState.authenticated(session!);
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    signOutCalls++;
    session = null;
  }
}

class _ScenarioFilesRepository
    implements FilesRepository, FilesEntryMutationRepository {
  _ScenarioFilesRepository(this._serverConfigurationRepository);

  final _MemoryServerConfigurationRepository _serverConfigurationRepository;
  final Map<String, List<FileEntry>> _entriesByPath = <String, List<FileEntry>>{
    '/': List<FileEntry>.of(_rootEntries),
    '/Documents': List<FileEntry>.of(_documentsEntries),
  };
  bool _connected = false;
  int connectCalls = 0;
  int disconnectCalls = 0;
  final List<String> createdFolders = <String>[];
  final List<String> deletedEntries = <String>[];

  Uri get lastConfiguredBaseUrl =>
      _serverConfigurationRepository
          .configuration
          ?.serviceEndpoints
          .nextcloudBaseUrl ??
      Uri.parse('https://files.weave.test');

  @override
  Future<FilesConnectionState> connect() async {
    connectCalls++;
    _connected = true;
    return _connectionState();
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
    _connected = false;
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    if (!_connected) {
      throw const FilesFailure.sessionRequired(
        'Connect Files to browse your files.',
      );
    }

    return DirectoryListing(
      path: path,
      entries: List<FileEntry>.unmodifiable(
        _entriesByPath[path] ?? const <FileEntry>[],
      ),
    );
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
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) async {
    final normalizedParent = parentPath == '/' ? '' : parentPath;
    final path = '$normalizedParent/$name';
    final entry = FileEntry(
      id: path,
      name: name,
      path: path,
      isDirectory: true,
    );
    createdFolders.add(path);
    _entriesByPath.putIfAbsent(parentPath, () => <FileEntry>[]).add(entry);
    _entriesByPath[path] = <FileEntry>[];
    return entry;
  }

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    deletedEntries.add(entry.id);
    final parentPath = _parentPath(entry.path);
    _entriesByPath[parentPath]?.removeWhere(
      (candidate) => candidate.id == entry.id,
    );
    if (entry.isDirectory) {
      _entriesByPath.remove(entry.path);
    }
  }

  @override
  Future<FilesConnectionState> restoreConnection() async => _connectionState();

  String _parentPath(String path) {
    final lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) {
      return '/';
    }
    return path.substring(0, lastSlash);
  }

  FilesConnectionState _connectionState() {
    return _connected
        ? FilesConnectionState.connected(
            baseUrl: lastConfiguredBaseUrl,
            accountLabel: 'Alex Doe',
          )
        : FilesConnectionState.disconnected(baseUrl: lastConfiguredBaseUrl);
  }

  static const _rootEntries = <FileEntry>[
    FileEntry(
      id: 'documents',
      name: 'Documents',
      path: '/Documents',
      isDirectory: true,
    ),
    FileEntry(
      id: 'readme',
      name: 'Readme.md',
      path: '/Readme.md',
      isDirectory: false,
      sizeInBytes: 1200,
    ),
  ];

  static const _documentsEntries = <FileEntry>[
    FileEntry(
      id: 'plans',
      name: 'Plans',
      path: '/Documents/Plans',
      isDirectory: true,
    ),
    FileEntry(
      id: 'spec',
      name: 'spec.pdf',
      path: '/Documents/spec.pdf',
      isDirectory: false,
      sizeInBytes: 2048,
    ),
  ];
}

class _ScenarioChatRepository implements ChatRepository {
  final List<ChatMessage> _messages = <ChatMessage>[
    ChatMessage(
      id: r'$seed',
      senderId: '@alex:weave.test',
      senderDisplayName: 'Alex',
      sentAt: DateTime.utc(2026, 4, 20, 18),
      isMine: false,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: ChatMessageContentType.text,
      text: 'Golden path ready',
    ),
  ];

  @override
  Future<void> clearSession() async {}

  @override
  Future<void> connect() async {}

  @override
  Future<ChatConversation> createConversation({required String title}) {
    throw UnimplementedError();
  }

  @override
  Future<List<ChatConversation>> loadConversations() async =>
      const <ChatConversation>[
        ChatConversation(
          id: '!release:weave.test',
          title: 'Release Room',
          previewType: ChatConversationPreviewType.text,
          previewText: 'Golden path ready',
          unreadCount: 1,
          isInvite: false,
          isDirectMessage: false,
        ),
      ];

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async =>
      ChatRoomTimeline(
        roomId: roomId,
        roomTitle: 'Release Room',
        isInvite: false,
        canSendMessages: true,
        messages: List<ChatMessage>.unmodifiable(_messages),
      );

  @override
  Future<void> markRoomRead(String roomId) async {}

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    _messages.add(
      ChatMessage(
        id: 'msg-${_messages.length + 1}',
        senderId: '@me:weave.test',
        senderDisplayName: 'Me',
        sentAt: DateTime.utc(2026, 4, 20, 18, _messages.length),
        isMine: true,
        deliveryState: ChatMessageDeliveryState.sent,
        contentType: ChatMessageContentType.text,
        text: message,
      ),
    );
  }

  @override
  Future<void> signOut() async {}
}

class _SignedOutChatSecurityRepository implements ChatSecurityRepository {
  final _controller = StreamController<ChatVerificationSession>.broadcast();

  @override
  Future<void> acceptVerification() async {}

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async => 'unused';

  @override
  Future<void> cancelVerification() async {}

  @override
  Future<void> confirmSas({required bool matches}) async {}

  @override
  Future<void> dismissVerificationResult() async {}

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    return const ChatSecurityState(
      isMatrixSignedIn: false,
      bootstrapState: ChatSecurityBootstrapState.signedOut,
      accountVerificationState: ChatAccountVerificationState.unavailable,
      deviceVerificationState: ChatDeviceVerificationState.unavailable,
      keyBackupState: ChatKeyBackupState.unavailable,
      roomEncryptionReadiness: ChatRoomEncryptionReadiness.unavailable,
      secretStorageReady: false,
      crossSigningReady: false,
      hasEncryptedConversations: false,
      verificationSession: ChatVerificationSession.none(),
    );
  }

  @override
  Future<void> restoreSecurity({
    required String recoveryKeyOrPassphrase,
  }) async {}

  @override
  Future<void> startSasVerification() async {}

  @override
  Future<void> startVerification() async {}

  @override
  Future<void> unlockVerification({
    required String recoveryKeyOrPassphrase,
  }) async {}

  @override
  Stream<ChatVerificationSession> watchVerificationUpdates() =>
      _controller.stream;
}
