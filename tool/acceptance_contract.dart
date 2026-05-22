import 'dart:convert';
import 'dart:io';

const _sensitiveKeyNames = <String>{
  'token',
  'secret',
  'password',
  'authorization',
  'cookie',
  'session',
  'credential',
  'username',
  'userid',
  'displayname',
  'roomid',
  'eventid',
  'threadid',
  'filename',
  'accesstoken',
  'accesskey',
  'apikey',
};

const _sensitiveKeySuffixes = <String>{
  'token',
  'secret',
  'password',
  'authorization',
  'cookie',
  'credential',
  'accesskey',
  'apikey',
};

void main(List<String> args) {
  if (args.isEmpty || args.first == '--help' || args.first == '-h') {
    _printUsage();
    return;
  }

  final command = args.first;
  final options = _parseOptions(args.skip(1).toList());
  if (command != 'guard') {
    stderr.writeln('Unknown acceptance contract command: $command');
    _printUsage();
    exitCode = 64;
    return;
  }

  final root = Directory.current;
  final featuresDir = options['features'] ?? 'acceptance';
  final mappingPath = options['mapping'] ?? 'acceptance/scenario_mappings.json';
  final outputDir = options['out'];
  final testLogPath = options['test-log'];
  final appendSummaryPath = options['append-summary'];

  final scenarios = parseFeatureDirectory(root, featuresDir);
  final mappings = loadScenarioMappings(root, mappingPath);
  final runtimeEvidence = testLogPath == null
      ? RuntimeEvidence.notCollected()
      : extractRuntimeEvidence(File(_join(root.path, testLogPath)), mappings);
  final result = validateAcceptanceContract(
    root: root,
    scenarios: scenarios,
    mappings: mappings,
    runtimeEvidence: runtimeEvidence,
  );

  if (outputDir != null) {
    writeAcceptanceArtifacts(
      Directory(_join(root.path, outputDir)),
      result,
      runtimeEvidence,
    );
  }

  final summary = renderMarkdownSummary(result, runtimeEvidence);
  stdout.write(summary);
  if (appendSummaryPath != null) {
    final summaryFile = File(appendSummaryPath);
    summaryFile.writeAsStringSync('\n$summary', mode: FileMode.append);
  }

  if (!result.isValid) {
    for (final finding in result.findings) {
      stderr.writeln('acceptance-contract: ${finding.message}');
    }
    exitCode = 1;
  }
}

List<FeatureScenario> parseFeatureDirectory(
  Directory root,
  String relativeDir,
) {
  final directory = Directory(_join(root.path, relativeDir));
  if (!directory.existsSync()) {
    return const <FeatureScenario>[];
  }
  final files =
      directory
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.feature'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));
  return <FeatureScenario>[
    for (final file in files) ...parseFeatureFile(root, file),
  ];
}

List<FeatureScenario> parseFeatureFile(Directory root, File file) {
  final relativePath = _relativePath(root.path, file.path);
  final scenarios = <FeatureScenario>[];
  final pendingTags = <String>[];
  final lines = file.readAsLinesSync();

  for (var index = 0; index < lines.length; index += 1) {
    final line = lines[index].trim();
    if (line.startsWith('@')) {
      pendingTags.addAll(
        line.split(RegExp(r'\s+')).where((part) => part.isNotEmpty),
      );
      continue;
    }
    if (line.startsWith('Scenario: ')) {
      final scenarioName = line.substring('Scenario: '.length).trim();
      scenarios.add(
        FeatureScenario(
          featurePath: relativePath,
          line: index + 1,
          name: scenarioName,
          tags: List<String>.unmodifiable(pendingTags),
        ),
      );
      pendingTags.clear();
    }
  }

  return scenarios;
}

