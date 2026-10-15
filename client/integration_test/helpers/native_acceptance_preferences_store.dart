import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';

/// Keeps actual native-test persistence separate from the operator's profile.
class NativeAcceptancePreferencesStore implements PreferencesStore {
  NativeAcceptancePreferencesStore({
    required String runId,
    required PreferencesStore delegate,
    required Future<Set<String>> Function() keys,
  }) : _delegate = delegate,
       _keys = keys {
    if (RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$').stringMatch(runId) !=
        runId) {
      throw ArgumentError('An owned native storage run ID is required.');
    }
    _prefix = 'weave.native-acceptance.${runId.length}:$runId.';
  }

  factory NativeAcceptancePreferencesStore.production(String runId) =>
      NativeAcceptancePreferencesStore(
        runId: runId,
        delegate: SharedPreferencesStore(),
        keys: () => SharedPreferencesAsync().getKeys(),
      );

  final PreferencesStore _delegate;
  final Future<Set<String>> Function() _keys;
  late final String _prefix;

  @override
  Future<bool?> getBool(String key) => _delegate.getBool('$_prefix$key');
  @override
  Future<String?> getString(String key) => _delegate.getString('$_prefix$key');
  @override
  Future<void> setBool(String key, bool value) =>
      _delegate.setBool('$_prefix$key', value);
  @override
  Future<void> setString(String key, String value) =>
      _delegate.setString('$_prefix$key', value);
  @override
  Future<void> remove(String key) => _delegate.remove('$_prefix$key');

  Future<void> clearOwnedKeys() async {
    for (final key in await _keys()) {
      if (key.startsWith(_prefix)) await _delegate.remove(key);
    }
  }
}
