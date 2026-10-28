/// Stable shell identity shared by route composition, navigation and semantics.
enum ShellDestination {
  home('home'),
  chat('chat', domainId: 'chat', requiresMemberSpaces: true),
  files('files', domainId: 'files', requiresMemberSpaces: true),
  calendar('calendar', domainId: 'calendar', requiresMemberSpaces: true),
  settings('settings');

  const ShellDestination(
    this.id, {
    this.domainId,
    this.requiresMemberSpaces = false,
  });

  final String id;
  final String? domainId;
  final bool requiresMemberSpaces;
}

List<ShellDestination> shellDestinationsForDomains(
  Set<String> installedDomains,
) => ShellDestination.values
    .where(
      (destination) =>
          destination.domainId == null ||
          installedDomains.contains(destination.domainId),
    )
    .toList(growable: false);