List<ScenarioMapping> loadScenarioMappings(
  Directory root,
  String relativePath,
) {
  final file = File(_join(root.path, relativePath));
  if (!file.existsSync()) {
    return const <ScenarioMapping>[];
  }
  final decoded = jsonDecode(file.readAsStringSync());
  if (decoded is! Map<String, Object?>) {
    throw const FormatException('Scenario mapping root must be a JSON object.');
  }
  final rawScenarios = decoded['scenarios'];
  if (rawScenarios is! List) {
    throw const FormatException(
      'Scenario mapping must contain a scenarios list.',
    );
  }
  return rawScenarios
      .map(
        (raw) => ScenarioMapping.fromJson((raw as Map).cast<String, Object?>()),
      )
      .toList(growable: false);
}

AcceptanceContractResult validateAcceptanceContract({
  required Directory root,
  required List<FeatureScenario> scenarios,
  required List<ScenarioMapping> mappings,
  required RuntimeEvidence runtimeEvidence,
}) {
  final findings = <AcceptanceFinding>[];
  if (scenarios.isEmpty) {
    findings.add(const AcceptanceFinding('No .feature scenarios were found.'));
  }
  if (mappings.isEmpty) {
    findings.add(const AcceptanceFinding('No scenario mappings were found.'));
  }

  final scenariosByTag = <String, FeatureScenario>{};
  for (final scenario in scenarios) {
    if (scenario.tags.isEmpty) {
      findings.add(
        AcceptanceFinding(
          '${scenario.featurePath}:${scenario.line} "${scenario.name}" has no stable tag.',
        ),
      );
    }
    for (final tag in scenario.tags) {
      final existing = scenariosByTag[tag];
      if (existing != null) {
        findings.add(
          AcceptanceFinding(
            'Duplicate feature tag $tag on "${existing.name}" and "${scenario.name}".',
          ),
        );
      } else {
        scenariosByTag[tag] = scenario;
      }
    }
  }

  final mappingsByTag = <String, ScenarioMapping>{};
  for (final mapping in mappings) {
    final existing = mappingsByTag[mapping.tag];
    if (existing != null) {
      findings.add(
        AcceptanceFinding(
          'Duplicate mapping tag ${mapping.tag} for "${existing.scenario}" and "${mapping.scenario}".',
        ),
      );
    } else {
      mappingsByTag[mapping.tag] = mapping;
    }
  }

  final scenarioResults = <ScenarioMappingResult>[];
  for (final scenario in scenarios) {
    final tagMappings = scenario.tags
        .map((tag) => mappingsByTag[tag])
        .whereType<ScenarioMapping>()
        .toList(growable: false);
    if (tagMappings.isEmpty) {
      findings.add(
        AcceptanceFinding(
          '${scenario.featurePath}:${scenario.line} "${scenario.name}" has no executable mapping.',
        ),
      );
      scenarioResults.add(
        ScenarioMappingResult.unmapped(
          scenario: scenario,
          runtimeEvidence: runtimeEvidence,
        ),
      );
      continue;
    }
    for (final mapping in tagMappings) {
      scenarioResults.add(
        _validateScenarioMapping(
          root,
          scenario,
          mapping,
          findings,
          runtimeEvidence,
        ),
      );
    }
  }

  for (final mapping in mappings) {
    final scenario = scenariosByTag[mapping.tag];
    if (scenario == null) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} references no checked-in feature scenario.',
        ),
      );
      continue;
    }
    if (mapping.scenario != scenario.name) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} names "${mapping.scenario}" but feature names "${scenario.name}".',
        ),
      );
    }
    if (mapping.featurePath != scenario.featurePath) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} points to ${mapping.featurePath} but feature scenario is in ${scenario.featurePath}.',
        ),
      );
    }
  }

  return AcceptanceContractResult(
    scenarios: List<FeatureScenario>.unmodifiable(scenarios),
    mappings: List<ScenarioMapping>.unmodifiable(mappings),
    scenarioResults: List<ScenarioMappingResult>.unmodifiable(scenarioResults),
    findings: List<AcceptanceFinding>.unmodifiable(findings),
  );
}

