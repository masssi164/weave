import 'dart:convert';
import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
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
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';

import '../integration_test/helpers/auth_helper.dart';
import '../integration_test/helpers/test_config.dart';
import '../integration_test/helpers/test_http_overrides.dart';

void main() {
  HttpOverrides.global = TestHttpOverrides();

  final liveConfig = TestConfig.fromEnvironment();
  final skipReason = liveConfig.hasCredentials
      ? false
      : 'Requires WEAVE_TEST_USERNAME and WEAVE_TEST_PASSWORD dart-defines.';

  late TestConfig config;
  late http.Client httpClient;
  late AuthHelper authHelper;

  setUp(() {
    config = liveConfig;
    httpClient = createTrustedTestHttpClient();
    authHelper = AuthHelper();
  });

  tearDown(() {
    httpClient.close();
  });

  test('setup -> sign-in -> shell ready', () async {
    final session = await authHelper.signInForAppSession(config);
    final container = _createAppContainer(config: config, session: session);
    addTearDown(container.dispose);

    final bootstrap = await container.read(appBootstrapProvider.future);

    expect(session.accessToken, isNotEmpty);
    expect(bootstrap.phase, BootstrapPhase.ready);
  }, skip: skipReason);

  test('settings/config change -> targeted invalidation fires', () async {
    final session = await authHelper.signInForAppSession(config);
    final container = _createAppContainer(config: config, session: session);
    addTearDown(container.dispose);

    final updatedBackendUrl = config.backendApiBaseUrl.replace(
      pathSegments: [
        ...config.backendApiBaseUrl.pathSegments.where(
          (segment) => segment.isNotEmpty,
        ),
        'e2e-settings-change',
      ],
    );

    await container
        .read(applyServerConfigurationChangesProvider)
        .call(
          ServerConfigurationSaveResult(
            configuration: _serverConfiguration(
              config.copyWith(backendApiBaseUrl: updatedBackendUrl),
            ),
            authConfigurationChanged: false,
            matrixHomeserverChanged: false,
            nextcloudBaseUrlChanged: false,
            backendApiBaseUrlChanged: true,
          ),
        );
    await container
        .read(serverConfigurationRepositoryProvider)
        .saveConfiguration(
          _serverConfiguration(
            config.copyWith(backendApiBaseUrl: updatedBackendUrl),
          ),
        );

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
  }, skip: skipReason);

  test('authenticated GET /api/v1/me returns expected claims', () async {
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
  }, skip: skipReason);

  test('authenticated GET /api/v1/workspace/capabilities returns expected '
      'structure', () async {
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
  }, skip: skipReason);

  test(
    'backend unavailable -> backend client surfaces unreachable failure',
    () async {
      final accessToken = await authHelper.signIn(config);
      final unreachableConfig = config.copyWith(
        backendApiBaseUrl: config.unreachableBackendApiBaseUrl(),
      );
      final client = HttpWeaveApiClient(httpClient: httpClient);

      Object? error;
      try {
        await client.fetchWorkspaceCapabilities(
          baseUrl: unreachableConfig.backendApiBaseUrl,
          accessToken: accessToken,
        );
      } catch (thrown) {
        error = thrown;
      }

      expect(error, isA<AppFailure>());
      expect(
        (error as AppFailure).message,
        contains('Unable to reach the Weave backend right now.'),
      );
    },
    skip: skipReason,
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
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
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
