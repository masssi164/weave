import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';

class BackendCalendarRepository implements CalendarRepository {
  const BackendCalendarRepository({required CalendarFacadeClient client})
    : _client = client;

  final CalendarFacadeClient _client;

  @override
  Future<CalendarScopeList> loadScopes() {
    return _client.listScopes();
  }

  @override
  Future<CalendarEventList> loadEvents({CalendarScope? scope}) {
    return _client.listEvents(selectedScope: scope);
  }

  @override
  Future<CalendarClientSetup> loadClientSetup() {
    return _client.clientSetup();
  }

  @override
  Future<CalendarEvent> readEvent(String id) {
    return _client.readEvent(id);
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    return _client.createEvent(draft);
  }

  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) {
    return _client.updateEvent(
      id: id,
      patch: draft.toPatch(etag: etag),
    );
  }

  @override
  Future<void> deleteEvent(String id) {
    return _client.deleteEvent(id);
  }
}
