import 'package:riverpod/riverpod.dart';
import 'package:riverpod/misc.dart' show Override;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/l10n/shared_preferences_app_locale_preference_repository.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/profile/domain/repositories/user_profile_repository.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import 'live_oidc_test_driver.dart';
import 'multi_user_test_config.dart';
import 'namespaced_test_storage.dart';
import 'test_config.dart';
import 'test_http_overrides.dart';

/// Fixed, identity-free stages exposed only to support-safe live-test progress
/// reporting. No URL, credential, provider response, or actor identifier is
/// carried by this callback.
enum LiveActorOpenPhase { organizationDiscovery, oidcSignIn, appBootstrap }

/// One isolated app-data profile reused across process-like session contexts.
///
/// Each role owns a distinct secure store and preferences store. Opening a new
/// [LiveActorSession] creates a new provider graph and a fresh OIDC PKCE
/// session while preserving only that role's device-bound local state.
class LiveActorProfile {
  LiveActorProfile({
    required this.role,
    required MultiUserTestConfig configuration,
  }) : _actorConfig = configuration.actorConfig(role),
       _handoffRunRef = configuration.runHash,
       _secureStore = NamespacedSecureStore(
         namespace: _storageNamespace(configuration, role),
         delegate: FlutterSecureStore(),
       ),
       _preferencesStore = NamespacedPreferencesStore(
         namespace: _storageNamespace(configuration, role),
         delegate: SharedPreferencesStore(),
       );

  final CollaborationActorRole role;
  final TestConfig _actorConfig;
  final String _handoffRunRef;
  final NamespacedSecureStore _secureStore;
  final NamespacedPreferencesStore _preferencesStore;
  late final ServerConfigurationRepository _serverConfigurationRepository =
      SharedPreferencesServerConfigurationRepository(
        store: _preferencesStore,
        deriver: ServiceEndpointDeriver(),
      );
  Future<void>? _discoveryInFlight;
  var _organizationDiscovered = false;
  var _organizationDiscoveryCount = 0;

  bool get organizationDiscovered => _organizationDiscovered;
  int get organizationDiscoveryCount => _organizationDiscoveryCount;
  bool get usesRealDeviceStorage => true;

  SharedPreferencesAppLocalePreferenceRepository get localePreferences =>
      SharedPreferencesAppLocalePreferenceRepository(store: _preferencesStore);

  Future<void> clearLocalePreference() {
    return _preferencesStore.remove(appLocalePreferenceStorageKey);
  }

  Future<void> clearTestStorage() async {
    await _secureStore.removeTouchedKeys();
    await _preferencesStore.removeTouchedKeys();
  }

  Future<LiveActorSession> open({
    List<Override> additionalOverrides = const <Override>[],
    void Function(LiveActorOpenPhase phase)? onPhase,
  }) async {
    onPhase?.call(LiveActorOpenPhase.organizationDiscovery);
    await _ensureOrganizationDiscovered();
    final container = _createContainer(additionalOverrides);
    try {
      onPhase?.call(LiveActorOpenPhase.oidcSignIn);
      final authState = await container
          .read(authSessionRepositoryProvider)
          .signIn(
            AuthConfiguration(
              issuer: _actorConfig.issuerUrl,
              clientId: _actorConfig.clientId,
            ),
          );
      if (!authState.isAuthenticated) {
        throw StateError('${role.name} did not establish an OIDC session.');
      }
      onPhase?.call(LiveActorOpenPhase.appBootstrap);
      await _requireReadyBootstrap(container);
      return LiveActorSession(
        container: container,
        authConfiguration: AuthConfiguration(
          issuer: _actorConfig.issuerUrl,
          clientId: _actorConfig.clientId,
        ),
      );
    } catch (_) {
      container.dispose();
      rethrow;
    }
  }

  Future<LiveActorSession> relaunch({
    List<Override> additionalOverrides = const <Override>[],
  }) async {
    final container = _createContainer(additionalOverrides);
    try {
      await _requireReadyBootstrap(container);
      return LiveActorSession(
        container: container,
        authConfiguration: AuthConfiguration(
          issuer: _actorConfig.issuerUrl,
          clientId: _actorConfig.clientId,
        ),
      );
    } catch (_) {
      container.dispose();
      rethrow;
    }
  }

  ProviderContainer _createContainer(List<Override> additionalOverrides) {
    final container = ProviderContainer(
      overrides: <Override>[
        secureStoreProvider.overrideWithValue(_secureStore),
        preferencesStoreProvider.overrideWith((ref) => _preferencesStore),
        serverConfigurationRepositoryProvider.overrideWithValue(
          _serverConfigurationRepository,
        ),
        oidcClientProvider.overrideWithValue(
          LiveOidcTestDriver(config: _actorConfig),
        ),
        ...additionalOverrides,
      ],
    );
    return container;
  }

  Future<void> _ensureOrganizationDiscovered() async {
    if (_organizationDiscovered) {
      return;
    }
    final existing = _discoveryInFlight;
    if (existing != null) {
      return existing;
    }
    final discovery = _discoverOrganization();
    _discoveryInFlight = discovery;
    try {
      await discovery;
      _organizationDiscovered = true;
      _organizationDiscoveryCount += 1;
    } catch (_) {
      _discoveryInFlight = null;
      rethrow;
    }
  }

