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
      return ShellModulePreferences(
        hiddenModules: _readModules(
          decoded['hiddenModules'],
          fallback: ShellModulePreferences.defaultHiddenModules,
        ).toSet(),
        moduleOrder: _readModules(
          decoded['moduleOrder'],
          fallback: ShellModule.values,
        ),
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
      'moduleOrder': preferences.orderedModules
          .map((module) => module.storageKey)
          .toList(growable: false),
    });

    return _store.setString(shellModulePreferencesStorageKey, encoded);
  }

  List<ShellModule> _readModules(
    Object? raw, {
    required Iterable<ShellModule> fallback,
  }) {
    if (raw is! List) {
      return fallback.toList(growable: false);
    }

    return raw
        .whereType<String>()
        .map(ShellModule.fromStorageKey)
        .whereType<ShellModule>()
        .toList(growable: false);
  }
}