ScenarioMappingResult _validateScenarioMapping(
  Directory root,
  FeatureScenario scenario,
  ScenarioMapping mapping,
  List<AcceptanceFinding> findings,
  RuntimeEvidence runtimeEvidence,
) {
  final executableFile = File(_join(root.path, mapping.executableTest));
  final executableExists = executableFile.existsSync();
  final executableText = executableExists
      ? executableFile.readAsStringSync()
      : '';
  if (!executableExists) {
    findings.add(
      AcceptanceFinding(
        'Mapping ${mapping.tag} references missing executable test ${mapping.executableTest}.',
      ),
    );
  }
  if (mapping.evidenceMarkers.isEmpty) {
    findings.add(
      AcceptanceFinding('Mapping ${mapping.tag} has no evidence markers.'),
    );
  }

  final markerResults = <EvidenceMarkerResult>[];
  for (final marker in mapping.evidenceMarkers) {
    final sourcePresent = executableText.contains(marker);
    final runtimeObserved = runtimeEvidence.observedMarkers.contains(marker);
    if (!sourcePresent) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} references missing evidence marker $marker in ${mapping.executableTest}.',
        ),
      );
    }
    if (runtimeEvidence.wasCollected && !runtimeObserved) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} runtime evidence did not observe marker $marker.',
        ),
      );
    }
    markerResults.add(
      EvidenceMarkerResult(
        marker: marker,
        sourcePresent: sourcePresent,
        runtimeObserved: runtimeObserved,
      ),
    );
  }

  for (final evidence in mapping.additionalEvidence) {
    final file = File(_join(root.path, evidence.path));
    if (!file.existsSync()) {
      findings.add(
        AcceptanceFinding(
          'Mapping ${mapping.tag} references missing additional evidence ${evidence.path}.',
        ),
      );
      continue;
    }
    final evidenceText = file.readAsStringSync();
    for (final fragment in evidence.fragments) {
      if (!evidenceText.contains(fragment)) {
        findings.add(
          AcceptanceFinding(
            'Mapping ${mapping.tag} is missing evidence fragment "$fragment" in ${evidence.path}.',
          ),
        );
      }
    }
  }

  final runtimeStatus = runtimeEvidence.wasCollected
      ? markerResults.every((marker) => marker.runtimeObserved)
            ? RuntimeScenarioStatus.passed
            : RuntimeScenarioStatus.failedOrIncomplete
      : RuntimeScenarioStatus.notCollected;

  return ScenarioMappingResult(
    scenario: scenario,
    mapping: mapping,
    markerResults: List<EvidenceMarkerResult>.unmodifiable(markerResults),
    runtimeStatus: runtimeStatus,
  );
}

RuntimeEvidence extractRuntimeEvidence(
  File logFile,
  List<ScenarioMapping> mappings,
) {
  if (!logFile.existsSync()) {
    return RuntimeEvidence.notCollected();
  }
  final expectedMarkers = mappings
      .expand((mapping) => mapping.evidenceMarkers)
      .toSet();
  final markers = <String, SanitizedEvidenceMarker>{};
  for (final line in logFile.readAsLinesSync()) {
    for (final marker in expectedMarkers) {
      if (_lineContainsMarker(line, marker)) {
        final existing = markers[marker];
        markers[marker] = SanitizedEvidenceMarker(
          marker: marker,
          count: (existing?.count ?? 0) + 1,
          sanitizedFields: _sanitizeMarkerFields(line),
        );
      }
    }
  }
  return RuntimeEvidence(
    wasCollected: true,
    markers: Map<String, SanitizedEvidenceMarker>.unmodifiable(markers),
  );
}

