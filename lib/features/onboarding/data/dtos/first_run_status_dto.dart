import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';

class FirstRunStatusDto {
  const FirstRunStatusDto({
    required this.identity,
    required this.invite,
    required this.access,
    required this.profile,
    required this.moduleProvisioning,
    required this.firstRunComplete,
    required this.actions,
  });

  factory FirstRunStatusDto.fromJson(Map<String, dynamic> json) {
    return FirstRunStatusDto(
      identity: FirstRunIdentityDto.fromJson(_object(json, 'identity')),
      invite: FirstRunInviteStatusDto.fromJson(_object(json, 'invite')),
      access: FirstRunAccessDto.fromJson(_object(json, 'access')),
      profile: FirstRunProfileStatusDto.fromJson(_object(json, 'profile')),
      moduleProvisioning: FirstRunModuleProvisioningDto.fromJson(
        _object(json, 'moduleProvisioning'),
      ),
      firstRunComplete: _bool(json, 'firstRunComplete'),
      actions: _stringList(json['actions']),
    );
  }

  final FirstRunIdentityDto identity;
  final FirstRunInviteStatusDto invite;
  final FirstRunAccessDto access;
  final FirstRunProfileStatusDto profile;
  final FirstRunModuleProvisioningDto moduleProvisioning;
  final bool firstRunComplete;
  final List<String> actions;

  FirstRunStatus toDomain() {
    return FirstRunStatus(
      identity: identity.toDomain(),
      invite: invite.toDomain(),
      access: access.toDomain(),
      profile: profile.toDomain(),
      moduleProvisioning: moduleProvisioning.toDomain(),
      firstRunComplete: firstRunComplete,
      actions: actions,
    );
  }
}

class FirstRunIdentityDto {
  const FirstRunIdentityDto({
    required this.userId,
    required this.username,
    required this.emailVerified,
    required this.displayName,
    required this.locale,
    required this.timezone,
    required this.roles,
    required this.groups,
    this.email,
  });

  factory FirstRunIdentityDto.fromJson(Map<String, dynamic> json) {
    final username = _string(json, 'username');
    return FirstRunIdentityDto(
      userId: _string(json, 'userId'),
      username: username,
      email: json['email'] as String?,
      emailVerified: _bool(json, 'emailVerified'),
      displayName: _optionalString(json, 'displayName') ?? username,
      locale: _optionalString(json, 'locale') ?? 'en',
      timezone: _optionalString(json, 'timezone') ?? 'UTC',
      roles: _stringList(json['roles']),
      groups: _stringList(json['groups']),
    );
  }

  final String userId;
  final String username;
  final String? email;
  final bool emailVerified;
  final String displayName;
  final String locale;
  final String timezone;
  final List<String> roles;
  final List<String> groups;

  FirstRunIdentity toDomain() {
    return FirstRunIdentity(
      userId: userId,
      username: username,
      email: email,
      emailVerified: emailVerified,
      displayName: displayName,
      locale: locale,
      timezone: timezone,
      roles: roles,
      groups: groups,
    );
  }
}

class FirstRunInviteStatusDto {
  const FirstRunInviteStatusDto({
    required this.status,
    required this.message,
    this.action,
  });

  factory FirstRunInviteStatusDto.fromJson(Map<String, dynamic> json) {
    return FirstRunInviteStatusDto(
      status: _string(json, 'status'),
      message: _string(json, 'message'),
      action: _optionalString(json, 'action'),
    );
  }

  final String status;
  final String message;
  final String? action;

  FirstRunInviteStatus toDomain() {
    return FirstRunInviteStatus(
      status: status,
      message: message,
      action: action,
    );
  }
}

class FirstRunAccessDto {
  const FirstRunAccessDto({
    required this.primaryRole,
    required this.roles,
    required this.groups,
    required this.canAdministerWorkspace,
    required this.canInviteUsers,
    required this.canUseWorkspaceModules,
  });

  factory FirstRunAccessDto.fromJson(Map<String, dynamic> json) {
    return FirstRunAccessDto(
      primaryRole: _string(json, 'primaryRole'),
      roles: _stringList(json['roles']),
      groups: _stringList(json['groups']),
      canAdministerWorkspace: _bool(json, 'canAdministerWorkspace'),
      canInviteUsers: _bool(json, 'canInviteUsers'),
      canUseWorkspaceModules: _bool(json, 'canUseWorkspaceModules'),
    );
  }

