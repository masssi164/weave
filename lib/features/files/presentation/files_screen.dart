import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class FilesScreen extends ConsumerWidget {
  const FilesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncFiles = ref.watch(filesProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.filesScreenTitle)),
        ...switch (asyncFiles) {
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
                  ref.invalidate(filesProvider);
                },
              ),
            ),
          ],
          AsyncData(:final value) => _buildStateSlivers(
            context,
            ref,
            l10n,
            value,
          ),
        },
      ],
    );
  }

  List<Widget> _buildStateSlivers(
    BuildContext context,
    WidgetRef ref,
    AppLocalizations l10n,
    FilesViewState state,
  ) {
    final slivers = <Widget>[
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
        sliver: SliverToBoxAdapter(child: _ConnectionCard(state: state)),
      ),
    ];

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
              ),
            ),
          ),
        );
      }
    }

    slivers.add(_buildContentSliver(context, ref, l10n, state));
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
            guidance: connectionState.message ?? l10n.filesMisconfiguredMessage,
            icon: Icons.settings_outlined,
          ),
        );
      case FilesConnectionStatus.disconnected:
        return _fillStateSliver(
          child: EmptyState(
            message: l10n.filesDisconnectedTitle,
            guidance:
                state.directoryFailure?.message ??
                l10n.filesDisconnectedMessage,
            icon: Icons.cloud_off_outlined,
            actionLabel: l10n.filesConnectButton,
            onAction: state.isBusy
                ? null
                : () {
                    ref.read(filesProvider.notifier).connect();
                  },
          ),
        );
      case FilesConnectionStatus.invalid:
        return _fillStateSliver(
          child: ErrorState(
            message: l10n.filesSessionExpiredTitle,
            guidance:
                connectionState.message ?? l10n.filesInvalidSessionMessage,
            retryLabel: l10n.filesReconnectButton,
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
        if (state.directoryFailure != null) {
          return _fillStateSliver(
            child: ErrorState(
              message: l10n.filesLoadErrorTitle,
              guidance: state.directoryFailure!.message,
              retryLabel: l10n.retryButton,
              onRetry: state.isBusy
                  ? null
                  : () {
                      ref.read(filesProvider.notifier).refresh();
                    },
            ),
          );
        }
        final listing = state.directoryListing;
        if (listing == null || listing.entries.isEmpty) {
          return _fillStateSliver(
            child: EmptyState(
              message: l10n.filesEmptyMessage,
              guidance: l10n.filesEmptyGuidance,
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
              _DirectorySummary(listing: listing),
              const SizedBox(height: 12),
              ...List<Widget>.generate(listing.entries.length * 2 - 1, (index) {
                if (index.isOdd) {
                  return const Divider(height: 1);
                }

                final entryIndex = index ~/ 2;
                final entry = listing.entries[entryIndex];
                return _FileEntryTile(entry: entry, isBusy: state.isBusy);
              }),
            ]),
          ),
        );
    }
  }

  Widget _fillStateSliver({required Widget child}) {
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
      sliver: SliverFillRemaining(hasScrollBody: true, child: child),
    );
  }
}

class _DirectoryToolbar extends ConsumerWidget {
  const _DirectoryToolbar({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final listing = state.directoryListing;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PathBreadcrumbs(path: state.currentPath, isBusy: state.isBusy),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(state.currentPath, style: theme.textTheme.titleMedium),
            if (!(listing?.isRoot ?? true))
              AccessibleButton(
                outlined: true,
                onPressed: state.isBusy
                    ? null
                    : () {
                        ref.read(filesProvider.notifier).goUp();
                      },
                semanticLabel: l10n.filesOpenParentSemantic,
                child: Text(l10n.filesUpButton),
              ),
            AccessibleButton(
              outlined: true,
              onPressed: state.isBusy
                  ? null
                  : () {
                      ref.read(filesProvider.notifier).refresh();
                    },
              semanticLabel: l10n.filesRefreshCurrentFolderSemantic,
              child: Text(l10n.filesRefreshButton),
            ),
            AccessibleButton(
              outlined: true,
              onPressed: state.isBusy
                  ? null
                  : () async {
                      final folderName = await showDialog<String>(
                        context: context,
                        builder: (context) => const _CreateFolderDialog(),
                      );
                      if (folderName == null) {
                        return;
                      }
                      ref.read(filesProvider.notifier).createFolder(folderName);
                    },
              semanticLabel: l10n.filesCreateFolderCurrentFolderSemantic,
              child: Text(l10n.filesCreateFolderButton),
            ),
            AccessibleButton(
              onPressed: state.isBusy
                  ? null
                  : () {
                      ref.read(filesProvider.notifier).pickAndUpload();
                    },
              semanticLabel: l10n.filesUploadCurrentFolderSemantic,
              child: Text(l10n.filesUploadButton),
            ),
          ],
        ),
      ],
    );
  }
}

