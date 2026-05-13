import 'dart:convert';

import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/domain/entities/shell_module_preferences.dart';
import 'package:weave/features/shell/domain/repositories/shell_module_preferences_repository.dart';

const shellModulePreferencesStorageKey = 'shell.modulePreferences.v1';

class SharedPreferencesShellModulePreferencesRepository
    implements ShellModulePreferencesRepository {
  const SharedPreferencesShellModulePreferencesRepository({
    required PreferencesStore store,
  }) : _store = store;

  final PreferencesStore _store;

  @override
  Future<ShellModulePreferences> loadPreferences() async {
    final raw = await _store.getString(shellModulePreferencesStorageKey);
    if (raw == null || raw.isEmpty) {
      return const ShellModulePreferences();
    }

    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      final hidden = decoded['hiddenModules'];
      if (hidden is! List) {
        return const ShellModulePreferences();
      }

      return ShellModulePreferences(
        hiddenModules: hidden
            .whereType<String>()
            .map(ShellModule.fromStorageKey)
            .whereType<ShellModule>()
            .toSet(),
      );
    } on FormatException {
      return const ShellModulePreferences();
    } on TypeError {
      return const ShellModulePreferences();
    }
  }

  @override
  Future<void> savePreferences(ShellModulePreferences preferences) {
    final encoded = jsonEncode({
      'hiddenModules': preferences.hiddenModules
          .map((module) => module.storageKey)
          .toList(growable: false),
    });

    return _store.setString(shellModulePreferencesStorageKey, encoded);
  }
}
