/// Stable shell identity shared by route composition, navigation and semantics.
enum ShellDestination {
  home('home'),
  chat('chat', requiresMemberSpaces: true),
  files('files', requiresMemberSpaces: true),
  calendar('calendar', requiresMemberSpaces: true),
  settings('settings');

  const ShellDestination(this.id, {this.requiresMemberSpaces = false});

  final String id;
  final bool requiresMemberSpaces;
}
