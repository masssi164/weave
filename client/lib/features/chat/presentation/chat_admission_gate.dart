import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Keeps the native Matrix view unmounted until the User API confirms a Space.
class ChatAdmissionGate extends ConsumerWidget {
  const ChatAdmissionGate({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    ref.listen(workspaceCapabilitySnapshotProvider, (previous, next) {
      final wasReady = previous?.asData?.value.chat.isReady == true;
      final isReady = next.asData?.value.chat.isReady == true;
      if (wasReady && !isReady) {
        ref.read(chatProvider.notifier).clearForAccessLoss();
      } else if (!wasReady && isReady) {
        // Regrant must fetch current rooms before showing them again.
        ref.read(chatProvider.notifier).reloadAfterAccessChange();
      }
    });
    ref.listen(weaveApiMemberSpacesProvider, (previous, next) {
      final oldRefs = previous?.asData?.value?.visibleSpaceRefs;
      final newRefs = next.asData?.value?.visibleSpaceRefs;
      if (oldRefs != null &&
          newRefs != null &&
          oldRefs.isNotEmpty &&
          newRefs.isNotEmpty &&
          (oldRefs.length != newRefs.length || !oldRefs.containsAll(newRefs))) {
        ref.read(chatProvider.notifier).reloadAfterAccessChange();
      }
    });
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);
    if (capabilities.asData?.value.chat.isReady == true) {
      return child;
    }

    void retry() {
      ref
        ..invalidate(weaveApiMemberSpacesProvider)
        ..invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
    }

    return Scaffold(
      appBar: AppBar(title: Text(l10n.chatScreenTitle)),
      body: SafeArea(
        child: Center(
          child: capabilities.when(
            loading: () => LoadingState(
              message: l10n.chatLoadingLabel,
              hint: l10n.bootstrapLoadingHint,
              icon: Icons.chat_bubble_outline,
            ),
            error: (_, _) => ErrorState(
              message: l10n.chatEmptyMessage,
              guidance: l10n.settingsWorkspaceRecoveryUnavailableAction,
              retryLabel: l10n.retryButton,
              onRetry: retry,
            ),
            data: (snapshot) {
              final recovery = workspaceCapabilityRecoveryPresentation(
                l10n,
                snapshot.chat,
              );
              return EmptyState(
                message: l10n.chatEmptyMessage,
                guidance: recovery.recovery,
                icon: Icons.chat_bubble_outline,
                actionLabel: l10n.retryButton,
                onAction: retry,
                semanticLabel: recovery.semanticLabel(l10n, l10n.navChat),
              );
            },
          ),
        ),
      ),
    );
  }
}
