import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/persistence/preferences_store.dart';

part 'shared_preferences_store.g.dart';

class SharedPreferencesStore implements PreferencesStore {
  SharedPreferencesStore() : _prefs = SharedPreferencesAsync();

  final SharedPreferencesAsync _prefs;

  @override
  Future<bool?> getBool(String key) => _prefs.getBool(key);

  @override
  Future<String?> getString(String key) => _prefs.getString(key);

  @override
  Future<void> setBool(String key, bool value) => _prefs.setBool(key, value);

  @override
  Future<void> setString(String key, String value) =>
      _prefs.setString(key, value);

  @override
  Future<void> remove(String key) => _prefs.remove(key);
}

@Riverpod(keepAlive: true)
PreferencesStore preferencesStore(Ref ref) => SharedPreferencesStore();
