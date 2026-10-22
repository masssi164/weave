import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
import 'package:weave/features/calendar/domain/entities/calendar_write_result.dart';
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
    ref.watch(
      integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
    );
    return CalendarScope.workspace;
  }

  void select(CalendarScope scope) {
    state = scope;
  }
}

final calendarWorkspaceContextProvider =
    FutureProvider<CalendarWorkspaceContext>((ref) async {
      final repository = ref.watch(calendarRepositoryProvider);
      final selected = ref.watch(selectedCalendarScopeProvider);
      final session = await ref.watch(weaveAuthenticatedSessionProvider.future);
      if (repository is CalendarWorkspaceRepository) {
        return (repository as CalendarWorkspaceRepository).loadWorkspaceContext(
          selected,
        );
      }
      final scopes = await repository.loadScopes();
      final zone = await ref.watch(calendarEvaluationTimeZoneProvider.future);
      final matches = scopes.scopes.where(
        (scope) =>
            scope.id == selected.id ||
            (selected == CalendarScope.workspace && scope.isWorkspace),
      );
      if (scopes.scopes.isNotEmpty && matches.length != 1) {
        throw const CalendarFailure(CalendarFailureKind.permission);
      }
      return CalendarWorkspaceContext(
        ownerKey: '${identityHashCode(repository)}:${session?.apiBaseUrl}',
        scopes: scopes,
        selectedScope: matches.isEmpty ? null : matches.single,
        evaluationTimeZone: zone,
      );
    });

