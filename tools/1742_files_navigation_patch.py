#!/usr/bin/env python3
"""Apply the reviewed breadcrumb regression fix and remaining journey migrations once."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "client/lib/features/files/presentation/files_screen.dart": "425168f46d6514c4b39cfdcc7a7c25e17711d368",
    "client/integration_test/dual_ios_collaboration_test.dart": "7e58cd85d0cdc7d76a7a4165575a5ded85790892",
    "client/integration_test/system_browser_auth_e2e_test.dart": "9c811557e7b18cfba08b3fee348ad889fb420cbe",
    "client/test/features/files/files_screen_test.dart": "83d4ae340b99a3e79fa40a7041852bd49633d70a",
}

def once(text, before, after):
    if text.count(before) != 1:
        raise SystemExit("Reviewed unique source anchor changed; refusing transformation")
    return text.replace(before, after, 1)

def region(text, start, end, transform):
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit("Reviewed source region changed")
    a, b = text.index(start), text.index(end)
    if b <= a:
        raise SystemExit("Reviewed source region order changed")
    return text[:a] + transform(text[a:b]) + text[b:]

sources = {}
for relative, expected in EXPECTED.items():
    path = ROOT / relative
    raw = path.read_bytes()
    digest = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if path.is_symlink() or digest != expected:
        raise SystemExit("Reviewed source fingerprint changed")
    sources[relative] = raw.decode()

screen_path = "client/lib/features/files/presentation/files_screen.dart"
sources[screen_path] = once(sources[screen_path], '''    final arguments = route.settings.arguments;
    final isRoot = route.settings.name == AppRoutes.files;
    final routePath = isRoot
        ? '/'
        : arguments is Map
        ? arguments['folderPath']
        : null;
    if (routePath == path) {
      found = true;
      return true;
    }
    return isRoot || route.isFirst;''', '''    final location = route.settings.arguments;
    if (location is FilesRouteLocation &&
        location.path == path && location.spaceRef == spaceRef) {
      found = true;
      return true;
    }
    // The overview contains locations; it is not their directory root.
    return route.settings.name == AppRoutes.files || route.isFirst;''')

native_path = "client/integration_test/dual_ios_collaboration_test.dart"
sources[native_path] = once(sources[native_path], '''  expect(overview, findsOneWidget);
  final owner = ProviderScope.containerOf(tester.element(overview));''', '''  expect(overview, findsOneWidget);
  // An iOS pop can reveal the previous page before its transition finishes.
  // Wait for its actual interactive route rather than tapping through a modal.
  await _wait(tester, () {
    final route = ModalRoute.of(tester.element(overview));
    return route?.isCurrent == true &&
        route?.animation?.isCompleted != false &&
        route?.secondaryAnimation?.isDismissed != false;
  }, const Duration(seconds: 15));
  final owner = ProviderScope.containerOf(tester.element(overview));''')

browser_path = "client/integration_test/system_browser_auth_e2e_test.dart"
browser = once(sources[browser_path],
    "import 'package:weave/features/files/presentation/files_screen.dart';",
    "import 'package:weave/features/files/presentation/files_screen.dart';\n"
    "import 'package:weave/features/files/presentation/files_locations_screen.dart';")
browser = once(browser, '''        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(FilesScreen), findsOneWidget);
        await tester.tap(
          find.descendant(
            of: find.byType(NavigationBar),
            matching: find.byIcon(Icons.calendar_month_outlined),''', '''        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(FilesLocationsScreen), findsOneWidget);
        final privateLocation = find.byWidgetPredicate((widget) =>
            widget is Semantics &&
            widget.properties.identifier == 'files-location-private');
        final locationDeadline = DateTime.now().add(const Duration(seconds: 90));
        while (privateLocation.hitTestable().evaluate().length != 1) {
          if (!DateTime.now().isBefore(locationDeadline)) {
            throw TestFailure('The current private Files location was not usable.');
          }
          await tester.pump(const Duration(milliseconds: 250));
        }
        await tester.tap(privateLocation);
        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(FilesScreen), findsOneWidget);
        await tester.pageBack();
        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(FilesLocationsScreen), findsOneWidget);
        await tester.tap(
          find.descendant(
            of: find.byType(NavigationBar),
            matching: find.byIcon(Icons.calendar_month_outlined),''')
sources[browser_path] = browser

tests_path = "client/test/features/files/files_screen_test.dart"
tests = once(sources[tests_path],
    "import 'package:weave/features/files/presentation/files_screen.dart';",
    "import 'package:weave/features/files/presentation/files_screen.dart';\n"
    "import 'package:weave/features/files/presentation/files_locations_screen.dart';\n"
    "import 'package:weave/features/files/presentation/files_folder_route.dart';")

def app_scaling(text):
    text = once(text, "  TargetPlatform? platform,", "  TargetPlatform? platform,\n  double? textScale,")
    return once(text, "    routerConfig: router,", '''    routerConfig: router,
    builder: (context, child) => textScale == null
        ? child!
        : MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),''')
tests = region(tests, "Widget _filesNavigationApp(", "GoRouter _filesRouter(", app_scaling)
# A pushed native route is read from the visible page, not the opt-in browser URL.
tests = once(tests,
    "expect(router.routeInformationProvider.value.uri.path, '/files/private');",
    "expect(GoRouterState.of(tester.element(find.byType(FilesScreen))).uri.path, '/files/private');")

def toolbar(text):
    a = text.index("              await tester.pumpWidget(")
    b = text.index("                  overrides: [", a)
    text = text[:a] + '''              final router = GoRouter(
                initialLocation: AppRoutes.filesLocation('/', spaceRef: 'workspace-default'),
                routes: [GoRoute(
                  path: AppRoutes.files,
                  builder: (context, state) => const Scaffold(body: FilesLocationsScreen()),
                  routes: [
                    GoRoute(path: AppRoutes.filesPrivateRelative,
                        builder: (context, state) => const FilesFolderRoute(path: '/')),
                    GoRoute(path: AppRoutes.filesSpaceRelative,
                        builder: (context, state) => const Scaffold(body:
                            SizedBox(height: 700, child: FilesScreen(isFolderRoute: true)))),
                  ],
                )],
              );
              addTearDown(router.dispose);
              await tester.pumpWidget(
                _filesNavigationApp(
                  router,
                  textScale: scale,
''' + text[b:]
    text = once(text, '''              // Stop mid-scroll, where a pinned large title clipped the selector's
              // semantics despite its rendered 48px height.''', '''              // Stop mid-scroll, where a pinned large title previously clipped
              // interactive toolbar semantics despite their rendered height.''')
    a = text.index("              final selector = find.byKey(const Key('files-space-selector'));")
    b = text.index("              expect(tester.takeException(), isNull);", a)
    text = text[:a] + '''              expect(find.byKey(const Key('files-space-selector')), findsNothing);
              expect(find.text('Organization workspace'), findsWidgets);
              await tester.pageBack();
              await tester.pumpAndSettle();
              expect(find.byType(FilesLocationsScreen), findsOneWidget);
              final privateLocation = find.byWidgetPredicate((widget) =>
                  widget is Semantics &&
                  widget.properties.identifier == 'files-location-private');
              expect(privateLocation.hitTestable(), findsOneWidget);
              expect(tester.getSize(privateLocation).height, greaterThanOrEqualTo(48));
              expect(tester.getSemantics(privateLocation).getSemanticsData().label,
                  contains(l10n.filesPrivateSpace));
              await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
              await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
              await tester.tap(privateLocation);
              await tester.pumpAndSettle();
              final container = ProviderScope.containerOf(tester.element(find.byType(FilesScreen)));
              expect(container.read(filesProvider).requireValue.rootSpaceRef, isNull);
              expect(repository.requestedPaths, ['/']);
''' + text[b:]
    return text

tests = region(tests,
    "    for (final locale in ['en', 'de']) {\n      for (final scale in [1.0, 1.5]) {",
    "    testWidgets('meets androidTapTargetGuideline'", toolbar)
sources[tests_path] = tests

for relative, text in sources.items():
    (ROOT / relative).write_text(text)
print("Applied typed breadcrumb history and retained all native/accessibility regression cases")
