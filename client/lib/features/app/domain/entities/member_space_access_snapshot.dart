/// The current member's durable Space admission from the User API.
class MemberSpaceAccessSnapshot {
  const MemberSpaceAccessSnapshot({
    required this.visibleSpaceRefs,
    required this.defaultSpaceReadable,
  });

  static const defaultSpaceRef = 'workspace-default';

  final Set<String> visibleSpaceRefs;
  final bool defaultSpaceReadable;

  bool get hasChatSpace => visibleSpaceRefs.isNotEmpty;
  bool get hasDefaultSpace =>
      visibleSpaceRefs.contains(defaultSpaceRef) && defaultSpaceReadable;
}
