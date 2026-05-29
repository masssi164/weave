import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('v0.1 release spine', () {
    final plan = File('../docs/release-v0.1-dogfood-plan.md');
    final goldenPath = File('../docs/v0.1-golden-path.md');
    final productLine = File('../docs/product-line-and-weaver-plan.md');
    final firstUse = File('../docs/admin-provisioned-first-use.md');
    final canonicalModels = File('../docs/canonical-feature-models.md');
    final architecture = File('../docs/architecture.md');
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

    test('documents professional demo golden path status', () {
      expect(goldenPath.existsSync(), isTrue);
      final markdown = goldenPath.readAsStringSync();
      final readme = File('../README.md').readAsStringSync();

      for (final required in <String>[
        'professional demo review contract',
        'provider-neutral organization operating layer',
        'organization URL, invite link, or deep link',
        'personal messages, channels/workspaces, upcoming work, decisions, and health impact',
        'available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later',
        'Live E2E is standard release evidence on the dedicated self-hosted live runner',
        'not gated by a solar or power-budget exception',
        'exactly one release-notes label',
        'no admin bypass',
      ]) {
        expect(markdown, contains(required));
      }

      for (final required in <String>[
        'Product positioning',
        'Weave Home and chat overview',
        'Workspace/Admin Health',
        'Governed Weaver runtime',
        'Operator release path',
      ]) {
        expect(markdown, contains(required));
      }

      expect(readme, contains('v0.1 Golden Path quick read'));
      expect(readme, contains('docs/v0.1-golden-path.md'));
    });

    test('documents provider-category admin foundation before Weaver runtime', () {
      // V01_ADMIN_PROVIDER_CATEGORIES
      // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
      // V01_ADMIN_HEALTH_POLICY_ENFORCEMENT
      // V01_ORG_CONTROL_PLANE_PROVIDER_FACADE
      // V01_IDM_RBAC_CAPABILITY_POLICY
      // V01_GOVERNED_WEAVER_RUNTIME_POLICY
      // V01_INFRA_CONTROL_PLANE_BOOTSTRAP
      // V01_ADMIN_CONSOLE_MVP
      expect(productLine.existsSync(), isTrue);
      expect(firstUse.existsSync(), isTrue);
      expect(architecture.existsSync(), isTrue);

      final productLineText = productLine.readAsStringSync();
      final firstUseText = firstUse.readAsStringSync();
      final planText = plan.readAsStringSync();
      final architectureText = architecture.readAsStringSync();

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
        'IDM/RBAC capability profiles and whitelisting',
        'Governed Weaver runtime integration',
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
        'IDM/RBAC capability profiles',
        'Governed Weaver runtime policy',
      ]) {
        expect(firstUseText, contains(required));
      }

      for (final required in <String>[
        'Provider categories are first-class product/admin concepts',
        'Weaver is represented only as a disabled-by-default category',
        'never raw provider setup, service endpoints, provider secrets, or diagnostics',
        'IDM/RBAC and capability whitelisting acceptance',
        'Governed Weaver runtime policy evidence',
      ]) {
        expect(planText, contains(required));
      }

      for (final required in <String>[
        'The Organization/Admin Console remains the control plane',
        'Workspace/Admin Health is organized around feature capability categories',
        'Capability policy responses are support-safe',
        'Weaver runtime integration consumes the workspace capability policy',
      ]) {
        expect(architectureText, contains(required));
      }

      for (final marker in <String>[
        'V01_ADMIN_PROVIDER_CATEGORIES',
        'V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT',
        'V01_ADMIN_HEALTH_POLICY_ENFORCEMENT',
        'V01_ORG_CONTROL_PLANE_PROVIDER_FACADE',
        'V01_IDM_RBAC_CAPABILITY_POLICY',
        'V01_GOVERNED_WEAVER_RUNTIME_POLICY',
        'V01_INFRA_CONTROL_PLANE_BOOTSTRAP',
        'V01_ADMIN_CONSOLE_MVP',
      ]) {
        // ignore: avoid_print
        print(marker);
      }
    });

    test('documents canonical feature models and provider facade boundaries', () {
      // V01_CANONICAL_PROVIDER_NEUTRAL_MODELS
      // V01_MEMBER_PROVIDER_NEUTRAL_STATES
      // V01_ADMIN_POLICY_DECIDES_CAPABILITIES
      // WEAVE_CHAT_DOMAIN_FACADE
      expect(canonicalModels.existsSync(), isTrue);
      final markdown = canonicalModels.readAsStringSync();

      for (final required in <String>[
        'Canonical feature models come before control-plane',
        'Provider schemas are adapter input/output only.',
        'Server facades expose Weave-owned models per capability',
        'Policy is deny-by-default.',
        'Space, Conversation, Message, Thread, Reaction, Attachment, Membership, and Presence',
        'Drive, Node, Folder, File, Version, Share, Permission, Lock, and EditSession',
        'Calendar, Event, Attendee, Recurrence, Availability, Resource, Meeting, Participant, Recording, Captions, and MediaSession',
        'Board, List, Task, Status, Assignee, Comment, Attachment, Dependency, and CustomField',
        'Organization, User, Group, Role, ProviderConfig, CapabilityPolicy, Whitelist, SecretRef, Readiness, and AuditEvent',
        'Identity/Keycloak plus Boards/Tasks/OpenProject and a Planner-like placeholder',
        'The chat canonical set is Space, Conversation, Message, Thread, Reaction, Attachment, Membership, and Presence.',
      ]) {
        expect(markdown, contains(required));
      }

      for (final diagram in <String>[
        'er_chat.mmd',
        'er_files_docs.mmd',
        'er_calendar_meetings.mmd',
        'er_boards_tasks.mmd',
        'er_identity_admin.mmd',
        'architecture_facade.mmd',
      ]) {
        expect(File('../docs/diagrams/$diagram').existsSync(), isTrue);
      }

      for (final marker in <String>[
        'V01_CANONICAL_PROVIDER_NEUTRAL_MODELS',
        'V01_MEMBER_PROVIDER_NEUTRAL_STATES',
        'V01_ADMIN_POLICY_DECIDES_CAPABILITIES',
        'WEAVE_CHAT_DOMAIN_FACADE',
      ]) {
        // ignore: avoid_print
        print(marker);
      }
    });

    test('keeps the Gherkin release spine mapped to executable evidence', () {
      final mappingText = mapping.readAsStringSync();
      final featureText = feature.readAsStringSync();

      for (final tag in <String>[
        '@weave-v01-home-daily-loop',
        '@weave-v01-user-ready-organization-flow',
        '@weave-v01-admin-provider-categories',
        '@weave-v01-org-manifest-client-admin-split',
        '@weave-v01-admin-health-policy-enforcement',
        '@weave-v01-org-control-plane-provider-facade',
        '@weave-v01-canonical-provider-neutral-models',
        '@weave-v01-member-provider-neutral-states',
        '@weave-v01-admin-policy-decides-capabilities',
        '@weave-v01-idm-rbac-capability-policy',
        '@weave-v01-governed-weaver-runtime-policy',
        '@weave-v01-channel-workspace',
        '@weave-v01-chat-domain-facade',
        '@weave-v01-board-write-audit',
        '@weave-v01-meeting-capsule',
        '@weave-v01-decision-ledger',
        '@weave-v01-infra-control-plane-bootstrap',
        '@weave-v01-admin-console-mvp',
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
