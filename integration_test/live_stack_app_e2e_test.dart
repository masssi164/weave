import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory_io.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import 'package:weave/main.dart';

import 'helpers/live_oidc_test_driver.dart';
import 'helpers/test_config.dart';
import 'helpers/test_http_overrides.dart';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  // The self-hosted macOS runner can expose host accessibility state to the
  // launched Flutter app. Keep the live E2E deterministic and prevent a
  // platform-requested semantics handle from leaking past test teardown.
  binding.platformDispatcher.semanticsEnabledTestValue = false;
  HttpOverrides.global = TestHttpOverrides();

  late TestConfig config;
  late LiveOidcTestDriver liveOidcDriver;
  Directory? matrixSupportDirectory;
  SdkMatrixClientFactory? liveMatrixClientFactory;

  setUp(() async {
    config = TestConfig.fromEnvironment();
    config.requireCredentials();
    liveOidcDriver = LiveOidcTestDriver(config: config);
    final supportDirectory = await Directory.systemTemp.createTemp(
      'weave-live-e2e-matrix-',
    );
    matrixSupportDirectory = supportDirectory;
    liveMatrixClientFactory = SdkMatrixClientFactory(
      appSupportDirectoryProvider: () async => supportDirectory,
    );
  });

  tearDown(() async {
    await liveMatrixClientFactory?.dispose();
    final supportDirectory = matrixSupportDirectory;
    if (supportDirectory != null && await supportDirectory.exists()) {
      await supportDirectory.delete(recursive: true);
    }
  });

  testWidgets(
    'real live-stack sign-in, Matrix connect, and backend files browse',
    (tester) async {
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
            matrixClientFactoryProvider.overrideWithValue(
              liveMatrixClientFactory!,
            ),
          ],
          child: const WeaveApp(),
        ),
      );

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

      await tester.tap(find.widgetWithText(AccessibleButton, 'Anmelden').first);
      await tester.pump();

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

      final chatState = container.read(chatProvider);
      final matrixConnected =
          chatState.phase == ChatViewPhase.empty ||
          chatState.phase == ChatViewPhase.content;

      final chatRepository = container.read(chatRepositoryProvider);
      final matrixClientFactory = container.read(matrixClientFactoryProvider);
      final matrixClient = await matrixClientFactory.getClientForHomeserver(
        config.matrixHomeserverUrl,
      );
      final roomName =
          'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}';
      final roomId = await matrixClient.createGroupChat(
        groupName: roomName,
        enableEncryption: false,
        waitForSync: true,
        federated: false,
      );
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

      // Keep the Matrix outcome visible while still validating the backend files path.
      // ignore: avoid_print
      print(
        'MATRIX_RESULT phase=${chatState.phase} '
        'failure=${chatState.failure} '
        'cause=${chatState.failure?.cause}',
      );

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
        reason:
            'Backend files facade should connect and return a real directory listing.',
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
              'directoryFailure=${state.directoryFailure?.message} '
              'directoryFailureCause=${state.directoryFailure?.cause} '
              'configuredBackendApiBaseUrl=${config.backendApiBaseUrl} '
              'hasListing=${state.directoryListing != null}';
        },
      );

      final filesState = container.read(filesProvider).requireValue;
      final filesFacadeConnected =
          filesState.connectionState.isConnected &&
          filesState.directoryListing != null;

      final seededFileName =
          'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}.txt';
      final seededFileBody = utf8.encode(
        'weave live e2e ${DateTime.now().toUtc().toIso8601String()}',
      );
      await container
          .read(filesRepositoryProvider)
          .uploadFile(
            '/',
            FileUploadRequest(
              fileName: seededFileName,
              sizeInBytes: seededFileBody.length,
              byteStream: Stream<List<int>>.value(seededFileBody),
            ),
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
            'Files view should show the file uploaded through the backend files facade.',
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
          !filesFacadeConnected ||
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
          'filesFacadeConnected=$filesFacadeConnected '
          'filesFacadeStatus=${filesState.connectionState.status} '
          'filesFacadeMessage=${filesState.connectionState.message} '
          'filesFacadeEntries=${refreshedFilesState.directoryListing?.entries.length} '
          'filesFacadeMatchedFiles=${matchedFiles.length} '
          'seededFileName=$seededFileName',
        );
      }

      expect(matrixConnected, isTrue);
      expect(deliveredMessage, isNotEmpty);
      expect(filesFacadeConnected, isTrue);
      expect(matchedFiles, isNotEmpty);
    },
    semanticsEnabled: false,
  );
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