  final String primaryRole;
  final List<String> roles;
  final List<String> groups;
  final bool canAdministerWorkspace;
  final bool canInviteUsers;
  final bool canUseWorkspaceModules;

  FirstRunAccess toDomain() {
    return FirstRunAccess(
      primaryRole: primaryRole,
      roles: roles,
      groups: groups,
      canAdministerWorkspace: canAdministerWorkspace,
      canInviteUsers: canInviteUsers,
      canUseWorkspaceModules: canUseWorkspaceModules,
    );
  }
}

class FirstRunProfileStatusDto {
  const FirstRunProfileStatusDto({
    required this.status,
    required this.missing,
    required this.message,
    this.action,
  });

  factory FirstRunProfileStatusDto.fromJson(Map<String, dynamic> json) {
    return FirstRunProfileStatusDto(
      status: _string(json, 'status'),
      missing: _stringList(json['missing']),
      message: _string(json, 'message'),
      action: _optionalString(json, 'action'),
    );
  }

  final String status;
  final List<String> missing;
  final String message;
  final String? action;

  FirstRunProfileStatus toDomain() {
    return FirstRunProfileStatus(
      status: status,
      missing: missing,
      message: message,
      action: action,
    );
  }
}

class FirstRunModuleProvisioningDto {
  const FirstRunModuleProvisioningDto({
    required this.identity,
    required this.profile,
    required this.matrix,
    required this.nextcloud,
  });

  factory FirstRunModuleProvisioningDto.fromJson(Map<String, dynamic> json) {
    return FirstRunModuleProvisioningDto(
      identity: FirstRunModuleStatusDto.fromJson(_object(json, 'identity')),
      profile: FirstRunModuleStatusDto.fromJson(_object(json, 'profile')),
      matrix: FirstRunModuleStatusDto.fromJson(_object(json, 'matrix')),
      nextcloud: FirstRunModuleStatusDto.fromJson(_object(json, 'nextcloud')),
    );
  }

  final FirstRunModuleStatusDto identity;
  final FirstRunModuleStatusDto profile;
  final FirstRunModuleStatusDto matrix;
  final FirstRunModuleStatusDto nextcloud;

  FirstRunModuleProvisioning toDomain() {
    return FirstRunModuleProvisioning(
      identity: identity.toDomain(),
      profile: profile.toDomain(),
      matrix: matrix.toDomain(),
      nextcloud: nextcloud.toDomain(),
    );
  }
}

class FirstRunModuleStatusDto {
  const FirstRunModuleStatusDto({
    required this.state,
    required this.message,
    this.action,
  });

  factory FirstRunModuleStatusDto.fromJson(Map<String, dynamic> json) {
    return FirstRunModuleStatusDto(
      state: _provisioningState(_string(json, 'state')),
      message: _string(json, 'message'),
      action: _optionalString(json, 'action'),
    );
  }

  final FirstRunProvisioningState state;
  final String message;
  final String? action;

  FirstRunModuleStatus toDomain() {
    return FirstRunModuleStatus(state: state, message: message, action: action);
  }
}

Map<String, dynamic> _object(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is Map<String, dynamic>) {
    return value;
  }
  throw AppFailure.unknown(
    'The Weave backend returned an invalid onboarding status payload.',
    cause: 'Expected object for $key.',
  );
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) {
    return value;
  }
  throw AppFailure.unknown(
    'The Weave backend returned an invalid onboarding status payload.',
    cause: 'Expected string for $key.',
  );
}

String? _optionalString(Map<String, dynamic> json, String key) {
  final value = json[key];
  return value is String && value.trim().isNotEmpty ? value : null;
}

bool _bool(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is bool) {
    return value;
  }
  throw AppFailure.unknown(
    'The Weave backend returned an invalid onboarding status payload.',
    cause: 'Expected boolean for $key.',
  );
}

List<String> _stringList(Object? value) {
  if (value is! List) {
    return const <String>[];
  }
  return value.whereType<String>().toList(growable: false);
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
