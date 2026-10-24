import 'dart:async';
import 'dart:ui' show SemanticsAction;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/home/presentation/home_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../helpers/test_app.dart';

WorkspaceCapabilityState ready(WorkspaceCapability capability) =>
    WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.ready,
    );
final capabilities = WorkspaceCapabilitySnapshot(
  shellAccess: ready(WorkspaceCapability.shellAccess),
  chat: ready(WorkspaceCapability.chat),
  files: ready(WorkspaceCapability.files),
  calendar: ready(WorkspaceCapability.calendar),
  boards: ready(WorkspaceCapability.boards),
);
const connection = WorkspaceConnectionState(
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

void main() {
  for (final locale in const [Locale('en'), Locale('de')]) {
    testWidgets(
      'Home has accessible product actions in ${locale.languageCode}',
      (tester) async {
        final l10n = lookupAppLocalizations(locale);
        var homeReads = 0;
        var spaceReads = 0;
        final destinations = {
          l10n.homeOpenChat: AppRoutes.chat,
          l10n.homeOpenFiles: AppRoutes.files,
          l10n.homeOpenCalendar: AppRoutes.calendar,
          l10n.navSettings: AppRoutes.settings,
        };
        final router = GoRouter(
          initialLocation: AppRoutes.home,
          routes: [
            GoRoute(
              path: AppRoutes.home,
              builder: (_, _) => const Scaffold(
                body: SingleChildScrollView(child: ShellWorkspaceStatus()),
              ),
            ),
            for (final route in destinations.values)
              GoRoute(
                path: route,
                builder: (_, _) => Scaffold(body: Text('destination:$route')),
              ),
          ],
        );
        addTearDown(router.dispose);
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              workspaceConnectionStateProvider.overrideWithValue(
                const AsyncData(connection),
              ),
              workspaceCapabilitySnapshotProvider.overrideWithValue(
                AsyncData(capabilities),
              ),
              weaveApiMemberSpacesProvider.overrideWith((ref) async {
                spaceReads++;
                return const MemberSpaceAccessSnapshot(
                  visibleSpaceRefs: {'workspace-default'},
                  defaultSpaceReadable: true,
                );
              }),
              weaveApiWorkspaceHomeProvider.overrideWith((ref) async {
                homeReads++;
                throw const AppFailure.unknown('RAW BACKEND DIAGNOSTICS');
              }),
            ],
            child: MaterialApp.router(
              locale: locale,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              routerConfig: router,
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text(l10n.homeWorkspaceActionsTitle), findsOneWidget);
        expect(
          find.textContaining(l10n.settingsWorkspaceRecoveryAvailable),
          findsNothing,
        );
        expect(
          find.textContaining(l10n.settingsWorkspaceBoardsLabel),
          findsNothing,
        );
        expect(find.textContaining('RAW BACKEND DIAGNOSTICS'), findsNothing);
        expect(homeReads, 0);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(ShellWorkspaceStatus)),
        );
        await container.read(weaveApiMemberSpacesProvider.future);
        var expectedSpaceReads = 1;
        for (final entry in destinations.entries) {
          final action = find.bySemanticsLabel(entry.key);
          expect(action, findsOneWidget);
          expect(
            tester
                .getSemantics(action)
                .getSemanticsData()
                .hasAction(SemanticsAction.tap),
            isTrue,
          );
          expect(tester.getSize(action).height, greaterThanOrEqualTo(48));
          await tester.tap(action);
          await tester.pumpAndSettle();
          expect(find.text('destination:${entry.value}'), findsOneWidget);
          await container.read(weaveApiMemberSpacesProvider.future);
          if (entry.value != AppRoutes.settings) expectedSpaceReads++;
          expect(spaceReads, expectedSpaceReads);
          router.go(AppRoutes.home);
          await tester.pumpAndSettle();
        }
      },
    );
  }

  testWidgets('Home retry reloads failed member Space admission', (
    tester,
  ) async {
    var spaceReads = 0;
    await tester.pumpWidget(
      ProviderScope(
        retry: (_, _) => null,
        overrides: [
          workspaceConnectionStateProvider.overrideWithValue(
            const AsyncData(connection),
          ),
          workspaceCapabilitySnapshotProvider.overrideWith(
            (ref) => ref
                .watch(weaveApiMemberSpacesProvider)
                .whenData((_) => capabilities),
          ),
          weaveApiMemberSpacesProvider.overrideWith((ref) async {
            if (++spaceReads == 1) {
              throw const AppFailure.unknown('Space read temporarily failed');
            }
            return const MemberSpaceAccessSnapshot(
              visibleSpaceRefs: {'workspace-default'},
              defaultSpaceReadable: true,
            );
          }),
        ],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SingleChildScrollView(child: ShellWorkspaceStatus()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(spaceReads, 1);
    expect(find.text('Workspace access could not be checked.'), findsOneWidget);
    expect(find.textContaining('Backend'), findsNothing);
    expect(find.textContaining('Space read temporarily failed'), findsNothing);
    expect(find.text('Open Chat'), findsNothing);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(spaceReads, 2);
    expect(find.text('Open Chat'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
  });

  testWidgets(
    'Home failure shows localized member recovery without raw diagnostics',
    (tester) async {
      final response = Completer<WorkspaceHomeSnapshot?>();
      await tester.pumpWidget(
        createTestApp(
          const HomeRecentActivity(),
          overrides: [
            weaveApiWorkspaceHomeProvider.overrideWith(
              (ref) => response.future,
            ),
          ],
        ),
      );
      await tester.pump();
      response.completeError(
        const AppFailure.unknown(
          'Configure provider password at https://private.example.test with Bearer unsafe-token',
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();
      expect(
        find.text('Recent workspace activity is unavailable right now.'),
        findsOneWidget,
      );
      expect(find.text('Retry'), findsOneWidget);
      expect(find.textContaining('password'), findsNothing);
      expect(find.textContaining('private.example.test'), findsNothing);
      expect(find.textContaining('unsafe-token'), findsNothing);
    },
  );
}
