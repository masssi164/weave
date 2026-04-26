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
