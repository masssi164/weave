import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';

abstract interface class CalendarRepository {
  Future<CalendarScopeList> loadScopes();

  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  });

  Future<CalendarEvent> readEvent(String id);

  Future<CalendarEvent> createEvent(CalendarEventDraft draft);

  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  });

  Future<void> deleteEvent(String id, {String? etag});
}

abstract interface class CalendarWorkspaceRepository {
  Future<CalendarWorkspaceContext> loadWorkspaceContext(
    CalendarScope selectedScope,
  );
}

/// Immutable mutation binding to the member and scope that opened the operation.
abstract interface class CalendarWorkspaceMutationRepository {
  CalendarRepository bindMutationContext(CalendarWorkspaceContext expected);
}
