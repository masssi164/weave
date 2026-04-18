import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import 'helpers/auth_helper.dart';
import 'helpers/test_config.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late TestConfig config;
  late http.Client httpClient;
  late AuthHelper authHelper;

  setUp(() {
    config = TestConfig.fromEnvironment();
    httpClient = http.Client();
    authHelper = AuthHelper(httpClient: httpClient);
  });

  tearDown(() {
    httpClient.close();
  });

  testWidgets('setup -> sign-in -> shell ready', (tester) async {
    final session = await authHelper.signInForAppSession(config);
    final container = _createAppContainer(config: config, session: session);
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(container: container, child: const WeaveApp()),
    );
    await tester.pumpAndSettle();

    expect(session.accessToken, isNotEmpty);
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('Chat'), findsWidgets);
  });

  testWidgets('settings/config change -> targeted invalidation fires', (
    tester,
  ) async {
    final session = await authHelper.signInForAppSession(config);
    final container = _createAppContainer(config: config, session: session);
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(container: container, child: const WeaveApp()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();

    final field = _textFieldWithLabel('Backend API Base URL');
    await tester.ensureVisible(field);
    final updatedBackendUrl = config.backendApiBaseUrl.replace(
      pathSegments: [
        ...config.backendApiBaseUrl.pathSegments.where(
          (segment) => segment.isNotEmpty,
        ),
        'e2e-settings-change',
      ],
    );
    await tester.enterText(field, updatedBackendUrl.toString());
    await tester.pump();
    await tester.tap(find.text('Save Changes'));
    await tester.pumpAndSettle();

    final backendInvalidation = container.read(
      integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
    );
    expect(backendInvalidation?.sequence, 1);
    expect(
      backendInvalidation?.reason,
      IntegrationInvalidationReason.backendApiBaseUrlChanged,
    );
    expect(
      container.read(
        integrationInvalidationProvider(WorkspaceIntegration.matrix),
      ),
      isNull,
    );
    expect(
      container.read(
        integrationInvalidationProvider(WorkspaceIntegration.nextcloud),
      ),
      isNull,
    );
  });

  testWidgets('authenticated GET /api/v1/me returns expected claims', (
    tester,
  ) async {
    final accessToken = await authHelper.signIn(config);

    final response = await httpClient.get(
      config.apiUri('/api/v1/me'),
      headers: <String, String>{
        'Accept': 'application/json',
        'Authorization': 'Bearer $accessToken',
      },
    );

    expect(response.statusCode, 200, reason: response.body);
    final payload = _decodeObject(response.body);
    expect(payload['sub'], isA<String>());
    expect((payload['sub'] as String).trim(), isNotEmpty);
    expect(payload['email'], isA<String>());
    expect((payload['email'] as String).trim(), isNotEmpty);
  });

  testWidgets(
    'authenticated GET /api/v1/workspace/capabilities returns expected '
    'structure',
    (tester) async {
      final accessToken = await authHelper.signIn(config);

      final response = await httpClient.get(
        config.apiUri('/api/v1/workspace/capabilities'),
        headers: <String, String>{
          'Accept': 'application/json',
          'Authorization': 'Bearer $accessToken',
        },
      );

      expect(response.statusCode, 200, reason: response.body);
      final payload = _decodeObject(response.body);

      final features = payload['features'];
      if (features != null) {
        expect(features, isA<Map>());
      } else {
        for (final key in <String>[
          'shellAccess',
          'chat',
          'files',
          'calendar',
          'boards',
        ]) {
          expect(payload[key], isA<Map>(), reason: 'Missing "$key".');
        }
      }
    },
  );

  testWidgets('backend unavailable -> clear UX failure state', (tester) async {
    final session = await authHelper.signInForAppSession(config);
    final unreachableConfig = config.copyWith(
      backendApiBaseUrl: config.unreachableBackendApiBaseUrl(),
    );
    final container = _createAppContainer(
      config: unreachableConfig,
      session: session,
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(container: container, child: const WeaveApp()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    await _pumpUntil(
      tester,
      () =>
          container.read(weaveBackendConnectionStateProvider) ==
          WeaveBackendConnectionState.unreachable,
    );

    expect(
      find.text(
        'Backend API is unreachable. Check that the local Weave stack is '
        'running and the configured backend URL is correct.',
      ),
      findsOneWidget,
    );
    expect(find.text('Retry'), findsWidgets);
  });
}

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

ProviderContainer _createAppContainer({
  required TestConfig config,
  required AuthSession session,
}) {
  return ProviderContainer.test(
    overrides: [
      serverConfigurationRepositoryProvider.overrideWithValue(
        _MemoryServerConfigurationRepository(_serverConfiguration(config)),
      ),
      authSessionRepositoryProvider.overrideWithValue(
        _SessionAuthRepository(session),
      ),
      chatRepositoryProvider.overrideWithValue(_EmptyChatRepository()),
      chatSecurityRepositoryProvider.overrideWithValue(
        _SignedOutChatSecurityRepository(),
      ),
      filesRepositoryProvider.overrideWithValue(_DisconnectedFilesRepository()),
    ],
  );
}

ServerConfiguration _serverConfiguration(TestConfig config) {
  return ServerConfiguration(
    providerType: OidcProviderType.keycloak,
    oidcIssuerUrl: config.issuerUrl,
    oidcClientRegistration: OidcClientRegistration.manual(
      clientId: config.clientId,
    ),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: config.matrixHomeserverUrl,
      nextcloudBaseUrl: config.nextcloudBaseUrl,
      backendApiBaseUrl: config.backendApiBaseUrl,
    ),
  );
}

Map<String, dynamic> _decodeObject(String body) {
  final decoded = jsonDecode(body);
  if (decoded is! Map<String, dynamic>) {
    throw StateError('Expected a JSON object response.');
  }

  return decoded;
}

Future<void> _pumpUntil(
  WidgetTester tester,
  bool Function() condition, {
  Duration timeout = const Duration(seconds: 15),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      throw StateError('Timed out waiting for integration test condition.');
    }

    await tester.pump(const Duration(milliseconds: 100));
  }
}

class _MemoryServerConfigurationRepository
    implements ServerConfigurationRepository {
  _MemoryServerConfigurationRepository(this.configuration);

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

class _SessionAuthRepository implements AuthSessionRepository {
  _SessionAuthRepository(this._session);

  AuthSession? _session;

  @override
  Future<void> clearLocalSession() async {
    _session = null;
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    final session = _session;
    if (session == null || !session.matches(configuration)) {
      return const AuthState.signedOut();
    }

    return AuthState.authenticated(session);
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    final session = _session;
    if (session == null || !session.matches(configuration)) {
      return const AuthState.signedOut();
    }

    return AuthState.authenticated(session);
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    _session = null;
  }
}

class _EmptyChatRepository implements ChatRepository {
  @override
  Future<void> clearSession() async {}

  @override
  Future<void> connect() async {}

  @override
  Future<List<ChatConversation>> loadConversations() async =>
      const <ChatConversation>[];

  @override
  Future<void> signOut() async {}
}

class _SignedOutChatSecurityRepository implements ChatSecurityRepository {
  @override
  Future<void> acceptVerification() async {}

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async =>
      'unused-recovery-key';

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
      const Stream<ChatVerificationSession>.empty();
}

class _DisconnectedFilesRepository implements FilesRepository {
  @override
  Future<FilesConnectionState> connect() async =>
      const FilesConnectionState.disconnected();

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async =>
      DirectoryListing(path: path, entries: const []);

  @override
  Future<FilesConnectionState> restoreConnection() async =>
      const FilesConnectionState.disconnected();
}
