import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension OnboardingStatusResponseMapper on openapi.OnboardingStatusResponse {
  FirstRunStatus toDomain() {
    final identity = this.identity;
    final invite = this.invite;
    final access = this.access;
    final profile = this.profile;
    final moduleProvisioning = this.moduleProvisioning;

    if (identity == null ||
        invite == null ||
        access == null ||
        profile == null ||
        moduleProvisioning == null ||
        firstRunComplete == null) {
      throw const AppFailure.unknown(
        'The Weave backend returned an invalid onboarding status payload.',
        cause: 'Missing required onboarding status fields.',
      );
    }

    return FirstRunStatus(
      identity: identity.toDomain(),
      invite: invite.toDomain(),
      access: access.toDomain(),
      profile: profile.toFirstRunDomain(),
      moduleProvisioning: moduleProvisioning.toDomain(),
      firstRunComplete: firstRunComplete!,
      actions: actions ?? const <String>[],
    );
  }
}

extension _IdentityMapper on openapi.Identity {
  FirstRunIdentity toDomain() {
    final requiredUsername = _requiredString(username, 'identity.username');
    return FirstRunIdentity(
      userId: _requiredString(userId, 'identity.userId'),
      username: requiredUsername,
      email: email,
      emailVerified: _requiredBool(emailVerified, 'identity.emailVerified'),
      displayName: _optionalNonBlank(displayName) ?? requiredUsername,
      locale: _optionalNonBlank(locale) ?? 'en',
      timezone: _optionalNonBlank(timezone) ?? 'UTC',
      roles: roles ?? const <String>[],
      groups: groups ?? const <String>[],
    );
  }
}

extension _InviteStatusMapper on openapi.InviteStatus {
  FirstRunInviteStatus toDomain() {
    return FirstRunInviteStatus(
      status: _requiredString(status, 'invite.status'),
      message: _requiredString(message, 'invite.message'),
      action: _optionalNonBlank(action),
    );
  }
}

extension _AccessMapper on openapi.Access {
  FirstRunAccess toDomain() {
    return FirstRunAccess(
      primaryRole: _requiredString(primaryRole, 'access.primaryRole'),
      roles: roles ?? const <String>[],
      groups: groups ?? const <String>[],
      canAdministerWorkspace: _requiredBool(
        canAdministerWorkspace,
        'access.canAdministerWorkspace',
      ),
      canInviteUsers: _requiredBool(canInviteUsers, 'access.canInviteUsers'),
      canUseWorkspaceModules: _requiredBool(
        canUseWorkspaceModules,
        'access.canUseWorkspaceModules',
      ),
    );
  }
}

extension _ProfileStatusMapper on openapi.ProfileStatus {
  FirstRunProfileStatus toFirstRunDomain() {
    return FirstRunProfileStatus(
      status: _requiredString(status, 'profile.status'),
      missing: missing ?? const <String>[],
      message: _requiredString(message, 'profile.message'),
      action: _optionalNonBlank(action),
    );
  }
}

extension _ModuleProvisioningMapper on openapi.ModuleProvisioning {
  FirstRunModuleProvisioning toDomain() {
    return FirstRunModuleProvisioning(
      identity: _requiredModule(identity, 'moduleProvisioning.identity'),
      profile: _requiredModule(profile, 'moduleProvisioning.profile'),
      matrix: _requiredModule(matrix, 'moduleProvisioning.matrix'),
      nextcloud: _requiredModule(nextcloud, 'moduleProvisioning.nextcloud'),
    );
  }
}

FirstRunModuleStatus _requiredModule(openapi.ModuleStatus? value, String path) {
  if (value == null) {
    throw AppFailure.unknown(
      'The Weave backend returned an invalid onboarding status payload.',
      cause: 'Missing required $path.',
    );
  }
  return value.toFirstRunDomain(path);
}

extension _ModuleStatusMapper on openapi.ModuleStatus {
  FirstRunModuleStatus toFirstRunDomain(String path) {
    return FirstRunModuleStatus(
      state: _provisioningState(_requiredString(state, '$path.state')),
      message: _requiredString(message, '$path.message'),
      action: _optionalNonBlank(action),
    );
  }
}

String _requiredString(String? value, String path) {
  if (value != null) {
    return value;
  }
  throw AppFailure.unknown(
    'The Weave backend returned an invalid onboarding status payload.',
    cause: 'Expected string for $path.',
  );
}

bool _requiredBool(bool? value, String path) {
  if (value != null) {
    return value;
  }
  throw AppFailure.unknown(
    'The Weave backend returned an invalid onboarding status payload.',
    cause: 'Expected boolean for $path.',
  );
}

String? _optionalNonBlank(String? value) {
  return value != null && value.trim().isNotEmpty ? value : null;
}

FirstRunProvisioningState _provisioningState(String rawValue) {
  return switch (rawValue.trim()) {
    'not_configured' => FirstRunProvisioningState.notConfigured,
    'pending' => FirstRunProvisioningState.pending,
    'ready' => FirstRunProvisioningState.ready,
    'degraded' => FirstRunProvisioningState.degraded,
    'failed' => FirstRunProvisioningState.failed,
    _ => throw AppFailure.unknown(
      'The Weave backend returned an unknown onboarding provisioning state.',
      cause: rawValue,
    ),
  };
}
