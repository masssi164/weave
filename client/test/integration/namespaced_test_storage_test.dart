import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/persistence/secure_store.dart';

import '../../integration_test/helpers/namespaced_test_storage.dart';

void main() {
  test(
    'secure profile namespaces keys and deletes only touched keys',
    () async {
      final delegate = _MemorySecureStore(<String, String>{
        'unrelated.key': 'preserve-me',
        'weave.e2e.run.1.author.restored': 'old-value',
      });
      final store = NamespacedSecureStore(
        namespace: 'weave.e2e.run.1.author',
        delegate: delegate,
      );

      expect(await store.read('restored'), 'old-value');
      await store.write('session', 'token');
      expect(delegate.values['weave.e2e.run.1.author.session'], 'token');

      await store.removeTouchedKeys();

      expect(delegate.values['weave.e2e.run.1.author.restored'], isNull);
      expect(delegate.values['weave.e2e.run.1.author.session'], isNull);
      expect(delegate.values['unrelated.key'], 'preserve-me');
    },
  );

  test(
    'preferences profile preserves unrelated app data during cleanup',
    () async {
      final delegate = _MemoryPreferencesStore(<String, Object>{
        'unrelated.preference': true,
      });
      final store = NamespacedPreferencesStore(
        namespace: 'weave.e2e.run.2.collaborator',
        delegate: delegate,
      );

      await store.setString('configuration', 'saved');
      await store.setBool('setup', true);
      expect(await store.getString('configuration'), 'saved');
      expect(await store.getBool('setup'), isTrue);

      await store.removeTouchedKeys();

      expect(delegate.values['unrelated.preference'], isTrue);
      expect(delegate.values.keys, <String>['unrelated.preference']);
    },
  );
}

class _MemorySecureStore implements SecureStore {
  _MemorySecureStore(this.values);

  final Map<String, String> values;

  @override
  Future<void> delete(String key) async => values.remove(key);

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

class _MemoryPreferencesStore implements PreferencesStore {
  _MemoryPreferencesStore(this.values);

  final Map<String, Object> values;

  @override
  Future<bool?> getBool(String key) async => values[key] as bool?;

  @override
  Future<String?> getString(String key) async => values[key] as String?;

  @override
  Future<void> remove(String key) async => values.remove(key);

  @override
  Future<void> setBool(String key, bool value) async => values[key] = value;

  @override
  Future<void> setString(String key, String value) async => values[key] = value;
}
