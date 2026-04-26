import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
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
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../helpers/in_memory_stores.dart';

void main() {
  group('Release 1 auth/files golden paths', () {
    testWidgets(
      'setup, sign-in, ready shell, files browsing, chat room open plus send, sign-out/re-auth, and changed-server recovery',
      (tester) async {
        final authRepository = _ScenarioAuthSessionRepository();
        final serverConfigurationRepository =
            _MemoryServerConfigurationRepository();
        final filesRepository = _ScenarioFilesRepository(
          serverConfigurationRepository,
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
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
            serverConfigurationRepositoryProvider.overrideWithValue(
              serverConfigurationRepository,
            ),
            filesRepositoryProvider.overrideWithValue(filesRepository),
            chatRepositoryProvider.overrideWithValue(_ScenarioChatRepository()),
            chatSecurityRepositoryProvider.overrideWithValue(
              _SignedOutChatSecurityRepository(),
            ),
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
        expect(find.text('Get Started'), findsOneWidget);

        await tester.tap(find.text('Get Started'));
        await tester.pumpAndSettle();

        await tester.enterText(
          _textFieldWithLabel('OIDC Issuer URL'),
          'https://auth.weave.local/realms/weave',
        );
        await tester.tap(find.text('Next'));
        await tester.pumpAndSettle();

        expect(find.text('Review Service Endpoints'), findsOneWidget);
        expect(find.text('https://matrix.weave.local'), findsWidgets);
        expect(find.text('https://files.weave.local'), findsWidgets);
        expect(find.text('https://api.weave.local/api'), findsWidgets);

        await tester.tap(find.text('Finish'));
        await tester.pumpAndSettle();

        expect(
          container.read(appBootstrapProvider).requireValue.phase,
          BootstrapPhase.needsSignIn,
        );
        expect(find.text('Sign In'), findsWidgets);

        await tester.tap(find.widgetWithText(AccessibleButton, 'Sign In'));
        await tester.pumpAndSettle();

        expect(
          container.read(appBootstrapProvider).requireValue.phase,
          BootstrapPhase.ready,
        );
        expect(find.text('Release Room'), findsOneWidget);

        await tester.tap(find.text('Release Room'));
        await tester.pumpAndSettle();

        expect(find.text('Golden path ready'), findsOneWidget);
        await tester.enterText(find.byType(TextField), 'Looks shippable');
        await tester.tap(find.text('Send'));
        await tester.pumpAndSettle();

        expect(find.text('Looks shippable'), findsOneWidget);

        await tester.tap(find.text('Files').last);
        await tester.pumpAndSettle();

        expect(find.text('Connect Nextcloud'), findsWidgets);
        await tester.tap(find.text('Connect Nextcloud').first);
        await tester.pumpAndSettle();

        expect(find.text('Documents'), findsOneWidget);
        expect(find.text('Readme.md'), findsOneWidget);

        await tester.tap(find.text('Documents'));
        await tester.pumpAndSettle();

        expect(find.text('/Documents'), findsOneWidget);
        expect(find.text('Plans'), findsOneWidget);
        expect(find.text('spec.pdf'), findsOneWidget);

        await tester.tap(find.text('Up'));
        await tester.pumpAndSettle();

        await tester.tap(find.text('Settings').last);
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

        await tester.tap(find.text('Settings').last);
        await tester.pumpAndSettle();
        final settingsScrollViewAfterReauth = find.byType(Scrollable).last;
        await tester.scrollUntilVisible(
          _textFieldWithLabel('Nextcloud Base URL'),
          -300,
          scrollable: settingsScrollViewAfterReauth,
        );
        await tester.enterText(
          _textFieldWithLabel('Nextcloud Base URL'),
          'https://files-alt.weave.local',
        );
        await tester.tap(find.widgetWithText(AccessibleButton, 'Save Changes'));
        await tester.pumpAndSettle();

        await tester.tap(find.text('Files').last);
        await tester.pumpAndSettle();

        expect(find.text('https://files-alt.weave.local'), findsOneWidget);
        expect(find.text('Connect Nextcloud'), findsWidgets);
        expect(
          filesRepository.lastConfiguredBaseUrl.toString(),
          'https://files-alt.weave.local',
        );

        await tester.tap(find.text('Connect Nextcloud').first);
        await tester.pumpAndSettle();

        expect(find.text('Documents'), findsOneWidget);
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

AsyncValue<WorkspaceConnectionState> _workspaceConnectionState() {
  return const AsyncData(
    WorkspaceConnectionState(
      appAuth: IntegrationConnectionState(
        integration: WorkspaceIntegration.appAuth,
        status: IntegrationConnectionStatus.connected,
      ),
      matrix: IntegrationConnectionState(
        integration: WorkspaceIntegration.matrix,
        status: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      ),
      nextcloud: IntegrationConnectionState(
        integration: WorkspaceIntegration.nextcloud,
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

class _ScenarioFilesRepository implements FilesRepository {
  _ScenarioFilesRepository(this._serverConfigurationRepository);

  final _MemoryServerConfigurationRepository _serverConfigurationRepository;
  bool _connected = false;
  int connectCalls = 0;
  int disconnectCalls = 0;

  Uri get lastConfiguredBaseUrl =>
      _serverConfigurationRepository
          .configuration
          ?.serviceEndpoints
          .nextcloudBaseUrl ??
      Uri.parse('https://files.weave.local');

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
        'Connect Nextcloud to browse your files.',
      );
    }

    return switch (path) {
      '/' => const DirectoryListing(path: '/', entries: _rootEntries),
      '/Documents' => const DirectoryListing(
        path: '/Documents',
        entries: _documentsEntries,
      ),
      _ => const DirectoryListing(path: '/', entries: <FileEntry>[]),
    };
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
  Future<FilesConnectionState> restoreConnection() async => _connectionState();

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
      senderId: '@alex:weave.local',
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
  Future<List<ChatConversation>> loadConversations() async =>
      const <ChatConversation>[
        ChatConversation(
          id: '!release:weave.local',
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
        senderId: '@me:weave.local',
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
