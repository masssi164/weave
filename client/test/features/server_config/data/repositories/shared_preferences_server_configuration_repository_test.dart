import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import '../../../../helpers/in_memory_stores.dart';
import '../../../../helpers/server_config_test_data.dart';

void main() {
  SharedPreferencesServerConfigurationRepository repository(
    InMemoryPreferencesStore store,
  ) {
    final container = ProviderContainer.test(
      overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
    );
    addTearDown(container.dispose);
    return container.read(serverConfigurationRepositoryProvider)
        as SharedPreferencesServerConfigurationRepository;
  }

  Map<String, dynamic> legacy() => {
    ...jsonDecode(encodeTestConfiguration()) as Map<String, dynamic>,
    'organizationOrigin': 'https://weave.home.internal',
  };
  test('saves only canonical manifest and derives transport on load', () async {
    final store = InMemoryPreferencesStore();
    final repo = repository(store);
    final configuration = buildTestConfiguration();
    await repo.saveConfiguration(configuration);
    final raw =
        jsonDecode((await store.getString(serverConfigurationStorageKey))!)
            as Map<String, dynamic>;
    expect(raw.keys.toSet(), {
      'schemaVersion',
      'organizationOrigin',
      'validatedManifest',
      'manifestValidatedAt',
    });
    expect(raw['schemaVersion'], 3);
    final loaded = (await repo.loadConfiguration())!;
    expect(loaded.organizationOrigin, configuration.organizationOrigin);
    expect(loaded.validatedManifest, configuration.validatedManifest);
    expect(loaded.manifestValidatedAt, configuration.manifestValidatedAt);
    expect(loaded.oidcIssuerUrl, configuration.oidcIssuerUrl);
    expect(loaded.oidcClientRegistration.clientId, 'weave-app');
    expect(
      loaded.serviceEndpoints.backendApiBaseUrl,
      configuration.serviceEndpoints.backendApiBaseUrl,
    );
    expect(
      loaded.serviceEndpoints.matrixHomeserverUrl,
      configuration.serviceEndpoints.matrixHomeserverUrl,
    );
    expect(loaded.hasMatrixOAuthConfiguration, isFalse);
    expect(jsonEncode(raw), isNot(contains('accessToken')));
    expect(jsonEncode(raw), isNot(contains('refreshToken')));
  });
  test(
    'derived overrides cannot become a second persisted authority',
    () async {
      final store = InMemoryPreferencesStore();
      final repo = repository(store);
      final configuration = buildTestConfiguration();
      await repo.saveConfiguration(
        configuration.copyWith(
          oidcIssuerUrl: Uri.parse('https://other.example'),
          serviceEndpoints: configuration.serviceEndpoints.copyWith(
            backendApiBaseUrl: Uri.parse('https://other.example/api'),
          ),
          matrixOAuthIssuer: Uri.parse('https://other.example'),
          matrixOAuthClientId: 'separate-client',
        ),
      );
      final loaded = (await repo.loadConfiguration())!;
      expect(loaded.oidcIssuerUrl, configuration.oidcIssuerUrl);
      expect(
        loaded.serviceEndpoints.backendApiBaseUrl,
        configuration.serviceEndpoints.backendApiBaseUrl,
      );
      expect(loaded.hasMatrixOAuthConfiguration, isFalse);
    },
  );
  for (final version in [1, 2]) {
    test(
      'v$version origin profile is migration input before fresh bootstrap',
      () async {
        final raw = legacy()..['schemaVersion'] = version;
        raw['matrixHomeserverUrl'] = 'https://provider.example/private';
        raw['matrixOAuthIssuer'] = 'https://separate.example';
        raw['matrixOAuthClientId'] = 'separate-client';
        final store = InMemoryPreferencesStore({
          serverConfigurationStorageKey: jsonEncode(raw),
        });
        final loaded = (await repository(store).loadConfiguration())!;
        expect(
          loaded.organizationOrigin,
          Uri.parse('https://weave.home.internal'),
        );
        expect(loaded.validatedManifest, isNull);
        expect(loaded.oidcIssuerUrl, Uri.parse('https://auth.home.internal'));
        expect(loaded.hasMatrixOAuthConfiguration, isFalse);
        await expectLater(
          repository(store).saveConfiguration(loaded),
          throwsA(isA<AppFailure>()),
        );
        expect(
          await store.getString(serverConfigurationStorageKey),
          jsonEncode(raw),
        );
      },
    );
    test(
      'v$version provider-only profile needs discovery and remains preserved',
      () async {
        final raw = encodeTestConfiguration(schemaVersion: version);
        final store = InMemoryPreferencesStore({
          serverConfigurationStorageKey: raw,
        });
        expect(await repository(store).loadConfiguration(), isNull);
        expect(await store.getString(serverConfigurationStorageKey), raw);
      },
    );
  }
  test(
    'v2 cached manifest migrates without obsolete endpoint overrides',
    () async {
      final configuration = buildTestConfiguration();
      final raw = legacy()
        ..['validatedManifest'] = configuration.validatedManifest
        ..['manifestValidatedAt'] = configuration.manifestValidatedAt!
            .toIso8601String()
        ..['nextcloudBaseUrl'] = 'https://provider.example/dav'
        ..['matrixOAuthIssuer'] = 'https://separate.example'
        ..['matrixOAuthClientId'] = 'separate-client'
        ..['oidcIssuerUrl'] = 'https://obsolete.example';
      final store = InMemoryPreferencesStore({
        serverConfigurationStorageKey: jsonEncode(raw),
      });
      final repo = repository(store);
      final loaded = (await repo.loadConfiguration())!;
      expect(loaded.oidcIssuerUrl, configuration.oidcIssuerUrl);
      await repo.saveConfiguration(loaded);
      final saved = (await store.getString(serverConfigurationStorageKey))!;
      expect(saved, isNot(contains('nextcloudBaseUrl')));
      expect(saved, isNot(contains('matrixOAuth')));
      expect(saved, isNot(contains('obsolete.example')));
    },
  );
  for (final invalid in [
    'origin',
    'issuer',
    'client',
    'matrix',
    'unknown',
    'timestamp',
    'manifest',
  ]) {
    test(
      'persisted $invalid failure cannot bypass strict manifest validation',
      () async {
        final store = InMemoryPreferencesStore();
        final repo = repository(store);
        await repo.saveConfiguration(buildTestConfiguration());
        final raw =
            jsonDecode((await store.getString(serverConfigurationStorageKey))!)
                as Map<String, dynamic>;
        switch (invalid) {
          case 'origin':
            raw['organizationOrigin'] = 'https://other.example';
          case 'issuer':
            raw['validatedManifest']['oidc']['issuer'] = 'http://auth.example';
          case 'client':
            raw['validatedManifest']['oidc']['clientId'] = '';
          case 'matrix':
            raw['validatedManifest']['protocols']['matrixClientServerBaseUrl'] =
                'https://provider.example/_matrix/client';
          case 'unknown':
            raw['validatedManifest']['providerUrls'] = [];
          case 'timestamp':
            raw['manifestValidatedAt'] = null;
          case 'manifest':
            raw.remove('validatedManifest');
        }
        await store.setString(serverConfigurationStorageKey, jsonEncode(raw));
        await expectLater(repo.loadConfiguration(), throwsA(isA<AppFailure>()));
      },
    );
  }
  for (final field in ['oidcIssuerUrl', 'oidcClientId', 'backendApiBaseUrl']) {
    test('legacy missing $field fails closed before migration', () async {
      final raw = legacy()..remove(field);
      final repo = repository(
        InMemoryPreferencesStore({
          serverConfigurationStorageKey: jsonEncode(raw),
        }),
      );
      await expectLater(repo.loadConfiguration(), throwsA(isA<AppFailure>()));
    });
  }
  test('clears existing storage key', () async {
    final store = InMemoryPreferencesStore(buildStoredConfiguration());
    await repository(store).clearConfiguration();
    expect(await store.getString(serverConfigurationStorageKey), isNull);
  });
}
