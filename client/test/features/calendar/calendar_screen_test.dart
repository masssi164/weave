import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';

import '../../helpers/test_app.dart';

class _FakeCalendarRepository implements CalendarRepository {
  _FakeCalendarRepository({
    required List<CalendarEvent> events,
    this.failCreates = false,
  }) : events = List<CalendarEvent>.of(events);

  final List<CalendarEvent> events;
  final bool failCreates;
  final List<CalendarEventDraft> createdDrafts = <CalendarEventDraft>[];
  final List<String> deletedIds = <String>[];
  final List<String?> updatedEtags = <String?>[];

  @override
  Future<CalendarScopeList> loadScopes() async {
    return const CalendarScopeList(
      scopes: [
        CalendarScope.workspace,
        CalendarScope(
          id: 'team-engineering',
          type: 'team',
          label: 'Engineering team',
          teamId: 'engineering',
        ),
      ],
    );
  }

  @override
  Future<CalendarEventList> loadEvents({CalendarScope? scope}) async {
    return CalendarEventList(
      scope: scope ?? CalendarScope.workspace,
      events: List<CalendarEvent>.of(events),
    );
  }

  @override
  Future<CalendarClientSetup> loadClientSetup() async {
    return const CalendarClientSetup(
      scope: CalendarScope.workspace,
      username: 'weave-backend',
      credentialPolicy: 'secret-free-setup-metadata',
      endpoints: CalendarExternalEndpoints(
        serverUrl: '/caldav',
        caldavDiscoveryUrl: '/caldav',
        principalUrl: '/caldav/principals/users/weave-backend/',
      ),
      options: [],
    );
  }

  @override
  Future<CalendarEvent> readEvent(String id) async {
    return events.firstWhere((event) => event.id == id);
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    createdDrafts.add(draft);
    if (failCreates) {
      throw StateError('create failed');
    }
    final event = CalendarEvent(
      id: 'created-${createdDrafts.length}',
      title: draft.title,
      description: draft.description,
      startTime: draft.startTime,
      endTime: draft.endTime,
      timezone: draft.timezone,
      location: draft.location,
      allDay: draft.allDay,
      scope: draft.scope,
    );
    events.add(event);
    return event;
  }

  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) async {
    updatedEtags.add(etag);
    final index = events.indexWhere((event) => event.id == id);
    final event = CalendarEvent(
      id: id,
      title: draft.title,
      description: draft.description,
      startTime: draft.startTime,
      endTime: draft.endTime,
      timezone: draft.timezone,
      location: draft.location,
      allDay: draft.allDay,
      etag: etag,
      scope: draft.scope,
    );
    events[index] = event;
    return event;
  }

  @override
  Future<void> deleteEvent(String id) async {
    deletedIds.add(id);
    events.removeWhere((event) => event.id == id);
  }
}

const _readySnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
);

WorkspaceCapabilitySnapshot _calendarUnavailableSnapshot() {
  return WorkspaceCapabilitySnapshot(
    shellAccess: _readySnapshot.shellAccess,
    chat: _readySnapshot.chat,
    files: _readySnapshot.files,
    calendar: const WorkspaceCapabilityState(
      capability: WorkspaceCapability.calendar,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: WorkspaceCapabilityPolicyState.allowed,
    ),
    boards: _readySnapshot.boards,
  );
}

void main() {
  group('CalendarScreen', () {
    testWidgets(
      'renders a full calendar surface with events and setup status',
      (tester) async {
        final repository = _FakeCalendarRepository(
          events: [
            CalendarEvent(
              id: 'event-1',
              title: 'Design review',
              startTime: DateTime(2026, 6, 27, 10),
              endTime: DateTime(2026, 6, 27, 11),
              location: 'Workspace room',
            ),
          ],
        );

        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                const AsyncData(_readySnapshot),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Calendar'), findsWidgets);
        expect(find.text('Agenda'), findsOneWidget);
        expect(find.text('Day'), findsOneWidget);
        expect(find.text('Week'), findsOneWidget);
        expect(find.text('Month'), findsOneWidget);
        expect(find.text('Design review'), findsOneWidget);
        expect(find.textContaining('Workspace room'), findsOneWidget);
        expect(
          find.bySemanticsLabel(RegExp('Design review.*starts.*ends')),
          findsOneWidget,
        );

        await tester.drag(find.byType(ListView), const Offset(0, -600));
        await tester.pumpAndSettle();

        expect(find.text('Use Calendar in other apps'), findsOneWidget);
      },
    );

    testWidgets(
      'contains an unavailable Calendar locally and disables its create action',
      (tester) async {
        final repository = _FakeCalendarRepository(events: []);

        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                AsyncData(_calendarUnavailableSnapshot()),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Calendar is unavailable'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            RegExp(
              'Calendar.*State: Unavailable.*This capability is not available',
            ),
          ),
          findsOneWidget,
        );
        final createButton = tester.widget<IconButton>(
          find.widgetWithIcon(IconButton, Icons.add),
        );
        expect(createButton.onPressed, isNull);
        expect(repository.createdDrafts, isEmpty);
      },
    );

    testWidgets('creates and deletes events through the calendar facade', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository(events: []);

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              const AsyncData(_readySnapshot),
            ),
            calendarRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Create event'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, 'Planning sync');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(repository.createdDrafts.single.title, 'Planning sync');
      expect(find.text('Planning sync'), findsOneWidget);

      await tester.tap(find.byTooltip('Delete Planning sync'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      expect(repository.deletedIds.single, 'created-1');
    });

    testWidgets('updates an event with its observed concurrency version', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository(
        events: [
          CalendarEvent(
            id: 'event-1',
            title: 'Design review',
            startTime: DateTime(2026, 6, 27, 10),
            endTime: DateTime(2026, 6, 27, 11),
            etag: '"event-version-1"',
          ),
        ],
      );

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              const AsyncData(_readySnapshot),
            ),
            calendarRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Edit Design review'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, 'Updated review');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(repository.updatedEtags.single, '"event-version-1"');
      expect(find.text('Updated review'), findsOneWidget);
    });

    testWidgets('keeps the calendar visible when saving an event fails', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository(
        events: [
          CalendarEvent(
            id: 'event-1',
            title: 'Design review',
            startTime: DateTime(2026, 6, 27, 10),
            endTime: DateTime(2026, 6, 27, 11),
          ),
        ],
        failCreates: true,
      );

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              const AsyncData(_readySnapshot),
            ),
            calendarRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Create event'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, 'Planning sync');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(repository.createdDrafts.single.title, 'Planning sync');
      expect(find.text('Design review'), findsOneWidget);
      expect(
        find.text('The calendar could not save that change right now.'),
        findsOneWidget,
      );
      expect(
        find.text('Calendar event details are unavailable right now.'),
        findsNothing,
      );
    });
  });
}