void writeAcceptanceArtifacts(
  Directory outputDir,
  AcceptanceContractResult result,
  RuntimeEvidence runtimeEvidence,
) {
  outputDir.createSync(recursive: true);
  File(_join(outputDir.path, 'gherkin-scenarios.json')).writeAsStringSync(
    const JsonEncoder.withIndent(
      '  ',
    ).convert(result.scenarios.map((scenario) => scenario.toJson()).toList()),
  );
  File(
    _join(outputDir.path, 'scenario-mapping-results.json'),
  ).writeAsStringSync(
    const JsonEncoder.withIndent('  ').convert(result.toJson()),
  );
  File(_join(outputDir.path, 'evidence-markers.json')).writeAsStringSync(
    const JsonEncoder.withIndent('  ').convert(runtimeEvidence.toJson()),
  );
  File(
    _join(outputDir.path, 'acceptance-summary.md'),
  ).writeAsStringSync(renderMarkdownSummary(result, runtimeEvidence));
}

String renderMarkdownSummary(
  AcceptanceContractResult result,
  RuntimeEvidence runtimeEvidence,
) {
  final buffer = StringBuffer()
    ..writeln('## Live Stack acceptance contract')
    ..writeln()
    ..writeln('- Mapping guard: ${result.isValid ? 'passed' : 'failed'}')
    ..writeln('- Scenarios: ${result.scenarios.length}')
    ..writeln(
      '- Runtime evidence: ${runtimeEvidence.wasCollected ? 'collected' : 'not collected'}',
    )
    ..writeln()
    ..writeln('| Scenario | Mapping | Runtime evidence | Markers |')
    ..writeln('| --- | --- | --- | --- |');
  for (final scenarioResult in result.scenarioResults) {
    final markers = scenarioResult.markerResults.isEmpty
        ? 'none'
        : scenarioResult.markerResults
              .map(
                (marker) => runtimeEvidence.wasCollected
                    ? '${marker.marker}:${marker.runtimeObserved ? 'seen' : 'missing'}'
                    : '${marker.marker}:mapped',
              )
              .join('<br>');
    buffer.writeln(
      '| ${_escapeMarkdown(scenarioResult.scenario.name)} | ${scenarioResult.mapping == null ? 'missing' : 'mapped'} | ${scenarioResult.runtimeStatus.label} | $markers |',
    );
  }
  if (result.findings.isNotEmpty) {
    buffer
      ..writeln()
      ..writeln('### Guard findings');
    for (final finding in result.findings) {
      buffer.writeln('- ${finding.message}');
    }
  }
  buffer.writeln();
  return buffer.toString();
}

bool _lineContainsMarker(String line, String marker) =>
    line == marker || line.startsWith('$marker ') || line.contains(' $marker ');

Map<String, String> _sanitizeMarkerFields(String line) {
  final fields = <String, String>{};
  final keyValuePattern = RegExp(r'([A-Za-z][A-Za-z0-9_]*)=([^\s]+)');
  for (final match in keyValuePattern.allMatches(line)) {
    final key = match.group(1)!;
    final value = match.group(2)!;
    final lowerKey = key.toLowerCase();
    if (_isSensitiveMarkerKey(key) ||
        lowerKey == 'id' ||
        lowerKey.endsWith('id') ||
        lowerKey.endsWith('name')) {
      continue;
    }
    if (value.length > 120 || value.contains('://')) {
      continue;
    }
    fields[key] = value;
  }
  return fields;
}

bool _isSensitiveMarkerKey(String key) {
  final normalizedKey = key.toLowerCase().replaceAll(RegExp(r'[_-]'), '');
  if (_sensitiveKeyNames.contains(normalizedKey)) {
    return true;
  }
  return _sensitiveKeySuffixes.any(normalizedKey.endsWith);
}

