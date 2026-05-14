import 'package:weave/features/shell/domain/entities/shell_module.dart';

class ShellModulePreferences {
  const ShellModulePreferences({
    this.hiddenModules = const <ShellModule>{},
    this.moduleOrder = ShellModule.defaultOrder,
  });

  final Set<ShellModule> hiddenModules;
  final List<ShellModule> moduleOrder;

  List<ShellModule> get orderedModules => _normalizeModuleOrder(moduleOrder);

  List<ShellModule> get visibleModules => orderedModules
      .where((module) => !hiddenModules.contains(module))
      .toList(growable: false);

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

    return ShellModulePreferences(
      hiddenModules: nextHiddenModules,
      moduleOrder: orderedModules,
    );
  }

  ShellModulePreferences moveModule({
    required ShellModule module,
    required int delta,
  }) {
    final nextOrder = orderedModules.toList(growable: true);
    final currentIndex = nextOrder.indexOf(module);
    if (currentIndex < 0) {
      return this;
    }

    final nextIndex = (currentIndex + delta).clamp(0, nextOrder.length - 1);
    if (nextIndex == currentIndex) {
      return this;
    }

    nextOrder
      ..removeAt(currentIndex)
      ..insert(nextIndex, module);

    return ShellModulePreferences(
      hiddenModules: hiddenModules,
      moduleOrder: nextOrder,
    );
  }

  static List<ShellModule> _normalizeModuleOrder(List<ShellModule> savedOrder) {
    final normalized = <ShellModule>[];
    for (final module in savedOrder) {
      if (!normalized.contains(module)) {
        normalized.add(module);
      }
    }
    for (final module in ShellModule.defaultOrder) {
      if (!normalized.contains(module)) {
        normalized.add(module);
      }
    }
    return normalized;
  }
}
