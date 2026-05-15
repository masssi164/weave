class CalendarScope {
  const CalendarScope({required this.type, required this.label});

  static const workspace = CalendarScope(
    type: 'workspace',
    label: 'Weave workspace calendar',
  );

  final String type;
  final String label;

  bool get isWorkspace => type == workspace.type;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarScope && other.type == type && other.label == label;

  @override
  int get hashCode => Object.hash(type, label);
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
  const CalendarEvent({
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
  });

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
  });

  final String title;
  final String? description;
  final DateTime startTime;
  final DateTime endTime;
  final String timezone;
  final String? location;
  final bool allDay;

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
  });

  final String? title;
  final String? description;
  final DateTime? startTime;
  final DateTime? endTime;
  final String? timezone;
  final String? location;
  final bool? allDay;
  final String? etag;

  Map<String, Object?> toJson() => {
    if (title != null) 'title': title,
    if (description != null) 'description': description,
    if (startTime != null) 'startsAt': startTime!.toUtc().toIso8601String(),
    if (endTime != null) 'endsAt': endTime!.toUtc().toIso8601String(),
    if (timezone != null) 'timezone': timezone,
    if (location != null) 'location': location,
    if (allDay != null) 'allDay': allDay,
    if (etag != null) 'etag': etag,
  };
}
