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
  'WEAVE_SPEC_0007_INITIAL_TOOL_SET',
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
    expect(
      result.scenarios.map((scenario) => scenario.name),
      unorderedEquals(<String>[
        'Enterprise hard-plan decisions are locked before implementation lanes expand',
        'Server boundary drift fails before broad package migration',
        'Target architecture scenarios stay mapped to support-safe evidence',
        'Provider selections gain a gated relational persistence foundation',
        'Sign-in restores the Weave workspace and profile',
        'Weave chat sends and reads a workspace message through the backend facade',
        'Chat encryption diagnostic status is proved honestly',
        'Files are uploaded, shown, downloaded, and cleaned up in Weave',
        'Member lists and reads files through the Weave WebDAV facade',
        'Files writes remain blocked until WebDAV write policy is evidenced',
        'Files MCP tools cannot bypass the Files facade',
        'Provider stack readiness stays backend-owned and support-safe',
        'Workspace loop links Space, Channel, Chat, Files, and Decision',
        'Provider reality vertical reports domain availability honestly',
        'Public customer-ready wording stays blocked until evidence is complete',
        'Identity/RBAC is the first provider-switch proof',
        'Weaver approvals are product-domain grants, not OpenClaw exec permissions',
        'Domain-first MCP naming is a hard gate',
        'Northstar decisions require per-spec acceptance coverage',
        'Domain registry carries reality levels and capability names for Northstar claims',
        'Space anchor binds Northstar domains without raw provider identifiers',
        'Executable workflows require governed receipts and drift checks',
        'Meeting join and transcript claims stay blocked without consent and boundary evidence',
        'Provider portability rejects unaccounted-loss and broad lossless claims',
        'Local dogfood evidence uses weave.test and blocks live claims without runtime proof',
        'Spec frontmatter declares acceptance features and evidence gates',
        'Product claims require mapped Gherkin before merge',
        'Workflow preview preserves Space context and policy inputs',
        'Governed workflow execution records receipt drift and compensation state',
        'Meeting join stays blocked without consent and boundary evidence',
        'Transcript and follow-up artifacts stay encrypted retained and policy-linked',
        'Domain registry exposes capability reality levels without provider internals',
        'Provider-neutral capability names stay canonical across domain switches',
        'Space anchor links chat files boards meetings and decisions by canonical context',
        'Guest Space access remains policy bounded without raw provider identifiers',
        'Portability manifest accounts for preserved lossy and blocked records',
        'Broad lossless provider-switch claims stay blocked without reconciliation evidence',
        'Weaver runtime profile is generated from organization policy and roles',
        'Weaver tool invocation requires approval receipt and fails closed on drift',
        'weave.test remains the canonical local dogfood URL truth',
        'Local dogfood evidence blocks live-runtime claims until runtime proof exists',
        'MCP tools are named by Weave domain capability not provider adapter',
        'Tool discovery returns approved support-safe metadata only',
        'Spec framework intent stays reviewable before implementation',
        'Spec framework separates in-scope delivery evidence from out-of-scope live claims',
        'Delivery lead briefs scoped reviewers without leaking unsupported claims',
        'Spec functional requirements are traceable to acceptance artifacts',
        'Admin product core intent remains provider-neutral and support-safe',
        'Admin product core enforces non-negotiable provider facade constraints',
        'Admin product core functional requirements are scenario-backed',
        'SupportEvidence remains redacted backend-owned and member-safe',
        'Closed product questions remain encoded as acceptance boundaries',
        'Workflow primitive intent keeps context-driven automation bounded',
        'Workflow primitives separate preview execution and out-of-scope automation',
        'Workflow primitives enforce governed approval and drift constraints',
        'Workflow primitive functional requirements are scenario-backed',
        'Support incident workflow links notes tasks dry-run summaries and owner approval',
        'Sample workflows remain mapped to explicit acceptance evidence',
        'Workflow release impact requires support-safe evidence artifacts',
        'Meeting contract intent keeps contextual collaboration encrypted and bounded',
        'Meetings separate allowed contextual surfaces from out-of-scope recording claims',
        'Meetings enforce consent retention caption and accessibility constraints',
        'Meeting user admin and operator stories are scenario-backed',
        'Meeting functional requirements are scenario-backed',
        'Recording transcription and captions stay blocked without explicit consent and retention evidence',
        'Meeting release impact requires local and CI evidence before claims',
        'Domain registry intent makes capability truth canonical before claims',
        'Domain registry separates supported canonical fields from out-of-scope provider internals',
        'Domain registry functional requirements are scenario-backed',
        'Domain registry release impact blocks provider-neutral claims without registry evidence',
        'Spaces intent keeps organization context anchored across domains',
        'Spaces separate canonical cross-domain anchors from raw provider identifiers',
        'Spaces functional requirements are scenario-backed',
        'Spaces release impact requires anchor evidence before workspace claims',
        'Portability intent prevents unaccounted data loss before provider-switch claims',
        'Portability separates preserved lossy blocked and out-of-scope transfer data',
        'Portability functional requirements are scenario-backed',
        'Portability support evidence remains redacted and safe to review',
        'Governed Weaver intent keeps runtime tools policy-derived and auditable',
        'Governed Weaver separates approved tool execution from out-of-scope autonomous actions',
        'Governed Weaver functional requirements are scenario-backed',
        'RuntimeProfile projection model reflects policy roles grants and denied tools',
        'Local dogfood topology functional requirements are scenario-backed',
        'Domain-first MCP acceptance rejects provider-first tool claims',
        'Spec product boundaries separate product claims from implementation evidence',
        'Developer creates a reviewable spec before implementation work starts',
        'Spec domain model and contracts stay linked to acceptance evidence',
        'Decision record fixes provider-neutral product-core choices',
        'Required v0.1 domains expose capability states before member use',
        'Capability vocabulary stays stable across providers and UI surfaces',
        'SupportEvidence is redacted backend-owned and scenario-visible',
        'Governed agent participation requires receipts dry-run and owner approval',
        'Workflow domain model and contracts link preview execution receipt and compensation',
        'Meeting domain model and contracts link capsule consent transcript and follow-up artifacts',
        'Domain registry product boundaries hide provider internals behind canonical capability state',
        'Spaces product boundaries keep cross-domain anchors canonical and provider-id free',
        'Portability product boundaries reject unverified lossless migration promises',
        'Initial Weaver tool set is explicit approved and policy bounded',
        'Admin verifies organization domain and provisioning before member go-live',
        'Member sees a degraded capability without provider internals',
        'External guest receives policy-bounded Space access',
        'Document editing launches through Weave grants and locks',
        'Meeting artifacts become linked follow-up evidence',
        'Operator prepares a support bundle without secrets or member content',
        'Operator proves backup and restore before a release claim',
        'Admin reviews export, delete, and retention evidence before lifecycle action',
        'Weaver user consents to an approved tool action with an audit receipt',
        'Admin handles provider-switch manual review without changing member language',
        'Admin preflights deploy-new, attach-existing, and hybrid modes before mutation',
        'Weave Control, Admin Console, and Client keep separate responsibilities',
        'Deploy-new proof requires pipeline, server/infra readiness, Weave Control, and client-bootstrap handoff',
        'Attach-existing proof binds existing systems without redeploying them',
        'Hybrid setup keeps per-domain mutation boundaries separate',
        'Member joins through invite or organization link after admin bootstrap',
        'Admin reviews provider portability schema v2 evidence',
        'Documents stay honest until WOPI spike evidence exists',
        'Identity reconcile and offboarding fail closed before destructive changes',
        'Weaver runtime and tools remain preflight-only',
        'Release promotion requires accessibility and restore evidence',
        'Admin sees missing Forgejo runner registration before pipeline dispatch',
        'Calendar switch proof reports preserved and lossy fields',
        'Files switch proof validates metadata permissions and lossy cases',
        'Identity switch proof names mapping risk boundaries without secrets',
        'Provider neutrality beyond chat stays scoped to evidence',
        'Operator selects Forgejo without persisting GitHub secrets',
        'Runner readiness records the real local runner and keeps dispatch gated',
        'Direct local handoff and client evidence pass while Forgejo-runner terminal proof remains explicit',
        'Teams and Slack readiness specs block implementation starts',
        'Domain choices produce a support-safe Forgejo deployable plan',
        'Local Forgejo PipelineProvider gates dispatch and observes support-safe run status',
        'Weave Home starts the daily work loop',
        'A normal member sees a user-ready organization flow',
        'Dogfood member invite activation reaches the workspace',
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
        'Admin setup governs provider-neutral organization capabilities',
        'Space work joins context across domains',
        'Provider changes require dry-run approval rollback and audit',
        'Decisions and evidence are product domains',
        'Weaver provisioning is gated by organization policy and weaver-group membership',
        'Weaver memory is isolated per user',
        'Weaver uses domain-first tools with approval receipts',
        'Weaver automation heartbeat fails closed with support-safe audit and fallback',
        'Admin prepares readiness, enables Weaver, and a member uses governed Weaver in a workspace',
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
