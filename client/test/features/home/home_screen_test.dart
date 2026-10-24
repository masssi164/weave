import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
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

WorkspaceCapabilityState _ready(WorkspaceCapability capability) =>
    WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.ready,
    );

final _readyCapabilities = WorkspaceCapabilitySnapshot(
  shellAccess: _ready(WorkspaceCapability.shellAccess),
  chat: _ready(WorkspaceCapability.chat),
  files: _ready(WorkspaceCapability.files),
  calendar: _ready(WorkspaceCapability.calendar),
  boards: _ready(WorkspaceCapability.boards),
);

final _calendarDeniedCapabilities = WorkspaceCapabilitySnapshot(
  shellAccess: _readyCapabilities.shellAccess,
  chat: _readyCapabilities.chat,
  files: _readyCapabilities.files,
  calendar: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.blocked,
    policyState: WorkspaceCapabilityPolicyState.policyBlocked,
    memberSpaceAccessUnavailable: true,
  ),
  boards: _readyCapabilities.boards,
);

const _connection = WorkspaceConnectionState(
  appAuth: IntegrationConnectionState(
    integration: WorkspaceIntegration.appAuth,
    status: IntegrationConnectionStatus.connected,
  ),
  chat: IntegrationConnectionState(
    integration: WorkspaceIntegration.chat,
    status: IntegrationConnectionStatus.connected,
  ),
  files: IntegrationConnectionState(
    integration: WorkspaceIntegration.files,
    status: IntegrationConnectionStatus.connected,
  ),
);

Widget _homeTestApp(
  Widget child, {
  List<dynamic> overrides = const [],
  bool provideReadyCapabilities = true,
}) => createTestApp(
  child,
  overrides: [
    if (provideReadyCapabilities)
      workspaceCapabilitySnapshotProvider.overrideWithValue(
        AsyncData(_readyCapabilities),
      ),
    workspaceConnectionStateProvider.overrideWithValue(
      const AsyncData(_connection),
    ),
    ...overrides,
  ],
);

class _HomeAdmissionController
    extends Notifier<AsyncValue<WorkspaceCapabilitySnapshot>> {
  _HomeAdmissionController(this.initial);

  final AsyncValue<WorkspaceCapabilitySnapshot> initial;

  @override
  AsyncValue<WorkspaceCapabilitySnapshot> build() => initial;

  void show(AsyncValue<WorkspaceCapabilitySnapshot> admission) {
    state = admission;
  }
}

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
  _FakeCalendarRepository({required this.events});

  List<CalendarEvent> events;
  int eventReads = 0;

  @override
  Future<CalendarScopeList> loadScopes() async => const CalendarScopeList();

  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async {
    eventReads++;
    return CalendarEventList(
      scope: scope ?? CalendarScope.workspace,
      events: events,
    );
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
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<void> deleteEvent(String id, {String? etag}) {
    throw UnimplementedError();
  }
}

