import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/calendar/data/repositories/backend_calendar_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

part 'calendar_provider.g.dart';

final calendarViewDateProvider = NotifierProvider<CalendarViewDate, DateTime>(
  CalendarViewDate.new,
);

class CalendarViewDate extends Notifier<DateTime> {
  @override
  DateTime build() => DateTime.now();
  void select(DateTime date) {
    state = date;
  }
}

@Riverpod(keepAlive: true)
CalendarFacadeClient calendarFacadeClient(Ref ref) {
  return CalendarFacadeClient(
    httpClient: ref.watch(weaveApiHttpClientProvider),
    serverConfigurationRepository: ref.watch(
      serverConfigurationRepositoryProvider,
    ),
    authSessionRepository: ref.watch(authSessionRepositoryProvider),
    evaluationTimeZone: () =>
        ref.read(calendarEvaluationTimeZoneProvider.future),
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
  CalendarScope build() {
    ref.watch(weaveAuthenticatedSessionProvider);
    return CalendarScope.workspace;
  }

  void select(CalendarScope scope) {
    state = scope;
  }
}

@riverpod
Future<CalendarScopeList> calendarScopes(Ref ref) {
  ref.watch(weaveAuthenticatedSessionProvider);
  final repository = ref.watch(calendarRepositoryProvider);
  return repository.loadScopes();
}

@riverpod
Future<String> calendarEvaluationTimeZone(Ref ref) async {
  final profile = await ref.watch(userProfileProvider.future);
  if (profile == null || profile.timezone.isEmpty) {
    throw const CalendarFailure(CalendarFailureKind.unavailable);
  }
  return profile.timezone;
}

@riverpod
Future<CalendarEvent> calendarEvent(Ref ref, String id) {
  ref.watch(weaveAuthenticatedSessionProvider);
  final repository = ref.watch(calendarRepositoryProvider);
  return repository.readEvent(id);
}

@riverpod
class CalendarNotifier extends _$CalendarNotifier {
  int _generation = 0;
  int _operation = 0;

  @override
  Future<CalendarEventList> build() async {
    _generation++;
    ref.watch(weaveAuthenticatedSessionProvider);
    final repository = ref.watch(calendarRepositoryProvider);
    final scope = ref.watch(selectedCalendarScopeProvider);
    final date = ref.watch(calendarViewDateProvider);
    return _load(repository, scope, date);
  }

  Future<CalendarEventList> _load(
    CalendarRepository repository,
    CalendarScope scope,
    DateTime date,
  ) => repository.loadEvents(
    scope: scope,
    from: DateTime.utc(date.year, date.month - 1),
    to: DateTime.utc(date.year, date.month + 3),
  );

  Future<void> createEvent(CalendarEventDraft draft) =>
      _mutate((repository) => repository.createEvent(draft));

  Future<void> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) => _mutate((repository) => repository.updateEvent(id, draft, etag: etag));

  Future<void> deleteEvent(String id, {String? etag}) =>
      _mutate((repository) => repository.deleteEvent(id, etag: etag));

  Future<void> _mutate(Future<void> Function(CalendarRepository) action) async {
    final repository = ref.read(calendarRepositoryProvider);
    final scope = ref.read(selectedCalendarScopeProvider);
    final date = ref.read(calendarViewDateProvider);
    final session = ref.read(weaveAuthenticatedSessionProvider);
    final generation = _generation;
    final operation = ++_operation;
    final previous = state;
    bool current() =>
        ref.mounted &&
        generation == _generation &&
        operation == _operation &&
        ref.read(calendarRepositoryProvider) == repository &&
        ref.read(selectedCalendarScopeProvider) == scope &&
        ref.read(calendarViewDateProvider) == date &&
        ref.read(weaveAuthenticatedSessionProvider) == session;
    state = const AsyncLoading<CalendarEventList>();
    try {
      await action(repository);
      if (!current()) return;
      final events = await _load(repository, scope, date);
      if (current()) state = AsyncData(events);
    } catch (error, stackTrace) {
      if (current()) {
        // A session failure must never restore the preceding member's events.
        state =
            error is CalendarFailure &&
                error.kind == CalendarFailureKind.session
            ? AsyncError(error, stackTrace)
            : previous;
      }
      Error.throwWithStackTrace(error, stackTrace);
    }
  }
}
