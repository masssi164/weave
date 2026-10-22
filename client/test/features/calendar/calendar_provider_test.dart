import 'package:weave/features/calendar/domain/entities/calendar_write_result.dart';
import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

class _DeferredRepository implements CalendarRepository {
  final mutation = Completer<CalendarEvent>();
  String member = 'first';
  CalendarEvent? acknowledgement;
  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async => CalendarEventList(
    scope: scope ?? CalendarScope.workspace,
    events: [
      if (acknowledgement != null) acknowledgement!,
      CalendarEvent(
        id: '$member-event',
        title: member,
        startTime: DateTime.utc(2026, 10, 25, 10),
        endTime: DateTime.utc(2026, 10, 25, 11),
      ),
    ],
  );
  @override
  Future<CalendarScopeList> loadScopes() async => const CalendarScopeList(
    scopes: [
      CalendarScope.workspace,
      CalendarScope(id: 'calendar:other', type: 'team', label: 'Other'),
    ],
  );
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) =>
      mutation.future.then((event) => acknowledgement = event);
  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) => mutation.future;
  @override
  Future<void> deleteEvent(String id, {String? etag}) async {
    await mutation.future;
  }

  @override
  Future<CalendarEvent> readEvent(String id) => throw UnimplementedError();
}

class _AcknowledgedRepository extends _DeferredRepository {
  int writes = 0;
  bool refreshFails = false;
  CalendarFailure? writeFailure;
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    writes++;
    if (writeFailure != null) throw writeFailure!;
    return acknowledgement = CalendarEvent(
      id: 'created-event',
      title: draft.title,
      startTime: draft.startTime,
      endTime: draft.endTime,
      scope: draft.scope,
    );
  }

  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) {
    if (writes > 0 && refreshFails) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    return super.loadEvents(scope: scope, from: from, to: to);
  }
}

class _ReadbackRepository extends _DeferredRepository {
  _ReadbackRepository(this.operation, this.mode, {this.outside = false});
  final String operation;
  String mode;
  final bool outside;
  int writes = 0;
  int reads = 0;
  CalendarEvent get acknowledged => CalendarEvent(
    id: 'stable-event',
    title: 'New value',
    etag: '"new"',
    startTime: DateTime.utc(outside ? 2035 : 2026, 10, 25, 10),
    endTime: DateTime.utc(outside ? 2035 : 2026, 10, 25, 11),
  );
  CalendarEvent get old => CalendarEvent(
    id: 'stable-event',
    title: 'Old value',
    etag: '"old"',
    startTime: DateTime.utc(2026, 10, 25, 10),
    endTime: DateTime.utc(2026, 10, 25, 11),
  );
  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async => CalendarEventList(
    scope: scope ?? CalendarScope.workspace,
    events: writes == 0
        ? (operation == 'create' ? [] : [old])
        : mode == 'empty'
        ? []
        : mode == 'stale'
        ? [old]
        : operation == 'delete' || outside
        ? []
        : [acknowledged],
  );
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    writes++;
    return acknowledged;
  }

  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) async {
    writes++;
    return acknowledged;
  }

  @override
  Future<void> deleteEvent(String id, {String? etag}) async {
    writes++;
  }

  @override
  Future<CalendarEvent> readEvent(String id) async {
    reads++;
    return acknowledged;
  }
}