class _EntryActionStatusCard extends StatelessWidget {
  const _EntryActionStatusCard({required this.actionStatus});

  final FilesEntryActionStatus actionStatus;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final entryName = actionStatus.entryName;
    final message = switch (actionStatus.phase) {
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
      FilesEntryActionPhase.failed =>
        actionStatus.failure?.message ?? l10n.filesEntryActionFailedMessage,
    };
    final icon = switch (actionStatus.phase) {
      FilesEntryActionPhase.createdFolder => Icons.check_circle_outline,
      FilesEntryActionPhase.deletedEntry => Icons.check_circle_outline,
      FilesEntryActionPhase.failed => Icons.error_outline,
      FilesEntryActionPhase.idle => Icons.info_outline,
      _ => Icons.sync_outlined,
    };

    return Semantics(
      liveRegion: actionStatus.phase != FilesEntryActionPhase.idle,
      label: message,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              ExcludeSemantics(child: Icon(icon)),
              const SizedBox(width: 12),
              Expanded(child: Text(message, style: theme.textTheme.bodyMedium)),
              if (actionStatus.phase == FilesEntryActionPhase.creatingFolder ||
                  actionStatus.phase == FilesEntryActionPhase.deletingEntry)
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
        uploadStatus.failure?.message ??
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
      FilesUploadPhase.uploading when fileName != null =>
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
  const _PathBreadcrumbs({required this.path, required this.isBusy});

  final String path;
  final bool isBusy;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final segments = path.split('/')..removeWhere((segment) => segment.isEmpty);
    final crumbs = <Widget>[
      _BreadcrumbChip(
        label: l10n.filesRootBreadcrumb,
        semanticLabel: path == '/'
            ? l10n.filesCurrentFolderSemantic(l10n.filesRootBreadcrumb)
            : l10n.filesOpenFolderSemantic(l10n.filesRootBreadcrumb),
        isCurrent: path == '/',
        onPressed: path == '/' || isBusy
            ? null
            : () {
                ref.read(filesProvider.notifier).openDirectory('/');
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
            label: segments[index],
            semanticLabel: isCurrent
                ? l10n.filesCurrentFolderSemantic(segments[index])
                : l10n.filesOpenFolderSemantic(segments[index]),
            isCurrent: isCurrent,
            onPressed: isCurrent || isBusy
                ? null
                : () {
                    ref.read(filesProvider.notifier).openDirectory(crumbPath);
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

class _BreadcrumbChip extends StatelessWidget {
  const _BreadcrumbChip({
    required this.label,
    required this.semanticLabel,
    required this.isCurrent,
    required this.onPressed,
  });

  final String label;
  final String semanticLabel;
  final bool isCurrent;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
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

class _ConnectionCard extends ConsumerWidget {
  const _ConnectionCard({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final connectionState = state.connectionState;
    final description = switch (connectionState.status) {
      FilesConnectionStatus.connected => l10n.filesConnectionConnected(
        connectionState.accountLabel ?? l10n.filesNextcloudTitle,
      ),
      FilesConnectionStatus.invalid => l10n.filesConnectionInvalid,
      FilesConnectionStatus.disconnected => l10n.filesConnectionDisconnected,
      FilesConnectionStatus.misconfigured => l10n.filesConnectionMisconfigured,
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.filesNextcloudTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(description, style: theme.textTheme.bodyMedium),
            if (connectionState.baseUrl != null) ...[
              const SizedBox(height: 8),
              Text(
                connectionState.baseUrl.toString(),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                if (connectionState.status != FilesConnectionStatus.connected)
                  AccessibleButton(
                    onPressed: state.isBusy
                        ? null
                        : () {
                            ref.read(filesProvider.notifier).connect();
                          },
                    semanticLabel:
                        connectionState.status == FilesConnectionStatus.invalid
                        ? l10n.filesReconnectButton
                        : l10n.filesConnectButton,
                    child: Text(
                      connectionState.status == FilesConnectionStatus.invalid
                          ? l10n.filesReconnectButton
                          : l10n.filesConnectButton,
                    ),
                  ),
                if (connectionState.status == FilesConnectionStatus.connected ||
                    connectionState.status == FilesConnectionStatus.invalid)
                  AccessibleButton(
                    outlined: true,
                    onPressed: state.isBusy
                        ? null
                        : () {
                            ref.read(filesProvider.notifier).disconnect();
                          },
                    semanticLabel: l10n.filesDisconnectButton,
                    child: Text(l10n.filesDisconnectButton),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _CreateFolderDialog extends StatefulWidget {
  const _CreateFolderDialog();

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
      title: Text(l10n.filesCreateFolderDialogTitle),
      content: TextField(
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
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
          },
          child: Text(l10n.filesCancelButton),
        ),
        FilledButton(
          onPressed: name.isEmpty
              ? null
              : () {
                  Navigator.of(context).pop(name);
                },
          child: Text(l10n.filesCreateFolderConfirmButton),
        ),
      ],
    );
  }
}

class _DeleteEntryDialog extends StatelessWidget {
  const _DeleteEntryDialog({required this.entry});

  final FileEntry entry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return AlertDialog(
      title: Text(l10n.filesDeleteEntryDialogTitle(entry.name)),
      content: Text(l10n.filesDeleteEntryDialogMessage),
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop(false);
          },
          child: Text(l10n.filesCancelButton),
        ),
        FilledButton.tonalIcon(
          onPressed: () {
            Navigator.of(context).pop(true);
          },
          icon: const Icon(Icons.delete_outline),
          label: Text(l10n.filesDeleteButton),
        ),
      ],
    );
  }
}

class _FileEntryTile extends ConsumerWidget {
  const _FileEntryTile({required this.entry, required this.isBusy});

  static final DateFormat _modifiedDateTimeFormat = DateFormat.yMMMd().add_Hm();

  final FileEntry entry;
  final bool isBusy;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final subtitle = _subtitle(context, entry);
    final l10n = AppLocalizations.of(context);
    return Semantics(
      container: true,
      button: entry.isDirectory,
      label: entry.isDirectory
          ? l10n.filesFolderSemantic(entry.name)
          : l10n.filesFileSemantic(entry.name),
      child: ListTile(
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
            IconButton(
              tooltip: l10n.filesDeleteEntrySemantic(entry.name),
              onPressed: isBusy
                  ? null
                  : () async {
                      final confirmed = await showDialog<bool>(
                        context: context,
                        builder: (context) => _DeleteEntryDialog(entry: entry),
                      );
                      if (confirmed != true) {
                        return;
                      }
                      ref.read(filesProvider.notifier).deleteEntry(entry);
                    },
              icon: const Icon(Icons.delete_outline),
            ),
            if (entry.isDirectory)
              const ExcludeSemantics(child: Icon(Icons.chevron_right)),
          ],
        ),
        onTap: !entry.isDirectory || isBusy
            ? null
            : () {
                ref.read(filesProvider.notifier).openDirectory(entry.path);
              },
      ),
    );
  }

  String? _subtitle(BuildContext context, FileEntry entry) {
    if (entry.modifiedAt == null && entry.sizeInBytes == null) {
      return null;
    }

    final parts = <String>[];
    if (entry.modifiedAt != null) {
      parts.add(_modifiedDateTimeFormat.format(entry.modifiedAt!.toLocal()));
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
