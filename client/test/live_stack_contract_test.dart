import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../integration_test/helpers/test_config.dart';

void main() {
  final config = TestConfig.fromEnvironment();

  group('open-standard client contract', () {
    test(
      'offline verification contains endpoints but no human credentials',
      () {
        expect(config.offlineContractOnly, isTrue);
        expect(config.clientId, isNotEmpty);
        expect(config.issuerUrl.path, contains('/realms/'));
      },
      skip: config.offlineContractOnly
          ? false
          : 'Requires WEAVE_OFFLINE_CONTRACT_ONLY=true.',
    );

    test('backend paths do not duplicate the API prefix', () {
      expect(config.apiUri('/api/me').path, '/api/me');
    });

    test('Flutter consumes Weave-owned open protocol facades', () {
      // OIDC_PROTOCOL_ACCESS_CONTRACT
      final apiOrigin = _apiOrigin(config.backendApiBaseUrl);
      final surfaces = <Uri>[
        _facadeUri(config.backendApiBaseUrl, const ['dav', 'files']),
        _facadeUri(config.backendApiBaseUrl, const ['caldav']),
        config.matrixHomeserverUrl.replace(
          pathSegments: const ['_matrix', 'client', 'versions'],
          query: null,
          fragment: null,
        ),
      ];

      expect(surfaces[0].path, '/dav/files');
      expect(surfaces[1].path, '/caldav');
      expect(surfaces[2].path, '/_matrix/client/versions');
      expect(config.matrixHomeserverUrl, apiOrigin);
      for (final surface in surfaces) {
        expect(surface.scheme, anyOf('http', 'https'));
        expect(surface.userInfo, isEmpty);
        expect(surface.query, isEmpty);
        expect(surface.fragment, isEmpty);
        expect(_containsProviderSecret(surface.toString()), isFalse);
      }
    });

    test('Flutter production code does not call southbound provider APIs', () {
      // NO_PROVIDER_CREDENTIALS_CONTRACT
      const forbiddenFragments = <String>[
        '/api/v3/',
        '/work_packages',
        'openproject.weave',
        'gitlab.com/api',
        'secretref://',
      ];
      final offenders = <String>[];
      for (final file in _productionDartFiles()) {
        final content = file.readAsStringSync().toLowerCase();
        for (final fragment in forbiddenFragments) {
          if (content.contains(fragment.toLowerCase())) {
            offenders.add('${file.path}: $fragment');
          }
        }
      }
      expect(offenders, isEmpty);
    });
  });
}

Uri _facadeUri(Uri backendApiBaseUrl, List<String> segments) {
  final normalized =
      backendApiBaseUrl.pathSegments
          .where((segment) => segment.isNotEmpty && segment != 'api')
          .toList(growable: true)
        ..addAll(segments);
  return backendApiBaseUrl.replace(
    pathSegments: normalized,
    query: null,
    fragment: null,
  );
}

Uri _apiOrigin(Uri backendApiBaseUrl) {
  final segments = backendApiBaseUrl.pathSegments
      .where((segment) => segment.isNotEmpty && segment != 'api')
      .toList(growable: false);
  return backendApiBaseUrl.replace(
    pathSegments: segments,
    query: null,
    fragment: null,
  );
}

bool _containsProviderSecret(String value) => RegExp(
  r'(authorization:\s*(basic|bearer)|password=|client_secret|secretref://)',
  caseSensitive: false,
).hasMatch(value);

Iterable<File> _productionDartFiles() sync* {
  final root = Directory('lib');
  if (!root.existsSync()) {
    return;
  }
  for (final entity in root.listSync(recursive: true, followLinks: false)) {
    if (entity is File && entity.path.endsWith('.dart')) {
      yield entity;
    }
  }
}
