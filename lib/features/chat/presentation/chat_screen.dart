import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/chat/providers/chat_provider.dart';
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
    final asyncMessages = ref.watch(chatProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.chatScreenTitle)),
        SliverFillRemaining(
          hasScrollBody: false,
          child: asyncMessages.when(
            loading: () => LoadingState(message: l10n.loadingLabel),
            error: (error, _) => ErrorState(
              message: l10n.errorStateLabel,
              retryLabel: l10n.retryButton,
              onRetry: () => ref.invalidate(chatProvider),
            ),
            data: (messages) => messages.isEmpty
                ? EmptyState(
                    message: l10n.chatEmptyMessage,
                    icon: Icons.chat_bubble_outline,
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ],
    );
  }
}
