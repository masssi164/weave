import 'package:weave/features/calendar/domain/entities/calendar_event.dart';

abstract interface class CalendarRepository {
  Future<CalendarEventList> loadEvents();

  Future<CalendarEvent> createEvent(CalendarEventDraft draft);

  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft);

  Future<void> deleteEvent(String id);
}
