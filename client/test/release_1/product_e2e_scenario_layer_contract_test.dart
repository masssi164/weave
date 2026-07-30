import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final root = Directory.current.parent;
  final feature = File(
    '${root.path}/e2e/features/product_e2e_scenario_layer.feature',
  );
  final catalog = File('${root.path}/e2e/suites/scenario_catalog.json');

  test('product E2E scenario layer covers pre-runtime product flows', () {
    const markers = <String>{
      'PRODUCT_E2E_ORG_DOMAIN_PROVISIONING',
      'PRODUCT_E2E_MEMBER_DEGRADED_CAPABILITY',
      'PRODUCT_E2E_GUEST_BOUNDED_SPACE_ACCESS',
      'PRODUCT_E2E_DOCUMENT_SESSION_LAUNCH',
      'PRODUCT_E2E_MEETING_ARTIFACTS_FOLLOWUP',
      'PRODUCT_E2E_SUPPORT_BUNDLE_REDACTION',
      'PRODUCT_E2E_BACKUP_RESTORE_READINESS',
      'PRODUCT_E2E_EXPORT_DELETE_RETENTION',
      'PRODUCT_E2E_WEAVER_CONSENT_APPROVAL_RECEIPT',
      'PRODUCT_E2E_PROVIDER_SWITCH_MANUAL_REVIEW',
    };

    final featureText = feature.readAsStringSync();
    final scenarioTags = <String>{
      '@weave-product-org-domain-verification-provisioning',
      '@weave-product-member-degraded-capability-state',
      '@weave-product-guest-bounded-space-access',
      '@weave-product-document-session-launch',
      '@weave-product-meeting-artifacts-followup',
      '@weave-product-support-bundle-redaction',
      '@weave-product-backup-restore-readiness',
      '@weave-product-export-delete-retention',
      '@weave-product-weaver-consent-approval-receipt',
      '@weave-product-provider-switch-manual-review',
    };

    for (final tag in scenarioTags) {
      expect(featureText, contains(tag));
    }

    expect(featureText, contains('provider-neutral document state'));
    expect(featureText, contains('bounded guest access'));
    expect(featureText, contains('support bundle'));
    expect(featureText, contains('backup manifest and restore receipt'));
    expect(featureText, contains('short-lived single-use decision evidence'));
    expect(featureText, contains('independently reauthorizes'));
    expect(featureText, contains('immutable action evidence'));
    expect(featureText, contains('portable, lossy, unsupported'));
    expect(
      featureText,
      isNot(
        contains(RegExp(r'JWT|OAuth token|/api/v[0-9]+|homeserver|bucket')),
      ),
    );

    expect(markers.length, scenarioTags.length);
  });

  test(
    'product E2E scenario layer is classified across personas and domains',
    () {
      const marker = 'PRODUCT_E2E_SCENARIO_CATALOG_COVERAGE';
      expect(marker, isNotEmpty);

      final decoded =
          jsonDecode(catalog.readAsStringSync()) as Map<String, Object?>;
      final scenarios = (decoded['scenarios'] as List<Object?>)
          .cast<Map<String, Object?>>();
      final entries = {
        for (final scenario in scenarios) scenario['tag'] as String: scenario,
      };

      final required = <String, Set<String>>{
        '@weave-product-org-domain-verification-provisioning': {
          'admin-health-ops',
        },
        '@weave-product-member-degraded-capability-state': {
          'admin-health-ops',
          'provider-portability',
        },
        '@weave-product-guest-bounded-space-access': {
          'spaces',
          'admin-health-ops',
        },
        '@weave-product-document-session-launch': {'documents-office', 'files'},
        '@weave-product-meeting-artifacts-followup': {
          'meetings-calls',
          'decisions-evidence',
        },
        '@weave-product-support-bundle-redaction': {
          'admin-health-ops',
          'operator-release',
        },
        '@weave-product-backup-restore-readiness': {
          'admin-health-ops',
          'operator-release',
        },
        '@weave-product-export-delete-retention': {
          'provider-portability',
          'admin-health-ops',
        },
        '@weave-product-weaver-consent-approval-receipt': {
          'agent-runtime-control',
          'admin-health-ops',
        },
        '@weave-product-provider-switch-manual-review': {
          'provider-portability',
          'admin-health-ops',
        },
      };

      for (final MapEntry(key: tag, value: expectedDomains)
          in required.entries) {
        final entry = entries[tag];
        expect(entry, isNotNull, reason: '$tag missing from scenario catalog');
        expect(entry!['suiteId'], 'product-e2e-scenario-layer');
        expect(entry['testLevel'], 'offline-contract');
        final domains = (entry['domains'] as List<Object?>)
            .cast<String>()
            .toSet();
        expect(domains.containsAll(expectedDomains), isTrue, reason: tag);
      }

      final productLayerEntries = required.keys.map((tag) => entries[tag]!);
      final personas = productLayerEntries
          .expand(
            (entry) => (entry['personas'] as List<Object?>).cast<String>(),
          )
          .toSet();
      expect(personas, containsAll(<String>{'member', 'admin', 'operator'}));
      expect(personas, contains('external_guest'));
      expect(personas, contains('weaver_user'));
    },
  );
}
