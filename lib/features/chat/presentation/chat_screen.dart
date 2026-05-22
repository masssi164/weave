import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_provider.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_banner.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
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
    final firstRunStatus = ref.watch(firstRunStatusProvider);
    final matrixProvisioning = switch (firstRunStatus) {
      AsyncData(value: final status) => status?.moduleProvisioning.matrix,
      _ => null,
    };
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
        if (matrixProvisioning != null && !matrixProvisioning.isReady)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            sliver: SliverToBoxAdapter(
              child: _ChatProvisioningNotice(
                status: matrixProvisioning,
                onRefresh: () {
                  ref.invalidate(firstRunStatusProvider);
                  ref.read(chatProvider.notifier).retry();
                },
              ),
            ),
          ),
        if (state.staleFailure != null)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            sliver: SliverToBoxAdapter(
              child: _ChatStaleNotice(
                failure: state.staleFailure!,
                onRefresh: () => ref.read(chatProvider.notifier).retry(),
              ),
            ),
          ),
        if (state.isRefreshing)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            sliver: SliverToBoxAdapter(
              child: LinearProgressIndicator(
                semanticsLabel: l10n.chatRefreshingRoomsLabel,
              ),
            ),
          ),
        switch (state.phase) {
          ChatViewPhase.loading => SliverFillRemaining(
            hasScrollBody: true,
            child: LoadingState(
              message: l10n.chatLoadingLabel,
              hint: l10n.chatLoadingHint,
              icon: Icons.chat_bubble_outline,
            ),
          ),
          ChatViewPhase.connecting => SliverFillRemaining(
            hasScrollBody: true,
            child: LoadingState(
              message: l10n.chatConnectingLabel,
              hint: l10n.chatConnectingHint,
              icon: Icons.sync_outlined,
            ),
          ),
          ChatViewPhase.empty => SliverFillRemaining(
            hasScrollBody: true,
            child: EmptyState(
              message: l10n.chatEmptyMessage,
              guidance: l10n.chatEmptyGuidance,
              icon: Icons.chat_bubble_outline,
            ),
          ),
          ChatViewPhase.error ||
          ChatViewPhase.unsupported => SliverFillRemaining(
            hasScrollBody: true,
            child: _ChatErrorState(
              failure: state.failure!,
              onRetry: () => ref.read(chatProvider.notifier).retry(),
              onConnect: () => ref.read(chatProvider.notifier).connect(),
            ),
          ),
          ChatViewPhase.content => _ChatOverviewSliver(
            conversations: state.conversations,
            onOpenConversation: (conversation) async {
              final router = GoRouter.maybeOf(context);
              if (router != null) {
                await context.push(
                  AppRoutes.chatRoom(conversation.id),
                  extra: conversation,
                );
              } else {
                await Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (context) =>
                        ChatRoomScreen(conversation: conversation),
                  ),
                );
              }
              await ref.read(chatProvider.notifier).retry();
            },
          ),
        },
      ],
    );
  }
}

class _ChatStaleNotice extends StatelessWidget {
  const _ChatStaleNotice({required this.failure, required this.onRefresh});

