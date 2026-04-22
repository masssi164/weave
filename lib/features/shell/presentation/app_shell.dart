import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The main application shell rendered by [StatefulShellRoute].
///
/// Renders a [Scaffold] with a Material 3 [NavigationBar] at the bottom.
/// The [navigationShell] is provided by GoRouter and manages the active
/// branch's widget tree via an [IndexedStack] internally.
class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.navigationShell});

  /// The navigation shell created by [StatefulShellRoute.indexedStack].
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final showRecentRooms = navigationShell.currentIndex != 0;

    return Scaffold(
      body: Column(
        children: [
          if (showRecentRooms)
            _ShellRecentRoomsCard(navigationShell: navigationShell, ref: ref),
          Expanded(child: navigationShell),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
        destinations: [
          NavigationDestination(
            icon: Icon(
              Icons.chat_bubble_outline,
              semanticLabel: l10n.semanticChatIcon,
            ),
            selectedIcon: Icon(
              Icons.chat_bubble,
              semanticLabel: l10n.semanticChatIcon,
            ),
            label: l10n.navChat,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.folder_outlined,
              semanticLabel: l10n.semanticFilesIcon,
            ),
            selectedIcon: Icon(
              Icons.folder,
              semanticLabel: l10n.semanticFilesIcon,
            ),
            label: l10n.navFiles,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.settings_outlined,
              semanticLabel: l10n.semanticSettingsIcon,
            ),
            selectedIcon: Icon(
              Icons.settings,
              semanticLabel: l10n.semanticSettingsIcon,
            ),
            label: l10n.navSettings,
          ),
        ],
      ),
    );
  }
}

class _ShellRecentRoomsCard extends StatelessWidget {
  const _ShellRecentRoomsCard({
    required this.navigationShell,
    required this.ref,
  });

  final StatefulNavigationShell navigationShell;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(chatProvider);

    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.shellRecentRoomsTitle,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  l10n.shellRecentRoomsDescription,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 16),
                switch (state.phase) {
                  ChatViewPhase.loading => _ShellCardStateWrapper(
                    child: LoadingState(message: l10n.shellRecentRoomsLoading),
                  ),
                  ChatViewPhase.connecting => _ShellCardStateWrapper(
                    child: LoadingState(message: l10n.chatConnectingLabel),
                  ),
                  ChatViewPhase.empty => _ShellCardStateWrapper(
                    child: EmptyState(
                      message: l10n.shellRecentRoomsEmpty,
                      icon: Icons.history,
                    ),
                  ),
                  ChatViewPhase.error ||
                  ChatViewPhase.unsupported => _ShellCardStateWrapper(
                    child: ErrorState(
                      message: _shellErrorMessage(l10n, state.failure!),
                      retryLabel: l10n.retryButton,
                      onRetry: () => ref.read(chatProvider.notifier).retry(),
                    ),
                  ),
                  ChatViewPhase.content => _ShellRecentRoomList(
                    conversations: state.conversations
                        .take(3)
                        .toList(growable: false),
                    onOpenConversation: (conversation) =>
                        _openConversation(context, conversation),
                  ),
                },
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _openConversation(
    BuildContext context,
    ChatConversation conversation,
  ) async {
    navigationShell.goBranch(0);
    await Navigator.of(context, rootNavigator: true).push(
      MaterialPageRoute<void>(
        builder: (context) => ChatRoomScreen(conversation: conversation),
      ),
    );
    await ref.read(chatProvider.notifier).retry();
  }

  String _shellErrorMessage(AppLocalizations l10n, ChatFailure failure) {
    return switch (failure.type) {
      ChatFailureType.cancelled ||
      ChatFailureType.sessionRequired ||
      ChatFailureType.unsupportedConfiguration ||
      ChatFailureType.unsupportedPlatform => l10n.shellRecentRoomsUnavailable,
      _ => failure.message,
    };
  }
}

class _ShellCardStateWrapper extends StatelessWidget {
  const _ShellCardStateWrapper({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SizedBox(height: 180, child: child);
  }
}

class _ShellRecentRoomList extends StatelessWidget {
  const _ShellRecentRoomList({
    required this.conversations,
    required this.onOpenConversation,
  });

  final List<ChatConversation> conversations;
  final ValueChanged<ChatConversation> onOpenConversation;

  @override
  Widget build(BuildContext context) {
    if (conversations.isEmpty) {
      return _ShellCardStateWrapper(
        child: EmptyState(
          message: AppLocalizations.of(context).shellRecentRoomsEmpty,
          icon: Icons.history,
        ),
      );
    }

    return Column(
      children: [
        for (var index = 0; index < conversations.length; index++) ...[
          _ShellRecentRoomTile(
            conversation: conversations[index],
            onTap: () => onOpenConversation(conversations[index]),
          ),
          if (index < conversations.length - 1) const Divider(height: 1),
        ],
      ],
    );
  }
}

class _ShellRecentRoomTile extends StatelessWidget {
  const _ShellRecentRoomTile({required this.conversation, required this.onTap});

  final ChatConversation conversation;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final preview = switch (conversation.previewType) {
      ChatConversationPreviewType.none => l10n.chatConversationNoPreview,
      ChatConversationPreviewType.text =>
        conversation.previewText ?? l10n.chatConversationNoPreview,
      ChatConversationPreviewType.encrypted =>
        l10n.chatConversationEncryptedPreview,
      ChatConversationPreviewType.unsupported =>
        l10n.chatConversationUnsupportedPreview,
    };
    final recency = conversation.lastActivityAt == null
        ? l10n.shellRecentRoomsNoActivity
        : MaterialLocalizations.of(
            context,
          ).formatShortDate(conversation.lastActivityAt!.toLocal());
    final semanticsLabel = l10n.shellRecentRoomsItemSemantic(
      conversation.title,
      preview,
      recency,
    );

    return Semantics(
      button: true,
      label: semanticsLabel,
      child: ListTile(
        onTap: onTap,
        contentPadding: EdgeInsets.zero,
        leading: CircleAvatar(
          child: Icon(
            conversation.isDirectMessage
                ? Icons.person_outline
                : Icons.chat_bubble_outline,
          ),
        ),
        title: Text(conversation.title),
        subtitle: Text(
          preview,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              recency,
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            if (conversation.unreadCount > 0)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    child: Text(
                      conversation.unreadCount.toString(),
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onPrimary,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
