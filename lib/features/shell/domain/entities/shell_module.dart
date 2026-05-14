enum ShellModule {
  workspaceOverview('workspaceOverview'),
  recentActivity('recentActivity');

  const ShellModule(this.storageKey);

  final String storageKey;

  static const defaultOrder = <ShellModule>[
    ShellModule.workspaceOverview,
    ShellModule.recentActivity,
  ];

  static ShellModule? fromStorageKey(String value) {
    for (final module in ShellModule.values) {
      if (module.storageKey == value) {
        return module;
      }
    }

    return null;
  }
}
