import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/acceptance_contract.dart' as acceptance;

void main() {
  test(
    'live stack feature scenarios are mapped to executable E2E evidence',
    () {
      final root = Directory.current;
      final scenarios = acceptance.parseFeatureDirectory(root, 'acceptance');
      final mappings = acceptance.loadScenarioMappings(
        root,
        'acceptance/scenario_mappings.json',
      );
      final result = acceptance.validateAcceptanceContract(
        root: root,
        scenarios: scenarios,
        mappings: mappings,
        runtimeEvidence: acceptance.RuntimeEvidence.notCollected(),
      );

      expect(
        result.findings.map((finding) => finding.message).toList(),
        isEmpty,
      );
      expect(
        result.scenarios.map((scenario) => scenario.name),
        unorderedEquals(<String>[
          'Sign-in restores the Weave workspace and profile',
          'Matrix chat sends and reads a workspace message',
          'Matrix encryption status is proved honestly',
          'Files are uploaded, shown, downloaded, and cleaned up in Weave',
          'Channel calendar events keep their meeting thread reference',
          'Boards preview supports accessible non-drag task work',
        ]),
      );
    },
  );

  test('mapping guard fails a newly added unmapped scenario', () {
    final root = Directory.current;
    final scenarios = <acceptance.FeatureScenario>[
      ...acceptance.parseFeatureDirectory(root, 'acceptance'),
      const acceptance.FeatureScenario(
        featurePath: 'acceptance/live_stack_app.feature',
        line: 999,
        name: 'A new product behavior starts here and must not be decorative',
        tags: <String>['@weave-live-unmapped-negative-fixture'],
      ),
    ];
    final mappings = acceptance.loadScenarioMappings(
      root,
      'acceptance/scenario_mappings.json',
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
    final root = Directory.current;
    final scenarios = acceptance.parseFeatureDirectory(root, 'acceptance');
    final mappings = acceptance
        .loadScenarioMappings(root, 'acceptance/scenario_mappings.json')
        .map((mapping) {
          if (mapping.tag != '@weave-live-auth-shell') {
            return mapping;
          }
          return acceptance.ScenarioMapping(
            tag: mapping.tag,
            scenario: mapping.scenario,
            featurePath: mapping.featurePath,
            executableTest: mapping.executableTest,
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
      final root = Directory.current;
      final scenarios = acceptance.parseFeatureDirectory(root, 'acceptance');
      final mappings = acceptance.loadScenarioMappings(
        root,
        'acceptance/scenario_mappings.json',
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
}
