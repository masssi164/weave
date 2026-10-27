import 'dart:convert';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:intl/intl.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/app/presentation/workspace_setup_action.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

String _exportDestinationLabel(String? destination, String fallback) {
  if (destination == null || destination.isEmpty) return fallback;
  final windowsPath = RegExp(r'^[A-Za-z]:[\\/]').hasMatch(destination);
  final uri = windowsPath
      ? Uri.file(destination, windows: true)
      : Uri.tryParse(destination);
  if (uri == null || (uri.hasScheme && uri.scheme != 'file')) return fallback;
  final segments = uri.pathSegments.where((segment) => segment.isNotEmpty);
  return segments.isEmpty ? fallback : segments.last;
}

class FilesScreen extends ConsumerStatefulWidget {
  const FilesScreen({
    super.key,
    this.initialPath = '/',
    this.initialRootSpaceRef,
    this.isFolderRoute = false,
  });

  final String initialPath;
  final String? initialRootSpaceRef;
  final bool isFolderRoute;

  @override
  ConsumerState<FilesScreen> createState() => _FilesScreenState();
}

class _FilesScreenState extends ConsumerState<FilesScreen> {
  String? _requestedInitialPath;
  bool _didInvalidateInitialFiles = false;
  final _scrollController = ScrollController();
  final _entryFocusNodes = <String, FocusNode>{};

