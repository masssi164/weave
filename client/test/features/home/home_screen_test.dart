import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/home/presentation/home_screen.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';
import '../../helpers/test_app.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.configuration});

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeCalendarRepository implements CalendarRepository {
  const _FakeCalendarRepository({required this.events});

  final List<CalendarEvent> events;

  @override
  Future<CalendarScopeList> loadScopes() async => const CalendarScopeList();

  @override
  Future<CalendarEventList> loadEvents({CalendarScope? scope}) async {
    return CalendarEventList(
      scope: scope ?? CalendarScope.workspace,
      events: events,
    );
  }

  @override
  Future<CalendarClientSetup> loadClientSetup() {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEvent> readEvent(String id) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft) {
    throw UnimplementedError();
  }

  @override
  Future<void> deleteEvent(String id) {
    throw UnimplementedError();
  }
}

void main() {
  group('HomeScreen', () {
    testWidgets('renders product center sections and cross-app summaries', (
      tester,
    ) async {
      final now = DateTime.now();
      final calendarRepository = _FakeCalendarRepository(
        events: [
          CalendarEvent(
            id: 'event-1',
            title: 'Planning sync',
            startTime: now.add(const Duration(hours: 2)),
            endTime: now.add(const Duration(hours: 3)),
            location: 'Workspace room',
          ),
        ],
      );
      final home = WorkspaceHomeSnapshot(
        version: 2,
        readiness: WorkspaceCapabilityReadiness.ready,
        summary: 'Weave Home is ready.',
        sections: const [],
        actions: const [],
        recentActivity: [
          WorkspaceHomeActivity(
            activityRef:
                'activity:sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
            domain: WorkspaceHomeActivityDomain.files,
            action: WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
            occurredAt: now.toUtc(),
            visibility: WorkspaceHomeActivityVisibility.workspace,
            actorRefHash:
                'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
            actorIsCurrentUser: true,
            supportSafe: true,
          ),
          WorkspaceHomeActivity(
            activityRef:
                'activity:sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
            domain: WorkspaceHomeActivityDomain.files,
            action: WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
            occurredAt: now.toUtc().subtract(const Duration(minutes: 2)),
            visibility: WorkspaceHomeActivityVisibility.workspace,
            actorRefHash:
                'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
            actorIsCurrentUser: false,
            supportSafe: true,
          ),
        ],
        supportSafe: true,
      );

      await tester.pumpWidget(
        createTestApp(
          const HomeScreen(),
          overrides: [
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: buildTestConfiguration(),
              ),
            ),
            calendarRepositoryProvider.overrideWithValue(calendarRepository),
            weaveApiWorkspaceHomeProvider.overrideWith((ref) async => home),
          ],
        ),
      );
      await _pumpHomeData(tester);

      expect(find.text('Product center'), findsOneWidget);
      expect(find.text('Today'), findsOneWidget);
      expect(find.text('Continue'), findsOneWidget);
      expect(find.text('Planning sync'), findsOneWidget);
      expect(find.text('You completed a Files change'), findsOneWidget);
      expect(
        find.text('A workspace member completed a Files change'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(RegExp('Planning sync.*Workspace room')),
        findsOneWidget,
      );

      final homeList = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(
        find.text('A workspace member completed a Files change'),
        200,
        scrollable: homeList,
      );
      expect(
        find.bySemanticsLabel(
          RegExp(
            'A workspace member completed a Files change.*Shared workspace activity',
          ),
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Roadmap.md'), findsNothing);
      expect(find.textContaining('sha256:'), findsNothing);

      await tester.scrollUntilVisible(
        find.text('Workspace status'),
        200,
        scrollable: homeList,
      );
      expect(find.text('Workspace status'), findsWidgets);
    });

    testWidgets('shows a useful empty state when all Home modules are hidden', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const HomeScreen(),
          overrides: [
            preferencesStoreProvider.overrideWithValue(
              InMemoryPreferencesStore({
                shellModulePreferencesStorageKey:
                    '{"hiddenModules":["workspaceStatus","recentActivity"]}',
              }),
            ),
            calendarRepositoryProvider.overrideWithValue(
              const _FakeCalendarRepository(events: []),
            ),
          ],
        ),
      );
      await _pumpHomeData(tester);

      expect(find.text('Home modules are hidden'), findsOneWidget);
      expect(find.text('Today'), findsNothing);
      expect(find.text('Continue'), findsNothing);
    });
  });
}

Future<void> _pumpHomeData(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}
