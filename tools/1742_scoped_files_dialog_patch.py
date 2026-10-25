#!/usr/bin/env python3
"""Bounded development patch: add regressions before applying the dialog repair."""
from pathlib import Path
import hashlib
import sys

ROOT = Path(__file__).resolve().parents[1]

def read_checked(relative, expected):
    path = ROOT / relative
    raw = path.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    if path.is_symlink() or actual != expected:
        raise SystemExit(f'Reviewed source changed: {relative}')
    return path, raw.decode()

def once(text, before, after):
    if text.count(before) != 1:
        raise SystemExit(f'Reviewed unique anchor changed: {before[:100]!r}')
    return text.replace(before, after, 1)

if len(sys.argv) != 2 or sys.argv[1] not in {'tests', 'implementation'}:
    raise SystemExit('Expected tests or implementation')

if sys.argv[1] == 'tests':
    path, text = read_checked('client/test/features/files/files_screen_test.dart',
                              '9835d5eced6f9286bc4a4af232607b60bc4a1dd5')
    fixture = '''class _ScopedPreviewFilesRepository extends _NavigationFilesRepository {
  Completer<FileDownload>? downloadGate;

  @override
  Future<DirectoryListing> listDirectory(String path, {String? spaceRef}) async {
    final listing = await super.listDirectory(path, spaceRef: spaceRef);
    return DirectoryListing(
      path: listing.path,
      parentFileId: listing.parentFileId,
      ownerKey: listing.ownerKey,
      allowedActions: listing.allowedActions,
      entries: const [FileEntry(
        id: 'preview-file',
        name: 'preview.txt',
        path: '/preview.txt',
        isDirectory: false,
        revision: 'preview-version',
        allowedActions: {'download'},
      )],
    );
  }

  @override
  Future<FileDownload> downloadFile(FileEntry entry) async {
    final pending = downloadGate;
    if (pending != null) return pending.future;
    return FileDownload(
      fileName: entry.name,
      mediaType: 'text/plain',
      bytes: Uint8List.fromList(utf8.encode('Shared route preview bytes.')),
    );
  }
}

'''
    text = once(text, 'List<dynamic> _navigationOverrides(', fixture + 'List<dynamic> _navigationOverrides(')
    tests = '''  for (final change in ['unchanged', 'revoked', 'account-replaced']) {
    testWidgets('route-scoped preview retains authorization: $change', (tester) async {
      final repository = _ScopedPreviewFilesRepository();
      final router = _filesRouter(initialLocation:
          AppRoutes.filesLocation('/', spaceRef: 'workspace-default'));
      addTearDown(router.dispose);
      await tester.pumpWidget(_filesNavigationApp(
        router, overrides: _navigationOverrides(repository)));
      await tester.pumpAndSettle();
      final screen = find.byType(FilesScreen);
      final directory = ProviderScope.containerOf(tester.element(screen));
      final l10n = AppLocalizations.of(tester.element(screen));
      final open = find.byKey(const ValueKey('files-open-preview-file'));
      if (open.hitTestable().evaluate().length != 1) {
        await Scrollable.ensureVisible(tester.element(open), alignment: 0.5);
        await tester.pumpAndSettle();
      }
      expect(open.hitTestable(), findsOneWidget);
      await tester.tap(open.hitTestable());
      await tester.pumpAndSettle();
      final content = find.byKey(const Key('files-preview-content'));
      expect(content, findsOneWidget);
      expect(tester.widget<SelectableText>(content).data, 'Shared route preview bytes.');
      expect(identical(ProviderScope.containerOf(tester.element(content)), directory), isTrue);
      if (change == 'revoked') {
        repository.denyRoot = true;
        await directory.read(filesProvider.notifier).refresh();
      } else if (change == 'account-replaced') {
        repository.owner = 'member-b';
        directory.read(workspaceInvalidationProvider.notifier).invalidate(
          integration: WorkspaceIntegration.files,
          reason: IntegrationInvalidationReason.authConfigurationChanged,
        );
      }
      await tester.pumpAndSettle();
      expect(content, change == 'unchanged' ? findsOneWidget : findsNothing);
      if (change != 'unchanged') {
        expect(find.text(l10n.filesUnavailableSpace), findsOneWidget);
        expect(find.text('Shared route preview bytes.'), findsNothing);
      }
      await tester.tap(find.descendant(of: find.byType(AlertDialog),
          matching: find.byType(TextButton)));
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
      expect(find.byType(FilesScreen), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('route-scoped preview discards bytes after pending-download revocation', (tester) async {
    final repository = _ScopedPreviewFilesRepository()
      ..downloadGate = Completer<FileDownload>();
    final router = _filesRouter(initialLocation:
        AppRoutes.filesLocation('/', spaceRef: 'workspace-default'));
    addTearDown(router.dispose);
    await tester.pumpWidget(_filesNavigationApp(
      router, overrides: _navigationOverrides(repository)));
    await tester.pumpAndSettle();
    final directory = ProviderScope.containerOf(tester.element(find.byType(FilesScreen)));
    final open = find.byKey(const ValueKey('files-open-preview-file'));
    if (open.hitTestable().evaluate().length != 1) {
      await Scrollable.ensureVisible(tester.element(open), alignment: 0.5);
      await tester.pumpAndSettle();
    }
    expect(open.hitTestable(), findsOneWidget);
    await tester.tap(open.hitTestable());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.byKey(const Key('files-preview-content')), findsNothing);
    repository.denyRoot = true;
    await directory.read(filesProvider.notifier).refresh();
    await tester.pump();
    repository.downloadGate!.complete(FileDownload(
      fileName: 'preview.txt', mediaType: 'text/plain',
      bytes: Uint8List.fromList(utf8.encode('Late revoked preview bytes.'))));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('files-preview-content')), findsNothing);
    expect(find.text('Late revoked preview bytes.'), findsNothing);
    await tester.tap(find.descendant(of: find.byType(AlertDialog),
        matching: find.byType(TextButton)));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

'''
    anchor = "  testWidgets('native Back and iOS swipe retain scope and parent focus', ("
    text = once(text, anchor, tests + anchor)
    path.write_text(text)
