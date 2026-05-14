import 'package:weave/features/shell/domain/entities/shell_module.dart';

class ShellModulePreferences {
  const ShellModulePreferences({
    this.hiddenModules = defaultHiddenModules,
    this.moduleOrder = ShellModule.values,
  });

  static const defaultHiddenModules = <ShellModule>{
    ShellModule.workspaceStatus,
  };

  final Set<ShellModule> hiddenModules;
  final List<ShellModule> moduleOrder;

  List<ShellModule> get orderedModules => _normalizedOrder(moduleOrder);

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
    if (currentIndex == -1) {
      return this;
    }

    final nextIndex = (currentIndex + delta).clamp(0, nextOrder.length - 1);
    if (nextIndex == currentIndex) {
      return this;
    }

    final moved = nextOrder.removeAt(currentIndex);
    nextOrder.insert(nextIndex, moved);
    return ShellModulePreferences(
      hiddenModules: hiddenModules,
      moduleOrder: nextOrder,
    );
  }

  static List<ShellModule> _normalizedOrder(List<ShellModule> storedOrder) {
    final order = <ShellModule>[];
    for (final module in storedOrder) {
      if (!order.contains(module)) {
        order.add(module);
      }
    }
    for (final module in ShellModule.values) {
      if (!order.contains(module)) {
        order.add(module);
      }
    }
    return List<ShellModule>.unmodifiable(order);
  }
}
