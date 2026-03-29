/// A stub calendar event model.
///
/// Will be replaced with the CalDAV event type once
/// the Nextcloud integration is built.
class CalendarEvent {
  const CalendarEvent({
    required this.id,
    required this.title,
    required this.startTime,
    required this.endTime,
  });

  final String id;
  final String title;
  final DateTime startTime;
  final DateTime endTime;
}
