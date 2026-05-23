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
class SelectedCalendarScope extends _$SelectedCalendarScope {
  @override
  CalendarScope build() => CalendarScope.workspace;

  void select(CalendarScope scope) {
    state = scope;
  }
}

@riverpod
Future<CalendarScopeList> calendarScopes(Ref ref) {
  final repository = ref.watch(calendarRepositoryProvider);
  return repository.loadScopes();
}

@riverpod
Future<CalendarClientSetup> calendarClientSetup(Ref ref) {
  final repository = ref.watch(calendarRepositoryProvider);
  return repository.loadClientSetup();
}

@riverpod
Future<CalendarEvent> calendarEvent(Ref ref, String id) {
  final repository = ref.watch(calendarRepositoryProvider);
  return repository.readEvent(id);
}

@riverpod
class CalendarNotifier extends _$CalendarNotifier {
  @override
  Future<CalendarEventList> build() async {
    final repository = ref.watch(calendarRepositoryProvider);
    final selectedScope = ref.watch(selectedCalendarScopeProvider);
    return repository.loadEvents(scope: selectedScope);
  }

  Future<void> createEvent(CalendarEventDraft draft) async {
    final repository = ref.read(calendarRepositoryProvider);
    state = const AsyncLoading<CalendarEventList>();
    state = await AsyncValue.guard(() async {
      await repository.createEvent(draft);
      return repository.loadEvents(
        scope: ref.read(selectedCalendarScopeProvider),
      );
    });
  }

  Future<void> updateEvent(String id, CalendarEventDraft draft) async {
    final repository = ref.read(calendarRepositoryProvider);
    state = const AsyncLoading<CalendarEventList>();
    state = await AsyncValue.guard(() async {
      await repository.updateEvent(id, draft);
      return repository.loadEvents(
        scope: ref.read(selectedCalendarScopeProvider),
      );
    });
  }

  Future<void> deleteEvent(String id) async {
    final repository = ref.read(calendarRepositoryProvider);
    final previousState = state.asData?.value;
    if (previousState != null) {
      state = AsyncData(
        CalendarEventList(
          scope: previousState.scope,
          events: previousState.events
              .where((event) => event.id != id)
              .toList(growable: false),
        ),
      );
    }
    state = await AsyncValue.guard(() async {
      await repository.deleteEvent(id);
      return repository.loadEvents(
        scope: ref.read(selectedCalendarScopeProvider),
      );
    });
  }
}
