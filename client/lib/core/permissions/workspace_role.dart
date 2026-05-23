/// Canonical Weave workspace roles exposed by the backend profile facade.
///
/// Keep this model small and explicit so admin-only UI gates do not depend on
/// ad-hoc string checks scattered through feature widgets. Backend endpoints
/// still enforce the authoritative permission boundary; Flutter uses this to
/// hide owner/admin setup affordances from normal users.
enum WorkspaceRole {
  owner('owner'),
  admin('admin'),
  operator('operator'),
  member('member'),
  guest('guest'),
  unknown('unknown');

  const WorkspaceRole(this.value);

  final String value;

  static WorkspaceRole parse(String value) {
    final normalized = value.trim().toLowerCase();
    return WorkspaceRole.values.firstWhere(
      (role) => role.value == normalized,
      orElse: () => WorkspaceRole.unknown,
    );
  }
}

extension WorkspaceRoleList on Iterable<String> {
  List<WorkspaceRole> get workspaceRoles => map(WorkspaceRole.parse)
      .where((role) => role != WorkspaceRole.unknown)
      .toSet()
      .toList(growable: false);

  bool get canAdministerWorkspace {
    final roles = workspaceRoles;
    return roles.contains(WorkspaceRole.owner) ||
        roles.contains(WorkspaceRole.admin) ||
        roles.contains(WorkspaceRole.operator);
  }

  bool get canInviteUsers => canAdministerWorkspace;

  String get primaryWorkspaceRole {
    final roles = workspaceRoles;
    for (final role in const <WorkspaceRole>[
      WorkspaceRole.owner,
      WorkspaceRole.admin,
      WorkspaceRole.operator,
      WorkspaceRole.member,
      WorkspaceRole.guest,
    ]) {
      if (roles.contains(role)) {
        return role.value;
      }
    }
    return WorkspaceRole.unknown.value;
  }
}
