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
  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async => CalendarEventList(
    scope: scope ?? CalendarScope.workspace,
    events: [
      CalendarEvent(
        id: '$member-event',
        title: member,
        startTime: DateTime.utc(2026, 10, 25, 10),
        endTime: DateTime.utc(2026, 10, 25, 11),
      ),
    ],
  );
  @override
  Future<CalendarScopeList> loadScopes() async => const CalendarScopeList();
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) =>
      mutation.future;
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

void main() {
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
