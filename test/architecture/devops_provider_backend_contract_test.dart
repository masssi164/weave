import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final matrixFile = File(
    'docs/research/devops-provider-capability-matrix-232.json',
  );
  final reportFile = File('docs/research/devops-gitlab-forgejo-232.md');

  test(
    'devops provider capability matrix is provider-neutral and fail-closed',
    () async {
      final matrix = jsonDecode(await matrixFile.readAsString());

      expect(matrix, isA<Map<String, Object?>>());
      final root = matrix as Map<String, Object?>;
      expect(root['schema_version'], 1);
      expect(root['issue'], 232);
      expect(root['status'], 'research-contract-before-adapters');
      expect(root['backend_facade_required'], isTrue);
      expect(root['flutter_provider_neutral'], isTrue);
      expect(root['no_provider_tokens_to_flutter'], isTrue);
      expect(root['no_raw_provider_errors_to_flutter'], isTrue);

      expect(
        _stringList(root, 'allowed_error_codes'),
        containsAll(<String>[
          'unauthorized',
          'forbidden',
          'not_found',
          'conflict',
          'rate_limited',
          'offline',
          'validation',
          'provider_unavailable',
          'provider_feature_unavailable',
          'unknown',
        ]),
      );

      final ports = _list(root, 'ports').cast<Map<String, Object?>>();
      expect(
        ports.map((port) => _string(port, 'name')),
        containsAll(<String>[
          'SourceControlProvider',
          'IssueTrackerProvider',
          'CiProvider',
          'ReleaseProvider',
        ]),
      );
      for (final port in ports) {
        expect(_stringList(port, 'read_operations'), isNotEmpty);
        expect(_stringList(port, 'write_operations_later'), isNotEmpty);
      }

      final capabilityKeys = _stringList(root, 'required_capability_keys');
      expect(capabilityKeys, containsAll(_requiredCapabilityKeys));
      final allowedValues = _stringList(root, 'allowed_capability_values');

      final providers = _list(root, 'providers');
      expect(providers, hasLength(2));
      final providersById = <String, Map<String, Object?>>{};
      for (final providerValue in providers) {
        expect(providerValue, isA<Map<String, Object?>>());
        final provider = providerValue as Map<String, Object?>;
        final id = _string(provider, 'id');
        providersById[id] = provider;

        expect(_string(provider, 'name'), isNotEmpty);
        expect(
          _string(provider, 'role'),
          isIn(<String>['primary_candidate', 'first_class_alternative']),
        );
        expect(_string(provider, 'license'), isNotEmpty);
        expect(
          _string(provider, 'commercial_self_hosting_fit'),
          contains('self_host'),
        );
        expect(
          _stringList(provider, 'minimal_read_scopes_or_role'),
          isNotEmpty,
        );
        expect(
          _stringList(provider, 'safe_later_write_scopes_or_role'),
          isNotEmpty,
        );

        final capabilities = _map(provider, 'capabilities');
        expect(capabilities.keys, containsAll(capabilityKeys));
        for (final key in capabilityKeys) {
          expect(
            _string(capabilities, key),
            isIn(allowedValues),
            reason: '$id.$key must use the documented vocabulary',
          );
        }

        final sources = _stringList(provider, 'source_urls');
        expect(sources, isNotEmpty, reason: '$id has cited sources');
        for (final source in sources) {
          expect(source, startsWith('https://'));
        }
      }

      expect(
        providersById.keys,
        containsAll(<String>['gitlab-ce-foss', 'forgejo']),
      );
      expect(
        _string(providersById['gitlab-ce-foss']!, 'role'),
        'primary_candidate',
      );
      expect(
        _string(providersById['forgejo']!, 'role'),
        'first_class_alternative',
      );
      expect(
        _stringList(providersById['gitlab-ce-foss']!, 'exclude_paid_features'),
        containsAll(<String>[
          'merge request approvals and eligible approver policy',
          'epics, issue weights, iterations, health status, advanced roadmaps/planning',
          'GitLab Duo/AI features',
        ]),
      );
    },
  );

  test('devops provider research documents boundaries and sources', () async {
    final report = await reportFile.readAsString();

    expect(report, contains('GitLab CE/FOSS'));
    expect(report, contains('Forgejo'));
    expect(report, contains('cd826607-5d0e-4872-8412-c5d3ee7a80c1'));
    expect(report, contains('devops-provider-capability-matrix-232.json'));
    expect(report, contains('SourceControlProvider'));
    expect(report, contains('IssueTrackerProvider'));
    expect(report, contains('CiProvider'));
    expect(report, contains('ReleaseProvider'));
    expect(report, contains('Provider-Tokens'));
    expect(report, contains('Raw HTTP Bodies'));
    expect(report, contains('Flutter bleibt providerneutral'));
    expect(report, contains('MR Approvals'));
    expect(report, contains('OAuth2 Token Scopes'));
    expect(report, contains('https://docs.gitlab.com/api/rest/'));
    expect(report, contains('https://forgejo.org/docs/latest/user/api-usage/'));
  });

  test(
    'devops Flutter code must not bypass backend provider facades',
    () async {
      final devopsPaths = <String>[
        'lib/features/devops',
        'lib/integrations/gitlab',
        'lib/integrations/forgejo',
      ];

      for (final path in devopsPaths) {
        final entity = Directory(path);
        if (!entity.existsSync()) {
          continue;
        }
        final files = entity
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('.dart'));
        for (final file in files) {
          final source = await file.readAsString();
          expect(source, isNot(contains('PRIVATE-TOKEN')));
          expect(source, isNot(contains('Authorization: token')));
          expect(source, isNot(contains('/api/v4')));
          expect(source, isNot(contains('/api/v1')));
          expect(source, isNot(contains('gitlab.com/api')));
          expect(source, isNot(contains('forgejo')));
        }
      }
    },
  );
}

const _requiredCapabilityKeys = <String>[
  'license_and_self_hosting',
  'projects_repositories',
  'groups_users_permissions',
  'issues',
  'merge_or_pull_requests',
  'ci_pipelines_jobs_actions',
  'releases_tags',
  'artifacts_packages_container_registry',
  'webhooks',
  'oauth_oidc_tokens',
  'pagination_rate_limits',
  'read_only_first_fit',
  'safe_later_write_fit',
  'paid_or_feature_boundary_risk',
  'accessibility_product_fit',
];

Map<String, Object?> _map(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<Map<String, Object?>>(), reason: key);
  return value! as Map<String, Object?>;
}

List<Object?> _list(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<List<Object?>>(), reason: key);
  return value! as List<Object?>;
}

List<String> _stringList(Map<String, Object?> source, String key) {
  final values = _list(source, key);
  for (final value in values) {
    expect(value, isA<String>(), reason: key);
  }
  return values.cast<String>();
}

String _string(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<String>(), reason: key);
  return value! as String;
}