@riverpod
Future<CalendarScopeList> calendarScopes(Ref ref) async =>
    (await ref.watch(calendarWorkspaceContextProvider.future)).scopes;

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
  int _operation = 0;
  static int _successfulReadGeneration = 0;

  @override
  Future<CalendarEventList> build() async {
    ref.watch(weaveAuthenticatedSessionProvider);
    final repository = ref.watch(calendarRepositoryProvider);
    ref.watch(selectedCalendarScopeProvider);
    final date = ref.watch(calendarViewDateProvider);
    final workspace = await ref.watch(calendarWorkspaceContextProvider.future);
    if (workspace.selectedScope == null) return const CalendarEventList();
    return _load(repository, workspace.selectedScope!, date);
  }

  Future<CalendarEventList> _load(
    CalendarRepository repository,
    CalendarScope scope,
    DateTime date,
  ) async {
    final result = await repository.loadEvents(
      scope: scope,
      from: DateTime.utc(date.year, date.month - 1),
      to: DateTime.utc(date.year, date.month + 3),
    );
    return CalendarEventList(
      scope: result.scope,
      events: result.events,
      readGeneration: ++_successfulReadGeneration,
    );
  }

  Future<CalendarWriteResult> createEvent(CalendarEventDraft draft) =>
      _mutate((repository) => repository.createEvent(draft));

  Future<CalendarWriteResult> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) => _mutate((repository) => repository.updateEvent(id, draft, etag: etag));

  Future<CalendarWriteResult> deleteEvent(String id, {String? etag}) =>
      _mutate((repository) async {
        await repository.deleteEvent(id, etag: etag);
        return null;
      }, deletedId: id);

  Future<CalendarWriteResult> _mutate(
    Future<CalendarEvent?> Function(CalendarRepository) action, {
    String? deletedId,
  }) async {
    final repository = ref.read(calendarRepositoryProvider);
    final workspace = await ref.read(calendarWorkspaceContextProvider.future);
    final scope = workspace.selectedScope;
    if (scope == null) {
      throw const CalendarFailure(CalendarFailureKind.permission);
    }
    final date = ref.read(calendarViewDateProvider);
    final operation = ++_operation;
    final previous = state;
    bool current(CalendarWorkspaceContext currentWorkspace) =>
        ref.mounted &&
        operation == _operation &&
        identical(ref.read(calendarRepositoryProvider), repository) &&
        currentWorkspace.ownerKey == workspace.ownerKey &&
        currentWorkspace.selectedScope?.id == scope.id &&
        ref.read(calendarViewDateProvider) == date;
    CalendarWriteResult acknowledged(
      CalendarEvent? event, {
      required bool contextCurrent,
    }) => CalendarWriteResult(
      CalendarWriteOutcome.committedRefreshPending,
      event: event,
      ownerKey: workspace.ownerKey,
      scopeId: scope.id,
      contextCurrent: contextCurrent,
    );
    state = const AsyncLoading<CalendarEventList>();
    CalendarEvent? event;
    try {
      final mutationRepository =
          repository is CalendarWorkspaceMutationRepository
          ? (repository as CalendarWorkspaceMutationRepository)
                .bindMutationContext(workspace)
          : repository;
      event = await action(mutationRepository);
    } catch (error, stackTrace) {
      final failure = error is CalendarFailure
          ? error
          : const CalendarFailure(
              CalendarFailureKind.unavailable,
              writeMayHaveCommitted: true,
            );
      final active = ref.mounted
          ? ref.read(calendarWorkspaceContextProvider).asData?.value
          : null;
      if (active != null && current(active)) {
        state = failure.kind == CalendarFailureKind.session
            ? AsyncError(failure, stackTrace)
            : failure.writeMayHaveCommitted
            ? AsyncError<CalendarEventList>(failure, stackTrace)
            : previous;
      }
      Error.throwWithStackTrace(
        CalendarWriteFailure(
          failure,
          outcome: failure.writeMayHaveCommitted
              ? CalendarWriteOutcome.uncertain
              : CalendarWriteOutcome.notCommitted,
        ),
        stackTrace,
      );
    }
    // The server's acknowledgement survives view/session changes. It must never
    // be converted to a rejected write or published into another member's view.
    if (!ref.mounted) return acknowledged(event, contextCurrent: false);
    CalendarWorkspaceContext active;
    try {
      active = await ref.read(calendarWorkspaceContextProvider.future);
    } catch (_) {
      return acknowledged(event, contextCurrent: false);
    }
    if (!current(active)) return acknowledged(event, contextCurrent: false);
    try {
      final events = await _load(repository, scope, date);
      if (!ref.mounted) return acknowledged(event, contextCurrent: false);
      final after = await ref.read(calendarWorkspaceContextProvider.future);
      if (!current(after)) return acknowledged(event, contextCurrent: false);
      bool matchesAcknowledgement(
        CalendarEvent actual,
        CalendarEvent acknowledged,
      ) {
        if (actual.id != acknowledged.id ||
            actual.scope.id != acknowledged.scope.id) {
          return false;
        }
        if (acknowledged.etag != null) return actual.etag == acknowledged.etag;
        return actual.title == acknowledged.title &&
            actual.description == acknowledged.description &&
            actual.location == acknowledged.location &&
            actual.startTime == acknowledged.startTime &&
            actual.endTime == acknowledged.endTime &&
            actual.allDay == acknowledged.allDay;
      }

      var confirmed = false;
      if (deletedId != null) {
        confirmed =
            previous.asData?.value.events.any(
                  (candidate) => candidate.id == deletedId,
                ) ==
                true &&
            !events.events.any((candidate) => candidate.id == deletedId);
      } else if (event != null) {
        confirmed = events.events.any(
          (candidate) => matchesAcknowledgement(candidate, event!),
        );
        final from = DateTime.utc(date.year, date.month - 1);
        final to = DateTime.utc(date.year, date.month + 3);
        final outsideQuery =
            !event.startTime.isBefore(to) || !event.endTime.isAfter(from);
        if (!confirmed && outsideQuery) {
          try {
            final readback = await repository.readEvent(event.id);
            confirmed = matchesAcknowledgement(readback, event);
          } on CalendarFailure catch (failure) {
            if (failure.kind == CalendarFailureKind.session ||
                failure.kind == CalendarFailureKind.permission) {
              rethrow;
            }
          } catch (_) {
            // The acknowledged write remains pending readback.
          }
        }
      }
      if (!ref.mounted) return acknowledged(event, contextCurrent: false);
      final finalContext = await ref.read(
        calendarWorkspaceContextProvider.future,
      );
      if (!current(finalContext)) {
        return acknowledged(event, contextCurrent: false);
      }
      state = AsyncData(events);
      if (!confirmed) return acknowledged(event, contextCurrent: true);
      return CalendarWriteResult(
        CalendarWriteOutcome.committed,
        event: event,
        ownerKey: workspace.ownerKey,
        scopeId: scope.id,
      );
    } catch (error, stackTrace) {
      if (ref.mounted && current(active)) {
        state =
            error is CalendarFailure &&
                error.kind == CalendarFailureKind.session
            ? AsyncError(error, stackTrace)
            : AsyncError<CalendarEventList>(error, stackTrace);
      }
      return acknowledged(event, contextCurrent: true);
    }
  }
}
