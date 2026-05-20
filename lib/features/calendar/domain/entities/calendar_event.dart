class CalendarScope {
  const CalendarScope({
    this.id = 'workspace',
    required this.type,
    required this.label,
    this.workspaceId = 'workspace',
    this.contextId = 'workspace-default',
    this.teamId,
    this.channelId,
    this.accessModel = 'shared-workspace-calendar',
    this.capabilities = const [],
  });

  static const workspace = CalendarScope(
    id: 'workspace',
    type: 'workspace',
    label: 'Weave workspace calendar',
    contextId: 'workspace-default',
    accessModel: 'shared-workspace-calendar',
  );

  final String id;
  final String type;
  final String label;
  final String workspaceId;
  final String contextId;
  final String? teamId;
  final String? channelId;
  final String accessModel;
  final List<String> capabilities;

  bool get isWorkspace => type == workspace.type;
  bool get isTeam => type == 'team';
  bool get isChannel => type == 'channel';

  Map<String, Object?> toJson() => {
    'id': id,
    'type': type,
    'label': label,
    'workspaceId': workspaceId,
    'contextId': contextId,
    if (teamId != null) 'teamId': teamId,
    if (channelId != null) 'channelId': channelId,
    'accessModel': accessModel,
    'capabilities': capabilities,
  };

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarScope &&
          other.id == id &&
          other.type == type &&
          other.label == label &&
          other.workspaceId == workspaceId &&
          other.contextId == contextId &&
          other.teamId == teamId &&
          other.channelId == channelId;

  @override
  int get hashCode =>
      Object.hash(id, type, label, workspaceId, contextId, teamId, channelId);
}

class CalendarThreadRef {
  const CalendarThreadRef({
    this.kind = 'context',
    required this.contextId,
    this.meetingThreadId,
    this.channelId,
    this.matrixRoomId,
    this.matrixThreadId,
    this.boardTaskIds = const [],
  });

  factory CalendarThreadRef.forScope(CalendarScope scope) => CalendarThreadRef(
    contextId: scope.contextId,
    channelId: scope.isChannel ? scope.channelId : null,
  );

  final String kind;
  final String contextId;
  final String? meetingThreadId;
  final String? channelId;
  final String? matrixRoomId;
  final String? matrixThreadId;
  final List<String> boardTaskIds;
}

class CalendarAttendee {
  const CalendarAttendee({
    this.name,
    this.email,
    this.role,
    this.responseStatus,
  });

  final String? name;
  final String? email;
  final String? role;
  final String? responseStatus;

  String get displayLabel {
    final displayName = name ?? email ?? 'Unknown attendee';
    final address = email != null && email != name ? ' <$email>' : '';
    final status = responseStatus == null ? '' : ' · $responseStatus';
    return '$displayName$address$status';
  }
}

class CalendarProviderRef {
  const CalendarProviderRef({
    required this.provider,
    required this.objectKind,
    this.opaqueId,
    this.etag,
    this.lastSyncedAt,
    this.rawProviderPathExposed = false,
  });

  final String provider;
  final String objectKind;
  final String? opaqueId;
  final String? etag;
  final DateTime? lastSyncedAt;
  final bool rawProviderPathExposed;
}

class CalendarScopeList {
  const CalendarScopeList({this.scopes = const [CalendarScope.workspace]});

  final List<CalendarScope> scopes;
}

class CalendarEventList {
  const CalendarEventList({
    this.scope = CalendarScope.workspace,
    this.events = const [],
  });

  final CalendarScope scope;
  final List<CalendarEvent> events;
}

class CalendarExternalEndpoints {
  const CalendarExternalEndpoints({
    required this.serverUrl,
    required this.caldavDiscoveryUrl,
    required this.principalUrl,
  });

  final String serverUrl;
  final String caldavDiscoveryUrl;
  final String principalUrl;
}

class CalendarAccessModel {
  const CalendarAccessModel({
    required this.type,
    required this.productScope,
    required this.privateUserCalendarsAvailable,
    required this.privateUserCalendarsReason,
    required this.externalClientCredentialModel,
    this.notes = const [],
  });

  static const workspaceBlockedPrivateCalendars = CalendarAccessModel(
    type: 'workspace-calendar',
    productScope: 'workspace',
    privateUserCalendarsAvailable: false,
    privateUserCalendarsReason:
        'Private personal calendars are out of scope for Weave shared scheduling.',
    externalClientCredentialModel: 'secret-free-setup-metadata',
  );

  final String type;
  final String productScope;
  final bool privateUserCalendarsAvailable;
  final String privateUserCalendarsReason;
  final String externalClientCredentialModel;
  final List<String> notes;
}

