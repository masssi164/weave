import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/persistence/preferences_store.dart';

part 'shared_preferences_store.g.dart';

class SharedPreferencesStore implements PreferencesStore {
  SharedPreferencesStore() : _prefs = SharedPreferences.getInstance();

  final Future<SharedPreferences> _prefs;

  @override
  Future<bool?> getBool(String key) async => (await _prefs).getBool(key);

  @override
  Future<String?> getString(String key) async => (await _prefs).getString(key);

  @override
  Future<void> setBool(String key, bool value) async {
    await (await _prefs).setBool(key, value);
  }

  @override
  Future<void> setString(String key, String value) async {
    await (await _prefs).setString(key, value);
  }

  @override
  Future<void> remove(String key) async {
    await (await _prefs).remove(key);
  }
}

@Riverpod(keepAlive: true)
PreferencesStore preferencesStore(Ref ref) => SharedPreferencesStore();