  @override
  void dispose() {
    _scrollController.dispose();
    for (final node in _entryFocusNodes.values) {
      node.dispose();
    }
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_didInvalidateInitialFiles) {
      return;
    }
    _didInvalidateInitialFiles = true;
    // A newly opened authorized screen must not reuse retained member content.
    if (ref
            .read(workspaceCapabilitySnapshotProvider)
            .asData
            ?.value
            .files
            .isReady ==
        true) {
      ref.invalidate(filesProvider);
    }
  }

  @override
  void didUpdateWidget(covariant FilesScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialPath != widget.initialPath ||
        oldWidget.initialRootSpaceRef != widget.initialRootSpaceRef) {
      _requestedInitialPath = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    ref.listen(workspaceCapabilitySnapshotProvider, (previous, next) {
      final wasReady = previous?.asData?.value.files.isReady == true;
      final isReady = next.asData?.value.files.isReady == true;
      if (wasReady && !isReady) {
        // The blocked view never watches Files, so it cannot show old content.
        _requestedInitialPath = null;
      }
      if (!wasReady && isReady) {
        // Grant restoration must fetch current content before it is shown.
        ref.invalidate(filesProvider);
        _requestedInitialPath = null;
      }
    });
    final capabilitySnapshot = ref.watch(workspaceCapabilitySnapshotProvider);
    final filesReady = capabilitySnapshot.asData?.value.files.isReady == true;
    // A blocked member must not start a Files session or issue a directory read.
    final asyncFiles = filesReady ? ref.watch(filesProvider) : null;
    if (asyncFiles != null) {
      _syncInitialPath(asyncFiles);
    }

    final currentFiles = asyncFiles?.asData?.value;
    final currentRead =
        filesReady &&
        asyncFiles?.isLoading == false &&
        asyncFiles?.hasError == false &&
        currentFiles?.hasSuccessfulCurrentRead == true;
    final pathSegments = widget.initialPath
        .split('/')
        .where((part) => part.isNotEmpty);
    final content = CustomScrollView(
      controller: _scrollController,
      slivers: [
        // Keep scrolled directory controls clear of a pinned title's semantics clip.
        SliverAppBar.large(
          pinned: widget.isFolderRoute,
          leading:
              !widget.isFolderRoute &&
                  _normalizeInitialPath(widget.initialPath) != '/'
              ? BackButton(
                  onPressed: () => context.go(
                    AppRoutes.filesLocation(
                      '/',
                      spaceRef: currentFiles?.rootSpaceRef,
                    ),
                  ),
                )
              : null,
          title: Text(
            widget.isFolderRoute && pathSegments.isNotEmpty
                ? pathSegments.last
                : l10n.filesScreenTitle,
          ),
        ),
        ...switch (capabilitySnapshot) {
          AsyncLoading() => <Widget>[
            _fillStateSliver(
              child: LoadingState(
                message: l10n.filesLoadingLabel,
                hint: l10n.filesLoadingHint,
                icon: Icons.folder_outlined,
              ),
            ),
          ],
          AsyncError() => <Widget>[
            _fillStateSliver(
              child: ErrorState(
                message: l10n.filesLoadErrorTitle,
                guidance: l10n.filesErrorGuidance,
                retryLabel: l10n.retryButton,
                onRetry: () {
                  ref
                    ..invalidate(weaveApiWorkspaceCapabilitySnapshotProvider)
                    ..invalidate(weaveApiMemberSpacesProvider);
                },
              ),
            ),
          ],
          AsyncData(:final value) when !value.files.isReady => <Widget>[
            _fillStateSliver(
              child: _FilesCapabilityBody(
                capability: value.files,
                onRetry: () => ref
                  ..invalidate(weaveApiWorkspaceCapabilitySnapshotProvider)
                  ..invalidate(weaveApiMemberSpacesProvider),
              ),
            ),
          ],
          AsyncData() => switch (asyncFiles!) {
            AsyncLoading() => <Widget>[
              _fillStateSliver(
                child: LoadingState(
                  message: l10n.filesLoadingLabel,
                  hint: l10n.filesLoadingHint,
                  icon: Icons.folder_outlined,
                ),
              ),
            ],
            AsyncError() => <Widget>[
              _fillStateSliver(
                child: ErrorState(
                  message: l10n.filesLoadErrorTitle,
                  guidance: l10n.filesErrorGuidance,
                  retryLabel: l10n.retryButton,
                  onRetry: () => ref.invalidate(filesProvider),
                ),
              ),
            ],
            AsyncData(:final value) => _buildStateSlivers(
              context,
              ref,
              l10n,
              value,
              isCurrentRead: !asyncFiles.isLoading && !asyncFiles.hasError,
            ),
          },
        },
      ],
    );
    return Semantics(
      identifier: currentRead
          ? 'files-loaded-${currentFiles!.successfulReadGeneration}'
          : null,
      container: true,
      explicitChildNodes: true,
      child: content,
    );
  }

  void _syncInitialPath(AsyncValue<FilesViewState> asyncFiles) {
    final targetPath = _normalizeInitialPath(widget.initialPath);
    if (_requestedInitialPath == targetPath) {
      return;
    }

    final state = switch (asyncFiles) {
      AsyncData(:final value) => value,
      _ => null,
    };
    if (state == null ||
        state.isBusy ||
        state.connectionState.status != FilesConnectionStatus.connected) {
      return;
    }

    _requestedInitialPath = targetPath;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final controller = ref.read(filesProvider.notifier);
      final space = widget.initialRootSpaceRef;
      if (space != null && state.rootSpaceRef != space) {
        await controller.selectRootSpace(space);
      }
      if (!mounted) return;
      final current = ref.read(filesProvider).asData?.value;
      if (current != null &&
          current.directoryFailure == null &&
          current.currentPath != targetPath) {
        await controller.openDirectory(targetPath);
      }
      if (!mounted || widget.isFolderRoute || targetPath == '/') return;
      final loaded = ref.read(filesProvider).asData?.value;
      final folderId = loaded?.directoryListing?.parentFileId;
      if (loaded?.hasSuccessfulCurrentRead == true &&
          loaded?.currentPath == targetPath &&
          folderId != null) {
        // Resolve legacy path links through current authorized metadata before
        // giving them the same native hierarchy as an in-app folder visit.
        context.go(
          AppRoutes.filesFolder(
            folderId,
            targetPath,
            spaceRef: loaded!.rootSpaceRef,
          ),
          extra: loaded.directoryListing?.ownerKey,
        );
      }
    });
  }

  String _normalizeInitialPath(String path) {
    final trimmed = path.trim();
    if (trimmed.isEmpty || trimmed == '/') {
      return '/';
    }

    final normalized = trimmed.startsWith('/') ? trimmed : '/$trimmed';
    return normalized.replaceAll(RegExp(r'/+'), '/');
  }

  List<Widget> _buildStateSlivers(
    BuildContext context,
    WidgetRef ref,
    AppLocalizations l10n,
    FilesViewState state, {
    required bool isCurrentRead,
  }) {
    final slivers = <Widget>[];

    if (state.connectionState.status == FilesConnectionStatus.connected) {
      slivers.add(
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
          sliver: SliverToBoxAdapter(child: _DirectoryToolbar(state: state)),
        ),
      );
      if (state.uploadStatus.phase != FilesUploadPhase.idle) {
        slivers.add(
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
            sliver: SliverToBoxAdapter(
              child: _UploadStatusCard(uploadStatus: state.uploadStatus),
            ),
          ),
        );
      }
      if (state.entryActionStatus.phase != FilesEntryActionPhase.idle) {
        slivers.add(
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
            sliver: SliverToBoxAdapter(
              child: _EntryActionStatusCard(
                actionStatus: state.entryActionStatus,
                onRetryRead: () =>
                    ref.read(filesProvider.notifier).retryFolderReadback(),
                onRetryWrite: () {
                  final operation = state.entryActionStatus;
                  if (operation.parentPath == state.currentPath &&
                      operation.rootSpaceRef == state.rootSpaceRef &&
                      operation.entryName != null) {
                    ref
                        .read(filesProvider.notifier)
                        .createFolder(operation.entryName!);
                  }
                },
              ),
            ),
          ),
        );
      }
    }

    slivers.add(_buildContentSliver(context, ref, l10n, state));
    if (isCurrentRead && state.hasSuccessfulCurrentRead) {
      slivers.add(
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          sliver: SliverToBoxAdapter(
            child: Semantics(
              identifier: 'files-listing-end',
              child: Text(
                l10n.filesListingEnd,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ),
        ),
      );
    }
    return slivers;
  }

  Widget _buildContentSliver(
    BuildContext context,
    WidgetRef ref,
    AppLocalizations l10n,
    FilesViewState state,
  ) {
    final connectionState = state.connectionState;
    switch (connectionState.status) {
      case FilesConnectionStatus.misconfigured:
        return _fillStateSliver(
          child: EmptyState(
            message: l10n.filesSetupNeededTitle,
            guidance: l10n.filesMisconfiguredMessage,
            icon: Icons.settings_outlined,
          ),
        );
      case FilesConnectionStatus.disconnected:
        return _fillStateSliver(
          child: EmptyState(
            message: l10n.filesDisconnectedTitle,
            guidance: l10n.filesDisconnectedMessage,
            icon: Icons.cloud_off_outlined,
          ),
        );
      case FilesConnectionStatus.invalid:
        return _fillStateSliver(
          child: ErrorState(
            message: l10n.filesSessionExpiredTitle,
            guidance: l10n.filesInvalidSessionMessage,
          ),
        );
      case FilesConnectionStatus.unavailable:
        return _fillStateSliver(
          child: ErrorState(
            message: l10n.filesUnavailableTitle,
            guidance: l10n.filesUnavailableGuidance,
            retryLabel: l10n.retryButton,
            onRetry: state.isBusy
                ? null
                : () {
                    ref.read(filesProvider.notifier).connect();
                  },
          ),
        );
      case FilesConnectionStatus.connected:
        if (state.isBusy && state.directoryListing == null) {
          return _fillStateSliver(
            child: LoadingState(
              message: l10n.filesLoadingLabel,
              hint: l10n.filesLoadingHint,
              icon: Icons.folder_outlined,
            ),
          );
        }
        final listing = state.directoryListing;
        final directoryFailure = state.directoryFailure;
        if (directoryFailure != null && listing == null) {
          return _fillStateSliver(
            child: ErrorState(
              message: l10n.filesLoadErrorTitle,
              guidance: l10n.settingsWorkspaceRecoveryUnavailableAction,
              retryLabel: l10n.retryButton,
              onRetry: state.isBusy
                  ? null
                  : () {
                      ref.read(filesProvider.notifier).refresh();
                    },
            ),
          );
        }
        if (listing == null ||
            (listing.entries.isEmpty && directoryFailure == null)) {
          return _fillStateSliver(
            child: EmptyState(
              message: state.rootSpaceRef == null
                  ? l10n.filesPrivateEmptyMessage
                  : l10n.filesSharedEmptyMessage,
              guidance: state.rootSpaceRef == null
                  ? l10n.filesPrivateEmptyGuidance
                  : l10n.filesSharedEmptyGuidance,
              icon: Icons.folder_outlined,
              actionLabel: l10n.filesRefreshButton,
              onAction: state.isBusy
                  ? null
                  : () {
                      ref.read(filesProvider.notifier).refresh();
                    },
            ),
          );
        }
        return SliverPadding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          sliver: SliverList(
            delegate: SliverChildListDelegate.fixed([
              if (directoryFailure != null) ...[
                _StaleDirectoryNotice(
                  failure: directoryFailure,
                  onRefresh: state.isBusy
                      ? null
                      : () {
                          ref.read(filesProvider.notifier).refresh();
                        },
                ),
                const SizedBox(height: 12),
              ],
              _DirectorySummary(listing: listing),
              if (listing.entries.isNotEmpty) ...[
                const SizedBox(height: 12),
                ...List<Widget>.generate(listing.entries.length * 2 - 1, (
                  index,
                ) {
                  if (index.isOdd) {
                    return const Divider(height: 1);
                  }

                  final entryIndex = index ~/ 2;
                  final entry = listing.entries[entryIndex];
                  return _FileEntryTile(
                    entry: entry,
                    isBusy: state.isBusy,
                    focusNode: _entryFocusNodes.putIfAbsent(
                      entry.id,
                      FocusNode.new,
                    ),
                    onOpenFolder: () => _openFolder(entry),
                  );
                }),
              ],
            ]),
          ),
        );
    }
  }

  Future<void> _openFolder(FileEntry entry) async {
    final controller = ref.read(filesProvider.notifier);
    final origin = ref.read(filesProvider).asData?.value;
    if (origin == null || origin.isBusy || !entry.allows('listChildren')) {
      return;
    }
    await context.push<void>(
      AppRoutes.filesFolder(
        entry.id,
        entry.path,
        spaceRef: origin.rootSpaceRef,
      ),
      extra: origin.directoryListing?.ownerKey,
    );
    if (!mounted || ModalRoute.of(context)?.isCurrent == false) return;
    final current = ref.read(filesProvider).asData?.value;
    if (current == null ||
        current.rootSpaceRef != origin.rootSpaceRef ||
        current.directoryListing?.ownerKey !=
            origin.directoryListing?.ownerKey ||
        !identical(ref.read(filesProvider.notifier), controller)) {
      return;
    }
    await controller.refresh();
    if (!mounted) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final returned = ref.read(filesProvider).asData?.value;
      final listing = returned?.directoryListing;
      if (returned?.rootSpaceRef != origin.rootSpaceRef ||
          listing?.ownerKey != origin.directoryListing?.ownerKey ||
          !identical(ref.read(filesProvider.notifier), controller)) {
        return;
      }
      if (listing?.entries.any((item) => item.id == entry.id) != true) return;
      final node = _entryFocusNodes[entry.id];
      node?.requestFocus();
      if (node?.context case final context?) {
        Scrollable.ensureVisible(
          context,
          alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
        );
      }
    });
  }

  Widget _fillStateSliver({required Widget child}) {
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
      sliver: SliverFillRemaining(hasScrollBody: true, child: child),
    );
  }
}

