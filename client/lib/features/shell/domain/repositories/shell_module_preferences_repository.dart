import 'package:weave/features/shell/domain/entities/shell_module_preferences.dart';

abstract interface class ShellModulePreferencesRepository {
  Future<ShellModulePreferences> loadPreferences();

  Future<void> savePreferences(ShellModulePreferences preferences);
}
