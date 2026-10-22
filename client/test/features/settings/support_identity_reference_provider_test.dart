import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/features/settings/presentation/providers/support_identity_reference_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_client_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/server_config_test_data.dart';
import '../../helpers/auth_test_data.dart';

class _ReadyBootstrap extends AppBootstrap {
  @override
  Future<BootstrapState> build() async => const BootstrapState.ready();
}

class _Auth implements AuthSessionRepository {
  _Auth(this.current);
  final AuthState Function() current;
  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      current();
  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      current();
  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => current();
  @override
  Future<void> clearLocalSession() async {}
  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

void main() {
  Map<String, Object?> identity({
    String account = 'private-account',
    String organization = 'private-org',
    String issuer = 'https://auth.home.internal',
  }) => {
    'accountId': account,
    'organizationId': organization,
    'identityIssuer': issuer,
  };
  for (final invalid in [
    'valid',
    'missing-account',
    'missing-org',
    'wrong-issuer',
    'http-error',
  ]) {
    test(
      'server identity references are support-safe and fail closed: $invalid',
      () async {
        var token = 'old-private-token';
        final payload = identity();
        if (invalid == 'missing-account') payload.remove('accountId');
        if (invalid == 'missing-org') payload.remove('organizationId');
        if (invalid == 'wrong-issuer') {
          payload['identityIssuer'] = 'https://other.test';
        }
        final container = ProviderContainer.test(
          overrides: [
            appBootstrapProvider.overrideWith(_ReadyBootstrap.new),
            authSessionRepositoryProvider.overrideWithValue(
              _Auth(
                () => AuthState.authenticated(
                  buildTestAuthSession(accessToken: token),
                ),
              ),
            ),
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(
                backendApiBaseUrl: 'https://api.example.test/api',
              ),
            ),
            weaveAuthenticatedSessionProvider.overrideWith(
              (ref) async => WeaveAuthenticatedSession(
                apiBaseUrl: Uri.parse('https://api.example.test/api'),
                accessToken: token,
              ),
            ),
            weaveApiHttpClientProvider.overrideWithValue(
              MockClient((request) async {
                expect(request.url.path, '/api/me');
                expect(request.headers['authorization'], 'Bearer $token');
                return http.Response(
                  jsonEncode(payload),
                  invalid == 'http-error' ? 503 : 200,
                  headers: {'content-type': 'application/json'},
                );
              }),
            ),
          ],
        );
        addTearDown(container.dispose);
        final first = await container.read(
          supportIdentityReferenceProvider.future,
        );
        if (invalid != 'valid') {
          expect(first, isNull);
        } else {
          expect(first!.account, matches(RegExp(r'^sha256:[0-9a-f]{64}$')));
          expect(first.organization, matches(RegExp(r'^sha256:[0-9a-f]{64}$')));
          expect(first.account, isNot(first.organization));
          expect(
            '${first.account} ${first.organization}',
            isNot(contains('private')),
          );
          token = 'refreshed-private-token';
          container.invalidate(weaveAuthenticatedSessionProvider);
          final after = await container.read(
            supportIdentityReferenceProvider.future,
          );
          expect(after!.account, first.account);
          expect(after.organization, first.organization);
        }
      },
    );
  }

  for (final boundary in ['sign-out', 'account-switch']) {
    test(
      'late identity response cannot publish references after $boundary',
      () async {
        final gate = Completer<http.Response>();
        final sent = Completer<void>();
        var signedIn = true;
        var token = 'private-token';
        final container = ProviderContainer.test(
          overrides: [
            appBootstrapProvider.overrideWith(_ReadyBootstrap.new),
            authSessionRepositoryProvider.overrideWithValue(
              _Auth(
                () => signedIn
                    ? AuthState.authenticated(
                        buildTestAuthSession(accessToken: token),
                      )
                    : const AuthState.signedOut(),
              ),
            ),
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(
                backendApiBaseUrl: 'https://api.example.test/api',
              ),
            ),
            weaveAuthenticatedSessionProvider.overrideWith(
              (ref) async => signedIn
                  ? WeaveAuthenticatedSession(
                      apiBaseUrl: Uri.parse('https://api.example.test/api'),
                      accessToken: 'private-token',
                    )
                  : null,
            ),
            weaveApiHttpClientProvider.overrideWithValue(
              MockClient((_) {
                sent.complete();
                return gate.future;
              }),
            ),
          ],
        );
        addTearDown(container.dispose);
        final future = container.read(supportIdentityReferenceProvider.future);
        await sent.future;
        if (boundary == 'sign-out') {
          signedIn = false;
        } else {
          token = 'other-member-token';
        }
        // Deliberately leave the cached lifecycle snapshot unchanged.
        gate.complete(
          http.Response(
            jsonEncode(identity()),
            200,
            headers: {'content-type': 'application/json'},
          ),
        );
        final latest = container.read(supportIdentityReferenceProvider.future);
        expect(await latest, isNull);
        expect(await future, isNull);
      },
    );
  }
}
