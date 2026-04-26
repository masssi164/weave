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
}
