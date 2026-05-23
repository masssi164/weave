import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final matrixFile = File(
    '../docs/research/boards-provider-capability-matrix.json',
  );

  test('boards provider capability matrix stays machine readable', () async {
    final matrix = jsonDecode(await matrixFile.readAsString());

    expect(matrix, isA<Map<String, Object?>>());
    final root = matrix as Map<String, Object?>;
    expect(root['schema_version'], 1);
    expect(root['status'], 'v0.1-workspace-spike-contract');

    final adapterContract = _map(root, 'adapter_contract');
    final capabilityKeys = _stringList(
      adapterContract,
      'required_capability_keys',
    );
    expect(capabilityKeys, containsAll(_requiredCapabilityKeys));
    expect(
      _stringList(adapterContract, 'support_safe_errors'),
      containsAll(<String>[
        'unauthorized',
        'forbidden',
        'not_found',
        'conflict',
        'rate_limited',
        'offline',
        'validation',
        'provider_unavailable',
        'unknown',
      ]),
    );

    final allowedCapabilityValues = _stringList(
      adapterContract,
      'allowed_capability_values',
    );
    final providers = _list(root, 'providers');
    expect(providers, hasLength(3));

    final providersById = <String, Map<String, Object?>>{};
    for (final providerValue in providers) {
      expect(providerValue, isA<Map<String, Object?>>());
      final provider = providerValue as Map<String, Object?>;
      final id = _string(provider, 'id');
      providersById[id] = provider;

      expect(_string(provider, 'name'), isNotEmpty);
      expect(_int(provider, 'issue'), isIn(<int>[119, 120, 121]));
      expect(
        _string(provider, 'result'),
        isIn(<String>['closes', 'refs_partial']),
      );
      expect(
        _string(provider, 'recommendation'),
        isIn(<String>['first_candidate', 'benchmark_only', 'bridge_adapter']),
      );
      expect(_string(provider, 'acceptance_summary'), isNotEmpty);

      final capabilities = _map(provider, 'capabilities');
      expect(capabilities.keys, containsAll(capabilityKeys));
      for (final key in capabilityKeys) {
        expect(
          _string(capabilities, key),
          isIn(allowedCapabilityValues),
          reason: '$id.$key must use the documented capability vocabulary',
        );
      }

      final sourceUrls = _stringList(provider, 'source_urls');
      expect(sourceUrls, isNotEmpty, reason: '$id has cited sources');
      for (final sourceUrl in sourceUrls) {
        expect(
          sourceUrl,
          startsWith('https://'),
          reason: '$id source URLs must be explicit external references',
        );
      }

      final gaps = _list(provider, 'gaps');
      expect(gaps, isNotEmpty, reason: '$id documents adapter gaps');
      for (final gapValue in gaps) {
        expect(gapValue, isA<Map<String, Object?>>());
        final gap = gapValue as Map<String, Object?>;
        expect(_string(gap, 'category'), isNotEmpty);
        expect(_string(gap, 'summary'), isNotEmpty);
      }
    }

    expect(
      providersById.keys,
      containsAll(<String>['vikunja', 'openproject', 'nextcloud-deck']),
    );
    expect(
      _string(providersById['vikunja']!, 'recommendation'),
      'first_candidate',
    );
    expect(
      _string(providersById['openproject']!, 'recommendation'),
      'benchmark_only',
    );
    expect(
      _string(providersById['nextcloud-deck']!, 'recommendation'),
      'bridge_adapter',
    );
  });

  test('provider spike notes reference the capability fixture', () async {
    final notes = await File(
      '../docs/research/boards-provider-spikes-119-121.md',
    ).readAsString();
    final domainContract = await File(
      '../docs/research/boards-task-domain-contract.md',
    ).readAsString();
    final strategy = await File(
      '../docs/research/boards-task-module-provider-strategy.md',
    ).readAsString();

    expect(notes, contains('boards-provider-capability-matrix.json'));
    expect(notes, contains('#119 Vikunja spike'));
    expect(notes, contains('#120 OpenProject accessibility benchmark'));
    expect(notes, contains('#121 Nextcloud Deck bridge spike'));
    expect(
      notes,
      contains('Vikunja remains the first implementation candidate'),
    );
    expect(notes, contains('Keep OpenProject benchmark-only for now'));
    expect(notes, contains('Deck is suitable as a low-friction bridge'));

    expect(domainContract, contains('boards-provider-capability-matrix.json'));
    expect(strategy, contains('boards-provider-spikes-119-121.md'));
  });
}

const _requiredCapabilityKeys = <String>[
  'projects_boards',
  'columns',
  'tasks_cards',
  'comments',
  'labels',
  'due_dates',
  'attachments',
  'auth_api_tokens',
  'pagination',
  'incremental_sync',
  'webhook_events',
  'export_portability',
  'non_drag_ui_baseline',
  'provider_neutral_mapping',
  'backend_adapter_required',
];

Map<String, Object?> _map(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<Map<String, Object?>>(), reason: key);
  return value! as Map<String, Object?>;
}

List<Object?> _list(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<List<Object?>>(), reason: key);
  return value! as List<Object?>;
}

List<String> _stringList(Map<String, Object?> source, String key) {
  final values = _list(source, key);
  for (final value in values) {
    expect(value, isA<String>(), reason: key);
  }
  return values.cast<String>();
}

String _string(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<String>(), reason: key);
  return value! as String;
}

int _int(Map<String, Object?> source, String key) {
  final value = source[key];
  expect(value, isA<int>(), reason: key);
  return value! as int;
}