void main() {
  for (final operation in ['create', 'update', 'delete']) {
    for (final mode in operation == 'delete' ? ['stale'] : ['empty', 'stale']) {
      test(
        '$operation ACK does not turn $mode successful agenda into observed write; retry reads only',
        () async {
          final repository = _ReadbackRepository(operation, mode);
          final container = ProviderContainer.test(
            overrides: [
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarEvaluationTimeZoneProvider.overrideWith(
                (ref) async => 'UTC',
              ),
              weaveAuthenticatedSessionProvider.overrideWith(
                (ref) async => WeaveAuthenticatedSession(
                  apiBaseUrl: Uri.parse('https://api.example/api'),
                  accessToken: 'fixture',
                ),
              ),
            ],
          );
          addTearDown(container.dispose);
          final subscription = container.listen(calendarProvider, (_, _) {});
          addTearDown(subscription.close);
          container
              .read(calendarViewDateProvider.notifier)
              .select(DateTime.utc(2026, 10));
          await container.read(calendarProvider.future);
          final notifier = container.read(calendarProvider.notifier);
          final draft = CalendarEventDraft(
            title: 'New value',
            timezone: 'UTC',
            startTime: repository.acknowledged.startTime,
            endTime: repository.acknowledged.endTime,
          );
          final result = await (operation == 'create'
              ? notifier.createEvent(draft)
              : operation == 'update'
              ? notifier.updateEvent('stable-event', draft, etag: '"old"')
              : notifier.deleteEvent('stable-event', etag: '"old"'));
          expect(result.outcome, CalendarWriteOutcome.committedRefreshPending);
          expect(repository.writes, 1);
          expect(repository.reads, 0);
          repository.mode = 'confirmed';
          container.invalidate(calendarProvider);
          final current = await container.read(calendarProvider.future);
          expect(
            current.events.map((e) => e.title),
            operation == 'delete' ? isEmpty : contains('New value'),
          );
          expect(repository.writes, 1);
        },
      );
    }
  }
  test(
    'future event outside agenda is independently read without injecting it into current view',
    () async {
      final repository = _ReadbackRepository('create', 'empty', outside: true);
      final container = ProviderContainer.test(
        overrides: [
          calendarRepositoryProvider.overrideWithValue(repository),
          calendarEvaluationTimeZoneProvider.overrideWith((ref) async => 'UTC'),
          weaveAuthenticatedSessionProvider.overrideWith(
            (ref) async => WeaveAuthenticatedSession(
              apiBaseUrl: Uri.parse('https://api.example/api'),
              accessToken: 'fixture',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      final subscription = container.listen(calendarProvider, (_, _) {});
      addTearDown(subscription.close);
      container
          .read(calendarViewDateProvider.notifier)
          .select(DateTime.utc(2026, 10));
      await container.read(calendarProvider.future);
      final result = await container
          .read(calendarProvider.notifier)
          .createEvent(
            CalendarEventDraft(
              title: 'New value',
              timezone: 'UTC',
              startTime: repository.acknowledged.startTime,
              endTime: repository.acknowledged.endTime,
            ),
          );
      expect(result.outcome, CalendarWriteOutcome.committed);
      expect(repository.reads, 1);
      expect(repository.writes, 1);
      expect(container.read(calendarProvider).requireValue.events, isEmpty);
    },
  );

  for (final outcome in CalendarWriteOutcome.values) {
    test('write acknowledgement stays distinct from $outcome', () async {
      final repository = _AcknowledgedRepository()
        ..refreshFails =
            outcome == CalendarWriteOutcome.committedRefreshPending;
      if (outcome == CalendarWriteOutcome.notCommitted) {
        repository.writeFailure = const CalendarFailure(
          CalendarFailureKind.permission,
        );
      }
      if (outcome == CalendarWriteOutcome.uncertain) {
        repository.writeFailure = const CalendarFailure(
          CalendarFailureKind.unavailable,
          writeMayHaveCommitted: true,
        );
      }
      final container = ProviderContainer.test(
        overrides: [
          calendarRepositoryProvider.overrideWithValue(repository),
          calendarEvaluationTimeZoneProvider.overrideWith((ref) async => 'UTC'),
          weaveAuthenticatedSessionProvider.overrideWith(
            (ref) async => WeaveAuthenticatedSession(
              apiBaseUrl: Uri.parse('https://api.example.test/api'),
              accessToken: 'test',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      final subscription = container.listen(calendarProvider, (_, _) {});
      addTearDown(subscription.close);
      await container.read(calendarProvider.future);
      final draft = CalendarEventDraft(
        title: 'Retained draft',
        startTime: DateTime.utc(2026, 10, 25, 10),
        endTime: DateTime.utc(2026, 10, 25, 11),
        timezone: 'UTC',
        scope: CalendarScope.workspace,
      );
      final operation = container
          .read(calendarProvider.notifier)
          .createEvent(draft);
      if (repository.writeFailure != null) {
        await expectLater(
          operation,
          throwsA(
            isA<CalendarWriteFailure>().having(
              (failure) => failure.outcome,
              'outcome',
              outcome,
            ),
          ),
        );
      } else {
        final result = await operation;
        expect(result.outcome, outcome);
        expect(result.event?.id, 'created-event');
        expect(result.ownerKey, isNotEmpty);
        if (repository.refreshFails) {
          repository.refreshFails = false;
          container.invalidate(calendarProvider);
          await container.read(calendarProvider.future);
        }
      }
      expect(repository.writes, 1);
    });
  }

  for (final changed in ['date', 'scope', 'same-owner-token']) {
    test('confirmed acknowledgement survives $changed transition', () async {
      final repository = _DeferredRepository();
      var session = WeaveAuthenticatedSession(
        apiBaseUrl: Uri.parse('https://api.example.test/api'),
        accessToken: 'first',
      );
      final container = ProviderContainer.test(
        overrides: [
          calendarRepositoryProvider.overrideWithValue(repository),
          calendarEvaluationTimeZoneProvider.overrideWith((ref) async => 'UTC'),
          weaveAuthenticatedSessionProvider.overrideWith(
            (ref) async => session,
          ),
        ],
      );
      addTearDown(container.dispose);
      final subscription = container.listen(calendarProvider, (_, _) {});
      addTearDown(subscription.close);
      await container.read(calendarProvider.future);
      final future = container
          .read(calendarProvider.notifier)
          .createEvent(
            CalendarEventDraft(
              title: 'Acknowledged',
              startTime: DateTime.utc(2026, 10, 25, 10),
              endTime: DateTime.utc(2026, 10, 25, 11),
              timezone: 'UTC',
              scope: CalendarScope.workspace,
            ),
          );
      await Future<void>.delayed(Duration.zero);
      if (changed == 'date') {
        container
            .read(calendarViewDateProvider.notifier)
            .select(DateTime.utc(2026, 12));
      }
      if (changed == 'scope') {
        container
            .read(selectedCalendarScopeProvider.notifier)
            .select(
              const CalendarScope(
                id: 'calendar:other',
                type: 'team',
                label: 'Other',
              ),
            );
      }
      if (changed == 'same-owner-token') {
        session = WeaveAuthenticatedSession(
          apiBaseUrl: session.apiBaseUrl,
          accessToken: 'refreshed',
        );
        container.invalidate(weaveAuthenticatedSessionProvider);
      }
      repository.mutation.complete(
        CalendarEvent(
          id: 'acknowledged-id',
          title: 'Acknowledged',
          startTime: DateTime.utc(2026, 10, 25, 10),
          endTime: DateTime.utc(2026, 10, 25, 11),
          scope: CalendarScope.workspace,
        ),
      );
      final result = await future;
      expect(result.event?.id, 'acknowledged-id');
      expect(
        result.outcome,
        changed == 'same-owner-token'
            ? CalendarWriteOutcome.committed
            : CalendarWriteOutcome.committedRefreshPending,
      );
      expect(result.contextCurrent, changed == 'same-owner-token');
    });
  }

  for (final change in ['scope', 'date', 'session']) {
    test(
      'late mutation failure does not restore previous $change view',
      () async {
        final repository = _DeferredRepository();
        var session = WeaveAuthenticatedSession(
          apiBaseUrl: Uri.parse('https://api.example.test/api'),
          accessToken: 'first',
        );
        final container = ProviderContainer(
          overrides: [
            calendarEvaluationTimeZoneProvider.overrideWith(
              (ref) async => 'UTC',
            ),
            calendarRepositoryProvider.overrideWithValue(repository),
            weaveAuthenticatedSessionProvider.overrideWith(
              (ref) async => session,
            ),
          ],
        );
        addTearDown(container.dispose);
        final subscription = container.listen(calendarProvider, (_, _) {});
        addTearDown(subscription.close);
        await container.read(weaveAuthenticatedSessionProvider.future);
        await container.read(calendarProvider.future);
        final pending = container
            .read(calendarProvider.notifier)
            .deleteEvent('first-event', etag: '"first"');
        final assertion = expectLater(pending, throwsA(isA<CalendarFailure>()));
        repository.member = 'second';
        if (change == 'scope') {
          container
              .read(selectedCalendarScopeProvider.notifier)
              .select(
                const CalendarScope(
                  id: 'calendar:other',
                  type: 'team',
                  label: 'Other',
                ),
              );
        } else if (change == 'date') {
          container
              .read(calendarViewDateProvider.notifier)
              .select(DateTime.utc(2026, 12));
        } else {
          session = WeaveAuthenticatedSession(
            apiBaseUrl: Uri.parse('https://api.example.test/api'),
            accessToken: 'second',
          );
          container.invalidate(weaveAuthenticatedSessionProvider);
          await container.read(weaveAuthenticatedSessionProvider.future);
        }
        await container.read(calendarProvider.future);
        expect(
          container.read(calendarProvider).requireValue.events.single.title,
          'second',
        );
        repository.mutation.completeError(
          const CalendarFailure(CalendarFailureKind.session),
        );
        await assertion;
        expect(
          container.read(calendarProvider).requireValue.events.single.title,
          'second',
        );
      },
    );
  }

  test(
    'session failure never restores cached member data even before invalidation arrives',
    () async {
      final repository = _DeferredRepository();
      final container = ProviderContainer(
        overrides: [
          calendarEvaluationTimeZoneProvider.overrideWith((ref) async => 'UTC'),
          calendarRepositoryProvider.overrideWithValue(repository),
          weaveAuthenticatedSessionProvider.overrideWith(
            (ref) async => WeaveAuthenticatedSession(
              apiBaseUrl: Uri.parse('https://api.example.test/api'),
              accessToken: 'first',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      final subscription = container.listen(calendarProvider, (_, _) {});
      addTearDown(subscription.close);
      await container.read(weaveAuthenticatedSessionProvider.future);
      await container.read(calendarProvider.future);
      final pending = container
          .read(calendarProvider.notifier)
          .deleteEvent('first-event', etag: '"first"');
      final assertion = expectLater(pending, throwsA(isA<CalendarFailure>()));
      repository.mutation.completeError(
        const CalendarFailure(CalendarFailureKind.session),
      );
      await assertion;
      expect(container.read(calendarProvider).hasError, isTrue);
      expect(container.read(calendarProvider).asData, isNull);
    },
  );
}
