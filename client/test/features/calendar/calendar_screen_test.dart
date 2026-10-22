import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/test_app.dart';
import '../../../integration_test/dual_ios_collaboration_test.dart' as dual;

class _FakeCalendarRepository implements CalendarRepository {
  _FakeCalendarRepository({
    required List<CalendarEvent> events,
    this.eventLoadGate,
    this.scopeLoadGate,
    this.readEventGate,
    this.failCreates = false,
    this.failUpdates = false,
  }) : events = List<CalendarEvent>.of(events);

  final List<CalendarEvent> events;
  final Future<void>? eventLoadGate;
  final Future<void>? scopeLoadGate;
  final Future<void>? readEventGate;
  int readCalls = 0;
  final bool failCreates;
  final bool failUpdates;
  final List<CalendarEventDraft> createdDrafts = <CalendarEventDraft>[];
  final List<String> deletedIds = <String>[];
  final List<String?> updatedEtags = <String?>[];
  final List<CalendarEventDraft> updatedDrafts = [];
  int scopeLoads = 0;
  int eventLoads = 0;

  @override
  Future<CalendarScopeList> loadScopes() async {
    scopeLoads++;
    if (scopeLoadGate != null) await scopeLoadGate;
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
    eventLoads++;
    if (eventLoadGate != null) await eventLoadGate;
    return CalendarEventList(
      scope: scope ?? CalendarScope.workspace,
      events: List<CalendarEvent>.of(events),
    );
  }

