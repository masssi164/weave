import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/files/presentation/files_locations_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

const _ready = WorkspaceCapabilitySnapshot(
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

const _access = MemberSpaceAccessSnapshot(
  visibleSpaceRefs: {'space-z', 'space-a'},
  defaultSpaceReadable: false,
  displayNames: {'space-z': 'Garden', 'space-a': 'Planning'},
);

GoRouter _router() => GoRouter(
  initialLocation: AppRoutes.files,
  routes: [
    GoRoute(
      path: AppRoutes.files,
      builder: (context, state) => const Scaffold(body: FilesLocationsScreen()),
      routes: [
        GoRoute(
          path: AppRoutes.filesPrivateRelative,
          builder: (context, state) =>
              const Scaffold(body: Text('private-root')),
        ),
        GoRoute(
          path: AppRoutes.filesSpaceRelative,
          builder: (context, state) =>
              Scaffold(body: Text('root:${state.pathParameters['spaceRef']}')),
        ),
      ],
    ),
  ],
);

Future<GoRouter> _pump(
  WidgetTester tester, {
  Future<MemberSpaceAccessSnapshot?> Function()? loadSpaces,
  AsyncValue<WorkspaceCapabilitySnapshot> capabilities = const AsyncData(
    _ready,
  ),
}) async {
  final router = _router();
  addTearDown(router.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        workspaceCapabilitySnapshotProvider.overrideWithValue(capabilities),
        weaveApiMemberSpacesProvider.overrideWith(
          (ref) => loadSpaces == null ? Future.value(_access) : loadSpaces(),
        ),
      ],
      child: MaterialApp.router(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        routerConfig: router,
      ),
    ),
  );
  await tester.pump();
  return router;
}

void main() {
  testWidgets('private and shared locations are folders, not a scope mode', (
    tester,
  ) async {
    await _pump(tester);
    await tester.pumpAndSettle();
    expect(find.byType(ListTile), findsNWidgets(3));
    expect(find.text('Garden'), findsOneWidget);
    expect(find.text('Planning'), findsOneWidget);
    expect(find.byKey(const Key('files-space-selector')), findsNothing);
    for (final tile in tester.widgetList<ListTile>(find.byType(ListTile))) {
      expect(tile.minTileHeight, greaterThanOrEqualTo(48));
    }
    expect(find.textContaining('space-z'), findsNothing);
  });

  testWidgets(
    'a group folder opens its own stable route and Back returns home',
    (tester) async {
      final router = await _pump(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Garden'));
      await tester.pumpAndSettle();
      expect(find.text('root:space-z'), findsOneWidget);
      expect(
        GoRouterState.of(tester.element(find.text('root:space-z'))).uri.path,
        '/files/spaces/space-z',
      );
      expect(router.canPop(), isTrue);
      router.pop();
      await tester.pumpAndSettle();
      expect(find.text('Garden'), findsOneWidget);
      expect(
        GoRouterState.of(
          tester.element(find.byType(FilesLocationsScreen)),
        ).uri.path,
        AppRoutes.files,
      );
      expect(router.canPop(), isFalse);
    },
  );

  testWidgets(
    'private navigation is explicit after visiting a shared location',
    (tester) async {
      final router = await _pump(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Garden'));
      await tester.pumpAndSettle();
      expect(find.text('root:space-z'), findsOneWidget);
      router.pop();
      await tester.pumpAndSettle();
      final private = find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.identifier == 'files-location-private',
      );
      expect(private, findsOneWidget);
      await tester.tap(private);
      await tester.pumpAndSettle();
      expect(find.text('private-root'), findsOneWidget);
      expect(
        GoRouterState.of(tester.element(find.text('private-root'))).uri.path,
        '/files/private',
      );
      expect(find.text('root:space-z'), findsNothing);
    },
  );

  testWidgets('unknown member state exposes no location names', (tester) async {
    await _pump(tester, loadSpaces: () async => null);
    await tester.pumpAndSettle();
    expect(find.byType(ListTile), findsNothing);
    expect(find.text('Garden'), findsNothing);
  });

  testWidgets(
    'refresh hides retained names and revoked locations stay absent',
    (tester) async {
      final refresh = Completer<MemberSpaceAccessSnapshot?>();
      var calls = 0;
      await _pump(
        tester,
        loadSpaces: () => calls++ == 0 ? Future.value(_access) : refresh.future,
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(FilesLocationsScreen)),
      );
      container.invalidate(weaveApiMemberSpacesProvider);
      expect(container.read(weaveApiMemberSpacesProvider).isLoading, isTrue);
      // Drain the provider notification, then render its loading state without
      // completing the replacement membership result.
      await tester.pump();
      await tester.pump();
      expect(find.text('Garden'), findsNothing);
      expect(find.text('Planning'), findsNothing);
      expect(find.byType(ListTile), findsNothing);
      refresh.complete(
        const MemberSpaceAccessSnapshot(
          visibleSpaceRefs: {'space-a'},
          defaultSpaceReadable: false,
          displayNames: {'space-a': 'Planning'},
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Garden'), findsNothing);
      expect(find.text('Planning'), findsOneWidget);
      expect(find.byType(ListTile), findsNWidgets(2));
    },
  );

  testWidgets('failed capability admission does not fetch Space names', (
    tester,
  ) async {
    var calls = 0;
    await _pump(
      tester,
      capabilities: AsyncError(StateError('denied'), StackTrace.empty),
      loadSpaces: () async {
        calls++;
        return _access;
      },
    );
    await tester.pumpAndSettle();
    expect(calls, 0);
    expect(find.byType(ListTile), findsNothing);
  });

  testWidgets('same display names are distinguished without exposing IDs', (
    tester,
  ) async {
    await _pump(
      tester,
      loadSpaces: () async => const MemberSpaceAccessSnapshot(
        visibleSpaceRefs: {'space-b', 'space-a'},
        defaultSpaceReadable: false,
        displayNames: {'space-b': 'Project', 'space-a': 'Project'},
      ),
    );
    await tester.pumpAndSettle();
    final l10n = AppLocalizations.of(
      tester.element(find.byType(FilesLocationsScreen)),
    );
    expect(find.text(l10n.filesSpaceOrdinal('Project', 1)), findsOneWidget);
    expect(find.text(l10n.filesSpaceOrdinal('Project', 2)), findsOneWidget);
    expect(find.textContaining('space-a'), findsNothing);
  });

  test(
    'folder routes carry immutable location identity separately from names',
    () {
      final private = Uri.parse(AppRoutes.filesFolder('file-id', '/Documents'));
      final shared = Uri.parse(
        AppRoutes.filesFolder('file-id', '/Documents', spaceRef: 'space-a'),
      );
      expect(private.path, '/files/private/folders/file-id');
      expect(shared.path, '/files/spaces/space-a/folders/file-id');
      expect(shared.queryParameters, {'folderPath': '/Documents'});
      expect(AppRoutes.filesLocation('/'), '/files/private');
      expect(AppRoutes.files, '/files');
    },
  );
}
