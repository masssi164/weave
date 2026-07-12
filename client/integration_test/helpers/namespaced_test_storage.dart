import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/persistence/secure_store.dart';

class NamespacedSecureStore implements SecureStore {
  NamespacedSecureStore({
    required this.namespace,
    required SecureStore delegate,
  }) : _delegate = delegate;

  final String namespace;
  final SecureStore _delegate;
  final Set<String> _touchedKeys = <String>{};

  String _key(String key) => '$namespace.$key';

  @override
  Future<void> delete(String key) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    await _delegate.delete(namespaced);
  }

  @override
  Future<String?> read(String key) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    return _delegate.read(namespaced);
  }

  @override
  Future<void> write(String key, String value) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    await _delegate.write(namespaced, value);
  }

  Future<void> removeTouchedKeys() async {
    for (final key in _touchedKeys.toList(growable: false)) {
      await _delegate.delete(key);
    }
    _touchedKeys.clear();
  }
}

class NamespacedPreferencesStore implements PreferencesStore {
  NamespacedPreferencesStore({
    required this.namespace,
    required PreferencesStore delegate,
  }) : _delegate = delegate;

  final String namespace;
  final PreferencesStore _delegate;
  final Set<String> _touchedKeys = <String>{};

  String _key(String key) => '$namespace.$key';

  @override
  Future<bool?> getBool(String key) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    return _delegate.getBool(namespaced);
  }

  @override
  Future<String?> getString(String key) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    return _delegate.getString(namespaced);
  }

  @override
  Future<void> remove(String key) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    await _delegate.remove(namespaced);
  }

  @override
  Future<void> setBool(String key, bool value) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    await _delegate.setBool(namespaced, value);
  }

  @override
  Future<void> setString(String key, String value) async {
    final namespaced = _key(key);
    _touchedKeys.add(namespaced);
    await _delegate.setString(namespaced, value);
  }

  Future<void> removeTouchedKeys() async {
    for (final key in _touchedKeys.toList(growable: false)) {
      await _delegate.remove(key);
    }
    _touchedKeys.clear();
  }
}