  @override
  Future<CalendarEvent> readEvent(String id) async {
    readCalls++;
    if (readEventGate != null) await readEventGate;
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

WorkspaceCapabilitySnapshot _calendarUnavailableSnapshot({
  bool policyBlocked = false,
  bool memberSpaceAccessUnavailable = false,
}) {
  return WorkspaceCapabilitySnapshot(
    shellAccess: _readySnapshot.shellAccess,
    chat: _readySnapshot.chat,
    files: _readySnapshot.files,
    calendar: WorkspaceCapabilityState(
      capability: WorkspaceCapability.calendar,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: policyBlocked
          ? WorkspaceCapabilityPolicyState.policyBlocked
          : WorkspaceCapabilityPolicyState.allowed,
      memberSpaceAccessUnavailable: memberSpaceAccessUnavailable,
    ),
    boards: _readySnapshot.boards,
  );
}

class _CalendarCapabilityController
    extends Notifier<AsyncValue<WorkspaceCapabilitySnapshot>> {
  @override
  AsyncValue<WorkspaceCapabilitySnapshot> build() =>
      AsyncData(_calendarUnavailableSnapshot());

  void show(WorkspaceCapabilitySnapshot snapshot) {
    state = AsyncData(snapshot);
  }
}

void main() {
  for (final completeBeforeFrame in [false, true]) {
    testWidgets(
      'Calendar readiness handles pending capability, completed before frame: $completeBeforeFrame',
      (tester) async {
        final gate = Completer<WorkspaceCapabilitySnapshot>();
        final capability = FutureProvider((ref) => gate.future);
        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => ref.watch(capability),
              ),
              calendarRepositoryProvider.overrideWithValue(
                _FakeCalendarRepository(events: []),
              ),
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'Europe/Berlin',
              ),
            ],
          ),
        );
        await tester.pump();
        final refresh = find.descendant(
          of: find.byType(CalendarScreen),
          matching: find.byType(RefreshIndicator),
        );
        expect(refresh, findsNothing);
        // Reproduce the premature assertion used by the protected UI readback.
        expect(
          () => expect(refresh, findsOneWidget),
          throwsA(isA<TestFailure>()),
        );
        final container = ProviderScope.containerOf(
          tester.element(find.byType(CalendarScreen)),
        );
        final started = tester.binding.clock.now();
        if (completeBeforeFrame) {
          gate.complete(_readySnapshot);
          await container.read(capability.future);
          expect(
            container
                .read(workspaceCapabilitySnapshotProvider)
                .requireValue
                .calendar
                .isReady,
            isTrue,
          );
          expect(refresh, findsNothing);
        } else {
          Timer(
            const Duration(milliseconds: 500),
            () => gate.complete(_readySnapshot),
          );
        }
        final ready = await dual.waitNativeDualCalendarRefresh(
          tester,
          screen: find.byType(CalendarScreen),
          container: container,
          deadline: started.add(const Duration(seconds: 1)),
          now: tester.binding.clock.now,
        );
        expect(
          tester.binding.clock.now().difference(started),
          completeBeforeFrame
              ? Duration.zero
              : const Duration(milliseconds: 500),
        );
        expect(ready, findsOneWidget);
      },
    );
  }

  for (final entry in <String, AsyncValue<WorkspaceCapabilitySnapshot>>{
    'denied': AsyncData(_calendarUnavailableSnapshot()),
    'policy-denied': AsyncData(
      _calendarUnavailableSnapshot(policyBlocked: true),
    ),
    'space-denied': AsyncData(
      _calendarUnavailableSnapshot(
        policyBlocked: true,
        memberSpaceAccessUnavailable: true,
      ),
    ),
    'error': AsyncError(
      const AppFailure.unknown('PRIVATE-DETAIL-MUST-NOT-APPEAR'),
      StackTrace.current,
    ),
    'loading': const AsyncLoading(),
  }.entries) {
    testWidgets('native Calendar readiness rejects ${entry.key}', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(entry.value),
          ],
        ),
      );
      await tester.pump();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(CalendarScreen)),
      );
      final diagnostics = <String>[];
      final started = tester.binding.clock.now();
      await expectLater(
        () => dual.waitNativeDualCalendarRefresh(
          tester,
          screen: find.byType(CalendarScreen),
          container: container,
          deadline: started.add(const Duration(seconds: 1)),
          now: tester.binding.clock.now,
          emit: diagnostics.add,
        ),
        throwsA(isA<TestFailure>()),
      );
      final reason = switch (entry.key) {
        'loading' => 'readiness-deadline',
        'error' => 'capability-error',
        _ => 'calendar-not-admitted',
      };
      expect(diagnostics.single, contains('reason=$reason'));
      if (entry.key == 'space-denied') {
        expect(diagnostics.single, contains('spaceAccessUnavailable=true'));
      }
      if (entry.key == 'error') {
        expect(diagnostics.single, contains('capabilityState=error'));
      }
      expect(diagnostics.single, isNot(contains('PRIVATE-DETAIL')));
      expect(
        tester.binding.clock.now().difference(started),
        entry.key == 'loading' ? const Duration(seconds: 1) : Duration.zero,
      );
    });
  }

  for (final count in [0, 2]) {
    testWidgets(
      'native Calendar readiness rejects $count rendered refresh controls',
      (tester) async {
        const surface = Key('calendar-surface');
        await tester.pumpWidget(
          createTestApp(
            Column(
              key: surface,
              children: [
                for (var index = 0; index < count; index++)
                  Expanded(
                    child: RefreshIndicator(
                      onRefresh: () async {},
                      child: ListView(
                        children: const [Text('Calendar control fixture')],
                      ),
                    ),
                  ),
              ],
            ),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                const AsyncData(_readySnapshot),
              ),
            ],
          ),
        );
        final container = ProviderScope.containerOf(
          tester.element(find.byKey(surface)),
        );
        final diagnostics = <String>[];
        await expectLater(
          () => dual.waitNativeDualCalendarRefresh(
            tester,
            screen: find.byKey(surface),
            container: container,
            deadline: tester.binding.clock.now().add(
              const Duration(seconds: 1),
            ),
            now: tester.binding.clock.now,
            emit: diagnostics.add,
          ),
          throwsA(isA<TestFailure>()),
        );
        expect(
          diagnostics.single,
          contains(
            'reason=calendar-refresh-${count == 0 ? 'missing' : 'not-unique'}',
          ),
        );
        expect(diagnostics.single, contains('refreshCount=$count'));
      },
    );
  }

  testWidgets('native Calendar readiness rejects missing navigation', (
    tester,
  ) async {
    const surface = Key('other-screen');
    await tester.pumpWidget(
      createTestApp(
        const SizedBox(key: surface),
        overrides: [
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData(_readySnapshot),
          ),
        ],
      ),
    );
    final container = ProviderScope.containerOf(
      tester.element(find.byKey(surface)),
    );
    final diagnostics = <String>[];
    await expectLater(
      () => dual.waitNativeDualCalendarRefresh(
        tester,
        screen: find.byType(CalendarScreen),
        container: container,
        deadline: tester.binding.clock.now().add(const Duration(seconds: 1)),
        now: tester.binding.clock.now,
        emit: diagnostics.add,
      ),
      throwsA(isA<TestFailure>()),
    );
    expect(
      diagnostics.single,
      contains('reason=calendar-screen-not-unique screenCount=0'),
    );
  });

  group('CalendarScreen', () {
    testWidgets(
      'moves from a readable loading state to an actionable empty state',
      (tester) async {
        final gate = Completer<void>();
        final repository = _FakeCalendarRepository(
          events: [],
          eventLoadGate: gate.future,
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
        await tester.pump();
        expect(repository.eventLoads, greaterThan(0));
        expect(find.text('Loading event details…'), findsOneWidget);
        expect(find.text('No events yet'), findsNothing);

        gate.complete();
        await tester.pumpAndSettle();
        expect(find.text('Loading event details…'), findsNothing);
        expect(find.text('No events yet'), findsOneWidget);
        await tester.tap(find.byTooltip('Create event'));
        await tester.pumpAndSettle();
        expect(find.text('Create calendar event'), findsOneWidget);
      },
    );

    testWidgets('tabs through event fields and returns focus after dismissal', (
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

      final trigger = find.widgetWithIcon(IconButton, Icons.add);
      Finder focused() => find.byElementPredicate(
        (element) =>
            identical(element, FocusManager.instance.primaryFocus?.context),
      );
      for (
        var i = 0;
        i < 30 &&
            find.ancestor(of: focused(), matching: trigger).evaluate().isEmpty;
        i++
      ) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(find.ancestor(of: focused(), matching: trigger), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      final fields = find.byType(TextField);
      expect(
        find.ancestor(of: focused(), matching: fields.first),
        findsOneWidget,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        find.ancestor(of: focused(), matching: fields.at(1)),
        findsOneWidget,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Create calendar event'), findsNothing);
      expect(find.ancestor(of: focused(), matching: trigger), findsOneWidget);
    });
    testWidgets(
      'waits for access, loads agenda automatically, and hides revoked events',
      (tester) async {
        final capabilityState =
            NotifierProvider<
              _CalendarCapabilityController,
              AsyncValue<WorkspaceCapabilitySnapshot>
            >(_CalendarCapabilityController.new);
        final repository = _FakeCalendarRepository(
          events: [
            CalendarEvent(
              id: 'private-event',
              title: 'Private review',
              startTime: DateTime(2026, 6, 27, 10),
              endTime: DateTime(2026, 6, 27, 11),
            ),
          ],
        );
        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => ref.watch(capabilityState),
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
        expect(repository.scopeLoads, 0);
        expect(repository.eventLoads, 0);

        final container = ProviderScope.containerOf(
          tester.element(find.byType(CalendarScreen)),
        );
        container.read(capabilityState.notifier).show(_readySnapshot);
        await tester.pumpAndSettle();

        expect(find.text('Private review'), findsOneWidget);
        expect(repository.scopeLoads, greaterThan(0));
        expect(repository.eventLoads, greaterThan(0));

        container
            .read(capabilityState.notifier)
            .show(_calendarUnavailableSnapshot());
        await tester.pumpAndSettle();

        expect(find.text('Private review'), findsNothing);
        expect(find.text('Calendar is unavailable'), findsOneWidget);
      },
    );

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
        expect(repository.scopeLoads, 0);
        expect(repository.eventLoads, 0);
        expect(repository.createdDrafts, isEmpty);
      },
    );

    for (final delayed in ['scopes', 'timezone', 'read']) {
      testWidgets('dual Calendar waits for delayed $delayed before saving', (
        tester,
      ) async {
        final gate = Completer<void>();
        var pointerDowns = 0;
        final repository = _FakeCalendarRepository(
          events: delayed == 'read'
              ? [
                  CalendarEvent(
                    id: 'delayed-event',
                    title: 'Existing event',
                    allowedActions: const ['update', 'delete'],
                    startTime: DateTime(2026, 6, 27, 10),
                    endTime: DateTime(2026, 6, 27, 11),
                    etag: '"v1"',
                  ),
                ]
              : [],
          readEventGate: delayed == 'read' ? gate.future : null,
          scopeLoadGate: delayed == 'scopes' ? gate.future : null,
        );
        await tester.pumpWidget(
          createTestApp(
            Listener(
              onPointerDown: (_) => pointerDowns++,
              child: Scaffold(
                body: const CalendarScreen(),
                bottomNavigationBar: NavigationBar(
                  destinations: const [
                    NavigationDestination(
                      icon: Icon(Icons.home),
                      label: 'Home',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.calendar_month),
                      label: 'Calendar',
                    ),
                  ],
                ),
              ),
            ),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                const AsyncData(_readySnapshot),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarEvaluationTimeZoneProvider.overrideWith((ref) async {
                if (delayed == 'timezone') await gate.future;
                return 'Europe/Berlin';
              }),
            ],
          ),
        );
        await tester.pump();
        if (delayed == 'read') await tester.pumpAndSettle();
        // The production callback remains asynchronous while its prerequisites resolve.
        expect(find.byType(AlertDialog), findsNothing);
        final release = Timer(const Duration(milliseconds: 750), gate.complete);
        try {
          await dual.editNativeDualCalendarEvent(
            tester,
            tooltip: delayed == 'read' ? 'Edit Existing event' : 'Create event',
            title: 'Delayed event',
            deadline: DateTime.now().add(const Duration(seconds: 5)),
          );
          await tester.pumpAndSettle();
          expect(pointerDowns, 1); // The editor-opening control is tapped once.
          expect(
            (delayed == 'read'
                    ? repository.updatedDrafts
                    : repository.createdDrafts)
                .single
                .title,
            'Delayed event',
          );
          if (delayed == 'read') expect(repository.readCalls, 1);
          expect(find.text('Delayed event'), findsOneWidget);
        } finally {
          release.cancel();
        }
      });
    }

    for (final failure in ['disabled', 'editor-error', 'missing-control']) {
      testWidgets('dual Calendar rejects $failure without extra activation', (
        tester,
      ) async {
        final repository = _FakeCalendarRepository(events: []);
        var pointerDowns = 0;
        await tester.pumpWidget(
          createTestApp(
            Listener(
              onPointerDown: (_) => pointerDowns++,
              child: const CalendarScreen(),
            ),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                AsyncData(
                  failure == 'disabled'
                      ? _calendarUnavailableSnapshot()
                      : _readySnapshot,
                ),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarEvaluationTimeZoneProvider.overrideWith((ref) async {
                throw StateError('private-editor-preparation-failure');
              }),
            ],
          ),
        );
        await tester.pumpAndSettle();
        Object? caught;
        try {
          await dual.editNativeDualCalendarEvent(
            tester,
            tooltip: failure == 'missing-control'
                ? 'Absent control'
                : 'Create event',
            title: 'Must not create',
            deadline: DateTime.now().add(const Duration(milliseconds: 50)),
          );
        } catch (error) {
          caught = error;
        }
        expect(caught, isA<TestFailure>());
        expect(pointerDowns, failure == 'editor-error' ? 1 : 0);
        expect(repository.createdDrafts, isEmpty);
        expect(repository.updatedDrafts, isEmpty);
        expect(find.byType(AlertDialog), findsNothing);
      });
    }

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