  Future<void> _discoverOrganization() async {
    final client = createTrustedTestHttpClient();
    try {
      final productBaseUrl = _productOrigin(_actorConfig.backendApiBaseUrl);
      final platformConfigUrl = _actorConfig.apiUri('/api/platform/config');
      final handoffUri = Uri(
        scheme: 'weave',
        host: 'join',
        queryParameters: <String, String>{
          'handoff_ref': 'handoff-${role.name}-$_handoffRunRef',
          'org': 'weave-e2e',
          'workspace': role == CollaborationActorRole.outsider
              ? 'workspace-b'
              : 'workspace-a',
          'profile': 'local-lan-dogfood',
          'run_id': _handoffRunRef,
          'product_base_url': productBaseUrl.toString(),
          'platform_config_url': platformConfigUrl.toString(),
        },
      );
      await ConsumeMemberHandoff(
        repository: _serverConfigurationRepository,
        discoveryClient: AppStartDiscoveryClient(httpClient: client),
        evidenceStore: _preferencesStore,
      ).call(handoffUri);
      final discovered = await _serverConfigurationRepository
          .loadConfiguration();
      if (discovered == null || !_matchesExpectedDiscovery(discovered)) {
        throw StateError(
          'Organization discovery did not match the isolated stack contract.',
        );
      }
    } finally {
      client.close();
    }
  }

  bool _matchesExpectedDiscovery(ServerConfiguration discovered) {
    return discovered.oidcIssuerUrl == _actorConfig.issuerUrl &&
        discovered.oidcClientRegistration.clientId == _actorConfig.clientId &&
        discovered.serviceEndpoints.backendApiBaseUrl ==
            _actorConfig.backendApiBaseUrl &&
        discovered.serviceEndpoints.matrixHomeserverUrl ==
            _actorConfig.matrixHomeserverUrl &&
        discovered.serviceEndpoints.nextcloudBaseUrl ==
            _actorConfig.apiUri('/api/dav/files');
  }

  Future<void> _requireReadyBootstrap(ProviderContainer container) async {
    final bootstrap = await container.read(appBootstrapProvider.future);
    if (bootstrap.phase != BootstrapPhase.ready) {
      throw StateError('${role.name} did not reach the application shell.');
    }
  }
}

class LiveActorSession {
  LiveActorSession({
    required this.container,
    required AuthConfiguration authConfiguration,
  }) : _authConfiguration = authConfiguration;

  final ProviderContainer container;
  final AuthConfiguration _authConfiguration;

  ChatRepository get chat => container.read(chatRepositoryProvider);
  FilesRepository get files => container.read(filesRepositoryProvider);
  CalendarRepository get calendar => container.read(calendarRepositoryProvider);
  UserProfileRepository get profile =>
      container.read(userProfileRepositoryProvider);

  Future<void> signOut() {
    return container
        .read(authSessionRepositoryProvider)
        .signOut(_authConfiguration);
  }

  Future<bool> hasRestorableSession() async {
    final state = await container
        .read(authSessionRepositoryProvider)
        .restoreSession(_authConfiguration);
    return state.isAuthenticated;
  }

  Future<({String accessToken, String deviceId})>
  matrixTransportCredentials() async {
    final state = await container
        .read(authSessionRepositoryProvider)
        .restoreSession(_authConfiguration);
    final session = state.session;
    final deviceId = await container
        .read(secureStoreProvider)
        .read(matrixDeviceIdentityStorageKey);
    if (!state.isAuthenticated ||
        session == null ||
        deviceId == null ||
        deviceId.isEmpty) {
      throw StateError(
        'The live Chat transport does not have an authenticated device.',
      );
    }
    return (accessToken: session.accessToken, deviceId: deviceId);
  }

  Future<RustMatrixDecryptionDiagnostics> chatDecryptionDiagnostics(
    String roomId,
  ) async {
    final cryptoSession = await container
        .read(matrixCryptoSessionCoordinatorProvider)
        .open(synchronize: false);
    return const RustMatrixCoreBridge().loadDecryptionDiagnostics(
      profileKey: cryptoSession.profileKey,
      roomId: roomId,
    );
  }

  Future<RustMatrixDecryptionDiagnostics> chatReceiveDiagnostics() async {
    final cryptoSession = await container
        .read(matrixCryptoSessionCoordinatorProvider)
        .open(synchronize: false);
    return const RustMatrixCoreBridge().loadReceiveDiagnostics(
      profileKey: cryptoSession.profileKey,
    );
  }

  Future<void> close() async {
    await container
        .read(matrixCryptoSessionCoordinatorProvider)
        .disposePreservingCryptoState();
    container.dispose();
  }
}

String _storageNamespace(
  MultiUserTestConfig configuration,
  CollaborationActorRole role,
) {
  return 'weave.e2e.${configuration.runHash}.${configuration.runIndex}.${role.name}';
}

Uri _productOrigin(Uri backendApiBaseUrl) {
  final labels = backendApiBaseUrl.host.split('.');
  final productHost = labels.length > 2 && labels.first == 'api'
      ? labels.skip(1).join('.')
      : backendApiBaseUrl.host;
  return Uri(
    scheme: backendApiBaseUrl.scheme,
    host: productHost,
    port: backendApiBaseUrl.hasPort ? backendApiBaseUrl.port : null,
  );
}
