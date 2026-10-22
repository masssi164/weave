import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/test_app.dart';

class _NamedCalendar implements CalendarRepository {
  _NamedCalendar(this.scope);
  final CalendarScope scope;
  @override
  Future<CalendarScopeList> loadScopes() async =>
      CalendarScopeList(scopes: [scope]);
  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async => CalendarEventList(
    scope: this.scope,
    events: [
      CalendarEvent(
        id: 'visible-event',
        title: 'Visible event',
        startTime: DateTime.utc(2026, 10, 25, 10),
        endTime: DateTime.utc(2026, 10, 25, 11),
        scope: this.scope,
      ),
    ],
  );
  @override
  Future<CalendarEvent> readEvent(String id) => throw UnimplementedError();
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) =>
      throw UnimplementedError();
  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) => throw UnimplementedError();
  @override
  Future<void> deleteEvent(String id, {String? etag}) =>
      throw UnimplementedError();
}

void main() {
  for (final example in ['custom-default', 'arbitrary', 'duplicate']) {
    testWidgets(
      'Calendar uses authorized actual Space names in summary, selector and details: $example',
      (tester) async {
        final reference = example == 'custom-default'
            ? 'workspace-default'
            : 'PRIVATE_SPACE_A';
        final actualName = example == 'custom-default'
            ? 'Owner named workspace'
            : 'Research';
        final scopes = [
          CalendarScope(
            id: 'calendar-one',
            type: 'workspace',
            label: 'PRIVATE_RAW_LABEL',
            contextId: reference,
          ),
          if (example == 'duplicate')
            const CalendarScope(
              id: 'calendar-two',
              type: 'channel',
              label: 'PRIVATE_RAW_CHANNEL_LABEL',
              contextId: 'PRIVATE_SPACE_B',
              channelId: 'PRIVATE_CHANNEL_ID',
            ),
        ];
        final access = MemberSpaceAccessSnapshot(
          visibleSpaceRefs: {
            reference,
            if (example == 'duplicate') 'PRIVATE_SPACE_B',
          },
          displayNames: {
            reference: actualName,
            if (example == 'duplicate') 'PRIVATE_SPACE_B': actualName,
          },
          defaultSpaceReadable: example == 'custom-default',
        );
        final repository = _NamedCalendar(scopes.first);
        const ready = WorkspaceCapabilityState(
          capability: WorkspaceCapability.calendar,
          readiness: WorkspaceCapabilityReadiness.ready,
        );
        await tester.pumpWidget(
          createTestApp(
            const CalendarScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                const AsyncData(
                  WorkspaceCapabilitySnapshot(
                    shellAccess: ready,
                    chat: ready,
                    files: ready,
                    calendar: ready,
                    boards: ready,
                  ),
                ),
              ),
              weaveApiMemberSpacesProvider.overrideWith((ref) async => access),
              weaveAuthenticatedSessionProvider.overrideWith(
                (ref) async => WeaveAuthenticatedSession(
                  apiBaseUrl: Uri.parse('https://api.example/api'),
                  accessToken: 'fixture',
                ),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarWorkspaceContextProvider.overrideWith(
                (ref) async => CalendarWorkspaceContext(
                  ownerKey: 'fixture-owner',
                  scopes: CalendarScopeList(scopes: scopes),
                  selectedScope: scopes.first,
                  evaluationTimeZone: 'UTC',
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        final expected = example == 'duplicate' ? 'Research (1)' : actualName;
        expect(find.text(expected), findsWidgets);
        if (example == 'duplicate') {
          expect(find.text('Research (2) · Channel calendar'), findsOneWidget);
        }
        expect(find.textContaining('PRIVATE_'), findsNothing);
        await tester.ensureVisible(find.text('Visible event'));
        await tester.tap(find.text('Visible event'));
        await tester.pumpAndSettle();
        expect(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.text(expected),
          ),
          findsOneWidget,
        );
        expect(find.textContaining('PRIVATE_'), findsNothing);
        expect(
          find.text(
            AppLocalizations.of(
              tester.element(find.byType(AlertDialog)),
            ).calendarWorkspaceScopeTitle,
          ),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}
