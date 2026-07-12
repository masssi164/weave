import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class HomeRecentActivity extends ConsumerWidget {
  const HomeRecentActivity({super.key});

  static const _maximumVisibleItems = 5;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final home = ref.watch(weaveApiWorkspaceHomeProvider);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Semantics(
        container: true,
        explicitChildNodes: true,
        label: l10n.shellRecentActivitySemanticLabel,
        child: Card(
          margin: EdgeInsets.zero,
          elevation: 0,
          color: theme.colorScheme.surfaceContainerHighest,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Semantics(
                  header: true,
                  child: Row(
                    children: [
                      Icon(
                        Icons.history,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          l10n.shellRecentActivityTitle,
                          style: theme.textTheme.titleMedium,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                switch (home) {
                  AsyncLoading() => _ActivityStatus(
                    icon: Icons.sync_outlined,
                    message: l10n.homeRecentActivityLoading,
                  ),
                  AsyncError() => _ActivityStatus(
                    icon: Icons.error_outline,
                    message: l10n.homeRecentActivityUnavailable,
                    actionLabel: l10n.retryButton,
                    onAction: () =>
                        ref.invalidate(weaveApiWorkspaceHomeProvider),
                  ),
                  AsyncData(value: final snapshot?) => _ActivityList(
                    activities: snapshot.recentActivity
                        .take(_maximumVisibleItems)
                        .toList(growable: false),
                  ),
                  AsyncData() => _ActivityStatus(
                    icon: Icons.history_toggle_off_outlined,
                    message: l10n.homeRecentActivityUnavailable,
                  ),
                },
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ActivityList extends StatelessWidget {
  const _ActivityList({required this.activities});

  final List<WorkspaceHomeActivity> activities;

  @override
  Widget build(BuildContext context) {
    if (activities.isEmpty) {
      return _ActivityStatus(
        icon: Icons.history_toggle_off_outlined,
        message: AppLocalizations.of(context).homeRecentActivityEmpty,
      );
    }

    return Column(
      children: [
        for (var index = 0; index < activities.length; index++) ...[
          _ActivityTile(activity: activities[index]),
          if (index < activities.length - 1) const Divider(height: 16),
        ],
      ],
    );
  }
}

class _ActivityTile extends StatelessWidget {
  const _ActivityTile({required this.activity});

  final WorkspaceHomeActivity activity;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final title = _activityTitle(l10n, activity);
    final visibility = switch (activity.visibility) {
      WorkspaceHomeActivityVisibility.workspace =>
        l10n.homeRecentActivityWorkspaceVisibility,
    };
    final recency = _formatRecency(context, activity.occurredAt);

    return Semantics(
      container: true,
      label: l10n.homeRecentActivityItemSemantic(title, visibility, recency),
      child: ExcludeSemantics(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(top: 2),
              child: Icon(Icons.description_outlined, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title),
                  const SizedBox(height: 2),
                  Text(
                    '$visibility · $recency',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _activityTitle(AppLocalizations l10n, WorkspaceHomeActivity activity) {
    return switch ((activity.domain, activity.action)) {
      (
        WorkspaceHomeActivityDomain.files,
        WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
      ) =>
        activity.actorIsCurrentUser
            ? l10n.homeRecentActivityCurrentMemberFilesCompleted
            : l10n.homeRecentActivityOtherMemberFilesCompleted,
    };
  }
}

class _ActivityStatus extends StatelessWidget {
  const _ActivityStatus({
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
    return Semantics(
      liveRegion: true,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(message)),
          if (actionLabel != null && onAction != null)
            TextButton(onPressed: onAction, child: Text(actionLabel!)),
        ],
      ),
    );
  }
}

String _formatRecency(BuildContext context, DateTime timestamp) {
  final l10n = AppLocalizations.of(context);
  final now = DateTime.now();
  final localTimestamp = timestamp.toLocal();
  final difference = now.difference(localTimestamp);
  if (difference.isNegative) {
    return MaterialLocalizations.of(context).formatShortDate(localTimestamp);
  }
  if (difference < const Duration(minutes: 1)) {
    return l10n.shellRecentActivityNow;
  }
  if (difference < const Duration(hours: 1)) {
    return l10n.shellRecentActivityMinutesAgo(difference.inMinutes);
  }
  if (_isSameDay(now, localTimestamp)) {
    return l10n.shellRecentActivityToday;
  }
  final yesterday = now.subtract(const Duration(days: 1));
  if (_isSameDay(yesterday, localTimestamp)) {
    return l10n.shellRecentActivityYesterday;
  }
  return MaterialLocalizations.of(context).formatShortDate(localTimestamp);
}

bool _isSameDay(DateTime left, DateTime right) {
  return left.year == right.year &&
      left.month == right.month &&
      left.day == right.day;
}