Map<String, String> _parseOptions(List<String> args) {
  final options = <String, String>{};
  for (var index = 0; index < args.length; index += 1) {
    final arg = args[index];
    if (!arg.startsWith('--')) {
      throw FormatException('Unexpected positional argument: $arg');
    }
    final equalsIndex = arg.indexOf('=');
    if (equalsIndex >= 0) {
      options[arg.substring(2, equalsIndex)] = arg.substring(equalsIndex + 1);
      continue;
    }
    if (index + 1 >= args.length) {
      throw FormatException('Missing value for option $arg');
    }
    options[arg.substring(2)] = args[index + 1];
    index += 1;
  }
  return options;
}

void _printUsage() {
  stdout.writeln('''
Usage: dart run tool/acceptance_contract.dart guard [options]

Options:
  --features <dir>        Feature directory (default: acceptance)
  --mapping <file>        Scenario mapping JSON (default: acceptance/scenario_mappings.json)
  --out <dir>             Optional artifact output directory
  --test-log <file>       Optional live E2E log to extract sanitized evidence markers
  --append-summary <file> Optional markdown file to append the acceptance summary to
''');
}

String _join(String first, String second) {
  if (File(second).isAbsolute) {
    return second;
  }
  if (first.endsWith(Platform.pathSeparator)) {
    return '$first$second';
  }
  return '$first${Platform.pathSeparator}$second';
}

String _relativePath(String rootPath, String path) {
  final normalizedRoot = rootPath.endsWith(Platform.pathSeparator)
      ? rootPath
      : '$rootPath${Platform.pathSeparator}';
  final relativePath = path.startsWith(normalizedRoot)
      ? path.substring(normalizedRoot.length)
      : path;
  return relativePath.replaceAll(Platform.pathSeparator, '/');
}

String _escapeMarkdown(String value) => value.replaceAll('|', r'\|');

class FeatureScenario {
  const FeatureScenario({
    required this.featurePath,
    required this.line,
    required this.name,
    required this.tags,
  });

  final String featurePath;
  final int line;
  final String name;
  final List<String> tags;

  Map<String, Object?> toJson() => <String, Object?>{
    'feature': featurePath,
    'line': line,
    'name': name,
    'tags': tags,
  };
}

class ScenarioMapping {
  const ScenarioMapping({
    required this.tag,
    required this.scenario,
    required this.featurePath,
    required this.executableTest,
    required this.evidenceMarkers,
    required this.additionalEvidence,
  });

  factory ScenarioMapping.fromJson(Map<String, Object?> json) {
    return ScenarioMapping(
      tag: json['tag']! as String,
      scenario: json['scenario']! as String,
      featurePath: json['feature']! as String,
      executableTest: json['executableTest']! as String,
      evidenceMarkers: (json['evidenceMarkers']! as List).cast<String>(),
      additionalEvidence:
          ((json['additionalEvidence'] as List?) ?? const <Object>[])
              .map(
                (raw) => AdditionalEvidenceMapping.fromJson(
                  (raw as Map).cast<String, Object?>(),
                ),
              )
              .toList(growable: false),
    );
  }

  final String tag;
  final String scenario;
  final String featurePath;
  final String executableTest;
  final List<String> evidenceMarkers;
  final List<AdditionalEvidenceMapping> additionalEvidence;

  Map<String, Object?> toJson() => <String, Object?>{
    'tag': tag,
    'scenario': scenario,
    'feature': featurePath,
    'executableTest': executableTest,
    'evidenceMarkers': evidenceMarkers,
    if (additionalEvidence.isNotEmpty)
      'additionalEvidence': additionalEvidence
          .map((evidence) => evidence.toJson())
          .toList(growable: false),
  };
}

class AdditionalEvidenceMapping {
  const AdditionalEvidenceMapping({
    required this.path,
    required this.fragments,
  });

  factory AdditionalEvidenceMapping.fromJson(Map<String, Object?> json) {
    return AdditionalEvidenceMapping(
      path: json['path']! as String,
      fragments: (json['fragments']! as List).cast<String>(),
    );
  }

  final String path;
  final List<String> fragments;