class _FilesCapabilityBody extends StatelessWidget {
  const _FilesCapabilityBody({required this.capability, required this.onRetry});

  final WorkspaceCapabilityState capability;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final recovery = workspaceCapabilityRecoveryPresentation(l10n, capability);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: EmptyState(
          message: l10n.filesUnavailableTitle,
          guidance: recovery.recovery,
          icon: Icons.folder_off_outlined,
          actionLabel: l10n.retryButton,
          onAction: onRetry,
          semanticLabel: recovery.semanticLabel(l10n, l10n.navFiles),
          secondaryAction: WorkspaceSetupAction(capability: capability),
        ),
      ),
    );
  }
}

String? _localizedFilesFailure(FilesFailure? failure, AppLocalizations l10n) {
  if (failure?.type == FilesFailureType.sessionRequired) {
    return l10n.filesInvalidSessionMessage;
  }
  if (failure?.message == FilesFailure.identityUnavailableCode) {
    return l10n.filesErrorGuidance;
  }
  return failure?.message;
}

class _StaleDirectoryNotice extends StatelessWidget {
  const _StaleDirectoryNotice({required this.failure, required this.onRefresh});

  final FilesFailure failure;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final failureMessage = _localizedFilesFailure(failure, l10n)!;

    return Semantics(
      container: true,
      liveRegion: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.tertiaryContainer,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: theme.colorScheme.tertiary),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.wifi_off_outlined,
                    color: theme.colorScheme.onTertiaryContainer,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      l10n.filesStaleDirectoryTitle,
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onTertiaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                l10n.filesStaleDirectoryGuidance,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onTertiaryContainer,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                failureMessage,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onTertiaryContainer,
                ),
              ),
              const SizedBox(height: 12),
              Align(
                alignment: AlignmentDirectional.centerEnd,
                child: TextButton.icon(
                  onPressed: onRefresh,
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.filesStaleDirectoryRetryButton),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DirectoryToolbar extends ConsumerWidget {
  const _DirectoryToolbar({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final listing = state.directoryListing;
    final access = ref.watch(weaveApiMemberSpacesProvider).asData?.value;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PathBreadcrumbs(
          path: state.currentPath,
          isBusy: state.isBusy,
          currentParentId: listing?.parentFileId,
          rootLabel: _filesScopeLabel(l10n, state.rootSpaceRef, access),
          spaceRef: state.rootSpaceRef,
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Semantics(
              identifier: 'files-refresh',
              child: AccessibleButton(
                outlined: true,
                onPressed: state.isBusy
                    ? null
                    : () {
                        ref.read(filesProvider.notifier).refresh();
                      },
                semanticLabel: l10n.filesRefreshCurrentFolderSemantic,
                child: Text(l10n.filesRefreshButton),
              ),
            ),
            if (listing?.allows('createFolder') == true)
              Semantics(
                identifier: 'files-create-folder',
                child: AccessibleButton(
                  outlined: true,
                  onPressed: state.isBusy
                      ? null
                      : () async {
                          final operationController = ref.read(
                            filesProvider.notifier,
                          );
                          final operationRepository = ref.read(
                            filesRepositoryProvider,
                          );
                          final folderName = await showDialog<String>(
                            context: context,
                            builder: (context) => _CreateFolderDialog(
                              destination: l10n.filesCurrentLocation(
                                _filesScopeLabel(
                                  l10n,
                                  state.rootSpaceRef,
                                  access,
                                ),
                                state.currentPath == '/'
                                    ? l10n.filesTopFolder
                                    : state.currentPath,
                              ),
                            ),
                          );
                          // A dialog may outlive its original directory route.
                          if (folderName == null || !context.mounted) return;
                          final active = ref.read(filesProvider).asData?.value;
                          if (active == null ||
                              !identical(
                                operationController,
                                ref.read(filesProvider.notifier),
                              ) ||
                              active.directoryListing?.ownerKey !=
                                  state.directoryListing?.ownerKey ||
                              active.directoryListing?.parentFileId !=
                                  state.directoryListing?.parentFileId ||
                              active.rootSpaceRef != state.rootSpaceRef ||
                              active.currentPath != state.currentPath ||
                              !identical(
                                operationRepository,
                                ref.read(filesRepositoryProvider),
                              )) {
                            return;
                          }
                          ref
                              .read(filesProvider.notifier)
                              .createFolder(folderName);
                        },
                  semanticLabel: l10n.filesCreateFolderCurrentFolderSemantic,
                  child: Text(l10n.filesCreateFolderButton),
                ),
              ),
            if (listing?.allows('upload') == true)
              Semantics(
                identifier: 'files-upload',
                child: AccessibleButton(
                  onPressed: state.isBusy
                      ? null
                      : () {
                          ref.read(filesProvider.notifier).pickAndUpload();
                        },
                  semanticLabel: l10n.filesUploadCurrentFolderSemantic,
                  child: Text(l10n.filesUploadButton),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _EntryActionStatusCard extends ConsumerWidget {
  const _EntryActionStatusCard({
    required this.actionStatus,
    this.onRetryRead,
    this.onRetryWrite,
  });

  final FilesEntryActionStatus actionStatus;
  final VoidCallback? onRetryRead;
  final VoidCallback? onRetryWrite;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final access = ref.watch(weaveApiMemberSpacesProvider).asData?.value;
    final entryName = actionStatus.entryName;
    final message =
        actionStatus.writeOutcome == FilesWriteOutcome.committedRefreshPending
        ? l10n.filesFolderSavedRefreshPending
        : actionStatus.writeOutcome == FilesWriteOutcome.uncertain
        ? l10n.filesFolderWriteUncertain
        : switch (actionStatus.phase) {
            FilesEntryActionPhase.replacingContent => l10n.filesContentUpdating(
              entryName ?? l10n.filesScreenTitle,
            ),
            FilesEntryActionPhase.replacedContent => l10n.filesContentUpdated(
              entryName ?? l10n.filesScreenTitle,
            ),
            FilesEntryActionPhase.idle => '',
            FilesEntryActionPhase.creatingFolder =>
              entryName == null
                  ? l10n.filesCreateFolderProgressUnknownMessage
                  : l10n.filesCreateFolderProgressMessage(entryName),
            FilesEntryActionPhase.createdFolder =>
              entryName == null
                  ? l10n.filesCreateFolderCompletedUnknownMessage
                  : l10n.filesCreateFolderCompletedMessage(entryName),
            FilesEntryActionPhase.deletingEntry =>
              entryName == null
                  ? l10n.filesDeleteProgressUnknownMessage
                  : l10n.filesDeleteProgressMessage(entryName),
            FilesEntryActionPhase.deletedEntry =>
              entryName == null
                  ? l10n.filesDeleteCompletedUnknownMessage
                  : l10n.filesDeleteCompletedMessage(entryName),
            FilesEntryActionPhase.exportingEntry =>
              entryName == null
                  ? l10n.filesExportProgressUnknownMessage
                  : l10n.filesExportProgressMessage(entryName),
            FilesEntryActionPhase.exportedEntry =>
              entryName == null
                  ? l10n.filesExportCompletedUnknownMessage
                  : l10n.filesExportCompletedMessage(
                      entryName,
                      _exportDestinationLabel(
                        actionStatus.destination,
                        l10n.filesExportUserVisibleFallback,
                      ),
                    ),
            FilesEntryActionPhase.failed =>
              _localizedFilesFailure(actionStatus.failure, l10n) ??
                  l10n.filesEntryActionFailedMessage,
          };
    final icon = switch (actionStatus.phase) {
      FilesEntryActionPhase.replacedContent => Icons.check_circle_outline,
      FilesEntryActionPhase.createdFolder => Icons.check_circle_outline,
      FilesEntryActionPhase.deletedEntry => Icons.check_circle_outline,
      FilesEntryActionPhase.exportedEntry => Icons.check_circle_outline,
      FilesEntryActionPhase.failed => Icons.error_outline,
      FilesEntryActionPhase.idle => Icons.info_outline,
      _ => Icons.sync_outlined,
    };

    return Semantics(
      liveRegion: actionStatus.phase != FilesEntryActionPhase.idle,
      label: actionStatus.parentPath == null
          ? message
          : '$message ${l10n.filesCurrentLocation(_filesScopeLabel(l10n, actionStatus.rootSpaceRef, access), actionStatus.parentPath == '/' ? l10n.filesTopFolder : actionStatus.parentPath!)}',
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              ExcludeSemantics(child: Icon(icon)),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(message, style: theme.textTheme.bodyMedium),
                    if (actionStatus.parentPath != null)
                      Text(
                        l10n.filesFolderLocation(
                          _filesScopeLabel(
                            l10n,
                            actionStatus.rootSpaceRef,
                            access,
                          ),
                          actionStatus.parentPath == '/'
                              ? l10n.filesTopFolder
                              : actionStatus.parentPath!,
                        ),
                      ),
                    if (actionStatus.writeOutcome ==
                        FilesWriteOutcome.committedRefreshPending)
                      AccessibleButton(
                        onPressed: onRetryRead,
                        semanticLabel: l10n.filesRefreshButton,
                        outlined: true,
                        child: Text(l10n.filesRefreshButton),
                      ),
                    if (actionStatus.writeOutcome ==
                        FilesWriteOutcome.uncertain)
                      AccessibleButton(
                        onPressed: onRetryWrite,
                        semanticLabel: l10n.retryButton,
                        outlined: true,
                        child: Text(l10n.retryButton),
                      ),
                  ],
                ),
              ),
              if (actionStatus.phase == FilesEntryActionPhase.creatingFolder ||
                  actionStatus.phase == FilesEntryActionPhase.deletingEntry ||
                  actionStatus.phase == FilesEntryActionPhase.exportingEntry ||
                  actionStatus.phase == FilesEntryActionPhase.replacingContent)
                const Padding(
                  padding: EdgeInsetsDirectional.only(start: 12),
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UploadStatusCard extends StatelessWidget {
  const _UploadStatusCard({required this.uploadStatus});

  final FilesUploadStatus uploadStatus;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final fileName = uploadStatus.fileName;
    final progressFraction = uploadStatus.progressFraction;
    final percent = progressFraction == null
        ? 0
        : (progressFraction * 100).round().clamp(0, 100);
    final message = switch (uploadStatus.phase) {
      FilesUploadPhase.idle => '',
      FilesUploadPhase.picking => l10n.filesUploadPickingMessage,
      FilesUploadPhase.uploading =>
        fileName == null
            ? l10n.filesUploadProgressUnknownMessage
            : progressFraction == null
            ? l10n.filesUploadProgressIndeterminateMessage(fileName)
            : l10n.filesUploadProgressMessage(fileName, percent),
      FilesUploadPhase.completed =>
        fileName == null
            ? l10n.filesUploadCompletedUnknownMessage
            : l10n.filesUploadCompletedMessage(fileName),
      FilesUploadPhase.failed =>
        _localizedFilesFailure(uploadStatus.failure, l10n) ??
            (fileName == null
                ? l10n.filesUploadFailedUnknownMessage
                : l10n.filesUploadFailedMessage(fileName)),
    };
    final icon = switch (uploadStatus.phase) {
      FilesUploadPhase.completed => Icons.check_circle_outline,
      FilesUploadPhase.failed => Icons.error_outline,
      _ => Icons.cloud_upload_outlined,
    };
    final semanticLabel = switch (uploadStatus.phase) {
      FilesUploadPhase.uploading
          when fileName != null && progressFraction != null =>
        l10n.filesUploadProgressSemantic(fileName, percent),
      _ => message,
    };

    return Semantics(
      liveRegion: uploadStatus.phase != FilesUploadPhase.idle,
      label: semanticLabel,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  ExcludeSemantics(child: Icon(icon)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(message, style: theme.textTheme.bodyMedium),
                  ),
                ],
              ),
              if (uploadStatus.phase == FilesUploadPhase.uploading) ...[
                const SizedBox(height: 12),
                ExcludeSemantics(
                  child: LinearProgressIndicator(value: progressFraction),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PathBreadcrumbs extends ConsumerWidget {
  const _PathBreadcrumbs({
    required this.path,
    required this.isBusy,
    required this.rootLabel,
    required this.currentParentId,
    required this.spaceRef,
  });
  final String? spaceRef;
  final String? currentParentId;
  final String rootLabel;

  final String path;
  final bool isBusy;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final segments = path.split('/')..removeWhere((segment) => segment.isEmpty);
    final crumbs = <Widget>[
      _BreadcrumbChip(
        identifier: 'files-location-root',
        currentParentId: path == '/' ? currentParentId : null,
        label: rootLabel,
        semanticLabel: path == '/'
            ? l10n.filesCurrentFolderSemantic(rootLabel)
            : l10n.filesOpenFolderSemantic(rootLabel),
        isCurrent: path == '/',
        onPressed: path == '/' || isBusy
            ? null
            : () {
                _openAncestor(context, '/', spaceRef);
              },
      ),
    ];

    for (var index = 0; index < segments.length; index++) {
      final crumbPath = '/${segments.take(index + 1).join('/')}';
      final isCurrent = crumbPath == path;
      crumbs
        ..add(
          ExcludeSemantics(
            child: Icon(
              Icons.chevron_right,
              size: 18,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        )
        ..add(
          _BreadcrumbChip(
            currentParentId: isCurrent ? currentParentId : null,
            label: segments[index],
            semanticLabel: isCurrent
                ? l10n.filesCurrentFolderSemantic(segments[index])
                : l10n.filesOpenFolderSemantic(segments[index]),
            isCurrent: isCurrent,
            onPressed: isCurrent || isBusy
                ? null
                : () {
                    _openAncestor(context, crumbPath, spaceRef);
                  },
          ),
        );
    }

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: crumbs),
    );
  }
}

void _openAncestor(BuildContext context, String path, String? spaceRef) {
  final router = GoRouter.of(context);
  var found = false;
  Navigator.of(context).popUntil((route) {
    final location = route.settings.arguments;
    if (location is FilesRouteLocation &&
        location.path == path &&
        location.spaceRef == spaceRef) {
      found = true;
      return true;
    }
    // The overview contains locations; it is not their directory root.
    return route.settings.name == AppRoutes.files || route.isFirst;
  });
  if (!found) router.go(AppRoutes.filesLocation(path, spaceRef: spaceRef));
}

class _BreadcrumbChip extends StatelessWidget {
  const _BreadcrumbChip({
    this.identifier,
    this.currentParentId,
    required this.label,
    required this.semanticLabel,
    required this.isCurrent,
    required this.onPressed,
  });

  final String label;
  final String? identifier;
  final String? currentParentId;
  final String semanticLabel;
  final bool isCurrent;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final chip = Semantics(
      identifier: identifier,
      button: onPressed != null,
      enabled: onPressed != null,
      selected: isCurrent,
      label: semanticLabel,
      child: ExcludeSemantics(
        child: ActionChip(
          avatar: isCurrent ? const Icon(Icons.place_outlined, size: 18) : null,
          label: Text(label),
          onPressed: onPressed,
        ),
      ),
    );
    return currentParentId == null
        ? chip
        : Semantics(
            identifier: 'files-current-folder-$currentParentId',
            container: true,
            explicitChildNodes: true,
            child: chip,
          );
  }
}

class _DirectorySummary extends StatelessWidget {
  const _DirectorySummary({required this.listing});

  final DirectoryListing listing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final folderCount = listing.entries
        .where((entry) => entry.isDirectory)
        .length;
    final fileCount = listing.entries.length - folderCount;

    return Text(
      l10n.filesDirectorySummary(folderCount, fileCount),
      style: theme.textTheme.bodyMedium?.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }
}

class _CreateFolderDialog extends StatefulWidget {
  const _CreateFolderDialog({required this.destination});
  final String destination;

  @override
  State<_CreateFolderDialog> createState() => _CreateFolderDialogState();
}

class _CreateFolderDialogState extends State<_CreateFolderDialog> {
  final TextEditingController _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_onChanged)
      ..dispose();
    super.dispose();
  }

  void _onChanged() {
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = _controller.text.trim();

    return AlertDialog(
      scrollable: true,
      title: Text(l10n.filesCreateFolderDialogTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(widget.destination),
          const SizedBox(height: 12),
          Semantics(
            identifier: 'files-folder-name',
            child: TextField(
              controller: _controller,
              autofocus: true,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                labelText: l10n.filesCreateFolderNameLabel,
                hintText: l10n.filesCreateFolderNameHint,
              ),
              onSubmitted: name.isEmpty
                  ? null
                  : (_) {
                      Navigator.of(context).pop(name);
                    },
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
          },
          child: Text(l10n.filesCancelButton),
        ),
        Semantics(
          identifier: 'files-confirm-create-folder',
          child: FilledButton(
            onPressed: name.isEmpty
                ? null
                : () {
                    Navigator.of(context).pop(name);
                  },
            child: Text(l10n.filesCreateFolderConfirmButton),
          ),
        ),
      ],
    );
  }
}

class _FilePreview extends ConsumerWidget {
  const _FilePreview({
    required this.entry,
    required this.rootSpaceRef,
    required this.ownerKey,
    required this.download,
  });
  final FileEntry entry;
  final String? rootSpaceRef;
  final String? ownerKey;
  final Future<FileDownload> download;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final value = ref.watch(filesProvider);
    final current = value.asData?.value;
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);
    final spaces = rootSpaceRef == null
        ? null
        : ref.watch(weaveApiMemberSpacesProvider);
    final stillAvailable =
        !value.isLoading &&
        !value.hasError &&
        !capabilities.isLoading &&
        !capabilities.hasError &&
        capabilities.asData?.value.files.isReady == true &&
        (rootSpaceRef == null ||
            (spaces?.isLoading == false &&
                spaces?.hasError == false &&
                spaces?.asData?.value?.visibleSpaceRefs.contains(
                      rootSpaceRef,
                    ) ==
                    true)) &&
        current?.rootSpaceRef == rootSpaceRef &&
        current?.directoryFailure == null &&
        current?.directoryListing?.ownerKey == ownerKey &&
        current?.directoryListing?.entries.any(
              (item) =>
                  item.id == entry.id &&
                  item.revision == entry.revision &&
                  item.allows('download'),
            ) ==
            true;
    return AlertDialog(
      title: Text(stillAvailable ? entry.name : l10n.filesScreenTitle),
      content: SizedBox(
        width: 560,
        child: !stillAvailable
            ? Text(l10n.filesUnavailableSpace)
            : FutureBuilder<FileDownload>(
                future: download,
                builder: (context, snapshot) {
                  if (snapshot.hasError) return Text(l10n.filesPreviewFailed);
                  final file = snapshot.data;
                  if (file == null) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (file.mediaType == 'text/plain' &&
                      file.bytes.length <= 512 * 1024) {
                    try {
                      final text = utf8.decode(
                        file.bytes,
                        allowMalformed: false,
                      );
                      return SingleChildScrollView(
                        child: Semantics(
                          identifier: 'files-preview-content',
                          child: SelectableText(
                            text,
                            key: const Key('files-preview-content'),
                          ),
                        ),
                      );
                    } on FormatException {
                      return Text(l10n.filesPreviewUnsupported);
                    }
                  }
                  if (const {
                        'image/png',
                        'image/jpeg',
                        'image/gif',
                        'image/webp',
                      }.contains(file.mediaType) &&
                      file.bytes.length <= 5 * 1024 * 1024) {
                    return Image.memory(
                      file.bytes,
                      key: const Key('files-preview-content'),
                      semanticLabel: entry.name,
                      cacheWidth: 2048,
                      errorBuilder: (_, error, stack) =>
                          Text(l10n.filesPreviewFailed),
                    );
                  }
                  return Text(l10n.filesPreviewUnsupported);
                },
              ),
      ),
      actions: [
        Semantics(
          identifier: 'files-preview-close',
          child: TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(MaterialLocalizations.of(context).closeButtonLabel),
          ),
        ),
      ],
    );
  }
}

class _FileEntryTile extends ConsumerWidget {
  const _FileEntryTile({
    required this.entry,
    required this.isBusy,
    required this.focusNode,
    required this.onOpenFolder,
  });

  final FileEntry entry;
  final bool isBusy;
  final FocusNode focusNode;
  final VoidCallback onOpenFolder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final subtitle = _subtitle(context, entry);
    final l10n = AppLocalizations.of(context);
    return Semantics(
      container: true,
      explicitChildNodes: true,
      button: entry.isDirectory,
      identifier: 'files-entry-${entry.id}',
      label: _semanticLabel(l10n, entry, subtitle),
      child: ListTile(
        key: ValueKey('files-entry-${entry.id}'),
        focusNode: focusNode,
        contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
        leading: ExcludeSemantics(
          child: Icon(
            entry.isDirectory
                ? Icons.folder_outlined
                : Icons.insert_drive_file_outlined,
          ),
        ),
        title: Text(entry.name),
        subtitle: subtitle == null ? null : Text(subtitle),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!entry.isDirectory && entry.allows('download'))
              Semantics(
                identifier: 'files-open-${entry.id}',
                child: IconButton(
                  key: ValueKey('files-open-${entry.id}'),
                  tooltip: l10n.filesOpenEntrySemantic(entry.name),
                  onPressed: isBusy
                      ? null
                      : () => showDialog<void>(
                          context: context,
                          // Dialog routes do not inherit the directory's scoped providers.
                          builder: (_) => UncontrolledProviderScope(
                            container: ProviderScope.containerOf(context),
                            child: _FilePreview(
                              entry: entry,
                              rootSpaceRef: ref
                                  .read(filesProvider)
                                  .asData
                                  ?.value
                                  .rootSpaceRef,
                              ownerKey: ref
                                  .read(filesProvider)
                                  .asData
                                  ?.value
                                  .directoryListing
                                  ?.ownerKey,
                              download: ref
                                  .read(filesProvider.notifier)
                                  .downloadForPreview(entry),
                            ),
                          ),
                        ),
                  icon: const Icon(Icons.visibility_outlined),
                ),
              ),
            if (!entry.isDirectory && entry.allows('updateContent'))
              IconButton(
                key: ValueKey('files-update-${entry.id}'),
                tooltip: l10n.filesReplaceEntrySemantic(entry.name),
                onPressed: isBusy
                    ? null
                    : () => ref
                          .read(filesProvider.notifier)
                          .pickAndReplace(entry),
                icon: const Icon(Icons.upload_file_outlined),
              ),
            if (!entry.isDirectory && entry.allows('download'))
              Semantics(
                identifier: 'files-export-${entry.id}',
                child: IconButton(
                  key: ValueKey('files-export-${entry.id}'),
                  tooltip: l10n.filesExportEntrySemantic(entry.name),
                  onPressed: isBusy
                      ? null
                      : () {
                          ref.read(filesProvider.notifier).exportEntry(entry);
                        },
                  icon: const Icon(Icons.file_download_outlined),
                ),
              ),
            if (entry.isDirectory)
              const ExcludeSemantics(child: Icon(Icons.chevron_right)),
          ],
        ),
        onTap: !entry.isDirectory || !entry.allows('listChildren') || isBusy
            ? null
            : onOpenFolder,
      ),
    );
  }

  String _semanticLabel(
    AppLocalizations l10n,
    FileEntry entry,
    String? subtitle,
  ) {
    final baseLabel = entry.isDirectory
        ? l10n.filesFolderSemantic(entry.name)
        : l10n.filesFileSemantic(entry.name);
    if (subtitle == null || subtitle.trim().isEmpty) {
      return baseLabel;
    }
    return '$baseLabel. $subtitle';
  }

  String? _subtitle(BuildContext context, FileEntry entry) {
    if (entry.modifiedAt == null && entry.sizeInBytes == null) {
      return null;
    }

    final parts = <String>[];
    if (entry.modifiedAt != null) {
      parts.add(
        DateFormat.yMMMd(
          Localizations.localeOf(context).toLanguageTag(),
        ).add_Hm().format(entry.modifiedAt!.toLocal()),
      );
    }
    if (entry.sizeInBytes != null) {
      parts.add(_formatSize(entry.sizeInBytes!));
    }
    return parts.join(' • ');
  }

  String _formatSize(int sizeInBytes) {
    const units = <String>['B', 'KB', 'MB', 'GB', 'TB'];
    var size = sizeInBytes.toDouble();
    var unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    final formatted = size >= 10 || unitIndex == 0
        ? size.toStringAsFixed(0)
        : size.toStringAsFixed(1);
    return '$formatted ${units[unitIndex]}';
  }
}

String _filesScopeLabel(
  AppLocalizations l10n,
  String? scope,
  MemberSpaceAccessSnapshot? access,
) {
  if (scope == null) return l10n.filesPrivateSpace;
  final name = access?.displayNameFor(scope);
  if (name == null) return l10n.filesUnavailableSpace;
  final ordinal = access?.displayOrdinalFor(scope);
  return ordinal == null ? name : l10n.filesSpaceOrdinal(name, ordinal);
}
