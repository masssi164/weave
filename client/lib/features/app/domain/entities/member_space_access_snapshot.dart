/// The current member's durable Space admission from the User API.
class MemberSpaceAccessSnapshot {
  const MemberSpaceAccessSnapshot({
    required this.visibleSpaceRefs,
    required this.defaultSpaceReadable,
    this.displayNames = const {},
  });

  static const defaultSpaceRef = 'workspace-default';

  final Set<String> visibleSpaceRefs;
  final bool defaultSpaceReadable;
  final Map<String, String> displayNames;

  /// Names are presentation only. Stable references retain all admission meaning.
  String? displayNameFor(String spaceRef) =>
      visibleSpaceRefs.contains(spaceRef) ? displayNames[spaceRef] : null;

  int? displayOrdinalFor(String spaceRef) {
    final name = displayNameFor(spaceRef);
    if (name == null) return null;
    final duplicates =
        visibleSpaceRefs.where((ref) => displayNames[ref] == name).toList()
          ..sort();
    return duplicates.length > 1 ? duplicates.indexOf(spaceRef) + 1 : null;
  }

  bool get hasChatSpace => visibleSpaceRefs.isNotEmpty;
  bool get hasDefaultSpace =>
      visibleSpaceRefs.contains(defaultSpaceRef) && defaultSpaceReadable;
}
