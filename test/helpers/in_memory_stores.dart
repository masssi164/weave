import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/persistence/secure_store.dart';

class InMemoryPreferencesStore implements PreferencesStore {
  InMemoryPreferencesStore([Map<String, Object?> initialValues = const {}])
    : _values = Map<String, Object?>.from(initialValues);

  final Map<String, Object?> _values;

  @override
  Future<bool?> getBool(String key) async => _values[key] as bool?;

  @override
  Future<String?> getString(String key) async => _values[key] as String?;

  @override
  Future<void> setBool(String key, bool value) async {
    _values[key] = value;
  }

  @override
  Future<void> setString(String key, String value) async {
    _values[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    _values.remove(key);
  }

  String? rawString(String key) => _values[key] as String?;
}

class InMemorySecureStore implements SecureStore {
  InMemorySecureStore([Map<String, String> initialValues = const {}])
    : _values = Map<String, String>.from(initialValues);

  final Map<String, String> _values;

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async {
    _values[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    _values.remove(key);
  }

  String? rawValue(String key) => _values[key];
}
