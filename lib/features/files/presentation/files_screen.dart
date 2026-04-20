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
            _fillStateSliver(child: LoadingState(message: l10n.loadingLabel)),
          ],
          AsyncError() => <Widget>[
            _fillStateSliver(
              child: ErrorState(
                message: l10n.errorStateLabel,
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
            message: connectionState.message ?? l10n.filesMisconfiguredMessage,
            icon: Icons.settings_outlined,
          ),
        );
      case FilesConnectionStatus.disconnected:
        return _fillStateSliver(
          child: EmptyState(
            message:
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
            message: connectionState.message ?? l10n.filesInvalidSessionMessage,
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
            child: LoadingState(message: l10n.loadingLabel),
          );
        }
        if (state.directoryFailure != null) {
          return _fillStateSliver(
            child: ErrorState(
              message: state.directoryFailure!.message,
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
      sliver: SliverFillRemaining(hasScrollBody: false, child: child),
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
          ],
        ),
      ],
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
  const _BreadcrumbChip({required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return ActionChip(label: Text(label), onPressed: onPressed);
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

class _FileEntryTile extends ConsumerWidget {
  const _FileEntryTile({required this.entry, required this.isBusy});

  static final DateFormat _modifiedDateTimeFormat = DateFormat.yMMMd().add_Hm();

  final FileEntry entry;
  final bool isBusy;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final subtitle = _subtitle(context, entry);
    final l10n = AppLocalizations.of(context);
    return MergeSemantics(
      child: Semantics(
        container: true,
        button: entry.isDirectory,
        label: entry.isDirectory
            ? l10n.filesFolderSemantic(entry.name)
            : l10n.filesFileSemantic(entry.name),
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 4,
            vertical: 4,
          ),
          leading: ExcludeSemantics(
            child: Icon(
              entry.isDirectory
                  ? Icons.folder_outlined
                  : Icons.insert_drive_file_outlined,
            ),
          ),
          title: Text(entry.name),
          subtitle: subtitle == null ? null : Text(subtitle),
          trailing: entry.isDirectory
              ? const ExcludeSemantics(child: Icon(Icons.chevron_right))
              : null,
          onTap: !entry.isDirectory || isBusy
              ? null
              : () {
                  ref.read(filesProvider.notifier).openDirectory(entry.path);
                },
        ),
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
