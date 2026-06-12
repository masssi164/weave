import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
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

void main() {
  test('saves DNS-first weave.test app-start configuration', () async {
    final repository = _RecordingServerConfigurationRepository();
    final httpClient = MockClient((request) async {
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
  });

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
