import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/preferences_store.dart';

import '../../integration_test/helpers/native_acceptance_preferences_store.dart';
import '../../integration_test/helpers/test_config.dart';

class _Store implements PreferencesStore {
  final values = <String, Object>{};
  @override
  Future<bool?> getBool(String key) async => values[key] as bool?;
  @override
  Future<String?> getString(String key) async => values[key] as String?;
  @override
  Future<void> setBool(String key, bool value) async {
    values[key] = value;
  }

  @override
  Future<void> setString(String key, String value) async {
    values[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    values.remove(key);
  }
}

void main() {
  test(
    'owned namespace persists across adapters and only clears its own keys',
    () async {
      final backing = _Store();
      await backing.setString('canonical-profile', 'operator sentinel');
      NativeAcceptancePreferencesStore owned(String run) =>
          NativeAcceptancePreferencesStore(
            runId: run,
            delegate: backing,
            keys: () async => backing.values.keys.toSet(),
          );
      final initial = owned('run-a');
      final other = owned('run-a.child');
      expect(await initial.getString('canonical-profile'), isNull);
      await initial.setString('canonical-profile', 'discovered profile');
      await initial.setBool('navigation', true);
      await other.setString('canonical-profile', 'other run');
      final restored = owned('run-a');
      expect(
        await restored.getString('canonical-profile'),
        'discovered profile',
      );
      expect(await restored.getBool('navigation'), isTrue);
      expect(await backing.getString('canonical-profile'), 'operator sentinel');
      await restored.clearOwnedKeys();
      expect(await initial.getString('canonical-profile'), isNull);
      expect(await other.getString('canonical-profile'), 'other run');
      expect(await backing.getString('canonical-profile'), 'operator sentinel');
    },
  );

  test('unsafe run IDs cannot acquire a preference namespace', () {
    for (final run in ['', '../other', 'run with spaces', 'a/b', 'run\n']) {
      expect(
        () => NativeAcceptancePreferencesStore(
          runId: run,
          delegate: _Store(),
          keys: () async => {},
        ),
        throwsArgumentError,
      );
    }
  });

  test('private canonical origin accepts only HTTPS root origins', () {
    expect(
      TestConfig.parseOrganizationOrigin('https://weave.example/').origin,
      'https://weave.example',
    );
    for (final value in [
      'http://weave.example',
      'https://u:p@weave.example',
      'https://weave.example/api',
      'https://weave.example?config=other',
      'https://weave.example/#fragment',
      'https://weave.example:0',
      'https://weave.example:65536',
      ' https://weave.example',
    ]) {
      expect(
        () => TestConfig.parseOrganizationOrigin(value),
        throwsArgumentError,
      );
    }
  });
}
