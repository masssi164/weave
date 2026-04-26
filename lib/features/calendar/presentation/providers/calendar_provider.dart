import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/calendar/data/repositories/backend_calendar_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

part 'calendar_provider.g.dart';

@Riverpod(keepAlive: true)
CalendarFacadeClient calendarFacadeClient(Ref ref) {
  return CalendarFacadeClient(
    httpClient: ref.watch(weaveApiHttpClientProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
  );
}

@Riverpod(keepAlive: true)
CalendarRepository calendarRepository(Ref ref) {
  final client = ref.watch(calendarFacadeClientProvider);
  return BackendCalendarRepository(client: client);
}

@riverpod
class CalendarNotifier extends _$CalendarNotifier {
  @override
  Future<List<CalendarEvent>> build() async {
    final repository = ref.watch(calendarRepositoryProvider);
    return repository.loadEvents();
  }

  Future<void> createEvent(CalendarEventDraft draft) async {
    final repository = ref.read(calendarRepositoryProvider);
    state = const AsyncLoading<List<CalendarEvent>>();
    state = await AsyncValue.guard(() async {
      await repository.createEvent(draft);
      return repository.loadEvents();
    });
  }

  Future<void> updateEvent(String id, CalendarEventDraft draft) async {
    final repository = ref.read(calendarRepositoryProvider);
    state = const AsyncLoading<List<CalendarEvent>>();
    state = await AsyncValue.guard(() async {
      await repository.updateEvent(id, draft);
      return repository.loadEvents();
    });
  }

  Future<void> deleteEvent(String id) async {
    final repository = ref.read(calendarRepositoryProvider);
    final previousEvents = state.asData?.value;
    if (previousEvents != null) {
      state = AsyncData(
        previousEvents.where((event) => event.id != id).toList(growable: false),
      );
    }
    state = await AsyncValue.guard(() async {
      await repository.deleteEvent(id);
      return repository.loadEvents();
    });
  }
}
