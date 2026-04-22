import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_provider.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_banner.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The Chat feature screen.
///
/// Uses [CustomScrollView] with a [SliverAppBar] and shows loading,
/// empty, or error states via the shared core widgets.
class ChatScreen extends ConsumerWidget {
  const ChatScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(chatProvider);
    final securityState = ref.watch(chatSecurityProvider);
    final security = securityState.security;
    final showSecurityBanner =
        security != null &&
        security.isMatrixSignedIn &&
        ChatSecurityBanner.messageForSecurity(l10n, security) != null;

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.chatScreenTitle)),
        if (showSecurityBanner)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            sliver: SliverToBoxAdapter(
              child: ChatSecurityBanner(security: security),
            ),
          ),
        switch (state.phase) {
          ChatViewPhase.loading => SliverFillRemaining(
            hasScrollBody: false,
            child: LoadingState(
              message: l10n.chatLoadingLabel,
              hint: l10n.chatLoadingHint,
              icon: Icons.chat_bubble_outline,
            ),
          ),
          ChatViewPhase.connecting => SliverFillRemaining(
            hasScrollBody: false,
            child: LoadingState(
              message: l10n.chatConnectingLabel,
              hint: l10n.chatConnectingHint,
              icon: Icons.sync_outlined,
            ),
          ),
          ChatViewPhase.empty => SliverFillRemaining(
            hasScrollBody: false,
            child: EmptyState(
              message: l10n.chatEmptyMessage,
              icon: Icons.chat_bubble_outline,
            ),
          ),
          ChatViewPhase.error ||
          ChatViewPhase.unsupported => SliverFillRemaining(
            hasScrollBody: false,
            child: _ChatErrorState(
              failure: state.failure!,
              onRetry: () => ref.read(chatProvider.notifier).retry(),
              onConnect: () => ref.read(chatProvider.notifier).connect(),
            ),
          ),
          ChatViewPhase.content => SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            sliver: SliverList.separated(
              itemCount: state.conversations.length,
              itemBuilder: (context, index) => _ConversationTile(
                conversation: state.conversations[index],
                onTap: () async {
                  await Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (context) => ChatRoomScreen(
                        conversation: state.conversations[index],
                      ),
                    ),
                  );
                  await ref.read(chatProvider.notifier).retry();
                },
              ),
              separatorBuilder: (context, index) => const SizedBox(height: 12),
            ),
          ),
        },
      ],
    );
  }
}

class _ChatErrorState extends StatelessWidget {
  const _ChatErrorState({
    required this.failure,
    required this.onRetry,
    required this.onConnect,
  });

  final ChatFailure failure;
  final VoidCallback onRetry;
  final VoidCallback onConnect;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final usesConnectAction =
        failure.type == ChatFailureType.cancelled ||
        failure.type == ChatFailureType.sessionRequired ||
        failure.type == ChatFailureType.unsupportedConfiguration;
    final hasAction = failure.type != ChatFailureType.unsupportedPlatform;

    return ErrorState(
      message: failure.message,
      retryLabel: hasAction
          ? (usesConnectAction ? l10n.chatConnectButton : l10n.retryButton)
          : null,
      onRetry: hasAction ? (usesConnectAction ? onConnect : onRetry) : null,
    );
  }
}

class _ConversationTile extends StatelessWidget {
  const _ConversationTile({required this.conversation, required this.onTap});

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
    final timestamp = conversation.lastActivityAt == null
        ? null
        : MaterialLocalizations.of(
            context,
          ).formatShortDate(conversation.lastActivityAt!);
    final recencyLabel = conversation.lastActivityAt == null
        ? null
        : _conversationRecencyLabel(
            context,
            l10n,
            conversation.lastActivityAt!,
          );
    final unreadLabel = l10n.chatConversationUnreadCount(
      conversation.unreadCount,
    );
    final semanticsLabel = <String>[
      conversation.title,
      preview,
      if (recencyLabel != null) recencyLabel,
      if (timestamp != null) timestamp,
      unreadLabel,
      if (conversation.isInvite) l10n.chatConversationInviteLabel,
      if (conversation.isDirectMessage) l10n.chatConversationDirectMessageLabel,
    ].join('. ');

    return Semantics(
      container: true,
      label: semanticsLabel,
      child: ExcludeSemantics(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: ListTile(
              onTap: onTap,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              leading: CircleAvatar(
                child: Icon(
                  conversation.isDirectMessage
                      ? Icons.person_outline
                      : Icons.chat_bubble_outline,
                ),
              ),
              title: Text(
                conversation.title,
                style: theme.textTheme.titleMedium,
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  preview,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              trailing: _ConversationTrailing(
                timestamp: timestamp,
                recencyLabel: recencyLabel,
                unreadCount: conversation.unreadCount,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ConversationTrailing extends StatelessWidget {
  const _ConversationTrailing({
    required this.timestamp,
    required this.recencyLabel,
    required this.unreadCount,
  });

  final String? timestamp;
  final String? recencyLabel;
  final int unreadCount;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        if (timestamp != null)
          Text(
            timestamp!,
            style: theme.textTheme.labelMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        if (recencyLabel != null) ...[
          const SizedBox(height: 6),
          DecoratedBox(
            decoration: BoxDecoration(
              color: theme.colorScheme.secondaryContainer,
              borderRadius: BorderRadius.circular(999),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Text(
                recencyLabel!,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSecondaryContainer,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ],
        if (unreadCount > 0) ...[
          const SizedBox(height: 8),
          DecoratedBox(
            decoration: BoxDecoration(
              color: theme.colorScheme.primary,
              borderRadius: BorderRadius.circular(999),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              child: Text(
                unreadCount.toString(),
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onPrimary,
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

String? _conversationRecencyLabel(
  BuildContext context,
  AppLocalizations l10n,
  DateTime lastActivityAt,
) {
  final now = DateTime.now();
  final localActivity = lastActivityAt.toLocal();
  final difference = now.difference(localActivity);

  if (difference.inMinutes < 0) {
    return null;
  }
  if (difference < const Duration(hours: 1)) {
    return l10n.chatConversationRecentNow;
  }
  if (_isSameDay(now, localActivity)) {
    return l10n.chatConversationRecentToday;
  }

  final yesterday = now.subtract(const Duration(days: 1));
  if (_isSameDay(yesterday, localActivity)) {
    return l10n.chatConversationRecentYesterday;
  }

  if (difference < const Duration(days: 7)) {
    return l10n.chatConversationRecentThisWeek;
  }

  return null;
}

bool _isSameDay(DateTime left, DateTime right) {
  return left.year == right.year &&
      left.month == right.month &&
      left.day == right.day;
}
