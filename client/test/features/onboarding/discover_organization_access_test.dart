import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

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

class _RecordingPreferencesStore implements PreferencesStore {
  final strings = <String, String>{};

  @override
  Future<bool?> getBool(String key) async => null;

  @override
  Future<String?> getString(String key) async => strings[key];

  @override
  Future<void> remove(String key) async {
    strings.remove(key);
  }

  @override
  Future<void> setBool(String key, bool value) async {}

  @override
  Future<void> setString(String key, String value) async {
    strings[key] = value;
  }
}

void main() {
  test(
    'manual server URI discovers without synthetic invitation metadata',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final evidenceStore = _RecordingPreferencesStore();
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
              controlPlaneBaseUrl: 'https://api.weave.example/api',
              issuer: 'https://auth.weave.example/realms/weave',
              matrixClientServerBaseUrl: 'https://api.weave.example',
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
            evidenceStore: evidenceStore,
          ).call(
            Uri.parse(
              '/join?organization_origin=https%3A%2F%2Fweave.example%2F',
            ),
          );

      expect(access.organizationOrigin, Uri.parse('https://weave.example/'));
      expect(access.handoff, isNull);
      expect(repository.saved, isNotNull);

      final evidence =
          jsonDecode(evidenceStore.strings[lastHandoffConsumedStorageKey]!)
              as Map<String, dynamic>;
      expect(evidence['accessKind'], 'server_uri');
      expect(evidence['organizationOriginHost'], 'weave.example');
      expect(evidence, isNot(contains('handoffRef')));
      expect(evidence, isNot(contains('organizationSlug')));

      final authState =
          jsonDecode(evidenceStore.strings[dogfoodAuthStateStorageKey]!)
              as Map<String, dynamic>;
      expect(authState['state'], 'ready_for_sso');
      expect(authState['organizationOriginHost'], 'weave.example');
      expect(authState, isNot(contains('handoffRef')));
    },
  );

  test('saves DNS-first weave.test app-start configuration', () async {
    final repository = _RecordingServerConfigurationRepository();
    final evidenceStore = _RecordingPreferencesStore();
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
            controlPlaneBaseUrl: 'https://api.weave.test:44443/api',
            issuer: 'https://auth.weave.test:44443/realms/weave',
            matrixClientServerBaseUrl: 'https://api.weave.test:44443/',
          ),
        ),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await DiscoverOrganizationAccess(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      evidenceStore: evidenceStore,
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
      'https://api.weave.test:44443',
    );
    expect(
      saved.serviceEndpoints.nextcloudBaseUrl.toString(),
      'https://api.weave.test:44443/api/dav/files',
    );
    final evidence =
        jsonDecode(evidenceStore.strings[lastHandoffConsumedStorageKey]!)
            as Map<String, dynamic>;
    expect(evidence['schemaVersion'], 'weave.client.last_handoff_consumed.v1');
    expect(evidence['handoffRef'], 'invite-abc123');
    expect(evidence['organizationSlug'], 'massimo-dogfood');
    expect(evidence['workspaceSlug'], 'home');
    expect(evidence['platformConfigHost'], 'weave.test');
    expect(evidence['platformConfigPath'], '/api/platform/config');
    expect(evidence['result'], 'saved_configuration');
    expect(evidence['supportSafe'], isTrue);

    final authState =
        jsonDecode(evidenceStore.strings[dogfoodAuthStateStorageKey]!)
            as Map<String, dynamic>;
    expect(authState['schemaVersion'], 'weave.client.dogfood_auth_state.v1');
    expect(authState['state'], 'ready_for_sso');
    expect(authState['handoffRef'], 'invite-abc123');
    expect(authState['organizationSlug'], 'massimo-dogfood');
    expect(authState['workspaceSlug'], 'home');
    expect(authState['supportSafe'], isTrue);
  });

  test(
    'retries app-start discovery on product origin after api DNS failure',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final evidenceStore = _RecordingPreferencesStore();
      final requests = <Uri>[];
      final httpClient = MockClient((request) async {
        requests.add(request.url);
        if (request.url.host == 'api.weave.test') {
          throw http.ClientException(
            'Failed host lookup: api.weave.test',
            request.url,
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
              controlPlaneBaseUrl: 'https://api.weave.test:44443/api',
              issuer: 'https://auth.weave.test:44443/realms/weave',
              matrixClientServerBaseUrl: 'https://api.weave.test:44443',
            ),
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      await DiscoverOrganizationAccess(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
        evidenceStore: evidenceStore,
      ).call(
        Uri.parse(
          'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
        ),
      );

      expect(requests.map((uri) => uri.toString()), [
        'https://api.weave.test:44443/api/platform/config',
        'https://weave.test:44443/api/platform/config',
      ]);
      expect(repository.saved, isNotNull);
      final evidence =
          jsonDecode(evidenceStore.strings[lastHandoffConsumedStorageKey]!)
              as Map<String, dynamic>;
      expect(evidence['result'], 'saved_configuration');
      expect(evidence['handoffRef'], 'handoff-s32-massimo-dogfood-home');
    },
  );

  test(
    'records exact failure marker when app-start discovery is unreachable',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final evidenceStore = _RecordingPreferencesStore();
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
          evidenceStore: evidenceStore,
        ).call(
          Uri.parse(
            'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
          ),
        ),
        throwsA(isA<AppFailure>()),
      );

      expect(repository.saved, isNull);
      final evidence =
          jsonDecode(evidenceStore.strings[lastHandoffConsumedStorageKey]!)
              as Map<String, dynamic>;
      expect(evidence['result'], 'failed');
      expect(evidence['phase'], 'app_start_discovery');
      expect(evidence['errorCode'], 'WEAVE-APP-START-DNS-FAILED');
      expect(evidence['handoffRef'], 'handoff-s32-massimo-dogfood-home');
      expect(evidence['supportSafe'], isTrue);

      final authState =
          jsonDecode(evidenceStore.strings[dogfoodAuthStateStorageKey]!)
              as Map<String, dynamic>;
      expect(authState['state'], 'recoverable_error');
      expect(authState['errorCode'], 'WEAVE-APP-START-DNS-FAILED');
      expect(authState['supportSafe'], isTrue);
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
            controlPlaneBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://auth.weave.example/realms/weave',
            matrixClientServerBaseUrl: 'https://api.weave.example',
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
      'https://api.weave.example',
    );
    expect(
      saved.serviceEndpoints.nextcloudBaseUrl.toString(),
      'https://api.weave.example/api/dav/files',
    );
  });

  test('rejects a raw Matrix provider advertised as the member facade', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://join.weave.example',
            controlPlaneBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://auth.weave.example/realms/weave',
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
      throwsA(
        isA<AppFailure>().having(
          (failure) => failure.message,
          'message',
          contains('matrixClientServerBaseUrl must be the Weave API origin'),
        ),
      ),
    );
    expect(repository.saved, isNull);
  });

  test('rejects app-start discovery URLs with embedded credentials', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode(
          _manifest(
            organizationOrigin: 'https://join.weave.example',
            controlPlaneBaseUrl: 'https://api.weave.example/api',
            issuer: 'https://user:pass@auth.weave.example/realms/weave',
            matrixClientServerBaseUrl: 'https://api.weave.example',
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
  required String controlPlaneBaseUrl,
  required String issuer,
  required String matrixClientServerBaseUrl,
}) => <String, Object>{
  'schemaVersion': 1,
  'organizationOrigin': organizationOrigin,
  'controlPlaneBaseUrl': controlPlaneBaseUrl,
  'oidc': <String, Object>{'issuer': issuer, 'clientId': 'weave-app'},
  'protocols': <String, Object>{
    'matrixClientServerBaseUrl': matrixClientServerBaseUrl,
    'filesWebDavBaseUrl': '$controlPlaneBaseUrl/dav/files',
    'calendarCalDavBaseUrl': '$controlPlaneBaseUrl/caldav',
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
