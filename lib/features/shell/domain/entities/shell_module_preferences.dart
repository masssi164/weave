import 'package:weave/features/shell/domain/entities/shell_module.dart';

class ShellModulePreferences {
  const ShellModulePreferences({this.hiddenModules = const <ShellModule>{}});

  final Set<ShellModule> hiddenModules;

  bool isVisible(ShellModule module) => !hiddenModules.contains(module);

  ShellModulePreferences setVisibility({
    required ShellModule module,
    required bool isVisible,
  }) {
    final nextHiddenModules = Set<ShellModule>.of(hiddenModules);
    if (isVisible) {
      nextHiddenModules.remove(module);
    } else {
      nextHiddenModules.add(module);
    }

    return ShellModulePreferences(hiddenModules: nextHiddenModules);
  }
}
