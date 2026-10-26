import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../helpers/server_config_test_data.dart';

class _RecordingServerConfigurationRepository
    implements ServerConfigurationRepository {
  ServerConfiguration? saved;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => saved;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    saved = configuration;
  }
}

void main() {
  for (final hint in ['platform_config_url', 'discovery_url']) {
    test('$hint cannot change the canonical bootstrap request', () async {
      final requests = <Uri>[];
      final client = AppStartDiscoveryClient(
        httpClient: MockClient((request) async {
          requests.add(request.url);
          expect(
            request.url,
            Uri.parse('https://weave.test:44443/api/platform/config'),
          );
          expect(request.followRedirects, isFalse);
          return http.Response(
            jsonEncode(
              _manifest(
                organizationOrigin: 'https://weave.test:44443',
                userApiBaseUrl: 'https://api.weave.test:44443/api',
                issuer: 'https://auth.weave.test:44443/realms/weave',
                matrixClientServerBaseUrl: 'https://matrix.weave.test:44443',
              ),
            ),
            200,
          );
        }),
      );
      final link = Uri(
        scheme: 'weave',
        host: 'join',
        queryParameters: {
          'handoff_ref': 'handoff-valid-123',
          'org': 'acme',
          'workspace': 'main',
          'organization_origin': 'https://weave.test:44443',
          hint: 'https://attacker.invalid/api/platform/config',
        },
      );
      final result = await client.fetch(
        const OrganizationAccessParser().parse(link),
      );
      expect(requests, [
        Uri.parse('https://weave.test:44443/api/platform/config'),
      ]);
      expect(result.userApiBaseUrl.host, 'api.weave.test');
    });
  }
  test(
    'canonical bootstrap refuses redirects rather than changing authority',
    () async {
      final requests = <Uri>[];
      final client = AppStartDiscoveryClient(
        httpClient: MockClient((request) async {
          requests.add(request.url);
          expect(request.followRedirects, isFalse);
          return http.Response(
            '',
            302,
            headers: {
              'location': 'https://attacker.invalid/api/platform/config',
            },
          );
        }),
      );
      await expectLater(
        client.fetch(
          const OrganizationAccessParser().parse(
            Uri.parse('https://weave.test:44443'),
          ),
        ),
        throwsA(isA<AppFailure>()),
      );
      expect(requests, [
        Uri.parse('https://weave.test:44443/api/platform/config'),
      ]);
    },
  );

  test(
    'manual server URI discovers without synthetic invitation metadata',
    () async {
      // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT: sole generated v2 bootstrap contract.
      final repository = _RecordingServerConfigurationRepository();
      final httpClient = MockClient((request) async {
        expect(
          request.url.toString(),
          'https://weave.example/api/platform/config',
        );
        expect(request.headers, isNot(contains('X-Weave-Handoff-Ref')));
        expect(request.headers, isNot(contains('X-Weave-Handoff-Run-Id')));
        return http.Response(
          jsonEncode(
            _manifest(
              organizationOrigin: 'https://weave.example',
              userApiBaseUrl: 'https://api.weave.example/api',
              issuer: 'https://auth.weave.example/realms/weave',
              matrixClientServerBaseUrl: 'https://matrix.weave.example',
            ),
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      final access =
          await DiscoverOrganizationAccess(
            repository: repository,
            discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
          ).call(
            Uri.parse(
              '/join?organization_origin=https%3A%2F%2Fweave.example%2F',
            ),
          );

      expect(access.organizationOrigin, Uri.parse('https://weave.example/'));
      expect(access.handoff, isNull);
      expect(repository.saved, isNotNull);

    },
  );

  test('saves DNS-first weave.test app-start configuration', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      expect(
        request.url.toString(),
        'https://weave.test:44443/api/platform/config',
      );
      expect(request.headers['X-Weave-Handoff-Ref'], 'invite-abc123');
      expect(request.headers['X-Weave-Handoff-Run-Id'], 's32-check');
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://weave.test:44443',
            userApiBaseUrl: 'https://api.weave.test:44443/api',
            issuer: 'https://auth.weave.test:44443/realms/weave',
            matrixClientServerBaseUrl: 'https://matrix.weave.test:44443/',
          ),
        ),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await DiscoverOrganizationAccess(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    ).call(
      Uri.parse(
        'https://weave.test:44443/join?handoff_ref=invite-abc123&org=massimo-dogfood&workspace=home&run_id=s32-check',
      ),
    );

    final saved = repository.saved;
    expect(saved, isNotNull);
    expect(
      saved!.oidcIssuerUrl.toString(),
      'https://auth.weave.test:44443/realms/weave',
    );
    expect(
      saved.serviceEndpoints.backendApiBaseUrl.toString(),
      'https://api.weave.test:44443/api',
    );
    expect(
      saved.serviceEndpoints.matrixHomeserverUrl.toString(),
      'https://matrix.weave.test:44443',
    );
  });

  test(
    'legacy alternate endpoint cannot substitute a matching-origin manifest',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final requests = <Uri>[];
      final httpClient = MockClient((request) async {
        requests.add(request.url);
        if (request.url.host == 'api.weave.test') {
          return http.Response(
            jsonEncode(
              _manifest(
                organizationOrigin: 'https://weave.test:44443',
                userApiBaseUrl: 'https://attacker.invalid/api',
                issuer: 'https://attacker.invalid/oidc',
                matrixClientServerBaseUrl: 'https://attacker.invalid',
              ),
            ),
            200,
          );
        }
        expect(
          request.url.toString(),
          'https://weave.test:44443/api/platform/config',
        );
        return http.Response(
          jsonEncode(
            _manifest(
              organizationOrigin: 'https://weave.test:44443',
              userApiBaseUrl: 'https://api.weave.test:44443/api',
              issuer: 'https://auth.weave.test:44443/realms/weave',
              matrixClientServerBaseUrl: 'https://matrix.weave.test:44443',
            ),
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      await DiscoverOrganizationAccess(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
        ).call(
        Uri.parse(
          'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
        ),
      );

      expect(requests.map((uri) => uri.toString()), [
        'https://weave.test:44443/api/platform/config',
      ]);
      expect(repository.saved, isNotNull);
    },
  );

  test(
    'surfaces a stable error when app-start discovery is unreachable',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final httpClient = MockClient((request) async {
        throw http.ClientException(
          'Failed host lookup: ${request.url.host}',
          request.url,
        );
      });

      await expectLater(
        DiscoverOrganizationAccess(
          repository: repository,
          discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
            ).call(
          Uri.parse(
            'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
          ),
        ),
        throwsA(
          isA<AppFailure>().having(
            (failure) => failure.message,
            'message',
            contains('WEAVE-APP-START-DNS-FAILED'),
          ),
        ),
      );

      expect(repository.saved, isNull);
    },
  );

  test('saves app-start configuration from public platform config', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      expect(
        request.url.toString(),
        'https://join.weave.example/api/platform/config',
      );
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://join.weave.example',
            userApiBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://auth.weave.example/realms/weave',
            matrixClientServerBaseUrl: 'https://matrix.weave.example',
          ),
        ),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await DiscoverOrganizationAccess(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    ).call(
      Uri.parse(
        'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&run_id=prod-001',
      ),
    );

    final saved = repository.saved;
    expect(saved, isNotNull);
    expect(
      saved!.oidcIssuerUrl.toString(),
      'https://auth.weave.example/realms/weave',
    );
    expect(saved.oidcClientRegistration.clientId, 'weave-app');
    expect(
      saved.serviceEndpoints.backendApiBaseUrl.toString(),
      'https://api.weave.example/api',
    );
    expect(
      saved.serviceEndpoints.matrixHomeserverUrl.toString(),
      'https://matrix.weave.example',
    );
  });

  test('accepts a Matrix homeserver separate from the User API', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://join.weave.example',
            userApiBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://auth.weave.example/realms/weave',
            matrixClientServerBaseUrl: 'https://matrix.weave.example',
          ),
        ),
        200,
      );
    });

    await DiscoverOrganizationAccess(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    ).call(
      Uri.parse(
        'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&run_id=prod-001',
      ),
    );
    expect(
      repository.saved?.serviceEndpoints.matrixHomeserverUrl.toString(),
      'https://matrix.weave.example',
    );
  });

  // Byte-for-byte copies of the examples in the pinned weave-specs corpus.
  test('accepts the pinned OrgManifest v2 valid example', () async {
    final fixture = await File(
      'test/fixtures/org_manifest_v2_valid.json',
    ).readAsString();
    final httpClient = MockClient((_) async => http.Response(fixture, 200));

    final config = await AppStartDiscoveryClient(httpClient: httpClient).fetch(
      OrganizationAccess(organizationOrigin: Uri.parse('https://weave.local/')),
    );

    expect(config.userApiBaseUrl.toString(), 'https://api.weave.local/api');
    expect(
      config.matrixClientServerBaseUrl.toString(),
      'https://api.weave.local',
    );
  });

  test('rejects a manifest without the Weave Matrix facade URL', () async {
    final manifest = _manifest(
      organizationOrigin: 'https://weave.example',
      userApiBaseUrl: 'https://api.weave.example/api',
      issuer: 'https://auth.weave.example/realms/weave',
      matrixClientServerBaseUrl: 'https://api.weave.example',
    );
    (manifest['protocols'] as Map<String, Object>).remove(
      'matrixClientServerBaseUrl',
    );
    final client = MockClient(
      (_) async => http.Response(jsonEncode(manifest), 200),
    );

    await expectLater(
      AppStartDiscoveryClient(httpClient: client).fetch(
        OrganizationAccess(
          organizationOrigin: Uri.parse('https://weave.example/'),
        ),
      ),
      throwsA(
        isA<AppFailure>().having(
          (failure) => failure.message,
          'message',
          contains('protocols.matrixClientServerBaseUrl is required'),
        ),
      ),
    );
  });

  test('rejects obsolete separate Matrix OAuth metadata', () async {
    final manifest = _manifest(
      organizationOrigin: 'https://weave.example',
      userApiBaseUrl: 'https://api.weave.example/api',
      issuer: 'https://auth.weave.example/realms/weave',
      matrixClientServerBaseUrl: 'https://api.weave.example',
    );
    (manifest['protocols'] as Map<String, Object>)['matrixOAuthClientId'] =
        'weave-matrix-app';
    final client = MockClient(
      (_) async => http.Response(jsonEncode(manifest), 200),
    );

    await expectLater(
      AppStartDiscoveryClient(httpClient: client).fetch(
        OrganizationAccess(
          organizationOrigin: Uri.parse('https://weave.example/'),
        ),
      ),
      throwsA(
        isA<AppFailure>().having(
          (failure) => failure.message,
          'message',
          contains('unsupported fields'),
        ),
      ),
    );
  });

  test('rejects the pinned OrgManifest v2 invalid DAV example', () async {
    final fixture = await File(
      'test/fixtures/org_manifest_v2_invalid.json',
    ).readAsString();
    final httpClient = MockClient((_) async => http.Response(fixture, 200));

    await expectLater(
      AppStartDiscoveryClient(httpClient: httpClient).fetch(
        OrganizationAccess(
          organizationOrigin: Uri.parse('https://weave.local/'),
        ),
      ),
      throwsA(
        isA<AppFailure>().having(
          (failure) => failure.message,
          'message',
          contains('unsupported fields'),
        ),
      ),
    );
  });

  test(
    'rejects a former schema version without replacing saved state',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final previous = buildTestConfiguration();
      repository.saved = previous;
      final manifest = _manifest(
        organizationOrigin: 'https://join.weave.example',
        userApiBaseUrl: 'https://api.weave.example/api',
        issuer: 'https://auth.weave.example/realms/weave',
        matrixClientServerBaseUrl: 'https://matrix.weave.example',
      )..['schemaVersion'] = 1;
      final httpClient = MockClient(
        (_) async => http.Response(jsonEncode(manifest), 200),
      );

      await expectLater(
        DiscoverOrganizationAccess(
          repository: repository,
          discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
        ).call(Uri.parse('https://join.weave.example')),
        throwsA(
          isA<AppFailure>().having(
            (failure) => failure.message,
            'message',
            contains('schemaVersion must be 2'),
          ),
        ),
      );
      expect(repository.saved, same(previous));
    },
  );

  test('rejects app-start discovery URLs with embedded credentials', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://join.weave.example',
            userApiBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://user:pass@auth.weave.example/realms/weave',
            matrixClientServerBaseUrl: 'https://matrix.weave.example',
          ),
        ),
        200,
      );
    });

    await expectLater(
      DiscoverOrganizationAccess(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      ).call(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&run_id=prod-001',
        ),
      ),
      throwsA(isA<AppFailure>()),
    );
    expect(repository.saved, isNull);
  });

  test('rejects obsolete provider-shaped platform configuration', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'apiBaseUrl': 'https://api.weave.example/api',
          'authBaseUrl': 'https://auth.weave.example',
          'oidcClientId': 'weave-app',
          'matrixHomeserverUrl': 'https://api.weave.example',
          'filesProductUrl': 'https://weave.example/files',
        }),
        200,
      );
    });

    await expectLater(
      DiscoverOrganizationAccess(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      ).call(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&run_id=prod-001',
        ),
      ),
      throwsA(
        isA<AppFailure>().having(
          (failure) => failure.message,
          'message',
          contains('unsupported fields'),
        ),
      ),
    );
    expect(repository.saved, isNull);
  });
}

Map<String, Object> _manifest({
  required String organizationOrigin,
  required String userApiBaseUrl,
  required String issuer,
  required String matrixClientServerBaseUrl,
}) => <String, Object>{
  'schemaVersion': 2,
  'organizationOrigin': organizationOrigin,
  'userApiBaseUrl': userApiBaseUrl,
  'oidc': <String, Object>{'issuer': issuer, 'clientId': 'weave-app'},
  'protocols': <String, Object>{
    'matrixClientServerBaseUrl': matrixClientServerBaseUrl,
  },
  'releasePosture': 'dogfood',
  'domains': <Map<String, Object>>[
    for (final domain in <String>[
      'identity',
      'chat',
      'files',
      'calendar',
      'boards',
      'health',
    ])
      <String, Object>{
        'domain': domain,
        'state': 'available',
        'capabilities': <String>[],
      },
  ],
  'recoveryActions': <Object>[],
};
