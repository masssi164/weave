import 'package:weave/features/calendar/domain/entities/calendar_event.dart';

abstract interface class CalendarRepository {
  Future<List<CalendarEvent>> loadEvents();
}
