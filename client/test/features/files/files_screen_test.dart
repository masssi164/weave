import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../helpers/server_config_test_data.dart';
import '../../helpers/test_app.dart';
import '../../../integration_test/dual_ios_collaboration_test.dart'
    show selectNativeDualFilesScope;

const _readyFilesSnapshot = WorkspaceCapabilitySnapshot(
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

Widget _filesTestApp(Widget child, {List<dynamic> overrides = const []}) {
  return createTestApp(
    child,
    overrides: [
      workspaceCapabilitySnapshotProvider.overrideWithValue(
        const AsyncData(_readyFilesSnapshot),
      ),
      ...overrides,
    ],
  );
}

WorkspaceCapabilitySnapshot _blockedFilesSnapshot() =>
    WorkspaceCapabilitySnapshot(
      shellAccess: _readyFilesSnapshot.shellAccess,
      chat: _readyFilesSnapshot.chat,
      files: const WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.blocked,
        policyState: WorkspaceCapabilityPolicyState.policyBlocked,
      ),
      calendar: _readyFilesSnapshot.calendar,
      boards: _readyFilesSnapshot.boards,
    );

class _CapabilitySnapshotController
    extends Notifier<AsyncValue<WorkspaceCapabilitySnapshot>> {
  @override
  AsyncValue<WorkspaceCapabilitySnapshot> build() =>
      AsyncData(_blockedFilesSnapshot());

  void show(WorkspaceCapabilitySnapshot snapshot) {
    state = AsyncData(snapshot);
  }
}

class _FakeFilesRepository
    implements
        FilesRepository,
        FilesEntryMutationRepository,
        FilesExportRepository {
  _FakeFilesRepository({
    required this.connectionState,
    this.listings = const <String, DirectoryListing>{},
    this.listDirectoryHandler,
    this.uploadFileHandler,
    this.createFolderHandler,
    this.grantActions = true,
    this.download,
  });

  final FileDownload? download;
  @override
  Future<FileDownload> downloadFile(FileEntry entry) async => download!;
  final FilesConnectionState connectionState;
  final Map<String, DirectoryListing> listings;
  final Future<DirectoryListing> Function(String path)? listDirectoryHandler;
  final Future<void> Function(
    String directoryPath,
    FileUploadRequest request,
    FileUploadProgressCallback? onProgress,
  )?
  uploadFileHandler;
  final Future<FileEntry> Function(String parentPath, String name)?
  createFolderHandler;
  final bool grantActions;
  final List<String> requestedPaths = <String>[];
  int restoreConnectionCalls = 0;

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    requestedPaths.add(path);
    final handler = listDirectoryHandler;
    if (handler != null) {
      return _withTestActions(await handler(path));
    }
    return _withTestActions(
      listings[path] ?? const DirectoryListing(path: '/', entries: []),
    );
  }

  DirectoryListing _withTestActions(DirectoryListing listing) {
    if (!grantActions) return listing;
    // Existing widget fixtures exercise supported actions through a fake
    // repository. Explicit fixture actions remain visible to negative tests.
    return DirectoryListing(
      path: listing.path,
      parentFileId: listing.parentFileId,
      allowedActions: const {'listChildren', 'createFolder', 'upload'},
      entries: listing.entries
          .map(
            (entry) => FileEntry(
              id: entry.id,
              name: entry.name,
              path: entry.path,
              isDirectory: entry.isDirectory,
              modifiedAt: entry.modifiedAt,
              sizeInBytes: entry.sizeInBytes,
              revision: entry.revision,
              allowedActions: entry.isDirectory
                  ? {...entry.allowedActions, 'inspect', 'listChildren'}
                  : {...entry.allowedActions, 'inspect', 'download'},
            ),
          )
          .toList(growable: false),
    );
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    await uploadFileHandler?.call(directoryPath, request, onProgress);
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) {
    final handler = createFolderHandler;
    if (handler != null) {
      return handler(parentPath, name);
    }
    return Future<FileEntry>.value(
      FileEntry(
        id: '$parentPath/$name',
        name: name,
        path: parentPath == '/' ? '/$name' : '$parentPath/$name',
        isDirectory: true,
      ),
    );
  }

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    throw UnimplementedError('No generated User delete operation exists.');
  }

  @override
  Future<FilesConnectionState> restoreConnection() async {
    restoreConnectionCalls++;
    return connectionState;
  }
}

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  final ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {}
}

class _FakeFilesImportPicker implements FilesImportPicker {
  _FakeFilesImportPicker(this.request);

  final FileUploadRequest? request;

  @override
  Future<FileUploadRequest?> pickFile() async => request;
}

// Unit-only state fixture for the real FilesScreen action/scroll layout.
class _PostReplaceFilesController extends FilesController {
  static const name = 'shared-workspace-project-release-notes.txt';
  @override
  Future<FilesViewState> build() async => FilesViewState(
    rootSpaceRef: 'workspace-default',
    connectionState: FilesConnectionState.connected(
      baseUrl: Uri.parse('https://api.example/api'),
      accountLabel: 'Weave files',
    ),
    directoryListing: const DirectoryListing(
      path: '/',
      entries: [
        FileEntry(
          id: 'shared',
          name: name,
          path: '/$name',
          isDirectory: false,
          sizeInBytes: 48,
          revision: 'before',
          allowedActions: {'inspect', 'download', 'updateContent'},
        ),
      ],
    ),
    uploadStatus: const FilesUploadStatus(
      phase: FilesUploadPhase.completed,
      fileName: name,
      uploadedBytes: 48,
      totalBytes: 48,
    ),
    entryActionStatus: const FilesEntryActionStatus(
      phase: FilesEntryActionPhase.exportedEntry,
      entryName: name,
      destination: '/owned/Documents/export.txt',
    ),
  );

  @override
  Future<void> pickAndReplace(FileEntry entry) async {
    state = AsyncData(
      state.requireValue.copyWith(
        entryActionStatus: const FilesEntryActionStatus(
          phase: FilesEntryActionPhase.replacedContent,
          entryName: name,
        ),
      ),
    );
  }
}

class _ExportedFilesController extends FilesController {
  _ExportedFilesController({
    this.destination =
        '/private/owned-container/Documents/Selected/brief 2.txt',
  });

