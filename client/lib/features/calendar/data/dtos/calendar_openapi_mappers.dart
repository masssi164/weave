import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension CalendarScopesOpenApiMapper on openapi.CalendarScopesResponse {
  CalendarScopeList toDomain() {
    final mapped = scopes
        ?.map((scope) => scope.toDomain())
        .toList(growable: false);
    return CalendarScopeList(
      scopes: mapped == null || mapped.isEmpty
          ? const [CalendarScope.workspace]
          : mapped,
    );
  }
}

extension CalendarClientSetupOpenApiMapper
    on openapi.CalendarClientSetupResponse {
  CalendarClientSetup toDomain() {
    return CalendarClientSetup(
      scope: scope?.toDomain() ?? CalendarScope.workspace,
      username: _requiredString(username, 'calendar setup username'),
      endpoints: endpoints?.toDomain() ?? _missingEndpoints(),
      credentialPolicy: _requiredString(
        credentialPolicy,
        'calendar credential policy',
      ),
      accessModel:
          accessModel?.toDomain() ??
          CalendarAccessModel.workspaceBlockedPrivateCalendars,
      credentialReadiness:
          credentialReadiness?.toDomain() ??
          CalendarCredentialReadiness.revocableCredentialsReady,
      options:
          options?.map((option) => option.toDomain()).toList(growable: false) ??
          const [],
    );
  }
}

extension CalendarScopeOpenApiMapper on openapi.CalendarScopeResponse {
  CalendarScope toDomain({
    CalendarScope defaultScope = CalendarScope.workspace,
  }) {
    final scopeType = _optionalString(type);
    if (scopeType == null) {
      return defaultScope;
    }
    final team = _optionalString(teamId);
    final channel = _optionalString(channelId);
    return CalendarScope(
      id: _optionalString(id) ?? _fallbackScopeId(scopeType, team, channel),
      type: scopeType,
      label: _optionalString(label) ?? _fallbackScopeLabel(scopeType),
      workspaceId: _optionalString(workspaceId) ?? defaultScope.workspaceId,
      contextId:
          _optionalString(contextId) ??
          _fallbackContextId(scopeType, teamId: team, channelId: channel),
      teamId: team,
      channelId: channel,
      accessModel: _optionalString(accessModel) ?? defaultScope.accessModel,
      capabilities: capabilities ?? const [],
    );
  }
}

extension CalendarExternalEndpointsOpenApiMapper
    on openapi.CalendarExternalEndpointsResponse {
  CalendarExternalEndpoints toDomain() {
    return CalendarExternalEndpoints(
      serverUrl: _requiredString(serverUrl, 'calendar server URL'),
      caldavDiscoveryUrl: _requiredString(
        caldavDiscoveryUrl,
        'calendar discovery URL',
      ),
      principalUrl: _requiredString(principalUrl, 'calendar principal URL'),
    );
  }
}

extension CalendarAccessModelOpenApiMapper
    on openapi.CalendarAccessModelResponse {
  CalendarAccessModel toDomain() {
    return CalendarAccessModel(
      type: _requiredString(type, 'calendar access model type'),
      productScope: _requiredString(productScope, 'calendar product scope'),
      privateUserCalendarsAvailable: privateUserCalendarsAvailable == true,
      privateUserCalendarsReason: _requiredString(
        privateUserCalendarsReason,
        'calendar private calendar reason',
      ),
      externalClientCredentialModel: _requiredString(
        externalClientCredentialModel,
        'calendar credential model',
      ),
      notes: notes ?? const [],
    );
  }
}

extension CalendarCredentialReadinessOpenApiMapper
    on openapi.CalendarCredentialReadinessResponse {
  CalendarCredentialReadiness toDomain() {
    return CalendarCredentialReadiness(
      status: _requiredString(status, 'calendar credential readiness status'),
      appleProfileSigned: appleProfileSigned == true,
      appleProfilePasswordIncluded: appleProfilePasswordIncluded == true,
      revocableCredentialsAvailable: revocableCredentialsAvailable == true,
      readOnlySubscriptionTokensAvailable:
          readOnlySubscriptionTokensAvailable == true,
      backendActorCredentialsExposed: backendActorCredentialsExposed == true,
      blockers: blockers ?? const [],
    );
  }
}

extension CalendarClientSetupOptionOpenApiMapper
    on openapi.CalendarClientSetupOptionResponse {
  CalendarClientSetupOption toDomain() {
    return CalendarClientSetupOption(
      platform: _requiredString(platform, 'calendar setup platform'),
      method: _requiredString(method, 'calendar setup method'),
      available: available == true,
      actionUrl: _optionalString(actionUrl),
      unavailableReason: _optionalString(unavailableReason),
      guidance: notes ?? const [],
    );
  }
}

CalendarExternalEndpoints _missingEndpoints() {
  throw const AppFailure.unknown(
    'The Weave backend returned calendar setup data without endpoints.',
  );
}

String _requiredString(String? value, String label) {
  final trimmed = value?.trim();
  if (trimmed != null && trimmed.isNotEmpty) {
    return trimmed;
  }
  throw AppFailure.unknown('The Weave backend returned $label as empty.');
}

String? _optionalString(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String _fallbackScopeId(String type, String? teamId, String? channelId) {
  return switch (type) {
    'team' => teamId == null ? 'team' : 'team:$teamId',
    'channel' => channelId == null ? 'channel' : 'channel:$channelId',
    _ => CalendarScope.workspace.id,
  };
}

String _fallbackScopeLabel(String type) {
  return switch (type) {
    'workspace' => CalendarScope.workspace.label,
    _ => type,
  };
}

String _fallbackContextId(String type, {String? teamId, String? channelId}) {
  return switch (type) {
    'team' => 'team-${teamId ?? 'engineering'}',
    'channel' => 'channel-${channelId ?? 'engineering-general'}',
    _ => 'workspace-default',
  };
}