  Map<String, Object?> toJson() => <String, Object?>{
    'path': path,
    'fragments': fragments,
  };
}

class AcceptanceContractResult {
  const AcceptanceContractResult({
    required this.scenarios,
    required this.mappings,
    required this.scenarioResults,
    required this.findings,
  });

  final List<FeatureScenario> scenarios;
  final List<ScenarioMapping> mappings;
  final List<ScenarioMappingResult> scenarioResults;
  final List<AcceptanceFinding> findings;

  bool get isValid => findings.isEmpty;

  Map<String, Object?> toJson() => <String, Object?>{
    'valid': isValid,
    'scenarios': scenarios.map((scenario) => scenario.toJson()).toList(),
    'mappings': mappings.map((mapping) => mapping.toJson()).toList(),
    'results': scenarioResults.map((result) => result.toJson()).toList(),
    'findings': findings.map((finding) => finding.toJson()).toList(),
  };
}

class ScenarioMappingResult {
  const ScenarioMappingResult({
    required this.scenario,
    required this.mapping,
    required this.markerResults,
    required this.runtimeStatus,
  });

  factory ScenarioMappingResult.unmapped({
    required FeatureScenario scenario,
    required RuntimeEvidence runtimeEvidence,
  }) {
    return ScenarioMappingResult(
      scenario: scenario,
      mapping: null,
      markerResults: const <EvidenceMarkerResult>[],
      runtimeStatus: runtimeEvidence.wasCollected
          ? RuntimeScenarioStatus.failedOrIncomplete
          : RuntimeScenarioStatus.notCollected,
    );
  }

  final FeatureScenario scenario;
  final ScenarioMapping? mapping;
  final List<EvidenceMarkerResult> markerResults;
  final RuntimeScenarioStatus runtimeStatus;

  Map<String, Object?> toJson() => <String, Object?>{
    'scenario': scenario.toJson(),
    'mapping': mapping?.toJson(),
    'markers': markerResults.map((marker) => marker.toJson()).toList(),
    'runtimeStatus': runtimeStatus.name,
  };
}

class EvidenceMarkerResult {
  const EvidenceMarkerResult({
    required this.marker,
    required this.sourcePresent,
    required this.runtimeObserved,
  });

  final String marker;
  final bool sourcePresent;
  final bool runtimeObserved;

  Map<String, Object?> toJson() => <String, Object?>{
    'marker': marker,
    'sourcePresent': sourcePresent,
    'runtimeObserved': runtimeObserved,
  };
}

enum RuntimeScenarioStatus {
  notCollected('not collected'),
  passed('passed'),
  failedOrIncomplete('failed or incomplete');

  const RuntimeScenarioStatus(this.label);

  final String label;
}

class RuntimeEvidence {
  const RuntimeEvidence({required this.wasCollected, required this.markers});

  factory RuntimeEvidence.notCollected() => const RuntimeEvidence(
    wasCollected: false,
    markers: <String, SanitizedEvidenceMarker>{},
  );

  final bool wasCollected;
  final Map<String, SanitizedEvidenceMarker> markers;

  Set<String> get observedMarkers => markers.keys.toSet();

  Map<String, Object?> toJson() => <String, Object?>{
    'wasCollected': wasCollected,
    'markers': markers.map((key, value) => MapEntry(key, value.toJson())),
  };
}

class SanitizedEvidenceMarker {
  const SanitizedEvidenceMarker({
    required this.marker,
    required this.count,
    required this.sanitizedFields,
  });

  final String marker;
  final int count;
  final Map<String, String> sanitizedFields;

  Map<String, Object?> toJson() => <String, Object?>{
    'marker': marker,
    'count': count,
    'sanitizedFields': sanitizedFields,
  };
}

class AcceptanceFinding {
  const AcceptanceFinding(this.message);

  final String message;

  Map<String, Object?> toJson() => <String, Object?>{'message': message};
}