  final String destination;
  @override
  Future<FilesViewState> build() async => FilesViewState(
    connectionState: FilesConnectionState.connected(
      baseUrl: Uri.parse('https://api.example/api'),
      accountLabel: 'member',
    ),
    directoryListing: const DirectoryListing(path: '/', entries: []),
    entryActionStatus: FilesEntryActionStatus(
      phase: FilesEntryActionPhase.exportedEntry,
      entryName: 'brief.txt',
      destination: destination,
    ),
  );
}

class _InterruptedFilesController extends _PostReplaceFilesController {
  var retries = 0;

  @override
  Future<FilesViewState> build() async => (await super.build()).copyWith(
    directoryFailure: const FilesFailure.unknown(
      FilesFailure.identityUnavailableCode,
    ),
    entryActionStatus: const FilesEntryActionStatus.idle(),
    uploadStatus: const FilesUploadStatus.idle(),
  );

  @override
  Future<void> refresh() async {
    retries++;
    state = AsyncData(state.requireValue.copyWith(clearDirectoryFailure: true));
  }
}

class _NamedFilesRepository extends _FakeFilesRepository
    implements FilesSpaceRepository {
  _NamedFilesRepository()
    : super(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.example/api'),
          accountLabel: 'Weave files',
        ),
      );
  String? createdIn;
  FileEntry? created;
  @override
  Future<DirectoryListing> listDirectory(
    String path, {
    String? spaceRef,
  }) async => DirectoryListing(
    path: path,
    parentFileId: 'root',
    allowedActions: const {'listChildren', 'createFolder'},
    entries: created == null ? [] : [created!],
  );
  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    String? spaceRef,
    FileUploadProgressCallback? onProgress,
  }) async {}
  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
    String? spaceRef,
  }) async {
    createdIn = spaceRef;
    return created = FileEntry(
      id: 'created-stable-folder',
      name: name,
      path: '/$name',
      isDirectory: true,
    );
  }
}

