import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';

class BackendCalendarRepository implements CalendarRepository {
  const BackendCalendarRepository({required CalendarFacadeClient client})
    : _client = client;

  final CalendarFacadeClient _client;

  @override
  Future<List<CalendarEvent>> loadEvents() {
    return _client.listEvents();
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    return _client.createEvent(draft);
  }

  @override
  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft) {
    return _client.updateEvent(id: id, patch: draft.toPatch());
  }

  @override
  Future<void> deleteEvent(String id) {
    return _client.deleteEvent(id);
  }
}
