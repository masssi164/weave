import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('live stack feature scenarios are mapped to executable E2E evidence', () {
    final feature = File('acceptance/live_stack_app.feature');
    final executable = File('integration_test/live_stack_app_e2e_test.dart');
    final productFlowDoc = File('docs/product-flow-activity-diagrams.md');

    expect(
      feature.existsSync(),
      isTrue,
      reason: 'Missing readable Live Stack scenarios.',
    );
    expect(
      executable.existsSync(),
      isTrue,
      reason: 'Missing executable Live Stack E2E test.',
    );
    expect(
      productFlowDoc.existsSync(),
      isTrue,
      reason: 'Missing presentable product-flow activity diagrams.',
    );

    final featureText = feature.readAsStringSync();
    final executableText = executable.readAsStringSync();
    final productFlowText = productFlowDoc.readAsStringSync();

    expect(
      RegExp(r'```mermaid\s+flowchart TD').allMatches(productFlowText).length,
      greaterThanOrEqualTo(7),
      reason:
          'Product-flow doc must contain the seven required activity diagrams.',
    );
    final scenarios = _scenarios(featureText);

    expect(scenarios.keys, unorderedEquals(_requiredMappings.keys));

    for (final entry in _requiredMappings.entries) {
      final scenario = scenarios[entry.key];
      expect(scenario, isNotNull, reason: 'Missing scenario: ${entry.key}');
      expect(
        scenario!.tags,
        contains(entry.value.tag),
        reason:
            'Scenario "${entry.key}" must carry stable tag ${entry.value.tag}.',
      );
      expect(
        productFlowText,
        contains(entry.value.tag),
        reason:
            'Scenario "${entry.key}" must be anchored in the product-flow activity diagrams.',
      );
      for (final fragment in entry.value.executableFragments) {
        expect(
          executableText,
          contains(fragment),
          reason:
              'Scenario "${entry.key}" must stay linked to executable Live Stack evidence fragment "$fragment".',
        );
      }
    }
  });
}

const _requiredMappings = <String, _ScenarioMapping>{
  'Auth sign-in restores the Weave workspace shell and profile facade':
      _ScenarioMapping(
        tag: '@weave-live-auth-shell',
        executableFragments: <String>[
          'LiveOidcTestDriver(config: config)',
          "find.text('Anmelden')",
          'PROFILE_RESULT',
          'profileUpdated',
        ],
      ),
  'Matrix chat sends messages and proves E2EE posture honestly':
      _ScenarioMapping(
        tag: '@weave-live-matrix-e2ee',
        executableFragments: <String>[
          'MATRIX_RESULT',
          'E2EE_RESULT',
          '_waitForAuthoritativeEncryptedWireEvent',
          'encryptedWirePlaintextLeaked',
        ],
      ),
  'Files are browsed, uploaded, and downloaded through the Weave product facade':
      _ScenarioMapping(
        tag: '@weave-live-files-boundary',
        executableFragments: <String>[
          'filesProvider.notifier).connect',
          'uploadFile',
          'downloadFile',
          'FILES_RESULT',
        ],
      ),
  'Channel calendar events round trip with stable meeting thread references':
      _ScenarioMapping(
        tag: '@weave-live-calendar-threadrefs',
        executableFragments: <String>[
          'calendarRepository.loadScopes',
          '_createCalendarEventWithReadAfterWrite',
          '_channelMeetingThreadReady',
          'CALENDAR_RESULT',
        ],
      ),
  'Boards preview stays provider-neutral and supports non-drag task operations':
      _ScenarioMapping(
        tag: '@weave-live-boards-preview-nondrag',
        executableFragments: <String>[
          '/api/boards/preview',
          '/api/boards/\$boardId/tasks',
          '/api/boards/tasks/\$taskId/move',
          'BOARDS_RESULT',
        ],
      ),
};

Map<String, _FeatureScenario> _scenarios(String featureText) {
  final scenarios = <String, _FeatureScenario>{};
  final pendingTags = <String>[];

  for (final rawLine in featureText.split('\n')) {
    final line = rawLine.trim();
    if (line.startsWith('@')) {
      pendingTags
        ..clear()
        ..addAll(line.split(RegExp(r'\s+')).where((part) => part.isNotEmpty));
      continue;
    }
    if (line.startsWith('Scenario: ')) {
      final name = line.substring('Scenario: '.length).trim();
      scenarios[name] = _FeatureScenario(
        name,
        List<String>.unmodifiable(pendingTags),
      );
      pendingTags.clear();
    }
  }

  return scenarios;
}

class _FeatureScenario {
  const _FeatureScenario(this.name, this.tags);

  final String name;
  final List<String> tags;
}

class _ScenarioMapping {
  const _ScenarioMapping({
    required this.tag,
    required this.executableFragments,
  });

  final String tag;
  final List<String> executableFragments;
}