void main() {
  testWidgets(
    'successful Files read markers change only on current completed reads',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        Completer<DirectoryListing>? pending;
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.example/api'),
            accountLabel: 'Weave files',
          ),
          listDirectoryHandler: (path) async => pending == null
              ? DirectoryListing(
                  path: path,
                  parentFileId: 'file:root',
                  entries: [],
                )
              : pending.future,
        );
        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              weaveApiMemberSpacesProvider.overrideWith(
                (ref) async => const MemberSpaceAccessSnapshot(
                  visibleSpaceRefs: {},
                  defaultSpaceReadable: false,
                ),
              ),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        final container = ProviderScope.containerOf(
          tester.element(find.byType(FilesScreen)),
        );
        final controller = container.read(filesProvider.notifier);
        final scrollable = find
            .descendant(
              of: find.byType(CustomScrollView),
              matching: find.byType(Scrollable),
            )
            .first;
        final loaded = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              (widget.properties.identifier?.startsWith('files-loaded-') ??
                  false),
        );
        final end = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'files-listing-end',
        );
        final root = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'files-location-root',
        );
        final current = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'files-current-folder-file:root',
        );
        await tester.scrollUntilVisible(
          root,
          -300,
          maxScrolls: 30,
          scrollable: scrollable,
        );
        await tester.pump();
        expect(loaded, findsOneWidget);
        final initial = tester
            .getSemantics(loaded)
            .getSemanticsData()
            .identifier;
        expect(initial, matches(r'^files-loaded-[1-9][0-9]*$'));
        final marker = tester.widget<Semantics>(loaded);
        expect(marker.explicitChildNodes, isTrue);
        expect(marker.excludeSemantics, isFalse);
        expect(tester.getSemantics(loaded).getSemanticsData().label, isEmpty);
        expect(
          tester
              .getSemantics(loaded)
              .getSemanticsData()
              .flagsCollection
              .isFocused
              .toBoolOrNull(),
          isNull,
        );
        await tester.scrollUntilVisible(
          end,
          300,
          maxScrolls: 30,
          scrollable: scrollable,
        );
        await tester.pumpAndSettle();
        expect(end.hitTestable(), findsOneWidget);
        expect(
          tester.getSemantics(end).getSemanticsData().label,
          'End of folder',
        );
        expect(
          tester.getSemantics(end).getSemanticsData().flagsCollection.isButton,
          isFalse,
        );
        expect(
          tester.getSemantics(loaded).getSemanticsData().identifier,
          initial,
        );
        await tester.scrollUntilVisible(
          root,
          -300,
          maxScrolls: 30,
          scrollable: scrollable,
        );
        await tester.pumpAndSettle();
        expect(
          tester
              .getSemantics(root)
              .getSemanticsData()
              .flagsCollection
              .isSelected
              .toBoolOrNull(),
          isTrue,
        );
        expect(
          tester.getSemantics(current).getSemanticsData().identifier,
          'files-current-folder-file:root',
        );
        pending = Completer<DirectoryListing>();
        final refresh = controller.refresh();
        await tester.pump();
        expect(loaded, findsNothing);
        expect(end, findsNothing);
        expect(
          container.read(filesProvider).requireValue.directoryListing,
          isNotNull,
        );
        pending.completeError(const FilesFailure.unknown('Read unavailable'));
        await refresh;
        await tester.pumpAndSettle();
        expect(
          loaded,
          findsNothing,
          reason: 'Retained stale empty data cannot prove absence.',
        );
        expect(
          container.read(filesProvider).requireValue.successfulReadGeneration,
          0,
        );
        expect(end, findsNothing);
        pending = Completer<DirectoryListing>();
        final retry = controller.refresh();
        await tester.pump();
        expect(loaded, findsNothing);
        pending.complete(
          DirectoryListing(
            path: '/',
            parentFileId: 'file:root',
            entries: List.generate(
              30,
              (index) => FileEntry(
                id: 'visible-$index',
                name: 'readback-$index.txt',
                path: '/readback-$index.txt',
                isDirectory: false,
              ),
            ),
          ),
        );
        await retry;
        await tester.pumpAndSettle();
        expect(loaded, findsOneWidget);
        final after = tester.getSemantics(loaded).getSemanticsData().identifier;
        expect(
          int.parse(after.split('-').last),
          greaterThan(int.parse(initial.split('-').last)),
        );
        await tester.scrollUntilVisible(
          end,
          300,
          maxScrolls: 30,
          scrollable: scrollable,
        );
        await tester.pumpAndSettle();
        expect(end.hitTestable(), findsOneWidget);
        expect(
          tester.getSemantics(loaded).getSemanticsData().identifier,
          after,
        );
        final row = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'files-entry-visible-29',
        );
        expect(
          tester.getSemantics(row).getSemanticsData().label,
          contains('readback-29.txt'),
        );
        final openFile = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'files-open-visible-29',
        );
        await tester.ensureVisible(openFile);
        await tester.pumpAndSettle();
        final buttonTooltips = <String>[];
        tester.getSemantics(openFile).visitChildren((node) {
          final data = node.getSemanticsData();
          if (data.flagsCollection.isButton) buttonTooltips.add(data.tooltip);
          return true;
        });
        expect(buttonTooltips, [
          'Open readback-29.txt',
          'Export readback-29.txt to native files',
        ]);
        expect(tester.getRect(row).top, lessThan(tester.getRect(end).top));
        await tester.scrollUntilVisible(
          root,
          -300,
          maxScrolls: 30,
          scrollable: scrollable,
        );
        await tester.pumpAndSettle();
        pending = Completer<DirectoryListing>();
        final open = controller.openDirectory('/Reports');
        await tester.pump();
        expect(loaded, findsNothing);
        pending.complete(
          const DirectoryListing(
            path: '/Reports',
            parentFileId: 'file:reports',
            entries: [],
          ),
        );
        await open;
        await tester.pumpAndSettle();
        final nested = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier ==
                  'files-current-folder-file:reports',
        );
        expect(nested, findsOneWidget);
        expect(
          tester.getSemantics(nested).getSemanticsData().identifier,
          'files-current-folder-file:reports',
        );
        expect(root.hitTestable(), findsOneWidget);
        pending = null;
        await tester.tap(root);
        await tester.pumpAndSettle();
        expect(container.read(filesProvider).requireValue.currentPath, '/');
        expect(current, findsOneWidget);
        expect(loaded, findsOneWidget);
        pending = Completer<DirectoryListing>();
        container.invalidate(filesProvider);
        await tester.pump();
        expect(container.read(filesProvider).isLoading, isTrue);
        await tester.pump();
        expect(loaded, findsNothing);
        expect(end, findsNothing);
        pending.complete(
          const DirectoryListing(
            path: '/',
            parentFileId: 'file:root',
            entries: [],
          ),
        );
        await tester.pumpAndSettle();
        expect(loaded, findsOneWidget);
        final rebuilt = tester
            .getSemantics(loaded)
            .getSemanticsData()
            .identifier;
        expect(
          int.parse(rebuilt.split('-').last),
          greaterThan(int.parse(after.split('-').last)),
        );
      } finally {
        semantics.dispose();
      }
    },
  );
  for (final locale in ['en', 'de']) {
    for (final changedContext in [false, true]) {
      testWidgets(
        'named Space selection and captured destination remain truthful $locale changed=$changedContext',
        (tester) async {
          tester.view.physicalSize = const Size(390, 844);
          tester.view.devicePixelRatio = 1;
          tester.platformDispatcher.textScaleFactorTestValue = 1.5;
          tester.platformDispatcher.localesTestValue = [Locale(locale)];
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
          addTearDown(tester.platformDispatcher.clearLocalesTestValue);
          final semantics = tester.ensureSemantics();
          try {
            final repository = _NamedFilesRepository();
            await tester.pumpWidget(
              _filesTestApp(
                const FilesScreen(),
                overrides: [
                  filesRepositoryProvider.overrideWithValue(repository),
                  appStartDiscoveryClientProvider.overrideWith(
                    (ref) => buildTestDiscoveryClient(
                      ref.watch(serverConfigurationRepositoryProvider),
                    ),
                  ),
                  serverConfigurationRepositoryProvider.overrideWith(
                    (ref) => _FakeServerConfigurationRepository(
                      buildTestConfiguration(),
                    ),
                  ),
                  weaveApiMemberSpacesProvider.overrideWith(
                    (ref) async => const MemberSpaceAccessSnapshot(
                      visibleSpaceRefs: {'opaque-b', 'opaque-a'},
                      displayNames: {
                        'opaque-b': 'Research',
                        'opaque-a': 'Research',
                      },
                      defaultSpaceReadable: false,
                    ),
                  ),
                ],
              ),
            );
            await tester.pumpAndSettle();
            final selector = find.byKey(const Key('files-space-selector'));
            await tester.ensureVisible(selector);
            await tester.tap(selector);
            await tester.pumpAndSettle();
            final namedOption = find.byWidgetPredicate(
              (w) =>
                  w is Semantics &&
                  w.properties.identifier == 'files-space-option-opaque-b',
            );
            expect(namedOption, findsOneWidget);
            expect(namedOption.hitTestable(), findsOneWidget);
            final optionData = tester
                .getSemantics(namedOption)
                .getSemanticsData();
            expect(optionData.identifier, 'files-space-option-opaque-b');
            expect(optionData.label, 'Research (2)');
            await tester.tap(namedOption.hitTestable());
            await tester.pumpAndSettle();
            expect(namedOption, findsNothing);
            await tester.tap(selector);
            await tester.pumpAndSettle();
            expect(namedOption, findsOneWidget);
            expect(namedOption.hitTestable(), findsOneWidget);
            expect(
              tester.getSemantics(namedOption).getSemanticsData().label,
              'Research (2)',
            );
            await tester.tap(namedOption.hitTestable());
            await tester.pumpAndSettle();
            final beforeReselect = ProviderScope.containerOf(
              tester.element(selector),
            ).read(filesProvider).requireValue.successfulReadGeneration;
            await selectNativeDualFilesScope(tester, 'opaque-b');
            await tester.pumpAndSettle();
            expect(namedOption, findsNothing);
            final container = ProviderScope.containerOf(
              tester.element(selector),
            );
            expect(
              container
                  .read(filesProvider)
                  .requireValue
                  .successfulReadGeneration,
              greaterThan(beforeReselect),
            );
            expect(
              container.read(filesProvider).requireValue.rootSpaceRef,
              'opaque-b',
            );
            await selectNativeDualFilesScope(tester, null);
            await tester.pumpAndSettle();
            expect(
              container.read(filesProvider).requireValue.rootSpaceRef,
              isNull,
            );
            final privateGeneration = container
                .read(filesProvider)
                .requireValue
                .successfulReadGeneration;
            await selectNativeDualFilesScope(tester, 'opaque-b');
            await tester.pumpAndSettle();
            expect(
              container.read(filesProvider).requireValue.rootSpaceRef,
              'opaque-b',
            );
            expect(
              container
                  .read(filesProvider)
                  .requireValue
                  .successfulReadGeneration,
              greaterThan(privateGeneration),
            );
            expect(find.text('opaque-b'), findsNothing);
            expect(find.text('opaque-a'), findsNothing);
            final l10n = AppLocalizations.of(tester.element(selector));
            final create = find.byWidgetPredicate(
              (w) =>
                  w is Semantics &&
                  w.properties.identifier == 'files-create-folder',
            );
            await tester.ensureVisible(create);
            await tester.pumpAndSettle();
            await tester.tap(create);
            await tester.pumpAndSettle();
            final destination = l10n.filesCurrentLocation(
              'Research (2)',
              l10n.filesTopFolder,
            );
            expect(
              find.descendant(
                of: find.byType(AlertDialog),
                matching: find.text(destination),
              ),
              findsOneWidget,
            );
            if (changedContext) {
              await container
                  .read(filesProvider.notifier)
                  .selectRootSpace('opaque-a');
              await tester.pumpAndSettle();
              expect(
                find.descendant(
                  of: find.byType(AlertDialog),
                  matching: find.text(destination),
                ),
                findsOneWidget,
              );
            }
            await tester.enterText(find.byType(TextField), 'Named folder');
            await tester.pump();
            expect(
              find.text(l10n.filesCreateFolderConfirmButton).hitTestable(),
              findsOneWidget,
            );
            await tester.tap(find.text(l10n.filesCreateFolderConfirmButton));
            await tester.pumpAndSettle();
            expect(
              repository.createdIn,
              changedContext ? isNull : 'opaque-b',
              reason:
                  'phase=${container.read(filesProvider).asData?.value.entryActionStatus.phase} failure=${container.read(filesProvider).asData?.value.entryActionStatus.failure?.type} path=${container.read(filesProvider).asData?.value.currentPath}',
            );
            if (!changedContext) {
              expect(
                find.bySemanticsLabel(
                  RegExp(
                    r'Research \(2\).*' + RegExp.escape(l10n.filesTopFolder),
                  ),
                ),
                findsWidgets,
              );
            }
            expect(tester.takeException(), isNull);
          } finally {
            semantics.dispose();
          }
        },
      );
    }
  }

  for (final locale in ['en', 'de']) {
    testWidgets(
      'interrupted identity read offers an operable localized retry $locale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        tester.platformDispatcher.localesTestValue = [Locale(locale)];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
        final controller = _InterruptedFilesController();
        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [filesProvider.overrideWith(() => controller)],
          ),
        );
        await tester.pumpAndSettle();
        final l10n = AppLocalizations.of(
          tester.element(find.byType(FilesScreen)),
        );
        expect(find.text(l10n.filesErrorGuidance), findsOneWidget);
        expect(find.text(l10n.filesInvalidSessionMessage), findsNothing);
        expect(find.text(FilesFailure.identityUnavailableCode), findsNothing);
        final retry = find.text(l10n.filesStaleDirectoryRetryButton);
        await tester.ensureVisible(retry);
        await tester.pumpAndSettle();
        expect(retry.hitTestable(), findsOneWidget);
        await tester.tap(retry);
        await tester.pumpAndSettle();
        expect(controller.retries, 1);
        expect(find.text(l10n.filesErrorGuidance), findsNothing);
        await tester.scrollUntilVisible(
          find.text(_PostReplaceFilesController.name),
          150,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text(_PostReplaceFilesController.name), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }
  for (final destination in [
    'content://provider/private-secret-token',
    'data:text/plain,private-secret-token',
    'blob:https://provider/private-secret-token',
  ]) {
    testWidgets('opaque export destination stays private $destination', (
      tester,
    ) async {
      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesProvider.overrideWith(
              () => _ExportedFilesController(destination: destination),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final context = tester.element(find.byType(FilesScreen));
      final fallback = AppLocalizations.of(
        context,
      ).filesExportUserVisibleFallback;
      expect(find.textContaining(fallback), findsOneWidget);
      expect(find.textContaining('private-secret-token'), findsNothing);
      final semantics = tester.ensureSemantics();
      expect(
        find.bySemanticsLabel(RegExp('private-secret-token')),
        findsNothing,
      );
      semantics.dispose();
    });
  }
  for (final destination in [
    'file:///private/Selected/brief%202.txt',
    r'C:\Selected\brief 2.txt',
  ]) {
    testWidgets('export retains actual file basename $destination', (
      tester,
    ) async {
      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesProvider.overrideWith(
              () => _ExportedFilesController(destination: destination),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('brief 2.txt'), findsOneWidget);
      expect(find.textContaining('Selected'), findsNothing);
    });
  }
  for (final locale in ['en', 'de']) {
    testWidgets(
      'export reports actual selected basename without sandbox path $locale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        tester.platformDispatcher.localesTestValue = [Locale(locale)];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
        await tester.pumpWidget(
          _filesTestApp(
            Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: const TextScaler.linear(1.4)),
                child: const FilesScreen(),
              ),
            ),
            overrides: [
              filesProvider.overrideWith(_ExportedFilesController.new),
            ],
          ),
        );
        await tester.pumpAndSettle();
        expect(find.textContaining('brief 2.txt'), findsOneWidget);
        expect(find.textContaining('/private/owned-container'), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final locale in ['en', 'de']) {
    testWidgets(
      'file modification date follows the visible app locale $locale',
      (tester) async {
        tester.platformDispatcher.localesTestValue = [Locale(locale)];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
        final modified = DateTime.utc(2026, 10, 15, 21, 43);
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.example/api'),
            accountLabel: 'member',
          ),
          listings: {
            '/': DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'dated',
                  name: 'brief.txt',
                  path: '/brief.txt',
                  isDirectory: false,
                  modifiedAt: modified,
                ),
              ],
            ),
          },
        );
        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        final expected = DateFormat.yMMMd(
          locale,
        ).add_Hm().format(modified.toLocal());
        expect(find.text(expected), findsOneWidget);
      },
    );
  }

  for (final valid in [true, false]) {
    testWidgets('Open uses actual download bytes; valid UTF8=$valid', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      try {
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.home.internal/api'),
            accountLabel: 'alice',
          ),
          listings: const {
            '/': DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'preview',
                  name: 'shared.txt',
                  path: '/shared.txt',
                  isDirectory: false,
                ),
              ],
            ),
          },
          download: FileDownload(
            fileName: 'shared.txt',
            mediaType: 'text/plain',
            bytes: Uint8List.fromList(
              valid
                  ? utf8.encode('Actual shared document content')
                  : [255, 254],
            ),
          ),
        );
        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.byKey(const Key('files-open-preview')));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const Key('files-open-preview')));
        await tester.pumpAndSettle();
        if (valid) {
          final exposed = find.byWidgetPredicate(
            (w) =>
                w is Semantics &&
                w.properties.identifier == 'files-preview-content',
          );
          expect(exposed, findsOneWidget);
          final data = tester.getSemantics(exposed).getSemanticsData();
          expect(data.identifier, 'files-preview-content');
          expect([
            data.label,
            data.value,
          ], contains('Actual shared document content'));
        }
        expect(
          find.byKey(const Key('files-preview-content')),
          valid ? findsOneWidget : findsNothing,
        );
        expect(
          find.text('Actual shared document content'),
          valid ? findsOneWidget : findsNothing,
        );
        if (!valid) {
          expect(
            find.textContaining('This file cannot be viewed here'),
            findsOneWidget,
          );
        }
      } finally {
        semantics.dispose();
      }
    });
  }

  group('FilesScreen', () {
    testWidgets('explains missing Space access with accessible recovery', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accountLabel: 'Weave files',
        ),
      );
      await tester.pumpWidget(
        createTestApp(
          const FilesScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              AsyncData(
                WorkspaceCapabilitySnapshot(
                  shellAccess: _readyFilesSnapshot.shellAccess,
                  chat: _readyFilesSnapshot.chat,
                  files: const WorkspaceCapabilityState(
                    capability: WorkspaceCapability.files,
                    readiness: WorkspaceCapabilityReadiness.blocked,
                    policyState: WorkspaceCapabilityPolicyState.policyBlocked,
                    memberSpaceAccessUnavailable: true,
                    supportRef: 'support:member-space-access:files:unavailable',
                  ),
                  calendar: _readyFilesSnapshot.calendar,
                  boards: _readyFilesSnapshot.boards,
                ),
              ),
            ),
            filesRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Ask a workspace admin to finish setup or grant you access.'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          RegExp(
            'Files.*State: No accessible workspace.*Recovery:.*finish setup or grant you access',
          ),
        ),
        findsOneWidget,
      );
      expect(find.bySemanticsLabel(RegExp('Disabled by policy')), findsNothing);
      expect(repository.restoreConnectionCalls, 0);
      expect(repository.requestedPaths, isEmpty);
      semantics.dispose();
    });

    testWidgets(
      'waits for member access, opens Files automatically, and hides revoked content',
      (tester) async {
        final capabilityState =
            NotifierProvider<
              _CapabilitySnapshotController,
              AsyncValue<WorkspaceCapabilitySnapshot>
            >(_CapabilitySnapshotController.new);
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.weave.local/api'),
            accountLabel: 'Weave files',
          ),
          listings: {
            '/': const DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'file:10fd870a-6c0e-4c06-9181-b0c1cdb855b7',
                  name: 'Private plan.txt',
                  path: '/Private plan.txt',
                  isDirectory: false,
                ),
              ],
            ),
          },
        );
        await tester.pumpWidget(
          createTestApp(
            const FilesScreen(),
            overrides: [
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => ref.watch(capabilityState),
              ),
              filesRepositoryProvider.overrideWithValue(repository),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Files are unavailable'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            RegExp('Files.*State: Disabled by policy.*Recovery:'),
          ),
          findsOneWidget,
        );
        expect(find.text('Private plan.txt'), findsNothing);
        expect(repository.restoreConnectionCalls, 0);
        expect(repository.requestedPaths, isEmpty);

        final container = ProviderScope.containerOf(
          tester.element(find.byType(FilesScreen)),
        );
        container.read(capabilityState.notifier).show(_readyFilesSnapshot);
        await tester.pumpAndSettle();

        expect(find.text('Private plan.txt'), findsOneWidget);
        expect(repository.restoreConnectionCalls, 1);
        expect(repository.requestedPaths, ['/']);
        expect(find.text('Connect Files'), findsNothing);

        container.read(capabilityState.notifier).show(_blockedFilesSnapshot());
        await tester.pumpAndSettle();

        expect(find.text('Private plan.txt'), findsNothing);
        expect(find.text('Files are unavailable'), findsOneWidget);

        repository.listings['/'] = const DirectoryListing(
          path: '/',
          entries: [
            FileEntry(
              id: 'file:20fd870a-6c0e-4c06-9181-b0c1cdb855b7',
              name: 'Current plan.txt',
              path: '/Current plan.txt',
              isDirectory: false,
            ),
          ],
        );
        container.read(capabilityState.notifier).show(_readyFilesSnapshot);
        await tester.pumpAndSettle();

        expect(find.text('Private plan.txt'), findsNothing);
        expect(find.text('Current plan.txt'), findsOneWidget);
        expect(repository.restoreConnectionCalls, 2);
        expect(repository.requestedPaths, ['/', '/']);
      },
    );

    testWidgets('points to the shared Weave sign-in when Files has no session', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Connect Files'), findsNothing);
      expect(find.text('Sign in to use Files'), findsOneWidget);
      expect(
        find.text('Sign in to Weave to browse workspace files.'),
        findsOneWidget,
      );
      expect(find.text('Weave Files'), findsOneWidget);
      expect(find.text('Weave product boundary'), findsOneWidget);
      expect(
        find.textContaining('Files actions use the Weave User API'),
        findsOneWidget,
      );
      expect(find.text('https://files.home.internal'), findsNothing);
      expect(
        find.bySemanticsLabel(
          RegExp(
            'Weave product boundary.*Weave User API.*raw provider paths and credentials',
            dotAll: true,
          ),
        ),
        findsOneWidget,
      );
    });

    testWidgets('explains unavailable Files access without browsing', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.unavailable(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          message: 'Files access is blocked by workspace policy.',
        ),
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Files are unavailable'), findsOneWidget);
      expect(
        find.text('Files access is blocked by workspace policy.'),
        findsOneWidget,
      );
      expect(find.text('Retry'), findsAtLeastNWidgets(1));
      expect(repository.requestedPaths, isEmpty);
    });

    testWidgets('shows shared guidance when a connected folder is empty', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Your private Files are empty.'), findsOneWidget);
      expect(
        find.text('Upload a file or create a folder in your private Files.'),
        findsOneWidget,
      );
      expect(find.text('Refresh'), findsAtLeastNWidgets(1));
      expect(find.text('https://files.home.internal'), findsNothing);
      expect(find.text('Nextcloud'), findsNothing);
    });

    testWidgets('hides file actions absent from server grants', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        grantActions: false,
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file:folder',
                name: 'Folder',
                path: '/Folder',
                isDirectory: true,
              ),
              FileEntry(
                id: 'file:note',
                name: 'note.txt',
                path: '/note.txt',
                isDirectory: false,
              ),
            ],
          ),
        },
      );
      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('New folder'), findsNothing);
      expect(find.text('Upload'), findsNothing);
      expect(find.byIcon(Icons.delete_outline), findsNothing);
      expect(find.byIcon(Icons.file_download_outlined), findsNothing);
      final folderTile = tester.widget<ListTile>(
        find.ancestor(of: find.text('Folder'), matching: find.byType(ListTile)),
      );
      expect(folderTile.onTap, isNull);
    });

    testWidgets('renders directory contents and allows folder navigation', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'folder-1',
                name: 'Documents',
                path: '/Documents',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents': DirectoryListing(
            path: '/Documents',
            entries: [
              FileEntry(
                id: 'folder-2',
                name: 'Reports',
                path: '/Documents/Reports',
                isDirectory: true,
              ),
              FileEntry(
                id: 'file-1',
                name: 'Notes.txt',
                path: '/Documents/Notes.txt',
                isDirectory: false,
              ),
            ],
          ),
          '/Documents/Reports': DirectoryListing(
            path: '/Documents/Reports',
            entries: [
              FileEntry(
                id: 'file-2',
                name: 'Q2.pdf',
                path: '/Documents/Reports/Q2.pdf',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Documents'), findsAtLeastNWidgets(1));

      await tester.tap(find.widgetWithText(ListTile, 'Documents'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ActionChip, 'Documents'));
      await tester.pumpAndSettle();

      expect(
        repository.requestedPaths,
        containsAllInOrder([
          '/',
          '/Documents',
          '/Documents/Reports',
          '/Documents',
        ]),
      );
      await tester.scrollUntilVisible(
        find.text('Notes.txt'),
        100,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Notes.txt'), findsOneWidget);
      expect(find.text('1 folder • 1 file'), findsOneWidget);
      expect(find.text('Up'), findsOneWidget);
      expect(
        find.widgetWithText(ActionChip, 'My private files'),
        findsOneWidget,
      );
    });

    testWidgets(
      'keeps the last known directory visible after refresh failure',
      (tester) async {
        var listCalls = 0;
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
          listDirectoryHandler: (path) async {
            listCalls += 1;
            if (listCalls > 1) {
              throw const FilesFailure.protocol(
                'Files could not be refreshed while offline.',
              );
            }
            return const DirectoryListing(
              path: '/',
              entries: [
                FileEntry(
                  id: 'file-1',
                  name: 'Roadmap.pdf',
                  path: '/Roadmap.pdf',
                  isDirectory: false,
                  sizeInBytes: 2048,
                ),
              ],
            );
          },
        );

        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Roadmap.pdf'), findsOneWidget);

        await tester.tap(
          find.widgetWithText(AccessibleButton, 'Refresh').first,
        );
        await tester.pumpAndSettle();

        expect(listCalls, 2);
        expect(find.text('Showing last known folder'), findsOneWidget);
        expect(
          find.text('Files could not be refreshed while offline.'),
          findsOneWidget,
        );
        await tester.scrollUntilVisible(
          find.text('Roadmap.pdf'),
          100,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text('Roadmap.pdf'), findsOneWidget);
        expect(find.text('2.0 KB'), findsOneWidget);
        expect(find.text('Refresh folder'), findsOneWidget);
      },
    );

    testWidgets('marks the current breadcrumb and lets users jump back home', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'folder-1',
                name: 'Documents',
                path: '/Documents',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents': DirectoryListing(
            path: '/Documents',
            entries: [
              FileEntry(
                id: 'folder-2',
                name: 'Reports',
                path: '/Documents/Reports',
                isDirectory: true,
              ),
            ],
          ),
          '/Documents/Reports': DirectoryListing(
            path: '/Documents/Reports',
            entries: [
              FileEntry(
                id: 'file-2',
                name: 'Q2.pdf',
                path: '/Documents/Reports/Q2.pdf',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(ListTile, 'Documents'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, 'Reports'));
      await tester.pumpAndSettle();

      final reportsChip = tester.widget<ActionChip>(
        find.widgetWithText(ActionChip, 'Reports'),
      );
      expect(reportsChip.onPressed, isNull);
      expect(find.bySemanticsLabel('Current folder: Reports'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Open folder: My private files'),
        findsOneWidget,
      );

      await tester.tap(find.widgetWithText(ActionChip, 'My private files'));
      await tester.pumpAndSettle();

      expect(repository.requestedPaths.last, '/');
      expect(find.text('Documents'), findsAtLeastNWidgets(1));
    });

    testWidgets('includes file metadata in row semantics', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.pdf',
                path: '/Roadmap.pdf',
                isDirectory: false,
                sizeInBytes: 2048,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Roadmap.pdf'), findsOneWidget);
      expect(find.text('2.0 KB'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Roadmap.pdf, file. 2.0 KB'),
        findsOneWidget,
      );
    });

    testWidgets('uploads a picked file with completion feedback', (
      tester,
    ) async {
      var uploadComplete = false;
      var uploadedDirectoryPath = '';
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: uploadComplete
                ? const [
                    FileEntry(
                      id: 'file-1',
                      name: 'brief.txt',
                      path: '/brief.txt',
                      isDirectory: false,
                      sizeInBytes: 4,
                    ),
                  ]
                : const [],
          );
        },
        uploadFileHandler: (directoryPath, request, onProgress) async {
          uploadedDirectoryPath = directoryPath;
          onProgress?.call(request.sizeInBytes, request.sizeInBytes);
          uploadComplete = true;
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(
                FileUploadRequest(
                  fileName: 'brief.txt',
                  sizeInBytes: 4,
                  byteStream: Stream<List<int>>.fromIterable(const [
                    [1, 2, 3, 4],
                  ]),
                ),
              ),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      expect(uploadedDirectoryPath, '/');
      expect(find.text('Uploaded brief.txt.'), findsOneWidget);
      expect(find.text('brief.txt'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Uploaded brief.txt.'),
        findsAtLeastNWidgets(1),
      );
    });

    testWidgets('shows accessible in-progress upload feedback', (tester) async {
      final uploadCompleter = Completer<void>();
      var uploadComplete = false;
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: uploadComplete
                ? const [
                    FileEntry(
                      id: 'file-1',
                      name: 'brief.txt',
                      path: '/brief.txt',
                      isDirectory: false,
                      sizeInBytes: 4,
                    ),
                  ]
                : const [],
          );
        },
        uploadFileHandler: (_, request, onProgress) async {
          onProgress?.call(2, request.sizeInBytes);
          await uploadCompleter.future;
          uploadComplete = true;
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(
                FileUploadRequest(
                  fileName: 'brief.txt',
                  sizeInBytes: 4,
                  byteStream: Stream<List<int>>.fromIterable(const [
                    [1, 2],
                    [3, 4],
                  ]),
                ),
              ),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pump();

      expect(find.text('Uploading brief.txt: 50%'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Upload progress for brief.txt: 50 percent'),
        findsOneWidget,
      );

      uploadCompleter.complete();
      await tester.pumpAndSettle();

      expect(find.text('Uploaded brief.txt.'), findsOneWidget);
      expect(find.text('brief.txt'), findsOneWidget);
    });

    testWidgets(
      'announces indeterminate upload progress without a misleading percent',
      (tester) async {
        final uploadCompleter = Completer<void>();
        final repository = _FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://files.home.internal'),
            accountLabel: 'alice',
          ),
          uploadFileHandler: (_, _, _) async {
            await uploadCompleter.future;
          },
        );

        await tester.pumpWidget(
          _filesTestApp(
            const FilesScreen(),
            overrides: [
              filesRepositoryProvider.overrideWithValue(repository),
              filesImportPickerProvider.overrideWithValue(
                _FakeFilesImportPicker(
                  const FileUploadRequest(
                    fileName: 'brief.txt',
                    sizeInBytes: 0,
                    byteStream: Stream<List<int>>.empty(),
                  ),
                ),
              ),
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              serverConfigurationRepositoryProvider.overrideWith(
                (ref) => _FakeServerConfigurationRepository(
                  buildTestConfiguration(),
                ),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('Upload'));
        await tester.pump();

        expect(find.text('Uploading brief.txt…'), findsOneWidget);
        expect(
          find.bySemanticsLabel('Upload progress for brief.txt: 0 percent'),
          findsNothing,
        );
        expect(
          find.bySemanticsLabel('Uploading brief.txt…'),
          findsAtLeastNWidgets(1),
        );

        uploadCompleter.complete();
        await tester.pumpAndSettle();
      },
    );

    testWidgets('cancelled file picker leaves no upload status behind', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'existing.txt',
                path: '/existing.txt',
                isDirectory: false,
                sizeInBytes: 8,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(null),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      expect(find.text('existing.txt'), findsOneWidget);
      expect(find.text('Upload failed.'), findsNothing);
      expect(find.text('Choose a file to upload…'), findsNothing);
      expect(repository.requestedPaths, ['/']);
    });

    testWidgets('shows a friendly accessible upload failure', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'existing.txt',
                path: '/existing.txt',
                isDirectory: false,
                sizeInBytes: 8,
              ),
            ],
          ),
        },
        uploadFileHandler: (_, _, _) async {
          throw const FilesFailure.storage(
            'There is not enough storage available to upload this file.',
          );
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            filesImportPickerProvider.overrideWithValue(
              _FakeFilesImportPicker(
                FileUploadRequest(
                  fileName: 'brief.txt',
                  sizeInBytes: 4,
                  byteStream: Stream<List<int>>.fromIterable(const [
                    [1, 2, 3, 4],
                  ]),
                ),
              ),
            ),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Upload'));
      await tester.pumpAndSettle();

      const message =
          'There is not enough storage available to upload this file.';
      expect(find.text(message), findsOneWidget);
      expect(find.bySemanticsLabel(message), findsAtLeastNWidgets(1));
      expect(repository.requestedPaths, ['/']);
    });

    testWidgets('creates a folder and refreshes the current directory', (
      tester,
    ) async {
      var createdFolderName = '';
      var createdParentPath = '';
      var creationComplete = false;
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
        listDirectoryHandler: (path) async {
          return DirectoryListing(
            path: '/',
            entries: creationComplete
                ? const [
                    FileEntry(
                      id: 'folder-1',
                      name: 'Plans',
                      path: '/Plans',
                      isDirectory: true,
                    ),
                  ]
                : const [],
          );
        },
        createFolderHandler: (parentPath, name) async {
          createdParentPath = parentPath;
          createdFolderName = name;
          creationComplete = true;
          return FileEntry(
            id: 'folder-1',
            name: name,
            path: '/$name',
            isDirectory: true,
          );
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('New folder'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.text('My private files · Top folder'),
        ),
        findsOneWidget,
      );
      expect(find.text('Root'), findsNothing);
      expect(find.text('/'), findsNothing);
      await tester.enterText(find.byType(TextField), 'Plans');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Create'));
      await tester.pumpAndSettle();

      expect(createdParentPath, '/');
      expect(createdFolderName, 'Plans');
      expect(find.text('Created folder Plans.'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Plans'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Plans'), findsAtLeastNWidgets(1));
      expect(
        find.bySemanticsLabel(
          RegExp(r'Created folder Plans\..*My private files.*Top folder'),
        ),
        findsAtLeastNWidgets(1),
      );
    });

    testWidgets('opens folder dialog from keyboard and restores action focus', (
      tester,
    ) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.home.internal'),
          accountLabel: 'alice',
        ),
      );
      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      final trigger = find.widgetWithText(OutlinedButton, 'New folder');
      Finder focused() => find.byElementPredicate(
        (element) =>
            identical(element, FocusManager.instance.primaryFocus?.context),
      );
      for (
        var i = 0;
        i < 30 &&
            find.ancestor(of: focused(), matching: trigger).evaluate().isEmpty;
        i++
      ) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(find.ancestor(of: focused(), matching: trigger), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      final field = find.byType(TextField);
      expect(find.ancestor(of: focused(), matching: field), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      final cancel = find.widgetWithText(TextButton, 'Cancel');
      expect(find.ancestor(of: focused(), matching: cancel), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Create folder'), findsNothing);
      expect(find.ancestor(of: focused(), matching: trigger), findsOneWidget);
    });

    testWidgets('does not offer delete without a generated User operation', (
      tester,
    ) async {
      const fileEntry = FileEntry(
        id: 'file-1',
        name: 'old.txt',
        path: '/old.txt',
        isDirectory: false,
        allowedActions: {'delete'},
      );
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accountLabel: 'alice',
        ),
        listings: const {
          '/': DirectoryListing(path: '/', entries: [fileEntry]),
        },
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('old.txt'), findsOneWidget);
      expect(find.byTooltip('Delete old.txt'), findsNothing);
      expect(find.byIcon(Icons.delete_outline), findsNothing);
    });

    for (final locale in ['en', 'de']) {
      for (final scale in [1.0, 1.5]) {
        testWidgets(
          'shared toolbar remains accessible after replacement preview scroll '
          '$locale text scale $scale',
          (tester) async {
            tester.view.physicalSize = const Size(402, 874);
            tester.view.devicePixelRatio = 1;
            addTearDown(tester.view.resetPhysicalSize);
            addTearDown(tester.view.resetDevicePixelRatio);
            tester.platformDispatcher.localesTestValue = [Locale(locale)];
            addTearDown(tester.platformDispatcher.clearLocalesTestValue);
            final semantics = tester.ensureSemantics();
            try {
              final repository = _FakeFilesRepository(
                connectionState: FilesConnectionState.connected(
                  baseUrl: Uri.parse('https://api.example/api'),
                  accountLabel: 'Weave files',
                ),
                download: FileDownload(
                  fileName: _PostReplaceFilesController.name,
                  mediaType: 'text/plain',
                  bytes: Uint8List.fromList(
                    utf8.encode('Updated release notes.'),
                  ),
                ),
              );
              await tester.pumpWidget(
                _filesTestApp(
                  Builder(
                    builder: (context) => MediaQuery(
                      data: MediaQuery.of(
                        context,
                      ).copyWith(textScaler: TextScaler.linear(scale)),
                      child: const SizedBox(height: 700, child: FilesScreen()),
                    ),
                  ),
                  overrides: [
                    filesProvider.overrideWith(_PostReplaceFilesController.new),
                    weaveApiMemberSpacesProvider.overrideWith(
                      (ref) async => const MemberSpaceAccessSnapshot(
                        visibleSpaceRefs: {'workspace-default'},
                        displayNames: {
                          'workspace-default': 'Organization workspace',
                        },
                        defaultSpaceReadable: true,
                      ),
                    ),
                    filesRepositoryProvider.overrideWithValue(repository),
                  ],
                ),
              );
              await tester.pumpAndSettle();
              final scrollable = find.byType(Scrollable).first;
              final update = find.byKey(const Key('files-update-shared'));
              await tester.scrollUntilVisible(
                update,
                80,
                scrollable: scrollable,
              );
              await tester.ensureVisible(update);
              await tester.pumpAndSettle();
              await tester.tap(update);
              await tester.pumpAndSettle();
              final open = find.byKey(const Key('files-open-shared'));
              await tester.ensureVisible(open);
              await tester.pumpAndSettle();
              await tester.tap(open);
              await tester.pumpAndSettle();
              final content = find.byKey(const Key('files-preview-content'));
              expect(content, findsOneWidget);
              expect(find.text('Updated release notes.'), findsOneWidget);
              final context = tester.element(content);
              final l10n = AppLocalizations.of(context);
              await tester.tap(
                find.widgetWithText(
                  TextButton,
                  MaterialLocalizations.of(context).closeButtonLabel,
                ),
              );
              await tester.pumpAndSettle();

              // Stop mid-scroll, where a pinned large title clipped the selector's
              // semantics despite its rendered 48px height.
              final gesture = await tester.startGesture(
                tester.getCenter(find.byType(CustomScrollView)),
              );
              await gesture.moveBy(const Offset(0, 50));
              await tester.pump(const Duration(seconds: 1));
              await gesture.up();
              await tester.pumpAndSettle();
              await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
              await expectLater(
                tester,
                meetsGuideline(labeledTapTargetGuideline),
              );

              // Essential workspace controls remain usable after returning to the
              // top; a scrolling title must not hide or disable them.
              tester.state<ScrollableState>(scrollable).position.jumpTo(0);
              await tester.pumpAndSettle();
              final selector = find.byKey(const Key('files-space-selector'));
              expect(tester.getSize(selector).height, greaterThanOrEqualTo(48));
              await tester.tap(selector);
              await tester.pumpAndSettle();
              await tester.tap(find.text(l10n.filesPrivateSpace).last);
              await tester.pumpAndSettle();
              final container = ProviderScope.containerOf(
                tester.element(selector),
              );
              expect(
                container.read(filesProvider).requireValue.rootSpaceRef,
                isNull,
              );
              expect(repository.requestedPaths, ['/']);
              expect(tester.takeException(), isNull);
            } finally {
              semantics.dispose();
            }
          },
        );
      }
    }

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      final repository = _FakeFilesRepository(
        connectionState: FilesConnectionState.disconnected(
          baseUrl: Uri.parse('https://files.home.internal'),
        ),
      );

      await tester.pumpWidget(
        _filesTestApp(
          const FilesScreen(),
          overrides: [
            filesRepositoryProvider.overrideWithValue(repository),
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) =>
                  _FakeServerConfigurationRepository(buildTestConfiguration()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
