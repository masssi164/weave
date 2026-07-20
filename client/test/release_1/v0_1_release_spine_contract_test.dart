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
    final sprint8Board = File('../docs/project/sprint-8-delivery-board.md');
    final sprint9Waterfall = File(
      '../docs/sprint-9-product-readiness-waterfall.md',
    );
    final mapping = File('../e2e/scenario_mappings.json');
    final feature = File('../e2e/features/v0_1_dogfood_release.feature');
    final sprint8DomainControlPlaneFeature = File(
      '../e2e/features/sprint_8_domain_control_plane.feature',
    );
    final productReadinessFeature = File(
      '../e2e/features/product_readiness_waterfall.feature',
    );

    test('documents dogfood-production scope without preview claims', () {
      // V01_HOME_DAILY_LOOP
      // V01_CHANNEL_WORKSPACE
      // V01_SPACE_CONTROL_ROOM
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
        'Space control room',
        'one support-safe Space ID',
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
        'V01_SPACE_CONTROL_ROOM',
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
        'The member enters a Space control room and uses one canonical Space identity across Weave product surfaces',
        'one support-safe Space ID links chat, files, board, calendar, and decision evidence without claiming full domain parity',
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
        'Agent Runtime Control',
        'Operator release path',
      ]) {
        expect(markdown, contains(required));
      }

      for (final required in <String>[
        'provider-neutral collaboration suite',
        '## What Works Today',
        '## What Is Guarded',
        'Weave is in active dogfood',
        'The portability promise is no unaccounted data loss',
        'docs/v0.1-golden-path.md',
      ]) {
        expect(readme, contains(required));
      }
    });

    test('documents provider-category admin foundation before Weaver runtime', () {
      // V01_ADMIN_PROVIDER_CATEGORIES
      // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
      // V01_ADMIN_HEALTH_POLICY_ENFORCEMENT
      // V01_ORG_CONTROL_PLANE_PROVIDER_FACADE
      // V01_IDM_RBAC_CAPABILITY_POLICY
      // V01_AGENT_RUNTIME_CONTROL_POLICY
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
        'disabled without a current entitlement group',
        'Keycloak/Auth',
        'Matrix/Chat',
        'Nextcloud/Files and Calendar backing',
        'OpenProject Boards validation',
        'the MatrixRTC Calls target',
        'IDM/RBAC capability profiles and whitelisting',
        'Agent Runtime Control integration',
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
        'Agent Runtime Control policy',
      ]) {
        expect(firstUseText, contains(required));
      }

      for (final required in <String>[
        'Provider categories are first-class product/admin concepts',
        'Agent Runtime Control is entitlement-bound',
        'never raw provider setup, service endpoints, provider secrets, or diagnostics',
        'IDM/RBAC and capability whitelisting acceptance',
        'Agent Runtime Control policy evidence',
      ]) {
        expect(planText, contains(required));
      }

      for (final required in <String>[
        'The Organization/Admin Console remains the control plane',
        'Workspace/Admin Health is organized around feature capability categories',
        'Capability policy responses are support-safe',
        'Agent Runtime Control consumes authoritative Keycloak entitlement',
      ]) {
        expect(architectureText, contains(required));
      }

      for (final marker in <String>[
        'V01_ADMIN_PROVIDER_CATEGORIES',
        'V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT',
        'V01_ADMIN_HEALTH_POLICY_ENFORCEMENT',
        'V01_ORG_CONTROL_PLANE_PROVIDER_FACADE',
        'V01_IDM_RBAC_CAPABILITY_POLICY',
        'V01_AGENT_RUNTIME_CONTROL_POLICY',
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

    test('documents the Sprint 8 domain control-plane evidence scenario', () {
      // SPRINT8_DOMAIN_CONTROL_PLANE_EVIDENCE
      expect(sprint8Board.existsSync(), isTrue);
      expect(sprint8DomainControlPlaneFeature.existsSync(), isTrue);
      final boardText = sprint8Board.readAsStringSync();
      final featureText = sprint8DomainControlPlaneFeature.readAsStringSync();

      for (final required in <String>[
        'Sprint 8 dependency DAG',
        '#434',
        'Sprint 8 acceptance scenario',
        './gradlew acceptanceContract',
        'scenario mapping evidence',
      ]) {
        expect(boardText, contains(required));
      }

      for (final required in <String>[
        '@weave-sprint8-domain-control-plane-evidence',
        'reviews canonical domain setup',
        'runs a Keycloak desired-state dry-run',
        'reviews domain-first readiness states',
        'provider switch is blocked',
        'Boards portability dry-run report',
        'provider-neutral domain states only',
        'Weaver remains disabled by default',
        'live-stack evidence is green, waived, or explicitly not required',
      ]) {
        expect(featureText, contains(required));
      }

      // ignore: avoid_print
      print('SPRINT8_DOMAIN_CONTROL_PLANE_EVIDENCE');
    });

    test('documents the Sprint 9 product-readiness waterfall evidence', () {
      // PRODUCT_READINESS_WATERFALL
      expect(sprint9Waterfall.existsSync(), isTrue);
      expect(productReadinessFeature.existsSync(), isTrue);
      final markdown = sprint9Waterfall.readAsStringSync();
      final featureText = productReadinessFeature.readAsStringSync();

      for (final required in <String>[
        'Sprint 9 product-readiness waterfall evidence',
        'Product-ready definition',
        'Domain registry version',
        'Migration contract version',
        'Keycloak dry-run sample',
        'Calls/MatrixRTC readiness artifact',
        'ARC workload identity and lifecycle proof',
        'Weaver/OpenClaw upstream provenance',
        'Security report',
        'Privacy report',
        'Accessibility report',
        'Support-safe release evidence bundle',
        'Live-stack execution is required for release-candidate promotion',
      ]) {
        expect(markdown, contains(required));
      }

      for (final required in <String>[
        '@weave-product-readiness-waterfall',
        'reviews the domain registry',
        'runs Keycloak desired-state dry-run',
        'provider apply is blocked',
        'reviews migration dry-run, lossy report, conflict report, rollback boundary, and member impact preview',
        'grants the agent runtime entitlement through Keycloak',
        'provisions a dedicated Keycloak workload identity for that cell',
        'MCP domain tool catalogs stay empty',
        'revoked without deleting provider data',
        'no raw provider tokens or secrets are exposed',
      ]) {
        expect(featureText, contains(required));
      }

      // SPRINT12_PORTABILITY_V2
      // SPRINT12_DOCUMENTS_WOPI_POSTURE
      // SPRINT12_IDENTITY_LIFECYCLE
      // SPRINT12_WEAVER_PREFLIGHT_DISABLED
      // SPRINT12_RELEASE_OPS_A11Y
      // ignore: avoid_print
      print('PRODUCT_READINESS_WATERFALL');
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
        '@weave-v01-agent-runtime-control-policy',
        '@weave-v01-mcp-workload-boundary',
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
