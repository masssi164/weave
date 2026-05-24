import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('v0.1 release spine', () {
    final plan = File('../docs/release-v0.1-dogfood-plan.md');
    final productLine = File('../docs/product-line-and-weaver-plan.md');
    final firstUse = File('../docs/admin-provisioned-first-use.md');
    final mapping = File('../e2e/scenario_mappings.json');
    final feature = File('../e2e/features/v0_1_dogfood_release.feature');

    test('documents dogfood-production scope without preview claims', () {
      // V01_HOME_DAILY_LOOP
      // V01_CHANNEL_WORKSPACE
      // V01_BOARD_WRITE_AUDIT
      // V01_MEETING_CAPSULE
      // V01_DECISION_LEDGER
      // V01_OPERATOR_RELEASE_PATH
      expect(plan.existsSync(), isTrue);
      final markdown = plan.readAsStringSync();

      for (final required in <String>[
        'Ship Weave as a daily work tool',
        'not as a demo stack',
        'Weave Home',
        'Channels as workspaces',
        'Boards with user writes',
        'Meeting Capsule',
        'Decision Ledger',
        'Workspace/Admin Health',
        'fresh deploy works',
        'restore smoke passes',
        'support bundle is redacted',
      ]) {
        expect(markdown, contains(required));
      }

      expect(markdown, contains('Product agent runtime integration'));
      expect(
        markdown,
        contains('Agent integration requires a separate research/ADR track'),
      );

      // These sanitized markers are consumed by the Live Stack acceptance
      // evidence guard. They make the non-live release-spine contract visible
      // in the same runtime evidence artifact as the app E2E markers.
      for (final marker in <String>[
        'V01_HOME_DAILY_LOOP',
        'V01_CHANNEL_WORKSPACE',
        'V01_BOARD_WRITE_AUDIT',
        'V01_MEETING_CAPSULE',
        'V01_DECISION_LEDGER',
        'V01_OPERATOR_RELEASE_PATH',
      ]) {
        // ignore: avoid_print
        print(marker);
      }
    });

    test(
      'documents provider-category admin foundation before Weaver runtime',
      () {
        // V01_ADMIN_PROVIDER_CATEGORIES
        expect(productLine.existsSync(), isTrue);
        expect(firstUse.existsSync(), isTrue);

        final productLineText = productLine.readAsStringSync();
        final firstUseText = firstUse.readAsStringSync();
        final planText = plan.readAsStringSync();

        for (final required in <String>[
          'identity/IDM',
          'chat',
          'files',
          'calendar',
          'boards/tasks',
          'meetings/calls',
          'documents/collaboration',
          'Weaver',
          'disabled by default',
          'Keycloak/Auth',
          'Matrix/Chat',
          'Nextcloud/Files and Calendar backing',
          'OpenProject Boards validation',
          'LiveKit Meetings readiness',
        ]) {
          expect(productLineText, contains(required));
        }

        for (final required in <String>[
          'Provider-category admin boundary',
          'Normal members never configure raw providers',
          'service endpoints',
          'provider secrets',
          'provider diagnostics',
          'support-safe readiness and next actions',
        ]) {
          expect(firstUseText, contains(required));
        }

        for (final required in <String>[
          'Provider categories are first-class product/admin concepts',
          'Weaver is represented only as a disabled-by-default category',
          'never raw provider setup, service endpoints, provider secrets, or diagnostics',
        ]) {
          expect(planText, contains(required));
        }

        // ignore: avoid_print
        print('V01_ADMIN_PROVIDER_CATEGORIES');
      },
    );

    test('keeps the Gherkin release spine mapped to executable evidence', () {
      final mappingText = mapping.readAsStringSync();
      final featureText = feature.readAsStringSync();

      for (final tag in <String>[
        '@weave-v01-home-daily-loop',
        '@weave-v01-user-ready-organization-flow',
        '@weave-v01-admin-provider-categories',
        '@weave-v01-channel-workspace',
        '@weave-v01-board-write-audit',
        '@weave-v01-meeting-capsule',
        '@weave-v01-decision-ledger',
        '@weave-v01-operator-release-path',
      ]) {
        expect(featureText, contains(tag));
        expect(mappingText, contains(tag));
      }

      expect(
        mappingText,
        contains('client/test/release_1/v0_1_release_spine_contract_test.dart'),
      );
      expect(
        mappingText,
        contains('client/test/release_1/ux_release_copy_contract_test.dart'),
      );
    });
  });
}
