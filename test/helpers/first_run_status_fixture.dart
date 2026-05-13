import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';

FirstRunStatus buildTestFirstRunStatus({
  bool firstRunComplete = true,
  FirstRunProfileStatus profile = const FirstRunProfileStatus(
    status: 'ready',
    missing: [],
    message: 'The Weave profile has the required first-run identity fields.',
  ),
  FirstRunModuleStatus matrix = const FirstRunModuleStatus(
    state: FirstRunProvisioningState.ready,
    message: 'Matrix chat provisioning is ready for this user.',
  ),
  FirstRunModuleStatus nextcloud = const FirstRunModuleStatus(
    state: FirstRunProvisioningState.ready,
    message: 'Nextcloud files/calendar provisioning is ready for this user.',
  ),
  List<String> actions = const [],
}) {
  final profileModule = FirstRunModuleStatus(
    state: profile.isReady
        ? FirstRunProvisioningState.ready
        : FirstRunProvisioningState.pending,
    message: profile.message,
    action: profile.action,
  );

  return FirstRunStatus(
    identity: const FirstRunIdentity(
      userId: 'user-123',
      username: 'alice',
      email: 'alice@example.test',
      emailVerified: true,
      displayName: 'Alice Example',
      locale: 'en',
      timezone: 'Europe/Berlin',
      roles: ['member'],
      groups: ['workspace-default'],
    ),
    invite: const FirstRunInviteStatus(
      status: 'active',
      message: 'The invite has been accepted and the account is active.',
    ),
    access: const FirstRunAccess(
      primaryRole: 'member',
      roles: ['member'],
      groups: ['workspace-default'],
      canAdministerWorkspace: false,
      canInviteUsers: false,
      canUseWorkspaceModules: true,
    ),
    profile: profile,
    moduleProvisioning: FirstRunModuleProvisioning(
      identity: const FirstRunModuleStatus(
        state: FirstRunProvisioningState.ready,
        message: 'Identity is available from SSO.',
      ),
      profile: profileModule,
      matrix: matrix,
      nextcloud: nextcloud,
    ),
    firstRunComplete: firstRunComplete,
    actions: actions,
  );
}