  final ChatFailure failure;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

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
                      l10n.chatStaleRoomsTitle,
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onTertiaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                l10n.chatStaleRoomsGuidance,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onTertiaryContainer,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                failure.message,
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
                  label: Text(l10n.chatStaleRoomsRetryButton),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChatProvisioningNotice extends StatelessWidget {
  const _ChatProvisioningNotice({
    required this.status,
    required this.onRefresh,
  });

  final FirstRunModuleStatus status;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final title = switch (status.state) {
      FirstRunProvisioningState.pending => l10n.chatProvisioningPendingTitle,
      FirstRunProvisioningState.degraded => l10n.chatProvisioningDegradedTitle,
      FirstRunProvisioningState.notConfigured ||
      FirstRunProvisioningState.failed =>
        l10n.chatProvisioningActionNeededTitle,
      FirstRunProvisioningState.ready => l10n.chatProvisioningReadyTitle,
    };

    return Semantics(
      container: true,
      liveRegion: true,
      label:
          '$title. ${status.message}${status.action == null ? '' : '. ${status.action}'}',
      child: ExcludeSemantics(
        child: Card(
          elevation: 0,
          color: theme.colorScheme.secondaryContainer,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(color: theme.colorScheme.secondary),
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
                      Icons.info_outline,
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        title,
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: theme.colorScheme.onSecondaryContainer,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  status.message,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSecondaryContainer,
                  ),
                ),
                if (status.action != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    status.action!,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: TextButton.icon(
                    onPressed: onRefresh,
                    icon: const Icon(Icons.refresh),
                    label: Text(l10n.chatProvisioningRetryButton),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
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
      message: l10n.chatErrorTitle,
      guidance: failure.message,
      retryLabel: hasAction
          ? (usesConnectAction ? l10n.chatConnectButton : l10n.retryButton)
          : null,
      onRetry: hasAction ? (usesConnectAction ? onConnect : onRetry) : null,
    );
  }
}

class _ChatOverviewSliver extends StatelessWidget {
  const _ChatOverviewSliver({
    required this.conversations,
    required this.onOpenConversation,
  });

  final List<ChatConversation> conversations;
  final ValueChanged<ChatConversation> onOpenConversation;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final overview = ChatOverview.fromConversations(conversations);

    return SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              header: true,
              child: Text(
                l10n.chatOverviewTitle,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.chatOverviewDescription,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 20),
            const _ChatContextCard(),
            const SizedBox(height: 20),
            _ChatOverviewSection(
              title: l10n.chatFavoritesSectionTitle,
              description: l10n.chatFavoritesSectionDescription,
              emptyMessage: l10n.chatFavoritesSectionEmpty,
              icon: Icons.star_outline,
              conversations: overview.favorites,
              onOpenConversation: onOpenConversation,
            ),
            const SizedBox(height: 20),
            _ChatOverviewSection(
              title: l10n.chatPersonalMessagesSectionTitle,
              description: l10n.chatPersonalMessagesSectionDescription,
              emptyMessage: l10n.chatPersonalMessagesSectionEmpty,
              icon: Icons.person_outline,
              conversations: overview.personalMessages,
              onOpenConversation: onOpenConversation,
            ),
            const SizedBox(height: 20),
            _ChatOverviewSection(
              title: l10n.chatChannelsSectionTitle,
              description: l10n.chatChannelsSectionDescription,
              emptyMessage: l10n.chatChannelsSectionEmpty,
              icon: Icons.tag,
              conversations: overview.channels,
              onOpenConversation: onOpenConversation,
            ),
            const SizedBox(height: 20),
            _ChatOverviewSection(
              title: l10n.chatAiChatsSectionTitle,
              description: l10n.chatAiChatsSectionDescription,
              emptyMessage: l10n.chatAiChatsSectionEmpty,
              icon: Icons.smart_toy_outlined,
              conversations: overview.aiChats,
              onOpenConversation: onOpenConversation,
            ),
          ],
        ),
      ),
    );
  }
}

class _ChatContextCard extends StatelessWidget {
  const _ChatContextCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final hints = <({IconData icon, String title, String description})>[
      (
        icon: Icons.forum_outlined,
        title: l10n.chatContextChannelHintTitle,
        description: l10n.chatContextChannelHintDescription,
      ),
      (
        icon: Icons.fact_check_outlined,
        title: l10n.chatContextEvidenceHintTitle,
        description: l10n.chatContextEvidenceHintDescription,
      ),
      (
        icon: Icons.psychology_alt_outlined,
        title: l10n.chatContextAgentHintTitle,
        description: l10n.chatContextAgentHintDescription,
      ),
    ];

    return Semantics(
      container: true,
      explicitChildNodes: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.primaryContainer.withValues(alpha: 0.24),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(
            color: theme.colorScheme.primary.withValues(alpha: 0.42),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.hub_outlined, color: theme.colorScheme.primary),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          header: true,
                          child: Text(
                            l10n.chatContextCardTitle,
                            style: theme.textTheme.titleLarge,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          l10n.chatContextCardDescription,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              ...hints.map(
                (hint) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _ChatContextHintTile(
                    icon: hint.icon,
                    title: hint.title,
                    description: hint.description,
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                l10n.chatContextCardPolicy,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChatContextHintTile extends StatelessWidget {
  const _ChatContextHintTile({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return MergeSemantics(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleSmall),
                const SizedBox(height: 2),
                Text(description, style: theme.textTheme.bodySmall),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChatOverviewSection extends StatelessWidget {
  const _ChatOverviewSection({
    required this.title,
    required this.description,
    required this.emptyMessage,
    required this.icon,
    required this.conversations,
    required this.onOpenConversation,
  });

  final String title;
  final String description;
  final String emptyMessage;
  final IconData icon;
  final List<ChatConversation> conversations;
  final ValueChanged<ChatConversation> onOpenConversation;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      explicitChildNodes: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: theme.colorScheme.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Semantics(
                  header: true,
                  child: Text(title, style: theme.textTheme.titleLarge),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            description,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          if (conversations.isEmpty)
            _ChatOverviewEmptyCard(message: emptyMessage, icon: icon)
          else
            ...conversations.map(
              (conversation) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _ConversationTile(
                  conversation: conversation,
                  onTap: () => onOpenConversation(conversation),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ChatOverviewEmptyCard extends StatelessWidget {
  const _ChatOverviewEmptyCard({required this.message, required this.icon});

  final String message;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                message,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      ),
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
