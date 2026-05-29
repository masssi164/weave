import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ShellRecentActivity extends ConsumerWidget {
  const ShellRecentActivity({super.key});

  static const _maxItemsPerSection = 2;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final chatState = ref.watch(chatProvider);
    final filesState = ref.watch(filesProvider);

    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
        child: Semantics(
          container: true,
          label: l10n.shellRecentActivitySemanticLabel,
          child: Card(
            margin: EdgeInsets.zero,
            elevation: 0,
            color: theme.colorScheme.surfaceContainerHighest,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Row(
                children: [
                  Icon(
                    Icons.history,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    l10n.shellRecentActivityTitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          _ActivityGroup(
                            label: l10n.shellRecentRoomsTitle,
                            child: _ChatActivityContent(
                              state: chatState,
                              onRetry: () =>
                                  ref.read(chatProvider.notifier).retry(),
                            ),
                          ),
                          const SizedBox(width: 16),
                          _ActivityGroup(
                            label: l10n.shellRecentFilesTitle,
                            child: _FilesActivityContent(
                              state: filesState,
                              onRetry: () => ref.invalidate(filesProvider),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ActivityGroup extends StatelessWidget {
  const _ActivityGroup({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          label,
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(width: 8),
        child,
      ],
    );
  }
}

class _ChatActivityContent extends StatelessWidget {
  const _ChatActivityContent({required this.state, required this.onRetry});

  final ChatUiState state;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return switch (state.phase) {
      ChatViewPhase.loading => _ActivityStatusPill(
        icon: Icons.sync_outlined,
        message: l10n.shellRecentRoomsLoading,
      ),
      ChatViewPhase.connecting => _ActivityStatusPill(
        icon: Icons.sync_outlined,
        message: l10n.chatConnectingLabel,
      ),
      ChatViewPhase.empty => _ActivityStatusPill(
        icon: Icons.chat_bubble_outline,
        message: l10n.shellRecentRoomsEmpty,
      ),
      ChatViewPhase.error || ChatViewPhase.unsupported => _ActivityStatusPill(
        icon: Icons.error_outline,
        message: _chatErrorLabel(l10n, state.failure!),
        actionLabel: l10n.retryButton,
        onAction: onRetry,
      ),
      ChatViewPhase.content => _RecentChatChips(
        conversations: _recentConversations(state.conversations),
      ),
    };
  }

  String _chatErrorLabel(AppLocalizations l10n, ChatFailure failure) {
    return switch (failure.type) {
      ChatFailureType.cancelled ||
      ChatFailureType.sessionRequired ||
      ChatFailureType.unsupportedConfiguration ||
      ChatFailureType.unsupportedPlatform => l10n.shellRecentRoomsUnavailable,
      _ => failure.message,
    };
  }

  List<ChatConversation> _recentConversations(
    List<ChatConversation> conversations,
  ) {
    return ChatOverview.fromConversations(conversations).activeConversations
        .take(ShellRecentActivity._maxItemsPerSection)
        .toList(growable: false);
  }
}

class _RecentChatChips extends StatelessWidget {
  const _RecentChatChips({required this.conversations});

  final List<ChatConversation> conversations;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    if (conversations.isEmpty) {
      return _ActivityStatusPill(
        icon: Icons.chat_bubble_outline,
        message: l10n.shellRecentRoomsEmpty,
      );
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var index = 0; index < conversations.length; index++) ...[
          _RecentChatChip(conversation: conversations[index]),
          if (index < conversations.length - 1) const SizedBox(width: 8),
        ],
      ],
    );
  }
}

class _RecentChatChip extends StatelessWidget {
  const _RecentChatChip({required this.conversation});

  final ChatConversation conversation;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final preview = switch (conversation.previewType) {
      ChatConversationPreviewType.none => l10n.chatConversationNoPreview,
      ChatConversationPreviewType.text =>
        conversation.previewText ?? l10n.chatConversationNoPreview,
      ChatConversationPreviewType.encrypted =>
        l10n.chatConversationEncryptedPreview,
      ChatConversationPreviewType.unsupported =>
        l10n.chatConversationUnsupportedPreview,
    };
    final recency =
        _formatRecency(context, conversation.lastActivityAt) ??
        l10n.shellRecentActivityUnknownRecency;
    final semanticLabel = l10n.shellRecentRoomItemSemantic(
      conversation.title,
      preview,
      recency,
    );

    return _ActivityChip(
      icon: conversation.isDirectMessage
          ? Icons.person_outline
          : Icons.chat_bubble_outline,
      label: conversation.title,
      semanticLabel: semanticLabel,
      onPressed: () =>
          context.go(AppRoutes.chatRoom(conversation.id), extra: conversation),
    );
  }
}

class _FilesActivityContent extends StatelessWidget {
  const _FilesActivityContent({required this.state, required this.onRetry});

  final AsyncValue<FilesViewState> state;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return switch (state) {
      AsyncLoading() => _ActivityStatusPill(
        icon: Icons.sync_outlined,
        message: l10n.shellRecentFilesLoading,
      ),
      AsyncError() => _ActivityStatusPill(
        icon: Icons.error_outline,
        message: l10n.shellRecentFilesError,
        actionLabel: l10n.retryButton,
        onAction: onRetry,
      ),
      AsyncData(:final value) => _FilesActivityData(state: value),
    };
  }
}

class _FilesActivityData extends StatelessWidget {
  const _FilesActivityData({required this.state});

  final FilesViewState state;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    switch (state.connectionState.status) {
      case FilesConnectionStatus.connected:
        final entries = _recentFiles(
          state.directoryListing?.entries ?? const [],
        );
        if (entries.isEmpty) {
          return _ActivityStatusPill(
            icon: Icons.folder_outlined,
            message: l10n.shellRecentFilesEmpty,
          );
        }
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var index = 0; index < entries.length; index++) ...[
              _RecentFileChip(entry: entries[index]),
              if (index < entries.length - 1) const SizedBox(width: 8),
            ],
          ],
        );
      case FilesConnectionStatus.disconnected:
      case FilesConnectionStatus.invalid:
      case FilesConnectionStatus.misconfigured:
        return _ActivityStatusPill(
          icon: Icons.folder_off_outlined,
          message: l10n.shellRecentFilesUnavailable,
        );
    }
  }

  List<FileEntry> _recentFiles(List<FileEntry> entries) {
    final sorted = entries.toList()
      ..sort((left, right) {
        final leftModified = left.modifiedAt;
        final rightModified = right.modifiedAt;
        if (leftModified == null && rightModified == null) {
          return left.name.compareTo(right.name);
        }
        if (leftModified == null) {
          return 1;
        }
        if (rightModified == null) {
          return -1;
        }
        return rightModified.compareTo(leftModified);
      });

    return sorted
        .take(ShellRecentActivity._maxItemsPerSection)
        .toList(growable: false);
  }
}

