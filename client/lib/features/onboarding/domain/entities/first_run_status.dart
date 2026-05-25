enum FirstRunProvisioningState {
  notConfigured,
  pending,
  ready,
  degraded,
  failed,
}

class FirstRunIdentity {
  const FirstRunIdentity({
    required this.userId,
    required this.username,
    required this.emailVerified,
    required this.displayName,
    required this.locale,
    required this.timezone,
    this.email,
    this.roles = const <String>[],
    this.groups = const <String>[],
  });

  final String userId;
  final String username;
  final String? email;
  final bool emailVerified;
  final String displayName;
  final String locale;
  final String timezone;
  final List<String> roles;
  final List<String> groups;
}

class FirstRunInviteStatus {
  const FirstRunInviteStatus({
    required this.status,
    required this.message,
    this.action,
  });

  final String status;
  final String message;
  final String? action;
}

class FirstRunAccess {
  const FirstRunAccess({
    required this.primaryRole,
    required this.canAdministerWorkspace,
    required this.canInviteUsers,
    required this.canUseWorkspaceModules,
    this.roles = const <String>[],
    this.groups = const <String>[],
  });

  final String primaryRole;
  final List<String> roles;
  final List<String> groups;
  final bool canAdministerWorkspace;
  final bool canInviteUsers;
  final bool canUseWorkspaceModules;
}

class FirstRunProfileStatus {
  const FirstRunProfileStatus({
    required this.status,
    required this.missing,
    required this.message,
    this.action,
  });

  final String status;
  final List<String> missing;
  final String message;
  final String? action;

  bool get isReady => status == 'ready';
}

class FirstRunModuleStatus {
  const FirstRunModuleStatus({
    required this.state,
    required this.message,
    this.action,
  });

  final FirstRunProvisioningState state;
  final String message;
  final String? action;

  bool get isReady => state == FirstRunProvisioningState.ready;
  bool get needsUserVisibleAttention =>
      state != FirstRunProvisioningState.ready;
}

class FirstRunModuleProvisioning {
  const FirstRunModuleProvisioning({
    required this.identity,
    required this.profile,
    required this.matrix,
    required this.nextcloud,
  });

  final FirstRunModuleStatus identity;
  final FirstRunModuleStatus profile;
  final FirstRunModuleStatus matrix;
  final FirstRunModuleStatus nextcloud;

  // The backend contract still carries provider-shaped storage while member
  // surfaces consume stable Weave product modules.
  FirstRunModuleStatus get chat => matrix;
}

class FirstRunStatus {
  const FirstRunStatus({
    required this.identity,
    required this.invite,
    required this.access,
    required this.profile,
    required this.moduleProvisioning,
    required this.firstRunComplete,
    required this.actions,
  });

  final FirstRunIdentity identity;
  final FirstRunInviteStatus invite;
  final FirstRunAccess access;
  final FirstRunProfileStatus profile;
  final FirstRunModuleProvisioning moduleProvisioning;
  final bool firstRunComplete;
  final List<String> actions;
}
