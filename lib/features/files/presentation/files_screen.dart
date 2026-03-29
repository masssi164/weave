import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/files/providers/files_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The Files feature screen.
class FilesScreen extends ConsumerWidget {
  const FilesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncFiles = ref.watch(filesProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.filesScreenTitle)),
        SliverFillRemaining(
          hasScrollBody: false,
          child: asyncFiles.when(
            loading: () => LoadingState(message: l10n.loadingLabel),
            error: (error, _) => ErrorState(
              message: l10n.errorStateLabel,
              retryLabel: l10n.retryButton,
              onRetry: () => ref.invalidate(filesProvider),
            ),
            data: (files) => files.isEmpty
                ? EmptyState(
                    message: l10n.filesEmptyMessage,
                    icon: Icons.folder_outlined,
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ],
    );
  }
}
