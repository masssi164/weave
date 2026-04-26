import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/calendar/data/repositories/stub_calendar_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';

part 'calendar_provider.g.dart';

@Riverpod(keepAlive: true)
CalendarFacadeClient calendarFacadeClient(Ref ref) {
  return const CalendarFacadeClient();
}

@Riverpod(keepAlive: true)
CalendarRepository calendarRepository(Ref ref) {
  final client = ref.watch(calendarFacadeClientProvider);
  return StubCalendarRepository(client: client);
}

@riverpod
class CalendarNotifier extends _$CalendarNotifier {
  @override
  Future<List<CalendarEvent>> build() async {
    final repository = ref.watch(calendarRepositoryProvider);
    return repository.loadEvents();
  }
}
