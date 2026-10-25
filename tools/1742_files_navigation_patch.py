#!/usr/bin/env python3
"""One-off reviewed source transformation, removed after applying on the feature branch."""
from __future__ import annotations

import hashlib
from pathlib import Path
import re
import subprocess

BRANCH = "feat/1742-collaboration-files-root"
ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "client/lib/features/files/presentation/files_screen.dart": "7f809c1ac7cd105b70bc50e2c85a3fd97fc48a01",
    "client/integration_test/dual_ios_collaboration_test.dart": "da239f6860dc7e4ed08d9fbaf7672d68b68ceccd",
    "client/test/features/files/files_screen_test.dart": "d51529b0037e0e121158ef483dc9cdfd3adfeae2",
}


def replace_once(text: str, before: str, after: str) -> str:
    if text.count(before) != 1:
        raise SystemExit("Expected unique source anchor was changed; no transformation applied")
    return text.replace(before, after, 1)


def segment(text: str, start: str, end: str, transform) -> str:
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit("Expected bounded source region is unavailable")
    a, b = text.index(start), text.index(end)
    if b <= a:
        raise SystemExit("Source region order changed")
    return text[:a] + transform(text[a:b]) + text[b:]


def blob_sha(raw: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()


if subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip() not in ("", BRANCH):
    raise SystemExit("Source transformation is restricted to the isolated feature checkout")

sources = {}
for relative, expected in EXPECTED.items():
    path = ROOT / relative
    if path.is_symlink() or blob_sha(path.read_bytes()) != expected:
        raise SystemExit("Reviewed source changed; refusing automatic replacement")
    sources[relative] = path.read_text()

screen_path = "client/lib/features/files/presentation/files_screen.dart"
screen = sources[screen_path]
screen = replace_once(screen, """            child: _DirectoryToolbar(
              state: state,
              canSelectRoot: !widget.isFolderRoute,
            ),""", """            child: _DirectoryToolbar(state: state),""")
screen = replace_once(screen, """  const _DirectoryToolbar({required this.state, required this.canSelectRoot});

  final FilesViewState state;
  final bool canSelectRoot;""", """  const _DirectoryToolbar({required this.state});

  final FilesViewState state;""")
screen = replace_once(screen, """    final spaces = access?.visibleSpaceRefs ?? const <String>{};
    final selectedSpace = state.rootSpaceRef;
""", "")
screen = segment(screen, "        if (canSelectRoot) ...[", "        _PathBreadcrumbs(", lambda _: "")
if "files-space-selector" in screen or "canSelectRoot" in screen:
    raise SystemExit("Obsolete scope control was not completely removed")
sources[screen_path] = screen

native_path = "client/integration_test/dual_ios_collaboration_test.dart"
native = sources[native_path]
native = replace_once(native,
    "import 'package:weave/features/files/presentation/files_screen.dart';",
    "import 'package:weave/features/files/presentation/files_screen.dart';\n"
    "import 'package:weave/features/files/presentation/files_locations_screen.dart';")


def migrate_native_main(text: str) -> str:
    text = replace_once(text,
        "      final configuration = await container.read(",
        "      ProviderContainer filesContainer() => _nativeFilesContainer(tester);\n"
        "      final configuration = await container.read(")
    # Only Files state accesses within the real journey change containers. All
    # identity, byte, permission, recovery and native-action assertions remain.
    text, reads = re.subn(r"\bcontainer(\s*\.read\(filesProvider\))", r"filesContainer()\1", text)
    text, listens = re.subn(r"\bcontainer(\s*\.listen\(filesProvider,)", r"filesContainer()\1", text)
    if reads < 5 or listens != 1:
        raise SystemExit("Native Files observation sites changed")
    for call in ("nativeDualFilesUploadProbe", "NativeDualFilesContext.capture"):
        text, count = re.subn(r"(" + re.escape(call) + r"\(\s*)container,", r"\1filesContainer(),", text)
        if count < 1:
            raise SystemExit("Native Files context call changed")
    text, count = re.subn(r"(tapNativeDualFilesAction\(\s*tester,\s*)container,", r"\1filesContainer(),", text)
    if count < 2:
        raise SystemExit("Native Files action sites changed")
    text, count = re.subn(r"(_selectSharedFiles\(\s*tester,)\s*container,", r"\1", text)
    if count < 5:
        raise SystemExit("Native Files navigation sites changed")
    return text

native = segment(native, "void main() {", "Future<void> _selectSharedFiles(", migrate_native_main)
new_navigation = r'''ProviderContainer _nativeFilesContainer(WidgetTester tester) {
  final screen = find.byType(FilesScreen);
  expect(screen, findsOneWidget);
  return ProviderScope.containerOf(tester.element(screen));
}

Future<void> _selectSharedFiles(
  WidgetTester tester,
  String spaceRef,
) async {
  await navigateNativeDualDestination(tester, Icons.folder_outlined);
  await selectNativeDualFilesScope(tester, spaceRef);
  await _wait(tester, () {
    final state = _nativeFilesContainer(tester).read(filesProvider).asData?.value;
    return state?.rootSpaceRef == spaceRef &&
        state?.directoryListing != null &&
        !state!.isBusy &&
        state.directoryFailure == null;
  }, const Duration(seconds: 90));
}

/// Opens a real folder location through native Back and ordinary folder rows.
/// This helper observes the destination controller; it never changes its scope.
Future<void> selectNativeDualFilesScope(
  WidgetTester tester,
  String? spaceRef,
) async {
  final overview = find.byType(FilesLocationsScreen);
  final screen = find.byType(FilesScreen);
  await _wait(tester,
      () => overview.evaluate().length == 1 || screen.evaluate().length == 1,
      const Duration(seconds: 90));
  var backActions = 0;
  while (overview.evaluate().isEmpty) {
    if (backActions++ >= 8 || screen.evaluate().length != 1) {
      throw TestFailure('The Files overview was not reached through navigation.');
    }
    final previous = tester.element(screen);
    await _tap(tester, find.byType(BackButton));
    await _wait(tester,
        () => overview.evaluate().length == 1 ||
            (screen.evaluate().length == 1 && tester.element(screen) != previous),
        const Duration(seconds: 90));
  }
  expect(overview, findsOneWidget);
  final owner = ProviderScope.containerOf(tester.element(overview));
  await _wait(tester, () {
    final capability = owner.read(workspaceCapabilitySnapshotProvider);
    final spaces = owner.read(weaveApiMemberSpacesProvider);
    return !capability.isLoading && !capability.hasError &&
        capability.asData?.value.files.isReady == true &&
        !spaces.isLoading && !spaces.hasError && spaces.asData?.value != null &&
        (spaceRef == null || spaces.asData!.value!.visibleSpaceRefs.contains(spaceRef));
  }, const Duration(seconds: 90));
  final choice = find.byWidgetPredicate((widget) => widget is Semantics &&
      widget.properties.identifier == (spaceRef == null
          ? 'files-location-private' : 'files-location-space-$spaceRef'));
  if (choice.evaluate().isEmpty) {
    final vertical = find.descendant(of: overview,
        matching: find.byWidgetPredicate((widget) => widget is Scrollable &&
            widget.axisDirection == AxisDirection.down));
    expect(vertical, findsOneWidget);
    await tester.drag(vertical, const Offset(0, 10000));
    await tester.pump(const Duration(milliseconds: 250));
    await tester.scrollUntilVisible(choice, 300, maxScrolls: 30, scrollable: vertical);
  }
  expect(choice, findsOneWidget);
  expect(tester.widget<Semantics>(choice).properties.enabled, isTrue);
  await _tap(tester, choice);
  await _wait(tester, () {
    if (screen.evaluate().length != 1) return false;
    final value = _nativeFilesContainer(tester).read(filesProvider);
    final state = value.asData?.value;
    return !value.isLoading && !value.hasError &&
        state?.rootSpaceRef == spaceRef && state?.isBusy == false &&
        state?.hasSuccessfulCurrentRead == true;
  }, const Duration(seconds: 90));
}

'''
native = segment(native, "Future<void> _selectSharedFiles(", "enum NativeDualFilesAction", lambda _: new_navigation)
if "files-space-selector" in native or "files-space-option-" in native:
    raise SystemExit("Native journey still uses obsolete mode controls")
sources[native_path] = native

tests_path = "client/test/features/files/files_screen_test.dart"
tests = sources[tests_path]
tests = replace_once(tests,
    "GoRouter _filesRouter({String initialLocation = AppRoutes.files}) => GoRouter(",
    "GoRouter _filesRouter({String initialLocation = '/files/private'}) => GoRouter(")


def migrate_named_location_test(text: str) -> str:
    text = replace_once(text, "'named Space selection and captured destination remain truthful", "'named folder navigation and captured destination remain truthful")
    text = replace_once(text, """            final repository = _NamedFilesRepository();
            await tester.pumpWidget(
              _filesTestApp(
                const FilesScreen(),""", """            final repository = _NamedFilesRepository();
            final router = _filesRouter(initialLocation: AppRoutes.files);
            addTearDown(router.dispose);
            await tester.pumpWidget(
              _filesNavigationApp(
                router,""")
    a = text.index("            final selector = find.byKey(const Key('files-space-selector'));")
    b = text.index("            final create = find.byWidgetPredicate(", a)
    text = text[:a] + r'''            final namedOption = find.byWidgetPredicate(
              (w) => w is Semantics &&
                  w.properties.identifier == 'files-location-space-opaque-b',
            );
            expect(namedOption, findsOneWidget);
            expect(namedOption.hitTestable(), findsOneWidget);
            final optionData = tester.getSemantics(namedOption).getSemanticsData();
            expect(optionData.identifier, 'files-location-space-opaque-b');
            expect(optionData.label, contains('Research (2)'));
            expect(optionData.label, isNot(contains('opaque-b')));
            await tester.tap(namedOption.hitTestable());
            await tester.pumpAndSettle();
            expect(namedOption, findsNothing);
            ProviderContainer current() => ProviderScope.containerOf(
              tester.element(find.byType(FilesScreen)),
            );
            final beforeReselect = current().read(filesProvider).requireValue.successfulReadGeneration;
            await selectNativeDualFilesScope(tester, 'opaque-b');
            await tester.pumpAndSettle();
            expect(namedOption, findsNothing);
            expect(current().read(filesProvider).requireValue.successfulReadGeneration,
                greaterThan(beforeReselect));
            expect(current().read(filesProvider).requireValue.rootSpaceRef, 'opaque-b');
            await selectNativeDualFilesScope(tester, null);
            await tester.pumpAndSettle();
            expect(current().read(filesProvider).requireValue.rootSpaceRef, isNull);
            final privateGeneration = current().read(filesProvider).requireValue.successfulReadGeneration;
            await selectNativeDualFilesScope(tester, 'opaque-b');
            await tester.pumpAndSettle();
            final container = current();
            expect(container.read(filesProvider).requireValue.rootSpaceRef, 'opaque-b');
            expect(container.read(filesProvider).requireValue.successfulReadGeneration,
                greaterThan(privateGeneration));
            expect(find.byKey(const Key('files-space-selector')), findsNothing);
            expect(find.text('opaque-b'), findsNothing);
            expect(find.text('opaque-a'), findsNothing);
            final l10n = AppLocalizations.of(tester.element(find.byType(FilesScreen)));
''' + text[b:]
    return text

tests = segment(tests,
    "  for (final locale in ['en', 'de']) {\n    for (final changedContext in [false, true]) {",
    "  for (final locale in ['en', 'de']) {\n    testWidgets(\n      'interrupted identity read",
    migrate_named_location_test)


def migrate_navigation_tests(text: str) -> str:
    # Every location now has the overview as a real parent. Keep the Back/swipe
    # assertions and assert the stable destination, rather than dropping them.
    text = text.replace("expect(router.canPop(), isFalse);", "expect(router.canPop(), isTrue);")
    text = replace_once(text, "expect(find.byType(BackButton), findsNothing);", "expect(find.byType(BackButton), findsOneWidget);")
    text = text.replace("'scope selection recovers a failed root read'", "'folder navigation recovers a failed root read'")
    text = text.replace("'scope selection leaves a failed child through native Back'", "'folder navigation leaves a failed child through native Back'")
    # The two shared-root tests start at the shared route, not a private route
    # whose controller was imperatively switched to a different scope.
    for title, next_title in [
        ("  testWidgets('native Back and iOS swipe retain scope and parent focus'", "  testWidgets('folder navigation recovers a failed root read'"),
        ("  testWidgets(\n    'folder failure keeps native Back available and shared root readable'", None),
    ]:
        a = text.index(title)
        b = text.index(next_title, a) if next_title else len(text)
        part = text[a:b]
        part = replace_once(part, "final router = _filesRouter();", "final router = _filesRouter(initialLocation: AppRoutes.filesLocation('/', spaceRef: 'workspace-default'));")
        part, n = re.subn(r"    (?:  )?await (?:rootContainer|container)\s*\.read\(filesProvider.notifier\)\s*\.selectRootSpace\('workspace-default'\);\n", "", part)
        if n != 1:
            raise SystemExit("Shared-root navigation setup changed")
        part = part.replace("repository.scopes.skip(1)", "repository.scopes")
        part = part.replace("expect(rootContainer.read(filesProvider).requireValue.rootSpaceRef, isNull);",
            "expect(ProviderScope.containerOf(tester.element(find.byType(FilesScreen))).read(filesProvider).requireValue.rootSpaceRef, isNull);\n"
            "    expect(router.routeInformationProvider.value.uri.path, '/files/private');\n"
            "    await tester.pageBack();\n"
            "    await tester.pumpAndSettle();\n"
            "    expect(router.canPop(), isFalse);\n"
            "    expect(router.routeInformationProvider.value.uri.path, AppRoutes.files);")
        text = text[:a] + part + text[b:]
    # The old root controller is disposed when native navigation returns to the
    # overview; inspect the newly opened location instead of a disposed object.
    for title, end in [
        ("  testWidgets('folder navigation recovers a failed root read'", "  testWidgets('folder navigation leaves a failed child through native Back'"),
        ("  testWidgets('folder navigation leaves a failed child through native Back'", "  for (final legacy in [false, true])"),
    ]:
        def fresh(part: str) -> str:
            part = replace_once(part, "final container = ProviderScope.containerOf(", "var container = ProviderScope.containerOf(")
            anchor = "    final state = container.read(filesProvider).requireValue;"
            return replace_once(part, anchor,
                "    container = ProviderScope.containerOf(tester.element(find.byType(FilesScreen)));\n" + anchor)
        text = segment(text, title, end, fresh)
    return text

tests = segment(tests,
    "  testWidgets('native Back and iOS swipe retain scope and parent focus'",
    "  group('FilesScreen', () {", migrate_navigation_tests)
sources[tests_path] = tests

route_path = "client/lib/features/files/presentation/files_folder_route.dart"
route = (ROOT / route_path).read_text()
route = replace_once(route,
    "child: Scaffold(body: FilesScreen(initialPath: path, isFolderRoute: true)),",
    "child: Scaffold(body: FilesScreen(initialPath: path, isFolderRoute: folderId != null || path == '/')),")
sources[route_path] = route

# Validate all transformations before modifying the first file.
for relative, text in sources.items():
    (ROOT / relative).write_text(text)
print("Applied four bounded Files navigation transformations; no assertions skipped")
