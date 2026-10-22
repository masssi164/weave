import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';

class BackendCalendarRepository
    implements
        CalendarRepository,
        CalendarWorkspaceRepository,
        CalendarWorkspaceMutationRepository {
  const BackendCalendarRepository({
    required CalendarFacadeClient client,
    CalendarWorkspaceContext? expectedWorkspace,
  }) : _client = client,
       _expectedWorkspace = expectedWorkspace;

  final CalendarFacadeClient _client;
  final CalendarWorkspaceContext? _expectedWorkspace;

  @override
  CalendarRepository bindMutationContext(CalendarWorkspaceContext expected) =>
      BackendCalendarRepository(client: _client, expectedWorkspace: expected);

  @override
  Future<CalendarWorkspaceContext> loadWorkspaceContext(
    CalendarScope selectedScope,
  ) => _client.workspaceContext(selectedScope);

  @override
  Future<CalendarScopeList> loadScopes() {
    return _client.listScopes();
  }

  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) {
    return _client.listEvents(selectedScope: scope, from: from, to: to);
  }

  @override
  Future<CalendarEvent> readEvent(String id) {
    return _client.readEvent(id);
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    return _client.createEvent(draft, expectedWorkspace: _expectedWorkspace);
  }

  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) {
    return _client.updateEvent(
      id: id,
      draft: draft,
      version: etag,
      expectedWorkspace: _expectedWorkspace,
    );
  }

  @override
  Future<void> deleteEvent(String id, {String? etag}) {
    return _client.deleteEvent(
      id,
      version: etag,
      expectedWorkspace: _expectedWorkspace,
    );
  }
}
