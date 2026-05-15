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
  _FakeCalendarRepository([List<CalendarEvent> events = const []])
    : events = List<CalendarEvent>.of(events);

  final List<CalendarEvent> events;
  int loadCount = 0;
  final List<String> readIds = [];
  final List<CalendarEventDraft> createdDrafts = [];
  final List<(String, CalendarEventDraft)> updatedDrafts = [];
  final List<String> deletedIds = [];

  @override
  Future<CalendarEventList> loadEvents() async {
    loadCount += 1;
    return CalendarEventList(
      scope: CalendarScope.workspace,
      events: List<CalendarEvent>.of(events),
    );
  }

  @override
  Future<CalendarClientSetup>
  loadClientSetup() async => const CalendarClientSetup(
    scope: CalendarScope.workspace,
    username: 'user-123',
    endpoints: CalendarExternalEndpoints(
      serverUrl: 'https://files.weave.local',
      caldavDiscoveryUrl: 'https://files.weave.local/remote.php/dav',
      principalUrl:
          'https://files.weave.local/remote.php/dav/principals/users/user-123/',
    ),
    credentialPolicy:
        'The backend never returns passwords, app passwords, or bearer tokens.',
    accessModel: CalendarAccessModel(
      type: 'workspace-calendar',
      productScope: 'workspace',
      privateUserCalendarsAvailable: false,
      privateUserCalendarsReason:
          'Private calendars need reviewed provisioning before they are shown.',
      externalClientCredentialModel:
          'nextcloud-login-flow-or-revocable-app-password',
      notes: ['Workspace calendar setup only.'],
    ),
    credentialReadiness: CalendarCredentialReadiness(
      status: 'blocked_until_revocable_credentials',
      appleProfileSigned: false,
      appleProfilePasswordIncluded: false,
      revocableCredentialsAvailable: false,
      readOnlySubscriptionTokensAvailable: false,
      backendActorCredentialsExposed: false,
      blockers: ['Apple profiles are unsigned.'],
    ),
    options: [
      CalendarClientSetupOption(
        platform: 'apple',
        method: 'mobileconfig',
        available: false,
        unavailableReason: 'Signed profiles are not implemented yet.',
      ),
      CalendarClientSetupOption(
        platform: 'android',
        method: 'davx5',
        available: true,
        actionUrl: 'davx5://files.weave.local/remote.php/dav',
      ),
    ],
  );

  @override
  Future<CalendarEvent> readEvent(String id) async {
    readIds.add(id);
    return events.singleWhere((event) => event.id == id);
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    createdDrafts.add(draft);
    final event = CalendarEvent(
      id: 'created-${createdDrafts.length}',
      title: draft.title,
      description: draft.description,
      location: draft.location,
      startTime: draft.startTime,
      endTime: draft.endTime,
      timezone: draft.timezone,
      allDay: draft.allDay,
    );
    events.add(event);
    return event;
  }

  @override
  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft) async {
    updatedDrafts.add((id, draft));
    final index = events.indexWhere((event) => event.id == id);
    final event = CalendarEvent(
      id: id,
      title: draft.title,
      description: draft.description,
      location: draft.location,
      startTime: draft.startTime,
      endTime: draft.endTime,
      timezone: draft.timezone,
      allDay: draft.allDay,
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

const _readyCapabilities = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.unavailable,
  ),
);

const _unavailableCapabilities = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.unavailable,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.unavailable,
  ),
);

List<dynamic> _calendarOverrides(
  _FakeCalendarRepository repository, {
  WorkspaceCapabilitySnapshot capabilities = _readyCapabilities,
}) {
  return [
    calendarRepositoryProvider.overrideWithValue(repository),
    workspaceCapabilitySnapshotProvider.overrideWithValue(
      AsyncData(capabilities),
    ),
  ];
}

List<dynamic> _calendarCapabilityOverrides({
  WorkspaceCapabilitySnapshot capabilities = _readyCapabilities,
}) {
  return [
    workspaceCapabilitySnapshotProvider.overrideWithValue(
      AsyncData(capabilities),
    ),
  ];
}

