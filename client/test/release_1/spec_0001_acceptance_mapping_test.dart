import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('WEAVE-SPEC-0001 acceptance evidence mapping', () {
    final spec = File(
      '../specs/0001-organization-embedding-product-core/spec.md',
    );
    final traceability = File(
      '../specs/0001-organization-embedding-product-core/traceability.yaml',
    );
    final feature = File('../e2e/features/weave_spec_0001_acceptance.feature');
    final mapping = File('../e2e/scenario_mappings.json');
    final adminSetup = File('../docs/admin-suite-readiness-setup-contract.md');
    final providerSwitch = File(
      '../docs/provider-replacement-and-anti-silo-contract.md',
    );

    test('maps member join and stable capability promises', () {
      // SPEC_0001_MEMBER_JOIN_INVITE_SSO_PASSKEY
      // SPEC_0001_STABLE_MEMBER_CAPABILITIES
      expect(spec.existsSync(), isTrue);
      expect(feature.existsSync(), isTrue);

      final specText = spec.readAsStringSync();
      final featureText = feature.readAsStringSync();

      for (final required in <String>[
        'Members join an already configured organization through invite/SSO/passkey',
        'member-safe capability manifests',
        'FR-004',
        'FR-005',
        'available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, and `coming_later`',
        'Normal members must not be confronted with admin/provider burden',
      ]) {
        expect(specText, contains(required));
      }

      for (final required in <String>[
        '@weave-spec-0001-member-join-invite-sso-passkey',
        '@weave-spec-0001-stable-member-capabilities',
        'the member never configures OIDC, provider endpoints, secrets, readiness, or repair flows',
        'raw provider diagnostics, endpoint details, provider setup, and admin controls remain absent',
      ]) {
        expect(featureText, contains(required));
      }

      for (final marker in <String>[
        'SPEC_0001_MEMBER_JOIN_INVITE_SSO_PASSKEY',
        'SPEC_0001_STABLE_MEMBER_CAPABILITIES',
      ]) {
        // ignore: avoid_print
        print(marker);
      }
    });

    test('maps admin setup readiness and provider switch evidence', () {
      // SPEC_0001_ADMIN_READINESS_SETUP
      // SPEC_0001_PROVIDER_SWITCH_PORTABILITY_EVIDENCE
      expect(adminSetup.existsSync(), isTrue);
      expect(providerSwitch.existsSync(), isTrue);

      final specText = spec.readAsStringSync();
      final adminSetupText = adminSetup.readAsStringSync();
      final providerSwitchText = providerSwitch.readAsStringSync();
      final featureText = feature.readAsStringSync();

      for (final required in <String>[
        'Guided admin setup assistant and readiness dashboard',
        'Provider switch contract: plan, preflight, portable export/import, cutover, rollback/recovery',
        'Full automated cross-provider migration for every domain in v0.1',
        'Live production provider migration or release/tag/publish action without explicit release evidence and signoff',
        'Accessibility, supportability, auditability, and deployability as release blockers',
      ]) {
        expect(specText, contains(required));
      }

      for (final required in <String>[
        'Guided setup assistant',
        'Readiness dashboard',
        'support-safe',
      ]) {
        expect(adminSetupText, contains(required));
      }

      for (final required in <String>[
        'Provider replacement workflow',
        'preflight',
        'rollback',
        'audit events',
      ]) {
        expect(providerSwitchText, contains(required));
      }

      for (final required in <String>[
        '@weave-spec-0001-admin-readiness-setup',
        '@weave-spec-0001-provider-switch-evidence',
        'does not claim full automated live production migration',
      ]) {
        expect(featureText, contains(required));
      }

      for (final marker in <String>[
        'SPEC_0001_ADMIN_READINESS_SETUP',
        'SPEC_0001_PROVIDER_SWITCH_PORTABILITY_EVIDENCE',
      ]) {
        // ignore: avoid_print
        print(marker);
      }
    });

    test('keeps Weaver AI runtime excluded from Spec 0001 acceptance', () {
      // SPEC_0001_WEAVER_AI_RUNTIME_EXCLUDED
      expect(traceability.existsSync(), isTrue);

      final specText = spec.readAsStringSync();
      final traceabilityText = traceability.readAsStringSync();
      final featureText = feature.readAsStringSync();

      for (final required in <String>[
        'Weaver/AI runtime is explicitly out of scope for this spec',
        'FR-008',
        'Weaver/AI runtime remains out of scope and cannot be accidentally implied as shipped.',
        'Weaver scope: out of scope for WEAVE-SPEC-0001',
      ]) {
        expect(specText, contains(required));
      }

      expect(traceabilityText, contains('Weaver/AI runtime'));
      expect(
        featureText,
        contains('@weave-spec-0001-weaver-ai-runtime-excluded'),
      );
      expect(
        featureText,
        contains(
          'runtime profiles, agent tools, and uncontrolled plugin installation cannot be implied as shipped',
        ),
      );

      // ignore: avoid_print
      print('SPEC_0001_WEAVER_AI_RUNTIME_EXCLUDED');
    });

    test(
      'scenario mappings keep Spec 0001 evidence explicit and executable',
      () {
        final decoded =
            jsonDecode(mapping.readAsStringSync()) as Map<String, Object?>;
        final scenarios = (decoded['scenarios'] as List<Object?>)
            .cast<Map<String, Object?>>();
        final tags = scenarios
            .map((scenario) => scenario['tag'] as String)
            .toSet();

        for (final tag in <String>[
          '@weave-spec-0001-member-join-invite-sso-passkey',
          '@weave-spec-0001-stable-member-capabilities',
          '@weave-spec-0001-admin-readiness-setup',
          '@weave-spec-0001-provider-switch-evidence',
          '@weave-spec-0001-weaver-ai-runtime-excluded',
        ]) {
          expect(tags, contains(tag));
        }
      },
    );
  });
}
