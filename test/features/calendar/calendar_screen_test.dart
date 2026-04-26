import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';

import '../../helpers/test_app.dart';

class _FakeCalendarRepository implements CalendarRepository {
  _FakeCalendarRepository([List<CalendarEvent> events = const []])
    : events = List<CalendarEvent>.of(events);

  final List<CalendarEvent> events;
  final List<CalendarEventDraft> createdDrafts = [];
  final List<(String, CalendarEventDraft)> updatedDrafts = [];
  final List<String> deletedIds = [];

  @override
  Future<List<CalendarEvent>> loadEvents() async =>
      List<CalendarEvent>.of(events);

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

void main() {
  group('CalendarScreen', () {
    testWidgets('renders without errors', (tester) async {
      await tester.pumpWidget(createTestApp(const CalendarScreen()));
      await tester.pumpAndSettle();
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
          overrides: [calendarRepositoryProvider.overrideWithValue(repository)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Planning'), findsOneWidget);
      expect(find.text('Sprint planning'), findsOneWidget);
      expect(find.text('Office'), findsOneWidget);
      expect(
        find.bySemanticsLabel(RegExp(r'Planning, starts')),
        findsOneWidget,
      );
    });

    testWidgets('creates and deletes events through the repository', (
      tester,
    ) async {
      final repository = _FakeCalendarRepository();

      await tester.pumpWidget(
        createTestApp(
          const CalendarScreen(),
          overrides: [calendarRepositoryProvider.overrideWithValue(repository)],
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
          overrides: [calendarRepositoryProvider.overrideWithValue(repository)],
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
      await tester.pumpWidget(createTestApp(const CalendarScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(createTestApp(const CalendarScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
