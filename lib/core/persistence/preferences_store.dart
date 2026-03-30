/// Non-secure persistence abstraction for harmless app configuration.
abstract interface class PreferencesStore {
  Future<bool?> getBool(String key);

  Future<String?> getString(String key);

  Future<void> setBool(String key, bool value);

  Future<void> setString(String key, String value);

  Future<void> remove(String key);
}
