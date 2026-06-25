import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
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
        jsonEncode({
          'publicBaseUrl': 'https://weave.test:44443',
          'apiBaseUrl': 'https://api.weave.test:44443/api',
          'authBaseUrl': 'https://auth.weave.test:44443',
          'oidcIssuerUrl': 'https://auth.weave.test:44443/realms/weave',
          'oidcClientId': 'weave-app',
          'matrixHomeserverUrl': 'https://matrix.weave.test:44443',
          'filesProductUrl': 'https://weave.test:44443/files',
        }),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await ConsumeMemberHandoff(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      evidenceStore: evidenceStore,
    ).call(
      Uri.parse(
        'https://weave.test:44443/join?handoff_ref=invite-abc123&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-check',
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
    expect(
      saved.serviceEndpoints.nextcloudBaseUrl.toString(),
      'https://weave.test:44443/files',
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
          jsonEncode({
            'publicBaseUrl': 'https://weave.test:44443',
            'apiBaseUrl': 'https://api.weave.test:44443/api',
            'authBaseUrl': 'https://auth.weave.test:44443',
            'oidcIssuerUrl': 'https://auth.weave.test:44443/realms/weave',
            'oidcClientId': 'weave-app',
            'matrixHomeserverUrl': 'https://matrix.weave.test:44443',
            'filesProductUrl': 'https://weave.test:44443/files',
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      await ConsumeMemberHandoff(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
        evidenceStore: evidenceStore,
      ).call(
        Uri.parse(
          'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
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
        ConsumeMemberHandoff(
          repository: repository,
          discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
          evidenceStore: evidenceStore,
        ).call(
          Uri.parse(
            'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
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
        jsonEncode({
          'publicBaseUrl': 'https://join.weave.example',
          'apiBaseUrl': 'https://api.weave.example/api',
          'authBaseUrl': 'https://auth.weave.example',
          'oidcIssuerUrl': 'https://auth.weave.example/realms/weave',
          'oidcClientId': 'weave-app',
          'matrixHomeserverUrl': 'https://matrix.weave.example',
          'filesProductUrl': 'https://weave.weave.example/files',
          'nextcloudBaseUrl': 'http://weave-nextcloud',
        }),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await ConsumeMemberHandoff(
      repository: repository,
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    ).call(
      Uri.parse(
        'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&profile=production&run_id=prod-001',
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
    expect(
      saved.serviceEndpoints.nextcloudBaseUrl.toString(),
      'https://weave.weave.example/files',
    );
  });

  test('rejects app-start discovery URLs with embedded credentials', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'apiBaseUrl': 'https://api.weave.example/api',
          'authBaseUrl': 'https://auth.weave.example',
          'oidcIssuerUrl': 'https://user:pass@auth.weave.example/realms/weave',
          'matrixHomeserverUrl': 'https://matrix.weave.example',
          'filesProductUrl': 'https://files.weave.example',
        }),
        200,
      );
    });

    await expectLater(
      ConsumeMemberHandoff(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      ).call(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&profile=production&run_id=prod-001',
        ),
      ),
      throwsA(isA<AppFailure>()),
    );
    expect(repository.saved, isNull);
  });

  test('rejects credential-bearing legacy authBaseUrl fallback', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'apiBaseUrl': 'https://api.weave.example/api',
          'authBaseUrl': 'https://user:pass@auth.weave.example',
          'matrixHomeserverUrl': 'https://matrix.weave.example',
          'nextcloudBaseUrl': 'https://files.weave.example',
        }),
        200,
      );
    });

    await expectLater(
      ConsumeMemberHandoff(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      ).call(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&profile=production&run_id=prod-001',
        ),
      ),
      throwsA(isA<AppFailure>()),
    );
    expect(repository.saved, isNull);
  });

  test(
    'falls back from authBaseUrl for older platform config responses',
    () async {
      final repository = _RecordingServerConfigurationRepository();
      final httpClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'apiBaseUrl': 'https://api.weave.example/api',
            'authBaseUrl': 'https://auth.weave.example',
            'matrixHomeserverUrl': 'https://matrix.weave.example',
            'nextcloudBaseUrl': 'https://files.weave.example',
          }),
          200,
        );
      });

      await ConsumeMemberHandoff(
        repository: repository,
        discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      ).call(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&profile=production&run_id=prod-001',
        ),
      );

      expect(
        repository.saved!.oidcIssuerUrl.toString(),
        'https://auth.weave.example/realms/weave',
      );
      expect(repository.saved!.oidcClientRegistration.clientId, 'weave-app');
    },
  );
}
