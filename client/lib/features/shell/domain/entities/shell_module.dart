enum ShellModule {
  workspaceStatus('workspaceStatus'),
  recentActivity('recentActivity');

  const ShellModule(this.storageKey);

  final String storageKey;

  static ShellModule? fromStorageKey(String value) {
    for (final module in ShellModule.values) {
      if (module.storageKey == value) {
        return module;
      }
    }

    return null;
  }
}
