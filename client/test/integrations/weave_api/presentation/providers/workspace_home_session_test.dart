import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _ReadyBootstrap extends AppBootstrap {
  @override
  Future<BootstrapState> build() async => const BootstrapState.ready();
}

class _Configuration implements ServerConfigurationRepository {
  ServerConfiguration? current = buildTestConfiguration();
  @override
  Future<ServerConfiguration?> loadConfiguration() async => current;
  @override
  Future<void> clearConfiguration() async {
    current = null;
  }

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    current = configuration;
  }
}

class _Auth implements AuthSessionRepository {
  AuthState state = AuthState.authenticated(
    buildTestAuthSession(accessToken: 'restored-token'),
  );
  int restores = 0;
  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    restores++;
    return state;
  }

  @override
  Future<void> clearLocalSession() async {
    state = const AuthState.signedOut();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) => clearLocalSession();
  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;
  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) =>
      throw UnimplementedError();
}

http.Response _home() => http.Response(
  jsonEncode({
    'version': 3,
    'readiness': 'ready',
    'summary': 'Current member Home.',
    'supportSafe': true,
    'sections': [],
    'actions': [],
    'recentActivity': [],
  }),
  200,
  headers: {'content-type': 'application/json'},
);

void main() {
  late _Configuration configuration;
  late _Auth auth;
  ProviderContainer container(http.Client transport) {
    final result = ProviderContainer(
      overrides: [
        serverConfigurationRepositoryProvider.overrideWithValue(configuration),
        authSessionRepositoryProvider.overrideWithValue(auth),
        appBootstrapProvider.overrideWith(_ReadyBootstrap.new),
        weaveApiClientProvider.overrideWithValue(
          HttpWeaveApiClient(httpClient: transport),
        ),
      ],
    );
    addTearDown(result.dispose);
    final subscription = result.listen(
      weaveApiWorkspaceHomeProvider,
      (_, _) {},
    );
    addTearDown(subscription.close);
    return result;
  }

  setUp(() {
    configuration = _Configuration();
    auth = _Auth();
  });

  test('Home uses the token returned by ordinary session restoration', () async {
    final state = container(
      MockClient((request) async {
        expect(request.url.path, '/api/workspace/home');
        expect(request.headers['authorization'], 'Bearer restored-token');
        return _home();
      }),
    );
    final result = await state.read(weaveApiWorkspaceHomeProvider.future);
    expect(result!.version, 3);
    expect(
      auth.restores,
      2,
      reason:
          'Restore before request and confirm the same session before publishing.',
    );
  });

  for (final change in ['member', 'sign-out', 'server']) {
    test('pending Home response is discarded after $change changes', () async {
      final reached = Completer<void>();
      final resume = Completer<void>();
      final state = container(
        MockClient((request) async {
          reached.complete();
          await resume.future;
          return _home();
        }),
      );
      final rejected = Completer<AsyncValue<WorkspaceHomeSnapshot?>>();
      final published = <WorkspaceHomeSnapshot>[];
      final subscription = state.listen(weaveApiWorkspaceHomeProvider, (
        previous,
        next,
      ) {
        final snapshot = next.asData?.value;
        if (snapshot != null) published.add(snapshot);
        if (next.hasError && !rejected.isCompleted) rejected.complete(next);
      });
      addTearDown(subscription.close);
      await reached.future.timeout(const Duration(seconds: 3));
      if (change == 'member') {
        auth.state = AuthState.authenticated(
          buildTestAuthSession(accessToken: 'other-member'),
        );
      } else if (change == 'sign-out') {
        await auth.clearLocalSession();
      } else {
        configuration.current = buildTestConfiguration(
          backendApiBaseUrl: 'https://different.example.test/api',
        );
      }
      resume.complete();
      final failure = await rejected.future.timeout(const Duration(seconds: 3));
      expect(
        failure.error,
        isA<AppFailure>().having(
          (value) => value.message,
          'session fence',
          contains('session changed'),
        ),
      );
      expect(published, isEmpty);
    });
  }

  test(
    'explicit integration invalidation cannot restore late Home activity',
    () async {
      final reached = Completer<void>();
      final resume = Completer<void>();
      final state = container(
        MockClient((request) async {
          reached.complete();
          await resume.future;
          return _home();
        }),
      );
      final published = <String>[];
      final subscription = state.listen(weaveApiWorkspaceHomeProvider, (
        previous,
        next,
      ) {
        final snapshot = next.asData?.value;
        if (snapshot != null) published.add(snapshot.summary);
      });
      addTearDown(subscription.close);
      await reached.future.timeout(const Duration(seconds: 3));
      await auth.clearLocalSession();
      state
          .read(workspaceInvalidationProvider.notifier)
          .invalidate(
            integration: WorkspaceIntegration.weaveBackend,
            reason: IntegrationInvalidationReason.explicitSignOut,
          );
      expect(await state.read(weaveApiWorkspaceHomeProvider.future), isNull);
      resume.complete();
      await state.pump();
      expect(published, isEmpty);
      expect(state.read(weaveApiWorkspaceHomeProvider).asData?.value, isNull);
    },
  );
}