class _RecentFileChip extends StatelessWidget {
  const _RecentFileChip({required this.entry});

  final FileEntry entry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final recency =
        _formatRecency(context, entry.modifiedAt) ??
        l10n.shellRecentActivityUnknownRecency;
    final contextPath = entry.isDirectory
        ? entry.path
        : _parentPath(entry.path);
    final typeLabel = entry.isDirectory
        ? l10n.shellRecentFileFolderType
        : l10n.shellRecentFileFileType;
    final semanticLabel = l10n.shellRecentFileItemSemantic(
      typeLabel,
      entry.name,
      contextPath,
      recency,
    );

    return _ActivityChip(
      icon: entry.isDirectory
          ? Icons.folder_outlined
          : Icons.description_outlined,
      label: entry.name,
      semanticLabel: semanticLabel,
      onPressed: () => context.go(AppRoutes.filesLocation(contextPath)),
    );
  }

  String _parentPath(String path) {
    final normalized = path.startsWith('/') ? path : '/$path';
    final lastSlash = normalized.lastIndexOf('/');
    if (lastSlash <= 0) {
      return '/';
    }
    return normalized.substring(0, lastSlash);
  }
}

class _ActivityChip extends StatelessWidget {
  const _ActivityChip({
    required this.icon,
    required this.label,
    required this.semanticLabel,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final String semanticLabel;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: semanticLabel,
      onTap: onPressed,
      child: ExcludeSemantics(
        child: ActionChip(
          visualDensity: VisualDensity.compact,
          avatar: Icon(icon, size: 18),
          label: Text(label),
          onPressed: onPressed,
        ),
      ),
    );
  }
}

class _ActivityStatusPill extends StatelessWidget {
  const _ActivityStatusPill({
    required this.icon,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      liveRegion: true,
      child: Container(
        constraints: const BoxConstraints(minHeight: 32),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          border: Border.all(color: theme.colorScheme.outlineVariant),
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 18, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(width: 6),
            Text(
              message,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(width: 8),
              TextButton(
                style: TextButton.styleFrom(
                  minimumSize: const Size(44, 32),
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                onPressed: onAction,
                child: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

String? _formatRecency(BuildContext context, DateTime? timestamp) {
  if (timestamp == null) {
    return null;
  }

  final now = DateTime.now();
  final localTimestamp = timestamp.toLocal();
  final difference = now.difference(localTimestamp);

  if (difference.inMinutes < 0) {
    return MaterialLocalizations.of(context).formatShortDate(localTimestamp);
  }
  if (difference < const Duration(minutes: 1)) {
    return AppLocalizations.of(context).shellRecentActivityNow;
  }
  if (difference < const Duration(hours: 1)) {
    return AppLocalizations.of(
      context,
    ).shellRecentActivityMinutesAgo(difference.inMinutes);
  }
  if (_isSameDay(now, localTimestamp)) {
    return AppLocalizations.of(context).shellRecentActivityToday;
  }

  final yesterday = now.subtract(const Duration(days: 1));
  if (_isSameDay(yesterday, localTimestamp)) {
    return AppLocalizations.of(context).shellRecentActivityYesterday;
  }

  return MaterialLocalizations.of(context).formatShortDate(localTimestamp);
}

bool _isSameDay(DateTime left, DateTime right) {
  return left.year == right.year &&
      left.month == right.month &&
      left.day == right.day;
}
