import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_auth_headers.dart';
import 'package:weave/integrations/nextcloud/data/services/nextcloud_login_launcher.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/presentation/providers/nextcloud_provider.dart';
import 'package:weave/main.dart';

import 'helpers/live_oidc_test_driver.dart';
import 'helpers/test_config.dart';
import 'helpers/test_http_overrides.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = TestHttpOverrides();

  late TestConfig config;
  late LiveOidcTestDriver liveOidcDriver;
  late http.Client nextcloudHttpClient;

  setUp(() {
    config = TestConfig.fromEnvironment();
    config.requireCredentials();
    liveOidcDriver = LiveOidcTestDriver(config: config);
    nextcloudHttpClient = createTrustedTestHttpClient();
  });

  tearDown(() {
    nextcloudHttpClient.close();
  });

  testWidgets('real live-stack sign-in, Matrix connect, and Nextcloud browse', (
    tester,
  ) async {
    final serverConfig = ServerConfiguration(
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

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          secureStoreProvider.overrideWithValue(_MemorySecureStore()),
          serverConfigurationRepositoryProvider.overrideWithValue(
            _MemoryServerConfigurationRepository(serverConfig),
          ),
          oidcClientProvider.overrideWithValue(liveOidcDriver),
          matrixAuthBrowserProvider.overrideWithValue(liveOidcDriver),
          nextcloudHttpClientProvider.overrideWithValue(nextcloudHttpClient),
          nextcloudLoginLauncherProvider.overrideWithValue(
            const _FailingNextcloudLoginLauncher(),
          ),
        ],
        child: const WeaveApp(),
      ),
    );

    // ignore: avoid_print
    print('APP_E2E_MARKER stage=app_started');
    await _pumpUntilSettled(tester);

    final container = ProviderScope.containerOf(
      tester.element(find.byType(WeaveApp)),
    );

    await _waitFor(
      tester,
      () => find.text('Anmelden').evaluate().isNotEmpty,
      reason: 'App should reach the sign-in screen with the live config.',
      diagnostics: () {
        final bootstrap = container.read(appBootstrapProvider);
        final texts = find
            .byType(Text)
            .evaluate()
            .map((element) => (element.widget as Text).data)
            .whereType<String>()
            .join(' | ');
        return 'bootstrap=$bootstrap\ntexts=$texts';
      },
    );

    // ignore: avoid_print
    print('APP_E2E_MARKER stage=before_sign_in');
    await tester.tap(find.widgetWithText(AccessibleButton, 'Anmelden').first);
    await tester.pump();
    // ignore: avoid_print
    print('APP_E2E_MARKER stage=after_sign_in_tap');

    // ignore: avoid_print
    print('APP_E2E_MARKER stage=before_matrix_connect');
    container.read(chatProvider.notifier).connect();
    await tester.pump();

    await _waitFor(
      tester,
      () {
        final state = container.read(chatProvider);
        return state.phase == ChatViewPhase.empty ||
            state.phase == ChatViewPhase.content ||
            state.phase == ChatViewPhase.error ||
            state.phase == ChatViewPhase.unsupported;
      },
      reason: 'Matrix chat should connect against the live homeserver.',
      timeout: const Duration(minutes: 2),
      diagnostics: () {
        final state = container.read(chatProvider);
        return 'chatPhase=${state.phase} '
            'failure=${state.failure?.message} '
            'cause=${state.failure?.cause} '
            'conversations=${state.conversations.length}';
      },
    );

    // ignore: avoid_print
    print('APP_E2E_MARKER stage=after_matrix_connect_wait');

    final chatState = container.read(chatProvider);
    final matrixConnected =
        chatState.phase == ChatViewPhase.empty ||
        chatState.phase == ChatViewPhase.content;

    final chatRepository = container.read(chatRepositoryProvider);
    final matrixClientFactory = container.read(matrixClientFactoryProvider);
    final matrixClient = await matrixClientFactory.getClientForHomeserver(
      config.matrixHomeserverUrl,
    );
    final roomName = 'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}';
    final roomId = await matrixClient.createGroupChat(
      groupName: roomName,
      enableEncryption: false,
      waitForSync: false,
      federated: false,
    );
    await _waitFor(
      tester,
      () => matrixClient.getRoomById(roomId) != null,
      reason: 'The live Matrix room should become available after creation.',
      timeout: const Duration(minutes: 1),
      diagnostics: () {
        final state = container.read(chatProvider);
        return 'roomId=$roomId '
            'chatPhase=${state.phase} '
            'failure=${state.failure?.message} '
            'cause=${state.failure?.cause} '
            'knownRooms=${matrixClient.rooms.length}';
      },
    );
    // ignore: avoid_print
    print('APP_E2E_MARKER stage=after_room_visible');
    final sentMessage =
        'live-e2e message ${DateTime.now().toUtc().toIso8601String()}';
    await chatRepository.sendMessage(roomId: roomId, message: sentMessage);
    final timeline = await chatRepository.loadRoomTimeline(roomId);
    final deliveredMessage = timeline.messages
        .where((message) => message.text == sentMessage)
        .toList(growable: false);
    // ignore: avoid_print
    print(
      'CHAT_RESULT roomId=$roomId roomName=$roomName '
      'timelineMessages=${timeline.messages.length} '
      'matchedMessages=${deliveredMessage.length}',
    );

    // Keep the Matrix outcome visible while still validating the Nextcloud path.
    // ignore: avoid_print
    print(
      'MATRIX_RESULT phase=${chatState.phase} '
      'failure=${chatState.failure} '
      'cause=${chatState.failure?.cause}',
    );

    // ignore: avoid_print
    print('APP_E2E_MARKER stage=before_nextcloud_connect');
    await container.read(filesProvider.notifier).connect();
    await tester.pump();

    await _waitFor(
      tester,
      () {
        final asyncState = container.read(filesProvider);
        if (asyncState.hasError) {
          return true;
        }
        if (!asyncState.hasValue) {
          return false;
        }
        final state = asyncState.requireValue;
        return state.connectionState.isConnected &&
            state.directoryListing != null;
      },
      reason: 'Nextcloud should connect and return a real directory listing.',
      timeout: const Duration(minutes: 1),
      diagnostics: () {
        final asyncState = container.read(filesProvider);
        if (asyncState.hasError) {
          return 'filesError=${asyncState.error}';
        }
        if (!asyncState.hasValue) {
          return 'filesState=loading';
        }
        final state = asyncState.requireValue;
        return 'filesConnected=${state.connectionState.isConnected} '
            'filesStatus=${state.connectionState.status} '
            'filesMessage=${state.connectionState.message} '
            'hasListing=${state.directoryListing != null}';
      },
    );

    final filesState = container.read(filesProvider).requireValue;
    final nextcloudConnected =
        filesState.connectionState.isConnected &&
        filesState.directoryListing != null;

    final nextcloudSession = await container
        .read(nextcloudConnectionServiceProvider)
        .requireLiveSession();
    final seededFileName =
        'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}.txt';
    final seededFileUri = nextcloudSession.baseUrl.resolve(
      'remote.php/dav/files/${Uri.encodeComponent(nextcloudSession.userId)}/$seededFileName',
    );
    final putResponse = await nextcloudHttpClient.put(
      seededFileUri,
      headers: <String, String>{
        ...buildNextcloudAuthHeaders(nextcloudSession),
        HttpHeaders.contentTypeHeader: 'text/plain; charset=utf-8',
      },
      body: 'weave live e2e ${DateTime.now().toUtc().toIso8601String()}',
    );
    expect(
      putResponse.statusCode,
      anyOf(201, 204),
      reason: 'Nextcloud WebDAV upload should succeed for the live session.',
    );

    await container.read(filesProvider.notifier).refresh();
    await _waitFor(
      tester,
      () {
        final state = container.read(filesProvider);
        if (!state.hasValue) {
          return false;
        }
        final listing = state.requireValue.directoryListing;
        return listing != null &&
            listing.entries.any((entry) => entry.name == seededFileName);
      },
      reason:
          'Files view should show the file uploaded to the live Nextcloud WebDAV path.',
      timeout: const Duration(minutes: 1),
    );

    final refreshedFilesState = container.read(filesProvider).requireValue;
    final matchedFiles = refreshedFilesState.directoryListing!.entries
        .where((entry) => entry.name == seededFileName)
        .toList(growable: false);
    // ignore: avoid_print
    print(
      'FILES_RESULT path=${refreshedFilesState.directoryListing!.path} '
      'entries=${refreshedFilesState.directoryListing!.entries.length} '
      'matchedFiles=${matchedFiles.length} '
      'fileName=$seededFileName',
    );

    if (!matrixConnected ||
        !nextcloudConnected ||
        deliveredMessage.isEmpty ||
        matchedFiles.isEmpty) {
      fail(
        'live_e2e_result '
        'authSignedIn=true '
        'matrixConnected=$matrixConnected '
        'matrixPhase=${chatState.phase} '
        'matrixFailure=${chatState.failure} '
        'matrixCause=${chatState.failure?.cause} '
        'chatRoomId=$roomId '
        'chatMatchedMessages=${deliveredMessage.length} '
        'nextcloudConnected=$nextcloudConnected '
        'nextcloudStatus=${filesState.connectionState.status} '
        'nextcloudMessage=${filesState.connectionState.message} '
        'nextcloudEntries=${refreshedFilesState.directoryListing?.entries.length} '
        'nextcloudMatchedFiles=${matchedFiles.length} '
        'seededFileName=$seededFileName',
      );
    }

    expect(matrixConnected, isTrue);
    expect(deliveredMessage, isNotEmpty);
    expect(nextcloudConnected, isTrue);
    expect(matchedFiles, isNotEmpty);
  });
}

