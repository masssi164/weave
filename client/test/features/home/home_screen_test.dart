import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/home/presentation/home_screen.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_files_repository.dart';
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
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => [
          ChatConversation(
            id: 'room-1',
            title: 'Product room',
            isDirectMessage: false,
            previewType: ChatConversationPreviewType.text,
            previewText: 'Calendar review',
            lastActivityAt: now,
            unreadCount: 1,
            isInvite: false,
          ),
        ],
      );
      final filesRepository = FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.weave.test'),
          accountLabel: 'Weave files',
        ),
        listings: {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.md',
                path: '/Roadmap.md',
                isDirectory: false,
                modifiedAt: now,
              ),
            ],
          ),
        },
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
            chatRepositoryProvider.overrideWithValue(chatRepository),
            filesRepositoryProvider.overrideWithValue(filesRepository),
          ],
        ),
      );
      await _pumpHomeData(tester);

      expect(find.text('Product center'), findsOneWidget);
      expect(find.text('Today'), findsOneWidget);
      expect(find.text('Continue'), findsOneWidget);
      expect(find.text('Planning sync'), findsOneWidget);
      expect(find.text('Product room'), findsOneWidget);
      expect(
        find.bySemanticsLabel(RegExp('Planning sync.*Workspace room')),
        findsOneWidget,
      );

      final homeList = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(
        find.text('Roadmap.md'),
        200,
        scrollable: homeList,
      );
      expect(find.text('Roadmap.md'), findsOneWidget);

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
