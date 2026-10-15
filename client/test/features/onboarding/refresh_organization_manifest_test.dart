import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/onboarding/domain/use_cases/refresh_organization_manifest.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../helpers/server_config_test_data.dart';

class _Repository implements ServerConfigurationRepository {
  ServerConfiguration? saved;
  @override
  Future<ServerConfiguration?> loadConfiguration() async => saved;
  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async =>
      saved = configuration;
  @override
  Future<void> clearConfiguration() async => saved = null;
}

void main() {
  final now = DateTime.utc(2026, 10, 12);
  Map<String, dynamic> manifest() {
    final json =
        jsonDecode(
              File(
                'test/fixtures/org_manifest_v2_valid.json',
              ).readAsStringSync(),
            )
            as Map<String, dynamic>;
    json['organizationOrigin'] = 'https://weave.example';
    return json;
  }

  ServerConfiguration saved({Duration age = const Duration(hours: 1)}) =>
      buildTestConfiguration(
        issuerUrl: 'https://auth.weave.local/realms/weave',
      ).copyWith(
        organizationOrigin: Uri.parse('https://weave.example'),
        validatedManifest: manifest(),
        manifestValidatedAt: now.subtract(age),
      );

  RefreshOrganizationManifest refresh(
    http.Client client,
    _Repository repository,
  ) => RefreshOrganizationManifest(
    discoveryClient: AppStartDiscoveryClient(httpClient: client),
    repository: repository,
    now: () => now,
  );

  test(
    'refresh uses sole generated config operation and updates derived URLs, preserves domain-local states',
    () async {
      final repository = _Repository();
      final response = manifest();
      (response['domains'] as List)[1]['state'] = 'unavailable';
      final result = await refresh(
        MockClient((request) async {
          expect(request.url.path, '/api/platform/config');
          expect(request.headers['Authorization'], isNull);
          return http.Response(jsonEncode(response), 200);
        }),
        repository,
      )(saved());
      expect(result.organizationOrigin, Uri.parse('https://weave.example'));
      expect(
        result.serviceEndpoints.backendApiBaseUrl,
        Uri.parse('https://api.weave.local/api'),
      );
      expect(
        result.serviceEndpoints.matrixHomeserverUrl,
        Uri.parse('https://api.weave.local'),
      );
      expect(result.validatedManifest!['domains'][1]['state'], 'unavailable');
      expect(repository.saved, same(result));
      expect(result.manifestValidatedAt, now);
      expect(
        result.oidcIssuerUrl,
        Uri.parse(response['oidc']['issuer'] as String),
      );
      expect(
        result.oidcClientRegistration.clientId,
        response['oidc']['clientId'],
      );
    },
  );

  for (final change in ['issuer', 'client', 'organization', 'unknown']) {
    test(
      'fails closed on fresh $change mismatch without overwriting validated profile',
      () async {
        final response = manifest();
        switch (change) {
          case 'issuer':
            response['oidc']['issuer'] = 'https://other.example/realms/weave';
          case 'client':
            response['oidc']['clientId'] = 'other';
          case 'organization':
            response['organizationOrigin'] = 'https://other.example';
          case 'unknown':
            response['providerUrls'] = [];
        }
        final repository = _Repository();
        await expectLater(
          refresh(
            MockClient((_) async => http.Response(jsonEncode(response), 200)),
            repository,
          )(saved()),
          throwsA(isA<AppFailure>()),
        );
        expect(repository.saved, isNull);
      },
    );
  }
  test(
    'bounded network continuity revalidates cached manifest and derives URLs without extending lifetime',
    () async {
      final repository = _Repository();
      final result = await refresh(
        MockClient((_) async => throw TimeoutException('offline')),
        repository,
      )(saved());
      expect(
        result.serviceEndpoints.backendApiBaseUrl,
        Uri.parse('https://api.weave.local/api'),
      );
      expect(
        result.manifestValidatedAt,
        now.subtract(const Duration(hours: 1)),
      );
      expect(repository.saved, same(result));
    },
  );
  for (final age in [const Duration(hours: 25), const Duration(hours: -1)]) {
    test('rejects expired or future cache age $age', () async {
      await expectLater(
        refresh(
          MockClient((_) async => throw TimeoutException('offline')),
          _Repository(),
        )(saved(age: age)),
        throwsA(isA<AppFailure>()),
      );
    });
  }
  test('cache cannot mask TLS or HTTP errors', () async {
    for (final client in [
      MockClient((_) async => throw const HandshakeException('bad TLS')),
      MockClient((_) async => http.Response('', 401)),
    ]) {
      await expectLater(
        refresh(client, _Repository())(saved()),
        throwsA(isA<AppFailure>()),
      );
    }
  });
  test('cached identity mismatch fails closed even during outage', () async {
    final profile = saved();
    profile.validatedManifest!['oidc']['clientId'] = 'changed';
    await expectLater(
      refresh(
        MockClient((_) async => throw TimeoutException('offline')),
        _Repository(),
      )(profile),
      throwsA(isA<AppFailure>()),
    );
  });
}
