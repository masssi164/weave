import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class CalendarScreen extends ConsumerWidget {
  const CalendarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final capabilitySnapshot = ref.watch(workspaceCapabilitySnapshotProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.navCalendar)),
      body: SafeArea(
        child: capabilitySnapshot.when(
          data: (snapshot) => _CalendarCapabilityBody(
            capability: snapshot.calendar,
            onRetry: () {
              ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
            },
          ),
          error: (_, _) => ErrorState(
            message: l10n.calendarUnavailableTitle,
            guidance: l10n.settingsWorkspaceRecoveryUnavailableAction,
            retryLabel: l10n.retryButton,
            onRetry: () {
              ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
            },
          ),
          loading: () => LoadingState(
            message: l10n.bootstrapLoadingLabel,
            hint: l10n.bootstrapLoadingHint,
            icon: Icons.calendar_today_outlined,
          ),
        ),
      ),
    );
  }
}

class _CalendarCapabilityBody extends StatelessWidget {
  const _CalendarCapabilityBody({
    required this.capability,
    required this.onRetry,
  });

  final WorkspaceCapabilityState capability;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final recovery = workspaceCapabilityRecoveryPresentation(l10n, capability);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: EmptyState(
          message: capability.isReady
              ? l10n.navCalendar
              : l10n.calendarUnavailableTitle,
          guidance: capability.isReady
              ? l10n.settingsWorkspaceRecoveryAvailableAction
              : recovery.recovery,
          icon: Icons.calendar_today_outlined,
          actionLabel: l10n.retryButton,
          onAction: onRetry,
          semanticLabel: recovery.semanticLabel(l10n, l10n.navCalendar),
        ),
      ),
    );
  }
}
