import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/test_app.dart';

class _FakeCalendarRepository implements CalendarRepository {
  _FakeCalendarRepository({
    required List<CalendarEvent> events,
    this.failCreates = false,
    this.failUpdates = false,
  }) : events = List<CalendarEvent>.of(events);

  final List<CalendarEvent> events;
  final bool failCreates;
  final bool failUpdates;
  final List<CalendarEventDraft> createdDrafts = <CalendarEventDraft>[];
  final List<String> deletedIds = <String>[];
  final List<String?> updatedEtags = <String?>[];
  final List<CalendarEventDraft> updatedDrafts = [];

  @override
  Future<CalendarScopeList> loadScopes() async {
    return const CalendarScopeList(
      scopes: [
        CalendarScope(
          type: 'workspace',
          label: 'Workspace',
          capabilities: ['read', 'create', 'update', 'delete'],
        ),
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
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async {
    return CalendarEventList(
      scope: scope ?? CalendarScope.workspace,
      events: List<CalendarEvent>.of(events),
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
      allowedActions: const ['update', 'delete'],
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
    updatedDrafts.add(draft);
    if (failUpdates) throw const CalendarFailure(CalendarFailureKind.conflict);
    final index = events.indexWhere((event) => event.id == id);
    final event = CalendarEvent(
      id: id,
      allowedActions: const ['update', 'delete'],
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
  Future<void> deleteEvent(String id, {String? etag}) async {
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
      'renders accessible Calendar without external credentials or setup',
      (tester) async {
        final repository = _FakeCalendarRepository(
          events: [
            CalendarEvent(
              id: 'event-1',
              allowedActions: const ['update', 'delete'],
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
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
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

        expect(find.text('Use Calendar in other apps'), findsNothing);
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
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
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
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'Europe/Berlin',
            ),
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
            allowedActions: const ['update', 'delete'],
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
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'Europe/Berlin',
            ),
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
            allowedActions: const ['update', 'delete'],
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
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'Europe/Berlin',
            ),
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

    testWidgets(
      'recurring occurrence controls explain the guard without mutating the series',
      (tester) async {
        final repository = _FakeCalendarRepository(
          events: [
            CalendarEvent(
              id: 'recurring',
              title: 'Weekly planning',
              startTime: DateTime(2026, 10, 25, 10),
              endTime: DateTime(2026, 10, 25, 11),
              recurring: true,
              allowedActions: const ['update', 'delete'],
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
        expect(find.textContaining('Repeating event:'), findsOneWidget);
        expect(
          tester
              .widget<IconButton>(
                find.byWidgetPredicate(
                  (widget) =>
                      widget is IconButton &&
                      widget.tooltip == 'Edit Weekly planning',
                ),
              )
              .onPressed,
          isNull,
        );
        expect(
          tester
              .widget<IconButton>(
                find.byWidgetPredicate(
                  (widget) =>
                      widget is IconButton &&
                      widget.tooltip == 'Delete Weekly planning',
                ),
              )
              .onPressed,
          isNull,
        );
        expect(repository.updatedEtags, isEmpty);
        expect(repository.deletedIds, isEmpty);
      },
    );

    testWidgets('read-only event actions remain disabled', (tester) async {
      final repository = _FakeCalendarRepository(
        events: [
          CalendarEvent(
            id: 'read-only',
            title: 'Read only',
            startTime: DateTime(2026, 10, 25, 10),
            endTime: DateTime(2026, 10, 25, 11),
            allowedActions: const ['read'],
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
      expect(
        tester
            .widget<IconButton>(
              find.byWidgetPredicate(
                (widget) =>
                    widget is IconButton && widget.tooltip == 'Edit Read only',
              ),
            )
            .onPressed,
        isNull,
      );
      expect(
        tester
            .widget<IconButton>(
              find.byWidgetPredicate(
                (widget) =>
                    widget is IconButton &&
                    widget.tooltip == 'Delete Read only',
              ),
            )
            .onPressed,
        isNull,
      );
    });

    testWidgets('transient preview edits through the normal event dialog', (
      tester,
    ) async {
      const handle = 'pv_0123456789abcdefghijklmnopqrstuv';
      final repository = _FakeCalendarRepository(
        events: [
          CalendarEvent(
            id: handle,
            previewHandle: handle,
            title: 'Provider planning',
            startTime: DateTime(2026, 10, 25, 10),
            endTime: DateTime(2026, 10, 25, 11),
            allowedActions: const ['read', 'materialize', 'update', 'delete'],
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
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'Europe/Berlin',
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Edit Provider planning'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, 'Revised planning');
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();
      expect(repository.updatedDrafts.single.title, 'Revised planning');
      expect(repository.updatedEtags.single, isNull);
      expect(find.text('Revised planning'), findsWidgets);
    });

    testWidgets(
      'version conflict explains reload and leaves the event visible',
      (tester) async {
        final repository = _FakeCalendarRepository(
          failUpdates: true,
          events: [
            CalendarEvent(
              id: 'conflict',
              title: 'Review',
              startTime: DateTime(2026, 10, 25, 10),
              endTime: DateTime(2026, 10, 25, 11),
              etag: '"old"',
              allowedActions: const ['update'],
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
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('Edit Review'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Save event'));
        await tester.pumpAndSettle();
        expect(
          find.textContaining('This event changed. Refresh'),
          findsOneWidget,
        );
        expect(find.text('Review'), findsOneWidget);
      },
    );

    testWidgets('all-day create keeps an exclusive next-day end', (
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
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'Europe/Berlin',
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Create event'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, 'All day');
      await tester.tap(find.byType(Switch));
      await tester.pumpAndSettle();
      expect(find.textContaining('first day after the event'), findsOneWidget);
      await tester.tap(find.text('Save event'));
      await tester.pumpAndSettle();
      final draft = repository.createdDrafts.single;
      expect(draft.allDay, isTrue);
      expect(
        draft.endTime.difference(draft.startTime),
        const Duration(days: 1),
      );
      expect(draft.timezone, 'Europe/Berlin');
    });

    for (final view in ['Day', 'Week', 'Month']) {
      testWidgets(
        '$view includes cross-boundary events and excludes their exclusive end',
        (tester) async {
          final now = DateTime.now();
          final day = DateTime.utc(now.year, now.month, now.day);
          final start = view == 'Month'
              ? DateTime.utc(now.year, now.month)
              : view == 'Week'
              ? day.subtract(Duration(days: day.weekday - 1))
              : day;
          final repository = _FakeCalendarRepository(
            events: [
              CalendarEvent(
                id: 'crossing',
                title: 'Across boundary',
                startTime: start.subtract(const Duration(days: 1)),
                endTime: start.add(const Duration(days: 2)),
                allDay: true,
              ),
              CalendarEvent(
                id: 'ended',
                title: 'Already ended',
                startTime: start.subtract(const Duration(days: 2)),
                endTime: start,
                allDay: true,
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
          await tester.tap(find.text(view));
          await tester.pumpAndSettle();
          expect(find.text('Across boundary'), findsWidgets);
          expect(find.text('Already ended'), findsNothing);
        },
      );
    }

    testWidgets(
      'editor preserves floating DST-gap fields on a title-only change',
      (tester) async {
        final repository = _FakeCalendarRepository(
          events: [
            CalendarEvent(
              id: 'gap',
              title: 'Floating time',
              startTime: DateTime.utc(2026, 3, 29, 2, 30),
              endTime: DateTime.utc(2026, 3, 29, 3, 30),
              timeKind: CalendarTimeKind.floating,
              etag: '"gap"',
              allowedActions: const ['update'],
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
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('Edit Floating time'));
        await tester.pumpAndSettle();
        await tester.enterText(find.byType(TextField).first, 'Title only');
        await tester.tap(find.text('Save event'));
        await tester.pumpAndSettle();
        expect(
          repository.updatedDrafts.single.startTime,
          DateTime.utc(2026, 3, 29, 2, 30),
        );
        expect(
          repository.updatedDrafts.single.timeKind,
          CalendarTimeKind.floating,
        );
      },
    );

    testWidgets(
      'a draft opened by a previous session cannot be submitted by the next member',
      (tester) async {
        final repository = _FakeCalendarRepository(events: []);
        var session = WeaveAuthenticatedSession(
          apiBaseUrl: Uri.parse('https://api.example.test/api'),
          accessToken: 'first',
        );
        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                const AsyncData(_readySnapshot),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
              weaveAuthenticatedSessionProvider.overrideWith(
                (ref) async => session,
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('Create event'));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byType(TextField).first,
          'Previous member draft',
        );
        final container = ProviderScope.containerOf(
          tester.element(find.byType(CalendarScreen)),
        );
        session = WeaveAuthenticatedSession(
          apiBaseUrl: Uri.parse('https://api.example.test/api'),
          accessToken: 'second',
        );
        container.invalidate(weaveAuthenticatedSessionProvider);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Save event'));
        await tester.pumpAndSettle();
        expect(repository.createdDrafts, isEmpty);
      },
    );
  });
}