class CalendarCredentialReadiness {
  const CalendarCredentialReadiness({
    required this.status,
    required this.appleProfileSigned,
    required this.appleProfilePasswordIncluded,
    required this.revocableCredentialsAvailable,
    required this.readOnlySubscriptionTokensAvailable,
    required this.backendActorCredentialsExposed,
    this.blockers = const [],
  });

  static const blockedUntilRevocableCredentials = CalendarCredentialReadiness(
    status: 'blocked_until_revocable_credentials',
    appleProfileSigned: false,
    appleProfilePasswordIncluded: false,
    revocableCredentialsAvailable: false,
    readOnlySubscriptionTokensAvailable: false,
    backendActorCredentialsExposed: false,
  );

  final String status;
  final bool appleProfileSigned;
  final bool appleProfilePasswordIncluded;
  final bool revocableCredentialsAvailable;
  final bool readOnlySubscriptionTokensAvailable;
  final bool backendActorCredentialsExposed;
  final List<String> blockers;
}

class CalendarClientSetupOption {
  const CalendarClientSetupOption({
    required this.platform,
    required this.method,
    required this.available,
    this.actionUrl,
    this.unavailableReason,
    this.guidance = const [],
  });

  final String platform;
  final String method;
  final bool available;
  final String? actionUrl;
  final String? unavailableReason;
  final List<String> guidance;
}

class CalendarClientSetup {
  const CalendarClientSetup({
    required this.scope,
    required this.username,
    required this.endpoints,
    required this.credentialPolicy,
    required this.options,
    this.accessModel = CalendarAccessModel.workspaceBlockedPrivateCalendars,
    this.credentialReadiness =
        CalendarCredentialReadiness.blockedUntilRevocableCredentials,
  });

  final CalendarScope scope;
  final String username;
  final CalendarExternalEndpoints endpoints;
  final String credentialPolicy;
  final CalendarAccessModel accessModel;
  final CalendarCredentialReadiness credentialReadiness;
  final List<CalendarClientSetupOption> options;
}

class CalendarEvent {
  CalendarEvent({
    required this.id,
    required this.title,
    required this.startTime,
    required this.endTime,
    this.description,
    this.timezone,
    this.location,
    this.allDay = false,
    this.etag,
    this.scope = CalendarScope.workspace,
    CalendarThreadRef? threadRef,
    this.attendees = const [],
    this.providerRef,
    this.updatedAt,
  }) : threadRef = threadRef ?? CalendarThreadRef.forScope(scope);

  final String id;
  final String title;
  final String? description;
  final DateTime startTime;
  final DateTime endTime;
  final String? timezone;
  final String? location;
  final bool allDay;
  final String? etag;
  final CalendarScope scope;
  final CalendarThreadRef threadRef;
  final List<CalendarAttendee> attendees;
  final CalendarProviderRef? providerRef;
  final DateTime? updatedAt;
}

class CalendarEventDraft {
  const CalendarEventDraft({
    required this.title,
    required this.startTime,
    required this.endTime,
    required this.timezone,
    this.description,
    this.location,
    this.allDay = false,
    this.scope = CalendarScope.workspace,
  });

  final String title;
  final String? description;
  final DateTime startTime;
  final DateTime endTime;
  final String timezone;
  final String? location;
  final bool allDay;
  final CalendarScope scope;

  CalendarEventPatch toPatch({String? etag}) {
    return CalendarEventPatch(
      title: title,
      description: description,
      startTime: startTime,
      endTime: endTime,
      timezone: timezone,
      location: location,
      allDay: allDay,
      etag: etag,
      scope: scope,
    );
  }

  Map<String, Object?> toJson() => {
    'title': title,
    'description': description,
    'startsAt': startTime.toUtc().toIso8601String(),
    'endsAt': endTime.toUtc().toIso8601String(),
    'timezone': timezone,
    'location': location,
    'allDay': allDay,
    'scope': scope.toJson(),
  };
}

class CalendarEventPatch {
  const CalendarEventPatch({
    this.title,
    this.description,
    this.startTime,
    this.endTime,
    this.timezone,
    this.location,
    this.allDay,
    this.etag,
    this.scope,
  });

  final String? title;
  final String? description;
  final DateTime? startTime;
  final DateTime? endTime;
  final String? timezone;
  final String? location;
  final bool? allDay;
  final String? etag;
  final CalendarScope? scope;

  Map<String, Object?> toJson() => {
    if (title != null) 'title': title,
    if (description != null) 'description': description,
    if (startTime != null) 'startsAt': startTime!.toUtc().toIso8601String(),
    if (endTime != null) 'endsAt': endTime!.toUtc().toIso8601String(),
    if (timezone != null) 'timezone': timezone,
    if (location != null) 'location': location,
    if (allDay != null) 'allDay': allDay,
    if (etag != null) 'etag': etag,
    if (scope != null) 'scope': scope!.toJson(),
  };
}