Future<void> _pumpUntilSettled(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 200));
  }
}

Future<void> _waitFor(
  WidgetTester tester,
  bool Function() predicate, {
  required String reason,
  Duration timeout = const Duration(seconds: 30),
  String Function()? diagnostics,
}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(const Duration(milliseconds: 250));
    if (predicate()) {
      return;
    }
  }
  final details = diagnostics?.call();
  fail(details == null ? reason : '$reason\n$details');
}

class _FailingNextcloudLoginLauncher implements NextcloudLoginLauncher {
  const _FailingNextcloudLoginLauncher();

  @override
  Future<void> launch(Uri loginUri) {
    throw NextcloudFailure.unsupportedPlatform(
      'Live app e2e should not fall back to the interactive Nextcloud login '
      'flow during CI. Expected bearer session reuse instead. '
      'loginUri=$loginUri',
    );
  }
}

class _MemorySecureStore implements SecureStore {
  final Map<String, String> _values = <String, String>{};

  @override
  Future<void> delete(String key) async {
    _values.remove(key);
  }

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async {
    _values[key] = value;
  }
}

class _MemoryServerConfigurationRepository
    implements ServerConfigurationRepository {
  _MemoryServerConfigurationRepository(this._configuration);

  ServerConfiguration? _configuration;

  @override
  Future<void> clearConfiguration() async {
    _configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => _configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    _configuration = configuration;
  }
}
