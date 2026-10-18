import 'dart:convert';
import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/profile/data/repositories/backend_user_profile_repository.dart';
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

class _RecordingHttpClient extends http.BaseClient {
  _RecordingHttpClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}

http.StreamedResponse _jsonResponse(Map<String, Object?> json) {
  return http.StreamedResponse(
    Stream.value(utf8.encode(jsonEncode(json))),
    200,
    headers: {'content-type': 'application/json'},
  );
}

class _ReadyBootstrap extends AppBootstrap {
  @override
  Future<BootstrapState> build() async => const BootstrapState.ready();
}

class _RefreshingOidcClient implements OidcClient {
  late Future<OidcTokenBundle> Function() refreshHandler;
  int refreshCalls = 0;

  @override
  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  }) {
    refreshCalls++;
    return refreshHandler();
  }

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  ) => throw StateError('No interactive sign-in in this test.');

  @override
  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  }) async {}
}

void main() {
  group('current profile request session', () {
    late InMemorySecureStore store;
    late _RefreshingOidcClient oidc;
    late OidcAuthSessionRepository auth;
    late ProviderContainer container;
    late ServerConfiguration configuration;
    var requests = 0;
    var responseStatus = 200;
    String? authorization;
    OidcTokenBundle refreshed() => OidcTokenBundle(
      accessToken: 'fresh-token',
      refreshToken: 'refresh-next',
      idToken: 'id-next',
      expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
      tokenType: 'Bearer',
      scopes: const ['openid'],
    );
    Future<void> expireStoredSession() => store.write(
      authSessionStorageKey,
      AuthSessionDto.fromSession(
        buildTestAuthSession(
          accessToken: 'initial-token',
          expiresAt: DateTime.now().toUtc().subtract(
            const Duration(minutes: 1),
          ),
        ),
      ).encode(),
    );
    const update = UserProfileUpdate(
      displayName: 'Updated',
      locale: 'de',
      timezone: 'Europe/Berlin',
    );

    setUp(() {
      configuration = buildTestConfiguration();
      store = InMemorySecureStore({
        authSessionStorageKey: AuthSessionDto.fromSession(
          buildTestAuthSession(accessToken: 'initial-token'),
        ).encode(),
      });
      oidc = _RefreshingOidcClient()..refreshHandler = () async => refreshed();
      auth = OidcAuthSessionRepository(secureStore: store, oidcClient: oidc);
      requests = 0;
      responseStatus = 200;
      authorization = null;
      container = ProviderContainer.test(
        overrides: [
          savedServerConfigurationProvider.overrideWith(
            (ref) async => configuration,
          ),
          appBootstrapProvider.overrideWith(_ReadyBootstrap.new),
          authSessionRepositoryProvider.overrideWithValue(auth),
          backendProfileClientProvider.overrideWithValue(
            BackendProfileClient(
              httpClient: _RecordingHttpClient((request) async {
                requests++;
                authorization = request.headers['Authorization'];
                if (responseStatus != 200) {
                  return http.StreamedResponse(
                    Stream.value(utf8.encode('{}')),
                    responseStatus,
                  );
                }
                return _jsonResponse({
                  'userId': 'member',
                  'username': 'member',
                  'displayName': 'Updated',
                  'locale': 'de',
                  'timezone': 'Europe/Berlin',
                  'roles': <String>[],
                  'groups': <String>[],
                });
              }),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
    });

    test(
      'profile save resolves fresh bearer after cached session expires',
      () async {
        final snapshot = await container.read(
          weaveAuthenticatedSessionProvider.future,
        );
        expect(snapshot!.accessToken, 'initial-token');
        await expireStoredSession();
        await container
            .read(userProfileRepositoryProvider)
            .updateProfile(update);
        expect(authorization, 'Bearer fresh-token');
        expect(oidc.refreshCalls, 1);
        expect(requests, 1);
        expect(
          await container.read(weaveAuthenticatedSessionProvider.future),
          same(snapshot),
        );
      },
    );

    for (final invalid in ['signed-out', 'wrong-binding', 'refresh-revoked']) {
      test('$invalid cannot reuse the cached profile bearer', () async {
        await container.read(weaveAuthenticatedSessionProvider.future);
        if (invalid == 'signed-out') {
          await auth.clearLocalSession();
        } else if (invalid == 'wrong-binding') {
          await store.write(
            authSessionStorageKey,
            AuthSessionDto.fromSession(
              buildTestAuthSession(issuer: 'https://other-issuer.test'),
            ).encode(),
          );
        } else {
          await expireStoredSession();
          oidc.refreshHandler = () async =>
              throw const AuthFailure.sessionRejected('revoked');
        }
        await expectLater(
          container.read(userProfileRepositoryProvider).updateProfile(update),
          throwsA(isA<AppFailure>()),
        );
        expect(requests, 0);
        expect(store.rawValue(authSessionStorageKey), isNull);
      });
    }

    for (final transition in ['sign-out', 'configuration-change']) {
      test('held refresh cannot PATCH after $transition', () async {
        await container.read(weaveAuthenticatedSessionProvider.future);
        await expireStoredSession();
        final started = Completer<void>();
        final pendingRefresh = Completer<OidcTokenBundle>();
        oidc.refreshHandler = () {
          started.complete();
          return pendingRefresh.future;
        };
        final pending = container
            .read(userProfileRepositoryProvider)
            .updateProfile(update);
        final denied = expectLater(pending, throwsA(isA<AppFailure>()));
        await started.future;
        if (transition == 'sign-out') {
          await auth.clearLocalSession();
          container
              .read(workspaceInvalidationProvider.notifier)
              .invalidate(
                integration: WorkspaceIntegration.weaveBackend,
                reason: IntegrationInvalidationReason.explicitSignOut,
              );
        } else {
          configuration = buildTestConfiguration(
            issuerUrl: 'https://new-issuer.test',
            clientId: 'new-client',
            backendApiBaseUrl: 'https://new-api.test/api',
          );
          container.invalidate(savedServerConfigurationProvider);
          await container.read(savedServerConfigurationProvider.future);
        }
        pendingRefresh.complete(refreshed());
        await denied;
        expect(requests, 0);
      });
    }

    for (final code in [401, 403]) {
      test('profile HTTP $code is denied without blind PATCH replay', () async {
        await container.read(weaveAuthenticatedSessionProvider.future);
        responseStatus = code;
        await expectLater(
          container.read(userProfileRepositoryProvider).updateProfile(update),
          throwsA(
            isA<AppFailure>().having(
              (failure) => failure.cause,
              'HTTP status',
              code,
            ),
          ),
        );
        expect(requests, 1);
        expect(oidc.refreshCalls, 0);
      });
    }
  });

  group('BackendUserProfileRepository', () {
    test('saves profile edits through PATCH /api/profile', () async {
      late http.BaseRequest capturedRequest;
      final repository = BackendUserProfileRepository(
        client: BackendProfileClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'userId': 'user-123',
              'username': 'alice',
              'email': 'alice@example.test',
              'emailVerified': true,
              'displayName': 'Alice Updated',
              'locale': 'de',
              'timezone': 'Europe/Berlin',
              'roles': ['member'],
              'groups': ['workspace-default'],
            });
          }),
        ),
        sessionResolver: () async => WeaveAuthenticatedSession(
          apiBaseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        ),
      );

      final profile = await repository.updateProfile(
        const UserProfileUpdate(
          displayName: 'Alice Updated',
          locale: 'de',
          timezone: 'Europe/Berlin',
        ),
      );

      expect(capturedRequest.method, 'PATCH');
      expect(
        capturedRequest.url.toString(),
        'https://api.weave.test/api/profile',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      final requestBody = jsonDecode((capturedRequest as http.Request).body);
      expect(requestBody, {
        'displayName': 'Alice Updated',
        'locale': 'de',
        'timezone': 'Europe/Berlin',
      });
      expect(requestBody, isNot(contains('avatar')));
      expect(requestBody, isNot(contains('profileVisibility')));
      expect(profile.displayName, 'Alice Updated');
      expect(profile.locale, 'de');
    });
  });
}
