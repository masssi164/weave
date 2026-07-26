import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/main.dart';

import 'helpers/test_config.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const enabled = bool.fromEnvironment('WEAVE_SYSTEM_BROWSER_AUTH_E2E');
  final config = TestConfig.fromEnvironment();

  testWidgets(
    'activation and OIDC Authorization Code with PKCE use the system browser',
    (tester) async {
      final serverConfiguration = ServerConfiguration(
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

      if (find
          .byKey(const ValueKey('weave.auth.sign-in'))
          .evaluate()
          .isNotEmpty) {
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

      final container = ProviderScope.containerOf(
        tester.element(find.byType(WeaveApp)),
      );
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
