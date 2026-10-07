import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';
import 'package:weave/main.dart';

import 'helpers/test_config.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const enabled = bool.fromEnvironment('WEAVE_SYSTEM_BROWSER_AUTH_E2E');
  const matrixEnabled = bool.fromEnvironment('WEAVE_MEMBER_MATRIX_E2E');
  const disposableMessageEnabled = bool.fromEnvironment(
    'WEAVE_DISPOSABLE_MATRIX_MESSAGE_E2E',
  );
  final config = TestConfig.fromEnvironment();

  testWidgets(
    'activation and OIDC Authorization Code with PKCE use the system browser',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final refreshed = await container
          .read(authSessionRepositoryProvider)
          .refreshSession(
            AuthConfiguration(
              issuer: config.issuerUrl,
              clientId: config.clientId,
            ),
          );
      expect(refreshed.isAuthenticated, isTrue);
      expect(refreshed.session?.accessToken, isNotEmpty);
      debugPrint(
        'PHYSICAL_AUTH_SESSION_RESULT status=passed activation=system-browser '
        'pkce=true workspaceRestored=true refresh=true supportSafe=true',
      );
    },
    skip: !enabled,
    timeout: const Timeout(Duration(minutes: 7)),
  );

  testWidgets(
    'one Weave login opens and restores the native Matrix member session',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      try {
        final first = await coordinator.open(allowInteractiveSignIn: false);
        expect(first.userId, startsWith('@'));
        expect(first.deviceId, isNotEmpty);

        await coordinator.disposePreservingCryptoState();
        final refreshed = await container
            .read(authSessionRepositoryProvider)
            .refreshSession(
              AuthConfiguration(
                issuer: config.issuerUrl,
                clientId: config.clientId,
              ),
            );
        expect(refreshed.isAuthenticated, isTrue);
        final restored = await coordinator.open(allowInteractiveSignIn: false);
        expect(restored.userId, first.userId);
        expect(restored.deviceId, first.deviceId);
        debugPrint(
          'PHYSICAL_MATRIX_MEMBER_RESULT status=passed login=single '
          'nativeSdk=true sync=true tokenRefresh=true deviceRetained=true '
          'supportSafe=true',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
    },
    skip: !matrixEnabled,
    timeout: const Timeout(Duration(minutes: 9)),
  );

  testWidgets(
    'one Weave login sends and reads an encrypted Matrix event on a disposable stack',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      final chat = container.read(chatRepositoryProvider);
      final marker =
          'Weave native Matrix E2E ${DateTime.now().toUtc().microsecondsSinceEpoch}';
      try {
        final first = await coordinator.open(allowInteractiveSignIn: false);
        final room = await chat.createConversation(title: marker);
        expect(room.id, startsWith('!'));
        await chat.sendMessage(roomId: room.id, message: marker);

        Future<void> requireDecryptedReadback() async {
          for (var attempt = 0; attempt < 20; attempt++) {
            final timeline = await chat.loadRoomTimeline(room.id);
            final matching = timeline.messages
                .where((message) => message.text == marker)
                .toList(growable: false);
            if (matching.length == 1) {
              expect(matching.single.isMine, isTrue);
              expect(matching.single.id, startsWith(r'$'));
              return;
            }
            await Future<void>.delayed(const Duration(seconds: 1));
          }
          fail('Native Matrix encrypted message readback did not arrive.');
        }

        await requireDecryptedReadback();
        await coordinator.disposePreservingCryptoState();
        final refreshed = await container
            .read(authSessionRepositoryProvider)
            .refreshSession(
              AuthConfiguration(
                issuer: config.issuerUrl,
                clientId: config.clientId,
              ),
            );
        expect(refreshed.isAuthenticated, isTrue);
        final restored = await coordinator.open(allowInteractiveSignIn: false);
        expect(restored.userId, first.userId);
        expect(restored.deviceId, first.deviceId);
        await requireDecryptedReadback();
        debugPrint(
          'NATIVE_MATRIX_MESSAGE_RESULT status=passed login=single '
          'nativeSdk=true encryptedSendRead=true tokenRefresh=true '
          'deviceRetained=true supportSafe=true',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
    },
    skip: !matrixEnabled || !disposableMessageEnabled,
    timeout: const Timeout(Duration(minutes: 12)),
  );
}

Future<ProviderContainer> _openWeaveSession(
  WidgetTester tester,
  TestConfig config,
) async {
  final serverConfiguration = ServerConfiguration(
    providerType: OidcProviderType.oidc,
    oidcIssuerUrl: config.issuerUrl,
    oidcClientRegistration: OidcClientRegistration.manual(
      clientId: config.clientId,
    ),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: config.matrixHomeserverUrl,
      backendApiBaseUrl: config.backendApiBaseUrl,
    ),
  );
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        serverConfigurationRepositoryProvider.overrideWithValue(
          _MemoryServerConfigurationRepository(serverConfiguration),
        ),
      ],
      child: const WeaveApp(),
    ),
  );
  await _waitForAny(tester, const [
    ValueKey('weave.auth.sign-in'),
    ValueKey('weave.workspace.home'),
  ], timeout: const Duration(minutes: 1));
  if (find.byKey(const ValueKey('weave.auth.sign-in')).evaluate().isNotEmpty) {
    await tester.tap(find.byKey(const ValueKey('weave.auth.sign-in')));
    await tester.pump();
  }
  // The production FlutterAppAuthOidcClient owns the system-browser
  // transition. A human completes activation and login in Keycloak.
  await _waitFor(
    tester,
    const ValueKey('weave.workspace.home'),
    timeout: const Duration(minutes: 5),
  );
  return ProviderScope.containerOf(tester.element(find.byType(WeaveApp)));
}

Future<void> _waitFor(
  WidgetTester tester,
  Key key, {
  required Duration timeout,
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(seconds: 1));
    if (find.byKey(key).evaluate().isNotEmpty) {
      return;
    }
  }
  fail('Timed out waiting for widget key $key.');
}

Future<void> _waitForAny(
  WidgetTester tester,
  List<Key> keys, {
  required Duration timeout,
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(seconds: 1));
    if (keys.any((key) => find.byKey(key).evaluate().isNotEmpty)) {
      return;
    }
  }
  fail('Timed out waiting for one of ${keys.join(', ')}.');
}

class _MemoryServerConfigurationRepository
    implements ServerConfigurationRepository {
  _MemoryServerConfigurationRepository(this._configuration);

  ServerConfiguration? _configuration;

  @override
  Future<void> clearConfiguration() async => _configuration = null;

  @override
  Future<ServerConfiguration?> loadConfiguration() async => _configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    _configuration = configuration;
  }
}
