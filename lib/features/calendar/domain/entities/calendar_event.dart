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
  });

  final CalendarScope scope;
  final String username;
  final CalendarExternalEndpoints endpoints;
  final String credentialPolicy;
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