void main() {
  group('HomeScreen', () {
    final deniedStates = <String, AsyncValue<WorkspaceCapabilitySnapshot>>{
      'denied': AsyncData(_calendarDeniedCapabilities),
      'loading': const AsyncLoading<WorkspaceCapabilitySnapshot>(),
      'failed': AsyncError<WorkspaceCapabilitySnapshot>(
        StateError('private capability diagnostics'),
        StackTrace.empty,
      ),
    };

    for (final entry in deniedStates.entries) {
      testWidgets(
        'Home does not fetch Calendar before current admission: ${entry.key}',
        (tester) async {
          tester.view.physicalSize = const Size(1000, 1800);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          var contextReads = 0;
          final repository = _FakeCalendarRepository(
            events: [_upcomingEvent('Protected planning')],
          );
          await tester.pumpWidget(
            _homeTestApp(
              const HomeScreen(),
              provideReadyCapabilities: false,
              overrides: [
                workspaceCapabilitySnapshotProvider.overrideWithValue(
                  entry.value,
                ),
                calendarRepositoryProvider.overrideWithValue(repository),
                calendarWorkspaceContextProvider.overrideWith((ref) async {
                  contextReads++;
                  return _calendarContext;
                }),
                weaveApiWorkspaceHomeProvider.overrideWith((ref) async => null),
              ],
            ),
          );
          await _pumpHomeData(tester);
          expect(contextReads, 0);
          expect(repository.eventReads, 0);
          expect(find.text('Protected planning'), findsNothing);
          expect(find.text('Product center'), findsOneWidget);
          expect(find.text('Continue'), findsOneWidget);
          expect(find.textContaining('private capability'), findsNothing);
          if (entry.key == 'denied') {
            expect(find.text('Open Chat'), findsOneWidget);
            expect(find.text('Open Files'), findsOneWidget);
          }
        },
      );
    }

    testWidgets(
      'Home hides retained Calendar entries and reloads after current regrant',
      (tester) async {
        var contextReads = 0;
        final repository = _FakeCalendarRepository(
          events: [_upcomingEvent('Initial admitted event')],
        );
        final admission =
            NotifierProvider<
              _HomeAdmissionController,
              AsyncValue<WorkspaceCapabilitySnapshot>
            >(() => _HomeAdmissionController(AsyncData(_readyCapabilities)));
        await tester.pumpWidget(
          _homeTestApp(
            const HomeScreen(),
            provideReadyCapabilities: false,
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => ref.watch(admission),
              ),
              calendarRepositoryProvider.overrideWithValue(repository),
              calendarWorkspaceContextProvider.overrideWith((ref) async {
                contextReads++;
                return _calendarContext;
              }),
              weaveApiWorkspaceHomeProvider.overrideWith((ref) async => null),
            ],
          ),
        );
        await _pumpHomeData(tester);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(HomeScreen)),
        );
        // Another mounted feature may retain the provider while Home hides it.
        final retained = container.listen(calendarProvider, (_, _) {});
        addTearDown(retained.close);
        expect(find.text('Initial admitted event'), findsOneWidget);
        expect(repository.eventReads, greaterThan(0));
        for (final entry in deniedStates.entries) {
          final previousTitle = repository.events.single.title;
          final previousReads = repository.eventReads;
          final previousContextReads = contextReads;
          container.read(admission.notifier).show(entry.value);
          await _pumpHomeData(tester);
          expect(find.text(previousTitle), findsNothing);
          expect(
            container.read(calendarProvider).requireValue.events.single.title,
            previousTitle,
          );
          expect(repository.eventReads, previousReads);
          expect(contextReads, previousContextReads);
          final freshTitle = 'Fresh event after ${entry.key}';
          repository.events = [_upcomingEvent(freshTitle)];
          container
              .read(admission.notifier)
              .show(AsyncData(_readyCapabilities));
          await _pumpHomeData(tester);
          expect(repository.eventReads, greaterThan(previousReads));
          expect(contextReads, greaterThan(previousContextReads));
          expect(find.text(freshTitle), findsOneWidget);
          expect(find.text(previousTitle), findsNothing);
        }
      },
    );

    for (final locale in const [Locale('en'), Locale('de')]) {
      testWidgets(
        'preserves calendar wall-clock fields in ${locale.languageCode}',
        (tester) async {
          // Keep the fixture inside Home's rolling upcoming-event window.
          final today = DateTime.now();
          final eventDay = DateTime.utc(today.year, today.month, today.day + 2);
          final event = CalendarEvent(
            id: 'wall-clock-event',
            title: 'Berlin wall-clock meeting',
            startTime: eventDay.add(const Duration(hours: 2)),
            endTime: eventDay.add(const Duration(hours: 3)),
            timeKind: CalendarTimeKind.zoned,
            timezone: 'Europe/Berlin',
          );
          await tester.pumpWidget(
            _homeTestApp(
              Builder(
                builder: (context) => Localizations.override(
                  context: context,
                  locale: locale,
                  child: const HomeScreen(),
                ),
              ),
              overrides: [
                appStartDiscoveryClientProvider.overrideWith(
                  (ref) => buildTestDiscoveryClient(
                    ref.watch(serverConfigurationRepositoryProvider),
                  ),
                ),
                serverConfigurationRepositoryProvider.overrideWith(
                  (ref) => _FakeServerConfigurationRepository(
                    configuration: buildTestConfiguration(),
                  ),
                ),
                calendarWorkspaceContextProvider.overrideWith(
                  (ref) async => const CalendarWorkspaceContext(
                    ownerKey: 'fixture-member',
                    scopes: CalendarScopeList(),
                    selectedScope: CalendarScope.workspace,
                    evaluationTimeZone: 'Europe/Berlin',
                  ),
                ),
                calendarRepositoryProvider.overrideWithValue(
                  _FakeCalendarRepository(events: [event]),
                ),
                weaveApiWorkspaceHomeProvider.overrideWith((ref) async => null),
              ],
            ),
          );
          await _pumpHomeData(tester);
          final material = MaterialLocalizations.of(
            tester.element(find.byType(HomeScreen)),
          );
          final expected =
              '${material.formatMediumDate(event.startTime)}, '
              '${material.formatTimeOfDay(const TimeOfDay(hour: 2, minute: 0))}-'
              '${material.formatTimeOfDay(const TimeOfDay(hour: 3, minute: 0))}';
          expect(find.text(expected), findsOneWidget);
          expect(
            find.bySemanticsLabel(RegExp(RegExp.escape(expected))),
            findsOneWidget,
          );
        },
      );
    }

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
        version: 3,
        readiness: WorkspaceCapabilityReadiness.ready,
        summary: 'Weave Home is ready.',
        sections: const [],
        actions: const [],
        recentActivity: [
          WorkspaceHomeActivity(
            activityRef:
                'activity:sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
            domain: WorkspaceHomeActivityDomain.files,
            action: WorkspaceHomeActivityAction.filesUserWriteCompleted,
            occurredAt: now.toUtc(),
            visibility: WorkspaceHomeActivityVisibility.private,
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
        _homeTestApp(
          const HomeScreen(),
          overrides: [
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: buildTestConfiguration(),
              ),
            ),
            calendarWorkspaceContextProvider.overrideWith(
              (ref) async => const CalendarWorkspaceContext(
                ownerKey: 'fixture-member',
                scopes: CalendarScopeList(),
                selectedScope: CalendarScope.workspace,
                evaluationTimeZone: 'Europe/Berlin',
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
        find.bySemanticsLabel(
          RegExp('You completed a Files change.*Private activity'),
        ),
        findsOneWidget,
      );
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
        _homeTestApp(
          const HomeScreen(),
          overrides: [
            preferencesStoreProvider.overrideWithValue(
              InMemoryPreferencesStore({
                shellModulePreferencesStorageKey:
                    '{"hiddenModules":["workspaceStatus","recentActivity"]}',
              }),
            ),
            calendarWorkspaceContextProvider.overrideWith(
              (ref) async => const CalendarWorkspaceContext(
                ownerKey: 'fixture-member',
                scopes: CalendarScopeList(),
                selectedScope: CalendarScope.workspace,
                evaluationTimeZone: 'Europe/Berlin',
              ),
            ),
            calendarRepositoryProvider.overrideWithValue(
              _FakeCalendarRepository(events: []),
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

const _calendarContext = CalendarWorkspaceContext(
  ownerKey: 'fixture-member',
  scopes: CalendarScopeList(),
  selectedScope: CalendarScope.workspace,
  evaluationTimeZone: 'Europe/Berlin',
);

CalendarEvent _upcomingEvent(String title) {
  final start = DateTime.now().add(const Duration(hours: 2));
  return CalendarEvent(
    id: 'fixture-event',
    title: title,
    startTime: start,
    endTime: start.add(const Duration(hours: 1)),
  );
}

Future<void> _pumpHomeData(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}
