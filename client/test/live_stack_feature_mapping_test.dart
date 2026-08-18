import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/acceptance_contract.dart' as acceptance;

const _specAcceptanceEvidenceMarkers = <String>[
  'WEAVE_SPEC_0000_FRONTMATTER_GATES',
  'WEAVE_SPEC_0000_PRODUCT_CLAIM_REQUIRES_SCENARIO',
  'WEAVE_SPEC_0002_WORKFLOW_PREVIEW_CONTEXT',
  'WEAVE_SPEC_0002_GOVERNED_EXECUTION_RECEIPT',
  'WEAVE_SPEC_0003_MEETING_CONSENT_BOUNDARY',
  'WEAVE_SPEC_0003_TRANSCRIPT_RETENTION_FOLLOWUP',
  'WEAVE_SPEC_0004_DOMAIN_REGISTRY_REALITY_LEVELS',
  'WEAVE_SPEC_0004_PROVIDER_NEUTRAL_CAPABILITY_NAMES',
  'WEAVE_SPEC_0005_SPACE_ANCHOR_CROSS_DOMAIN',
  'WEAVE_SPEC_0005_GUEST_BOUNDED_NO_PROVIDER_IDS',
  'WEAVE_SPEC_0006_PORTABILITY_MANIFEST_ACCOUNTS_LOSS',
  'WEAVE_SPEC_0006_LOSSLESS_CLAIM_BLOCKED',
  'WEAVE_SPEC_0007_RUNTIME_PROFILE_FROM_POLICY',
  'WEAVE_SPEC_0007_TOOL_APPROVAL_RECEIPT_FAIL_CLOSED',
  'WEAVE_SPEC_0008_WEAVE_TEST_CANONICAL_URL',
  'WEAVE_SPEC_0008_LOCAL_EVIDENCE_DOES_NOT_CLAIM_LIVE_RUNTIME',
  'WEAVE_SPEC_0009_DOMAIN_FIRST_TOOL_NAMES',
  'WEAVE_SPEC_0009_TOOL_DISCOVERY_SUPPORT_SAFE',
  'WEAVE_SPEC_0010_SETUP_GOVERNANCE',
  'WEAVE_SPEC_0010_SPACE_WORK',
  'WEAVE_SPEC_0010_PROVIDER_CHANGE',
  'WEAVE_SPEC_0010_EVIDENCE_AUDIT',
  'WEAVE_SPEC_0011_GROUP_POLICY_GATING',
  'WEAVE_SPEC_0011_MEMORY_ISOLATION',
  'WEAVE_SPEC_0011_DOMAIN_TOOL_APPROVAL',
  'WEAVE_SPEC_0011_HEARTBEAT_FALLBACK_AUDIT',
  'WEAVE_SPEC_0000_INTENT_REVIEWABLE',
  'WEAVE_SPEC_0000_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0000_REVIEWER_BRIEF',
  'WEAVE_SPEC_0000_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0001_INTENT_PROVIDER_NEUTRAL',
  'WEAVE_SPEC_0001_NON_NEGOTIABLE_CONSTRAINTS',
  'WEAVE_SPEC_0001_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0001_SUPPORT_EVIDENCE',
  'WEAVE_SPEC_0001_CLOSED_QUESTIONS',
  'WEAVE_SPEC_0002_INTENT_CONTEXT_DRIVEN',
  'WEAVE_SPEC_0002_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0002_NON_NEGOTIABLE_CONSTRAINTS',
  'WEAVE_SPEC_0002_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0002_SUPPORT_INCIDENT_RESOLUTION',
  'WEAVE_SPEC_0002_SAMPLE_WORKFLOWS',
  'WEAVE_SPEC_0002_RELEASE_IMPACT',
  'WEAVE_SPEC_0003_INTENT_ENCRYPTED_CONTEXT',
  'WEAVE_SPEC_0003_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0003_NON_NEGOTIABLE_CONSTRAINTS',
  'WEAVE_SPEC_0003_USER_ADMIN_OPERATOR_STORIES',
  'WEAVE_SPEC_0003_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0003_RECORDING_CONSENT_RETENTION',
  'WEAVE_SPEC_0003_RELEASE_IMPACT',
  'WEAVE_SPEC_0004_INTENT_CANONICAL_REGISTRY',
  'WEAVE_SPEC_0004_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0004_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0004_RELEASE_IMPACT',
  'WEAVE_SPEC_0005_INTENT_SPACE_ANCHOR',
  'WEAVE_SPEC_0005_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0005_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0005_RELEASE_IMPACT',
  'WEAVE_SPEC_0006_INTENT_NO_UNACCOUNTED_LOSS',
  'WEAVE_SPEC_0006_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0006_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0006_SUPPORT_REDACTION',
  'WEAVE_SPEC_0007_INTENT_GOVERNED_RUNTIME',
  'WEAVE_SPEC_0007_SCOPE_BOUNDARY',
  'WEAVE_SPEC_0007_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0007_RUNTIME_PROFILE_PROJECTION',
  'WEAVE_SPEC_0008_FUNCTIONAL_REQUIREMENTS',
  'WEAVE_SPEC_0009_ACCEPTANCE_PROVIDER_FIRST_REJECTED',
  'WEAVE_SPEC_0000_PRODUCT_BOUNDARIES',
  'WEAVE_SPEC_0000_DEVELOPER_REVIEWABLE_SPEC',
  'WEAVE_SPEC_0000_DOMAIN_MODEL_CONTRACTS',
  'WEAVE_SPEC_0001_DECISION_RECORD',
  'WEAVE_SPEC_0001_REQUIRED_V01_DOMAINS',
  'WEAVE_SPEC_0001_CAPABILITY_VOCABULARY',
  'WEAVE_SPEC_0001_SUPPORTEVIDENCE_CONTRACT',
  'WEAVE_SPEC_0002_GOVERNED_AGENT_PARTICIPATION',
  'WEAVE_SPEC_0002_DOMAIN_MODEL_CONTRACTS',
  'WEAVE_SPEC_0003_DOMAIN_MODEL_CONTRACTS',
  'WEAVE_SPEC_0004_PRODUCT_BOUNDARIES',
  'WEAVE_SPEC_0005_PRODUCT_BOUNDARIES',
  'WEAVE_SPEC_0006_PRODUCT_BOUNDARIES',
  'WEAVE_SPEC_0007_MCP_CATALOG_BOUNDARY',
];

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

    expect(_specAcceptanceEvidenceMarkers, isNotEmpty);
    expect(result.findings.map((finding) => finding.message).toList(), isEmpty);
    final scenarioNames = result.scenarios
        .map((scenario) => scenario.name)
        .toList();
    final mappedScenarioNames = mappings
        .map((mapping) => mapping.scenario)
        .toList();

    expect(scenarioNames, unorderedEquals(mappedScenarioNames));
    expect(
      scenarioNames,
      containsAll(<String>[
        'Authenticated member discovers standard protocol entrypoints',
        'Files WebDAV proof is separated from remaining client and native cutover',
        'PROPFIND Depth 0 and 1 list Weave-owned resources with WebDAV properties',
        'calendar-multiget returns selected calendar objects',
        'OIDC-provisioned member can connect to Matrix',
        'Matrix identity is independently authorized for an RTC slot',
        'MCP Calls catalog stays empty until MatrixRTC authorization is current',
        'Files native setup returns Weave WebDAV endpoint and Weave device credentials only',
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
        contains(
          'runtime evidence did not observe marker PHYSICAL_AUTH_SESSION_RESULT',
        ),
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
            'Admin bootstraps organization, validates domains, controls an entitled runtime cell, and members work provider-neutrally',
        tags: <String>['@weave-product-readiness-waterfall'],
      );
      const mapping = acceptance.ScenarioMapping(
        tag: '@weave-product-readiness-waterfall',
        scenario:
            'Admin bootstraps organization, validates domains, controls an entitled runtime cell, and members work provider-neutrally',
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
ACCESSIBILITY_RESULT accessible=true accessibility=ok accessToken=redacted accessKey=redacted apiKey=redacted token=redacted accessTokenPresent=true durationMs=42 id=123 displayName=Massimo fileRef=file:private.txt requestPath=/dav/files/private.txt subject=person@example.invalid auth=Bearer-secret
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
    expect(sanitized, isNot(contains('fileRef')));
    expect(sanitized, isNot(contains('requestPath')));
    expect(sanitized, isNot(contains('subject')));
    expect(sanitized, isNot(contains('auth')));
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
      contains('green-test-app-product-flow-e2e'),
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
