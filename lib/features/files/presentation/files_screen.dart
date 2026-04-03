import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
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
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          sliver: SliverFillRemaining(
            hasScrollBody: false,
            child: asyncFiles.when(
              loading: () => LoadingState(message: l10n.loadingLabel),
              error: (error, _) => ErrorState(
                message: l10n.errorStateLabel,
                retryLabel: l10n.retryButton,
                onRetry: () {
                  ref.invalidate(filesProvider);
                },
              ),
              data: (state) => _FilesBody(state: state),
            ),
          ),
        ),
      ],
    );
  }
}

class _FilesBody extends ConsumerWidget {
  const _FilesBody({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _ConnectionCard(state: state),
        const SizedBox(height: 16),
        if (state.connectionState.status == FilesConnectionStatus.connected) ...[
          Row(
            children: [
              Expanded(
                child: Text(
                  state.currentPath,
                  style: theme.textTheme.titleMedium,
                ),
              ),
              if (!(state.directoryListing?.isRoot ?? true))
                AccessibleButton(
                  outlined: true,
                  onPressed: state.isBusy
                      ? null
                      : () {
                          ref.read(filesProvider.notifier).goUp();
                        },
                  semanticLabel: 'Open parent folder',
                  child: const Text('Up'),
                ),
              const SizedBox(width: 12),
              AccessibleButton(
                outlined: true,
                onPressed: state.isBusy
                    ? null
                    : () {
                        ref.read(filesProvider.notifier).refresh();
                      },
                semanticLabel: 'Refresh the current folder',
                child: const Text('Refresh'),
              ),
            ],
          ),
          const SizedBox(height: 16),
        ],
        Expanded(child: _buildContent(context, ref, l10n)),
      ],
    );
  }

  Widget _buildContent(
    BuildContext context,
    WidgetRef ref,
    AppLocalizations l10n,
  ) {
    final connectionState = state.connectionState;
    switch (connectionState.status) {
      case FilesConnectionStatus.misconfigured:
        return EmptyState(
          message:
              connectionState.message ?? 'Configure a Nextcloud URL before connecting files.',
          icon: Icons.settings_outlined,
        );
      case FilesConnectionStatus.disconnected:
        return EmptyState(
          message:
              state.directoryFailure?.message ??
              'Connect Nextcloud to browse your files.',
          icon: Icons.cloud_off_outlined,
          actionLabel: 'Connect Nextcloud',
          onAction: state.isBusy
              ? null
              : () {
                  ref.read(filesProvider.notifier).connect();
                },
        );
      case FilesConnectionStatus.invalid:
        return ErrorState(
          message:
              connectionState.message ?? 'Reconnect Nextcloud because the saved session is no longer valid.',
          retryLabel: 'Reconnect Nextcloud',
          onRetry: state.isBusy
              ? null
              : () {
                  ref.read(filesProvider.notifier).connect();
                },
        );
      case FilesConnectionStatus.connected:
        if (state.isBusy && state.directoryListing == null) {
          return LoadingState(message: l10n.loadingLabel);
        }
        if (state.directoryFailure != null) {
          return ErrorState(
            message: state.directoryFailure!.message,
            retryLabel: l10n.retryButton,
            onRetry: state.isBusy
                ? null
                : () {
                    ref.read(filesProvider.notifier).refresh();
                  },
          );
        }
        final listing = state.directoryListing;
        if (listing == null || listing.entries.isEmpty) {
          return EmptyState(
            message: l10n.filesEmptyMessage,
            icon: Icons.folder_outlined,
            actionLabel: 'Refresh',
            onAction: state.isBusy
                ? null
                : () {
                    ref.read(filesProvider.notifier).refresh();
                  },
          );
        }
        return ListView.separated(
          itemCount: listing.entries.length,
          separatorBuilder: (_, _) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final entry = listing.entries[index];
            return _FileEntryTile(entry: entry, isBusy: state.isBusy);
          },
        );
    }
  }
}

class _ConnectionCard extends ConsumerWidget {
  const _ConnectionCard({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final connectionState = state.connectionState;
    final description = switch (connectionState.status) {
      FilesConnectionStatus.connected =>
        'Connected as ${connectionState.accountLabel ?? 'your Nextcloud account'}',
      FilesConnectionStatus.invalid => 'The saved Nextcloud session needs attention.',
      FilesConnectionStatus.disconnected => 'No Nextcloud session is connected on this device.',
      FilesConnectionStatus.misconfigured =>
        'Server setup is incomplete for Nextcloud files.',
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Nextcloud', style: theme.textTheme.titleMedium),
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
                    semanticLabel: connectionState.status == FilesConnectionStatus.invalid
                        ? 'Reconnect Nextcloud'
                        : 'Connect Nextcloud',
                    child: Text(
                      connectionState.status == FilesConnectionStatus.invalid
                          ? 'Reconnect Nextcloud'
                          : 'Connect Nextcloud',
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
                    semanticLabel: 'Disconnect Nextcloud',
                    child: const Text('Disconnect'),
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

  final FileEntry entry;
  final bool isBusy;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final subtitle = _subtitle(context, entry);
    return MergeSemantics(
      child: Semantics(
        container: true,
        button: entry.isDirectory,
        label: '${entry.name}, ${entry.isDirectory ? 'folder' : 'file'}',
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
          leading: ExcludeSemantics(
            child: Icon(
              entry.isDirectory ? Icons.folder_outlined : Icons.insert_drive_file_outlined,
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
      parts.add(DateFormat.yMMMd().add_Hm().format(entry.modifiedAt!.toLocal()));
    }
    if (entry.sizeInBytes != null) {
      parts.add('${entry.sizeInBytes} B');
    }
    return parts.join(' • ');
  }
}