void main() {
  group('CalendarScreen', () {
    testWidgets('renders without errors', (tester) async {
      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarCapabilityOverrides(),
        ),
      );
      await tester.pumpAndSettle();
    });

    testWidgets('keeps Calendar disabled when backend reports unavailable', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository([
        CalendarEvent(
          id: 'planning',
          title: 'Planning',
          startTime: DateTime.utc(2026, 4, 27, 9),
          endTime: DateTime.utc(2026, 4, 27, 10),
        ),
      ]);

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(
            repository,
            capabilities: _unavailableCapabilities,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Calendar is unavailable'), findsOneWidget);
      expect(
        find.textContaining('Backend readiness is unavailable'),
        findsOneWidget,
      );
      expect(find.text('Create event'), findsNothing);
      expect(find.text('Planning'), findsNothing);
      expect(repository.loadCount, 0);
    });

    testWidgets('shows backend facade calendar events accessibly', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository([
        CalendarEvent(
          id: 'planning',
          title: 'Planning',
          description: 'Sprint planning',
          location: 'Office',
          startTime: DateTime.utc(2026, 4, 27, 9),
          endTime: DateTime.utc(2026, 4, 27, 10),
          timezone: 'Europe/Berlin',
          allDay: false,
        ),
      ]);

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(repository),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Planning'), findsOneWidget);
      expect(find.text('Workspace calendar'), findsOneWidget);
      expect(find.text('Use Calendar in other apps'), findsOneWidget);
      expect(find.text('CalDAV discovery URL'), findsOneWidget);
      expect(
        find.text('https://files.weave.local/remote.php/dav'),
        findsOneWidget,
      );
      expect(find.text('android via davx5: available'), findsOneWidget);
      expect(find.text('apple via mobileconfig: planned'), findsOneWidget);
      expect(find.text('Private user calendars blocked'), findsOneWidget);
      expect(find.text('Workspace calendar setup only.'), findsOneWidget);
      expect(
        find.text('Status: blocked_until_revocable_credentials'),
        findsOneWidget,
      );
      expect(
        find.text(
          'Backend actor credentials are not exposed to client setup artifacts.',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('shared Weave workspace calendar'),
        findsOneWidget,
      );
      expect(find.text('Sprint planning'), findsOneWidget);
      expect(find.text('Office'), findsOneWidget);
      expect(
        find.bySemanticsLabel(RegExp(r'Planning, starts')),
        findsOneWidget,
      );
    });

    testWidgets('opens event details through the backend read facade', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository([
        CalendarEvent(
          id: 'planning',
          title: 'Planning',
          description: 'Fetched event details',
          location: 'Office',
          startTime: DateTime.utc(2026, 4, 27, 9),
          endTime: DateTime.utc(2026, 4, 27, 10),
          timezone: 'Europe/Berlin',
          allDay: false,
        ),
      ]);

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(repository),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('View Planning'));
      await tester.pumpAndSettle();

      expect(repository.readIds, ['planning']);
      expect(find.text('Calendar event details'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.text('Fetched event details'),
        ),
        findsOneWidget,
      );
      expect(find.text('Calendar scope'), findsOneWidget);
      expect(find.text('Weave workspace calendar'), findsOneWidget);
    });

    testWidgets('creates and deletes events through the repository', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository();

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(repository),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField).first, 'Customer demo');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(repository.createdDrafts.single.title, 'Customer demo');
      expect(find.text('Customer demo'), findsOneWidget);

      await tester.tap(find.byTooltip('Delete Customer demo'));
      await tester.pumpAndSettle();

      expect(repository.deletedIds.single, 'created-1');
      expect(find.text('Customer demo'), findsNothing);
    });

    testWidgets('keeps invalid event drafts in the accessible form', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository();

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(repository),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(find.text('Enter an event title.'), findsOneWidget);
      expect(repository.createdDrafts, isEmpty);
      expect(find.byType(AlertDialog), findsOneWidget);
    });

    testWidgets('edits events through the backend facade repository', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository([
        CalendarEvent(
          id: 'planning',
          title: 'Planning',
          description: 'Sprint planning',
          location: 'Office',
          startTime: DateTime.utc(2026, 4, 27, 9),
          endTime: DateTime.utc(2026, 4, 27, 10),
          timezone: 'Europe/Berlin',
          allDay: false,
        ),
      ]);

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarOverrides(repository),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Edit Planning'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField).first, 'Roadmap');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();

      expect(repository.updatedDrafts.single.$1, 'planning');
      expect(repository.updatedDrafts.single.$2.title, 'Roadmap');
      expect(find.text('Roadmap'), findsOneWidget);
      expect(find.text('Planning'), findsNothing);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarCapabilityOverrides(),
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: _calendarCapabilityOverrides(),
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