else:
    path, text = read_checked('client/lib/features/files/presentation/files_screen.dart',
                              'c042d8d0530bad18abe785024d6d01d59ae4f952')
    text = once(text,
        '                          final operationRepository = ref.read(\n',
        '                          final operationController = ref.read(filesProvider.notifier);\n'
        '                          final operationRepository = ref.read(\n')
    text = once(text, '''                          final active = ref.read(filesProvider).asData?.value;
                          if (folderName == null ||
                              !context.mounted ||
                              active == null ||''', '''                          // A dialog may outlive its original directory route.
                          if (folderName == null || !context.mounted) return;
                          final active = ref.read(filesProvider).asData?.value;
                          if (active == null ||
                              !identical(operationController, ref.read(filesProvider.notifier)) ||
                              active.directoryListing?.ownerKey != state.directoryListing?.ownerKey ||
                              active.directoryListing?.parentFileId != state.directoryListing?.parentFileId ||''')
    text = once(text, '''    return AlertDialog(
      title: Text(l10n.filesCreateFolderDialogTitle),''', '''    return AlertDialog(
      scrollable: true,
      title: Text(l10n.filesCreateFolderDialogTitle),''')
    text = once(text, '''    required this.rootSpaceRef,
    required this.download,
  });
  final FileEntry entry;
  final String? rootSpaceRef;
  final Future<FileDownload> download;''', '''    required this.rootSpaceRef,
    required this.ownerKey,
    required this.download,
  });
  final FileEntry entry;
  final String? rootSpaceRef;
  final String? ownerKey;
  final Future<FileDownload> download;''')
    text = once(text, '''    final current = ref.watch(filesProvider).asData?.value;
    final stillAvailable =
        current?.rootSpaceRef == rootSpaceRef &&
        current?.directoryListing?.entries.any(
              (item) => item.id == entry.id && item.allows('download'),
            ) ==
            true;''', '''    final value = ref.watch(filesProvider);
    final current = value.asData?.value;
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);
    final spaces = rootSpaceRef == null ? null : ref.watch(weaveApiMemberSpacesProvider);
    final stillAvailable =
        !value.isLoading && !value.hasError &&
        !capabilities.isLoading && !capabilities.hasError &&
        capabilities.asData?.value.files.isReady == true &&
        (rootSpaceRef == null ||
            (spaces?.isLoading == false && spaces?.hasError == false &&
             spaces?.asData?.value?.visibleSpaceRefs.contains(rootSpaceRef) == true)) &&
        current?.rootSpaceRef == rootSpaceRef &&
        current?.directoryFailure == null &&
        current?.directoryListing?.ownerKey == ownerKey &&
        current?.directoryListing?.entries.any(
              (item) => item.id == entry.id && item.revision == entry.revision && item.allows('download'),
            ) == true;''')
    text = once(text, '''                          builder: (_) => _FilePreview(
                            entry: entry,
                            rootSpaceRef: ref
                                .read(filesProvider)
                                .asData
                                ?.value
                                .rootSpaceRef,
                            download: ref
                                .read(filesProvider.notifier)
                                .downloadForPreview(entry),
                          ),''', '''                          // Dialog routes do not inherit the directory's scoped providers.
                          builder: (_) => UncontrolledProviderScope(
                            container: ProviderScope.containerOf(context),
                            child: _FilePreview(
                              entry: entry,
                              rootSpaceRef: ref.read(filesProvider).asData?.value.rootSpaceRef,
                              ownerKey: ref.read(filesProvider).asData?.value.directoryListing?.ownerKey,
                              download: ref.read(filesProvider.notifier).downloadForPreview(entry),
                            ),
                          ),''')
    path.write_text(text)
print(f'Applied {sys.argv[1]} changes to {path.relative_to(ROOT)}')
