import 'package:weave/features/calendar/domain/entities/calendar_event.dart';

abstract interface class CalendarRepository {
  Future<CalendarScopeList> loadScopes();

  Future<CalendarEventList> loadEvents({CalendarScope? scope});

  Future<CalendarClientSetup> loadClientSetup();

  Future<CalendarEvent> readEvent(String id);

  Future<CalendarEvent> createEvent(CalendarEventDraft draft);

  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft);

  Future<void> deleteEvent(String id);
}
