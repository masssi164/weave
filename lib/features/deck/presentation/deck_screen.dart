import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/deck/providers/deck_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The Deck feature screen.
class DeckScreen extends ConsumerWidget {
  const DeckScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncBoards = ref.watch(deckProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.deckScreenTitle)),
        SliverFillRemaining(
          hasScrollBody: false,
          child: asyncBoards.when(
            loading: () => LoadingState(message: l10n.loadingLabel),
            error: (error, _) => ErrorState(
              message: l10n.errorStateLabel,
              retryLabel: l10n.retryButton,
              onRetry: () => ref.invalidate(deckProvider),
            ),
            data: (boards) => boards.isEmpty
                ? EmptyState(
                    message: l10n.deckEmptyMessage,
                    icon: Icons.dashboard_outlined,
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ],
    );
  }
}
