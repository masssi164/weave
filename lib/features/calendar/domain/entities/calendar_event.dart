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
