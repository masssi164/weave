import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/acceptance_contract.dart' as acceptance;

void main() {
  test('live stack feature scenarios are mapped to executable E2E evidence', () {
    final root = Directory.current.parent;
    final scenarios = acceptance.parseFeatureDirectory(root, 'e2e/features');
    final mappings = acceptance.loadScenarioMappings(
      root,
      'e2e/scenario_mappings.json',
    );
    final result = acceptance.validateAcceptanceContract(
      root: root,
      scenarios: scenarios,
      mappings: mappings,
      runtimeEvidence: acceptance.RuntimeEvidence.notCollected(),
    );

    expect(result.findings.map((finding) => finding.message).toList(), isEmpty);
    expect(
      result.scenarios.map((scenario) => scenario.name),
      unorderedEquals(<String>[
        'Sign-in restores the Weave workspace and profile',
        'Matrix chat sends and reads a workspace message',
        'Matrix encryption status is proved honestly',
        'Files are uploaded, shown, downloaded, and cleaned up in Weave',
        'Provider stack readiness stays backend-owned and support-safe',
        'Calendar scopes are readable and event writes obey capability policy',
        'Boards workspace supports accessible non-drag task work',
        'Workspace loop links Space, Channel, Chat, Files, Board, Calendar, and Decision',
        'Provider reality vertical reports domain availability honestly',
        'Admin reviews provider portability schema v2 evidence',
        'Documents stay honest until WOPI spike evidence exists',
        'Identity reconcile and offboarding fail closed before destructive changes',
        'Weaver runtime and tools remain preflight-only',
        'Release promotion requires accessibility and restore evidence',
        'Admin sees missing Forgejo runner registration before pipeline dispatch',
        'Operator selects Forgejo without persisting GitHub secrets',
        'Runner readiness records the real local runner and keeps dispatch gated',
        'E2E evidence remains blocked until pipeline, stack, and E2E signals exist',
        'Teams and Slack readiness specs block implementation starts',
        'Weave Home starts the daily work loop',
        'A normal member sees a user-ready organization flow',
        'Admin sees provider categories before member use',
        'Organization manifest keeps member client separate from admin console',
        'Admin health enforces provider readiness and member policy boundaries',
        'Server control plane owns provider policy and audit',
        'Self-hosted and external providers map to the same Weave feature models',
        'Member client sees stable feature states without raw provider details',
        'Admin provider policy decides capability availability before provider access',
        'IDM roles and groups decide capability profiles before Weaver runtime',
        'Weaver runtime profiles are generated from organization policy',
        'Weaver discovers and invokes only approved domain tools',
        'A Space control room is the primary workspace surface',
        'Chat uses a canonical backend domain facade',
        'A user board write is authorized and audited',
        'A meeting capsule keeps work connected',
        'Decisions are captured as product records',
        'Infra bootstrap feeds the backend control plane safely',
        'Organization admins manage provider policy in a separate console',
        'Admin plans a provider switch with portable export/import evidence',
        'Operators can deploy, verify, back up, restore, and diagnose safely',
        'Member joins a configured organization through invite SSO or passkey',
        'Member sees stable capabilities only after joining',
        'Admin configures domains through setup and reviews readiness evidence',
        'Provider switch evidence distinguishes spec acceptance from live migration',
        'Admin validates domains, dry-runs identity, checks portability, and members see provider-neutral states',
        'Admin bootstraps organization, validates domains, configures providers, enables Weaver, and member works provider-neutrally',
        'Weaver AI runtime stays excluded from Spec 0001 acceptance',
      ]),
    );
  });

  test('mapping guard fails a newly added unmapped scenario', () {
    final root = Directory.current.parent;
    final scenarios = <acceptance.FeatureScenario>[
      ...acceptance.parseFeatureDirectory(root, 'e2e/features'),
      const acceptance.FeatureScenario(
        featurePath: 'e2e/features/live_stack_app.feature',
        line: 999,
        name: 'A new product behavior starts here and must not be decorative',
        tags: <String>['@weave-live-unmapped-negative-fixture'],
      ),
    ];
    final mappings = acceptance.loadScenarioMappings(
      root,
      'e2e/scenario_mappings.json',
    );

    final result = acceptance.validateAcceptanceContract(
      root: root,
      scenarios: scenarios,
      mappings: mappings,
      runtimeEvidence: acceptance.RuntimeEvidence.notCollected(),
    );

    expect(result.isValid, isFalse);
    expect(
      result.findings.map((finding) => finding.message).join('\n'),
      contains('has no executable mapping'),
    );
  });

  test('mapping guard fails when an evidence marker is not executable', () {
    final root = Directory.current.parent;
    final scenarios = acceptance.parseFeatureDirectory(root, 'e2e/features');
    final mappings = acceptance
        .loadScenarioMappings(root, 'e2e/scenario_mappings.json')
        .map((mapping) {
          if (mapping.tag != '@weave-live-auth-shell') {
            return mapping;
          }
          return acceptance.ScenarioMapping(
            tag: mapping.tag,
            scenario: mapping.scenario,
            featurePath: mapping.featurePath,
            executableTest: mapping.executableTest,
            evidenceMode: mapping.evidenceMode,
            evidenceMarkers: <String>['MISSING_NEGATIVE_FIXTURE_MARKER'],
            additionalEvidence: mapping.additionalEvidence,
          );
        })
        .toList(growable: false);

    final result = acceptance.validateAcceptanceContract(
      root: root,
      scenarios: scenarios,
      mappings: mappings,
      runtimeEvidence: acceptance.RuntimeEvidence.notCollected(),
    );

    expect(result.isValid, isFalse);
    expect(
      result.findings.map((finding) => finding.message).join('\n'),
      contains('references missing evidence marker'),
    );
  });

  test(
    'mapping guard fails when collected runtime evidence misses a marker',
    () {
      final root = Directory.current.parent;
      final scenarios = acceptance.parseFeatureDirectory(root, 'e2e/features');
      final mappings = acceptance.loadScenarioMappings(
        root,
        'e2e/scenario_mappings.json',
      );
      final result = acceptance.validateAcceptanceContract(
        root: root,
        scenarios: scenarios,
        mappings: mappings,
        runtimeEvidence: const acceptance.RuntimeEvidence(
          wasCollected: true,
          markers: <String, acceptance.SanitizedEvidenceMarker>{},
        ),
      );

      expect(result.isValid, isFalse);
      expect(
        result.findings.map((finding) => finding.message).join('\n'),
        contains('runtime evidence did not observe marker AUTH_RESULT'),
      );
    },
  );

  test(
    'mapping guard does not treat offline spec markers as live runtime evidence',
    () {
      final root = Directory.current.parent;
      const scenario = acceptance.FeatureScenario(
        featurePath: 'e2e/features/product_readiness_waterfall.feature',
        line: 6,
        name:
            'Admin bootstraps organization, validates domains, configures providers, enables Weaver, and member works provider-neutrally',
        tags: <String>['@weave-product-readiness-waterfall'],
      );
      const mapping = acceptance.ScenarioMapping(
        tag: '@weave-product-readiness-waterfall',
        scenario:
            'Admin bootstraps organization, validates domains, configures providers, enables Weaver, and member works provider-neutrally',
        featurePath: 'e2e/features/product_readiness_waterfall.feature',
        executableTest:
            'client/test/release_1/v0_1_release_spine_contract_test.dart',
        evidenceMode: acceptance.EvidenceMode.offlineSpec,
        evidenceMarkers: <String>['PRODUCT_READINESS_WATERFALL'],
        additionalEvidence: <acceptance.AdditionalEvidenceMapping>[],
      );

      final result = acceptance.validateAcceptanceContract(
        root: root,
        scenarios: const <acceptance.FeatureScenario>[scenario],
        mappings: const <acceptance.ScenarioMapping>[mapping],
        runtimeEvidence: const acceptance.RuntimeEvidence(
          wasCollected: true,
          markers: <String, acceptance.SanitizedEvidenceMarker>{},
        ),
      );
      final summary = acceptance.renderMarkdownSummary(
        result,
        const acceptance.RuntimeEvidence(
          wasCollected: true,
          markers: <String, acceptance.SanitizedEvidenceMarker>{},
        ),
      );

      expect(
        result.findings.map((finding) => finding.message).toList(),
        isEmpty,
      );
      expect(summary, contains('offline/spec executable evidence'));
      expect(summary, contains('PRODUCT_READINESS_WATERFALL:offline-spec'));
      expect(summary, isNot(contains('PRODUCT_READINESS_WATERFALL:seen')));

      final directory = Directory.systemTemp.createTempSync(
        'weave_offline_marker_',
      );
      addTearDown(() => directory.deleteSync(recursive: true));
      final logFile = File('${directory.path}${Platform.pathSeparator}e2e.log')
        ..writeAsStringSync('PRODUCT_READINESS_WATERFALL\n');
      final evidence = acceptance.extractRuntimeEvidence(
        logFile,
        const <acceptance.ScenarioMapping>[mapping],
      );

      expect(evidence.observedMarkers, isEmpty);
    },
  );

  test('feature parser accumulates consecutive tag lines', () {
    final directory = Directory.systemTemp.createTempSync(
      'weave_feature_tags_',
    );
    addTearDown(() => directory.deleteSync(recursive: true));
    final featureFile =
        File('${directory.path}${Platform.pathSeparator}tags.feature')
          ..writeAsStringSync('''
Feature: Split tags

@weave-live @acceptance
@critical
Scenario: Tagged over several lines
  Given something useful
''');

    final scenarios = acceptance.parseFeatureFile(directory, featureFile);

    expect(scenarios, hasLength(1));
    expect(scenarios.single.tags, <String>[
      '@weave-live',
      '@acceptance',
      '@critical',
    ]);
  });

  test('feature parser treats scenario outlines as acceptance scenarios', () {
    final directory = Directory.systemTemp.createTempSync(
      'weave_feature_outline_',
    );
    addTearDown(() => directory.deleteSync(recursive: true));
    final featureFile =
        File('${directory.path}${Platform.pathSeparator}outline.feature')
          ..writeAsStringSync('''
Feature: Outlined evidence

@weave-live-outline
Scenario Outline: Capability state is support-safe
  Given a <state> capability
  Then the member sees support-safe copy

Examples:
  | state          |
  | ready          |
  | policy-blocked |
''');

    final scenarios = acceptance.parseFeatureFile(directory, featureFile);

    expect(scenarios, hasLength(1));
    expect(scenarios.single.name, 'Capability state is support-safe');
    expect(scenarios.single.tags, <String>['@weave-live-outline']);
  });

  test('runtime evidence sanitizer keeps accessibility fields', () {
    final directory = Directory.systemTemp.createTempSync('weave_evidence_');
    addTearDown(() => directory.deleteSync(recursive: true));
    final logFile = File('${directory.path}${Platform.pathSeparator}e2e.log')
      ..writeAsStringSync('''
ACCESSIBILITY_RESULT accessible=true accessibility=ok accessToken=redacted accessKey=redacted apiKey=redacted token=redacted accessTokenPresent=true durationMs=42 id=123 displayName=Massimo
''');
    const mapping = acceptance.ScenarioMapping(
      tag: '@weave-live-a11y',
      scenario: 'Accessibility evidence is useful',
      featurePath: 'e2e/features/live_stack_app.feature',
      executableTest: 'integration_test/live_stack_e2e_test.dart',
      evidenceMode: acceptance.EvidenceMode.liveRuntime,
      evidenceMarkers: <String>['ACCESSIBILITY_RESULT'],
      additionalEvidence: <acceptance.AdditionalEvidenceMapping>[],
    );

    final evidence = acceptance.extractRuntimeEvidence(
      logFile,
      <acceptance.ScenarioMapping>[mapping],
    );
    final sanitized = evidence.markers['ACCESSIBILITY_RESULT']!.sanitizedFields;

    expect(sanitized['accessible'], 'true');
    expect(sanitized['accessibility'], 'ok');
    expect(sanitized['accessTokenPresent'], 'true');
    expect(sanitized['durationMs'], '42');
    expect(sanitized, isNot(contains('accessToken')));
    expect(sanitized, isNot(contains('accessKey')));
    expect(sanitized, isNot(contains('apiKey')));
    expect(sanitized, isNot(contains('token')));
    expect(sanitized, isNot(contains('id')));
    expect(sanitized, isNot(contains('displayName')));
  });

  test('release evidence manifest is support-safe and traceable', () {
    const mapping = acceptance.ScenarioMapping(
      tag: '@weave-live-auth-shell',
      scenario: 'Sign-in restores the Weave workspace and profile',
      featurePath: 'e2e/features/live_stack_app.feature',
      executableTest: 'integration_test/live_stack_e2e_test.dart',
      evidenceMode: acceptance.EvidenceMode.liveRuntime,
      evidenceMarkers: <String>['AUTH_RESULT'],
      additionalEvidence: <acceptance.AdditionalEvidenceMapping>[],
    );
    final result = acceptance.validateAcceptanceContract(
      root: Directory.current.parent,
      scenarios: const <acceptance.FeatureScenario>[
        acceptance.FeatureScenario(
          featurePath: 'e2e/features/live_stack_app.feature',
          line: 1,
          name: 'Sign-in restores the Weave workspace and profile',
          tags: <String>['@weave-live-auth-shell'],
        ),
      ],
      mappings: const <acceptance.ScenarioMapping>[mapping],
      runtimeEvidence: const acceptance.RuntimeEvidence(
        wasCollected: true,
        markers: <String, acceptance.SanitizedEvidenceMarker>{
          'AUTH_RESULT': acceptance.SanitizedEvidenceMarker(
            marker: 'AUTH_RESULT',
            count: 1,
            sanitizedFields: <String, String>{'status': 'ok'},
          ),
        },
      ),
    );

    final manifest = acceptance.renderReleaseEvidenceManifest(
      result,
      const acceptance.RuntimeEvidence(
        wasCollected: true,
        markers: <String, acceptance.SanitizedEvidenceMarker>{
          'AUTH_RESULT': acceptance.SanitizedEvidenceMarker(
            marker: 'AUTH_RESULT',
            count: 1,
            sanitizedFields: <String, String>{'status': 'ok'},
          ),
        },
      ),
      const acceptance.ReleaseEvidenceMetadata(
        source: 'live-stack-e2e',
        lane: 'release-candidate-live-evidence',
        commit: 'abc123',
        runId: '26503862442',
        runAttempt: '1',
        runUrl: 'https://github.com/masssi164/weave/actions/runs/26503862442',
      ),
    );
    final encoded = jsonEncode(manifest).toLowerCase();

    expect(manifest['schemaVersion'], 1);
    expect(manifest['source'], 'live-stack-e2e');
    expect(manifest['commit'], 'abc123');
    expect(manifest['lane'], 'release-candidate-live-evidence');
    expect(
      manifest['rcPromotionRule'],
      contains('green-credentialed-live-stack-e2e'),
    );
    expect(manifest['artifacts'], contains('release-evidence-manifest.json'));
    expect(encoded, isNot(contains('authorization')));
    expect(encoded, isNot(contains('bearer ')));
    expect(encoded, isNot(contains('access_token')));
    expect(encoded, isNot(contains('client_secret')));
  });

  test(
    'runtime evidence sanitizer drops provider locations and raw errors',
    () {
      final directory = Directory.systemTemp.createTempSync('weave_evidence_');
      addTearDown(() => directory.deleteSync(recursive: true));
      final logFile = File('${directory.path}${Platform.pathSeparator}e2e.log')
        ..writeAsStringSync('''
PROVIDER_STACK_RESULT status=degraded providerHost=matrix.internal endpoint=/admin rawError=SocketException error=provider-stack-timeout exception=TimeoutException providerUrl=https://matrix.internal/_matrix durationMs=17
''');
      const mapping = acceptance.ScenarioMapping(
        tag: '@weave-live-provider-stack-readiness',
        scenario:
            'Provider stack readiness stays backend-owned and support-safe',
        featurePath: 'e2e/features/live_stack_app.feature',
        executableTest: 'integration_test/live_stack_e2e_test.dart',
        evidenceMode: acceptance.EvidenceMode.liveRuntime,
        evidenceMarkers: <String>['PROVIDER_STACK_RESULT'],
        additionalEvidence: <acceptance.AdditionalEvidenceMapping>[],
      );

      final evidence = acceptance.extractRuntimeEvidence(
        logFile,
        <acceptance.ScenarioMapping>[mapping],
      );
      final sanitized =
          evidence.markers['PROVIDER_STACK_RESULT']!.sanitizedFields;

      expect(sanitized['status'], 'degraded');
      expect(sanitized['durationMs'], '17');
      expect(sanitized, isNot(contains('providerHost')));
      expect(sanitized, isNot(contains('endpoint')));
      expect(sanitized, isNot(contains('rawError')));
      expect(sanitized, isNot(contains('error')));
      expect(sanitized, isNot(contains('exception')));
      expect(sanitized, isNot(contains('providerUrl')));
    },
  );
}
