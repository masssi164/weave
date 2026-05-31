import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/boards/data/repositories/backend_boards_workspace_repository.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/presentation/providers/boards_workspace_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class BoardsWorkspaceScreen extends ConsumerWidget {
  const BoardsWorkspaceScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final workspace = ref.watch(boardsWorkspaceProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.boardsWorkspaceScreenTitle)),
        switch (workspace) {
          AsyncLoading() => SliverFillRemaining(
            hasScrollBody: false,
            child: LoadingState(message: l10n.loadingLabel),
          ),
          AsyncError() => SliverFillRemaining(
            hasScrollBody: false,
            child: ErrorState(
              message: l10n.errorStateLabel,
              retryLabel: l10n.retryButton,
              onRetry: () => ref.invalidate(boardsWorkspaceProvider),
            ),
          ),
          AsyncData(:final value) => SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            sliver: SliverList.list(
              children: [
                _WorkspaceBoundaryBanner(board: value),
                const SizedBox(height: 16),
                _BoardSummaryCard(board: value),
                const SizedBox(height: 16),
                _BoardLayout(board: value, ref: ref),
              ],
            ),
          ),
        },
      ],
    );
  }
}

class _WorkspaceBoundaryBanner extends StatelessWidget {
  const _WorkspaceBoundaryBanner({required this.board});

  final BoardWorkspace board;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label: l10n.boardsWorkspaceBoundarySemantic,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.tertiaryContainer,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.visibility_off_outlined,
                color: theme.colorScheme.onTertiaryContainer,
                semanticLabel: l10n.boardsWorkspaceIconSemantic,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.boardsWorkspaceBoundaryTitle,
                      style: theme.textTheme.titleLarge?.copyWith(
                        color: theme.colorScheme.onTertiaryContainer,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      l10n.boardsWorkspaceBoundaryDescription,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onTertiaryContainer,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        _InfoChip(
                          icon: Icons.flag_outlined,
                          label: l10n.boardsWorkspaceActiveDogfoodChip,
                        ),
                        _InfoChip(
                          icon: Icons.hub_outlined,
                          label: l10n.boardsWorkspaceProviderNeutralChip,
                        ),
                        _InfoChip(
                          icon: Icons.keyboard_alt_outlined,
                          label: l10n.boardsWorkspaceKeyboardChip,
                        ),
                        _InfoChip(
                          icon: board.isBackendFed
                              ? Icons.sync_alt_outlined
                              : Icons.lock_outline,
                          label: board.isBackendFed
                              ? l10n.boardsWorkspaceBackendFedChip
                              : board.isBackendBlocked
                              ? l10n.boardsWorkspaceProviderBlockedChip
                              : l10n.boardsWorkspaceStaticFixtureChip,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BoardSummaryCard extends StatelessWidget {
  const _BoardSummaryCard({required this.board});

  final BoardWorkspace board;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label: l10n.boardsWorkspaceBoardSemantic(
        board.name,
        board.columns.length,
        board.taskCount,
      ),
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                board.name,
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(board.description, style: theme.textTheme.bodyLarge),
              const SizedBox(height: 16),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _InfoChip(
                    icon: Icons.view_week_outlined,
                    label: l10n.boardsWorkspaceColumnCount(
                      board.columns.length,
                    ),
                  ),
                  _InfoChip(
                    icon: Icons.task_alt_outlined,
                    label: l10n.boardsWorkspaceTaskCount(board.taskCount),
                  ),
                  _InfoChip(
                    icon: Icons.move_down_outlined,
                    label: l10n.boardsWorkspaceNonDragMovement,
                  ),
                  _InfoChip(
                    icon: Icons.extension_outlined,
                    label: l10n.boardsWorkspaceProviderCapabilitySummary(
                      _providerLabel(l10n, board.capabilities.provider),
                    ),
                  ),
                  _InfoChip(
                    icon: board.canUseBackendNonDragActions
                        ? Icons.check_circle_outline
                        : Icons.block_outlined,
                    label: board.canUseBackendNonDragActions
                        ? l10n.boardsWorkspaceCapabilityNonDragReady
                        : l10n.boardsWorkspaceCapabilityNonDragBlocked,
                  ),
                ],
              ),
              if (board.capabilities.supportSafeSummary.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  board.capabilities.supportSafeSummary,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BoardLayout extends StatelessWidget {
  const _BoardLayout({required this.board, required this.ref});

  final BoardWorkspace board;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final useWideLayout = constraints.maxWidth >= 980;
        final columns = board.columns
            .map(
              (column) =>
                  _BoardColumnCard(board: board, column: column, ref: ref),
            )
            .toList();

        if (!useWideLayout) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final column in columns) ...[
                column,
                if (column != columns.last) const SizedBox(height: 12),
              ],
            ],
          );
        }

        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final column in columns) ...[
                SizedBox(width: 320, child: column),
                if (column != columns.last) const SizedBox(width: 12),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _BoardColumnCard extends StatelessWidget {
  const _BoardColumnCard({
    required this.board,
    required this.column,
    required this.ref,
  });

  final BoardWorkspace board;
  final BoardColumnWorkspace column;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final statusLabel = _statusLabel(l10n, column.semanticStatus);

    return Semantics(
      container: true,
      label: l10n.boardsWorkspaceColumnSemantic(
        column.name,
        statusLabel,
        column.tasks.length,
      ),
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerHighest,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      column.name,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  _StatusPill(status: column.semanticStatus),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                column.wipLimit == null
                    ? l10n.boardsWorkspaceColumnTaskSummary(column.tasks.length)
                    : l10n.boardsWorkspaceColumnWipSummary(
                        column.tasks.length,
                        column.wipLimit!,
                      ),
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(height: 12),
              for (final task in column.tasks) ...[
                _BoardTaskCard(
                  board: board,
                  task: task,
                  column: column,
                  ref: ref,
                ),
                if (task != column.tasks.last) const SizedBox(height: 10),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BoardTaskCard extends StatelessWidget {
  const _BoardTaskCard({
    required this.board,
    required this.task,
    required this.column,
    required this.ref,
  });

  final BoardWorkspace board;
  final BoardTaskWorkspace task;
  final BoardColumnWorkspace column;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final statusLabel = _statusLabel(l10n, task.status);

    return Semantics(
      container: true,
      label: l10n.boardsWorkspaceTaskSemantic(
        task.title,
        column.name,
        statusLabel,
        task.assigneeLabel,
        task.dueLabel,
        task.priorityLabel,
      ),
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 8, 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      task.title,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  _TaskActionMenu(
                    board: board,
                    column: column,
                    task: task,
                    ref: ref,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(task.description, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _InfoChip(
                    icon: Icons.person_outline,
                    label: task.assigneeLabel,
                  ),
                  _InfoChip(
                    icon: Icons.schedule_outlined,
                    label: task.dueLabel,
                  ),
                  _InfoChip(
                    icon: Icons.priority_high_outlined,
                    label: task.priorityLabel,
                  ),
                ],
              ),
              if (task.labels.isNotEmpty) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final label in task.labels)
                      Chip(
                        label: Text(label),
                        visualDensity: VisualDensity.compact,
                      ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _TaskActionMenu extends StatelessWidget {
  const _TaskActionMenu({
    required this.board,
    required this.column,
    required this.task,
    required this.ref,
  });

  final BoardWorkspace board;
  final BoardColumnWorkspace column;
  final BoardTaskWorkspace task;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return PopupMenuButton<_WorkspaceTaskAction>(
      tooltip: l10n.boardsWorkspaceTaskActionsTooltip(task.title),
      onSelected: (action) => _runTaskAction(context, l10n, action),
      itemBuilder: (context) => [
        PopupMenuItem(
          value: _WorkspaceTaskAction.moveNext,
          child: Text(l10n.boardsWorkspaceMoveTaskAction),
        ),
        PopupMenuItem(
          value: _WorkspaceTaskAction.markDone,
          child: Text(l10n.boardsWorkspaceMarkDoneAction),
        ),
        PopupMenuItem(
          value: _WorkspaceTaskAction.block,
          child: Text(l10n.boardsWorkspaceBlockTaskAction),
        ),
      ],
      icon: const Icon(Icons.more_vert),
    );
  }

  Future<void> _runTaskAction(
    BuildContext context,
    AppLocalizations l10n,
    _WorkspaceTaskAction action,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    if (!board.canUseBackendNonDragActions) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.boardsWorkspaceActionBackendRequired)),
      );
      return;
    }

    final session = await ref.read(weaveAuthenticatedSessionProvider.future);
    if (session == null) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.boardsWorkspaceActionBackendRequired)),
      );
      return;
    }

    final repository = BackendBoardsWorkspaceRepository(
      httpClient: ref.read(weaveApiHttpClientProvider),
      apiBaseUrl: session.apiBaseUrl,
      accessToken: session.accessToken,
    );

    try {
      switch (action) {
        case _WorkspaceTaskAction.moveNext:
          final target = _nextColumn(board, column);
          if (target == null) {
            messenger.showSnackBar(
              SnackBar(content: Text(l10n.boardsWorkspaceActionNoNextColumn)),
            );
            return;
          }
          await repository.moveTask(
            taskId: task.id,
            targetColumnId: target.id,
            targetPosition: target.tasks.length,
          );
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.boardsWorkspaceActionMoved)),
          );
        case _WorkspaceTaskAction.markDone:
          await repository.completeTask(task.id);
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.boardsWorkspaceActionCompleted)),
          );
        case _WorkspaceTaskAction.block:
          final target = _columnForStatus(board, BoardTaskStatus.blocked);
          await repository.updateTaskStatus(
            taskId: task.id,
            status: 'blocked',
            targetColumnId: target?.id,
          );
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.boardsWorkspaceActionBlocked)),
          );
      }
      ref.invalidate(boardsWorkspaceProvider);
    } on AppFailure catch (error) {
      messenger.showSnackBar(
        SnackBar(content: Text(_actionFailureText(l10n, error))),
      );
    }
  }

  String _actionFailureText(AppLocalizations l10n, AppFailure error) {
    return l10n.boardsWorkspaceActionFailed;
  }
}

enum _WorkspaceTaskAction { moveNext, markDone, block }

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.status});

  final BoardTaskStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final (icon, color) = switch (status) {
      BoardTaskStatus.notStarted => (
        Icons.radio_button_unchecked,
        Colors.blueGrey,
      ),
      BoardTaskStatus.inProgress => (Icons.autorenew, Colors.indigo),
      BoardTaskStatus.blocked => (
        Icons.report_problem_outlined,
        Colors.deepOrange,
      ),
      BoardTaskStatus.done => (Icons.check_circle_outline, Colors.green),
    };
    final label = _statusLabel(l10n, status);

    return Semantics(
      label: l10n.boardsWorkspaceStatusSemantic(label),
      child: Chip(
        avatar: Icon(icon, size: 18, color: color),
        label: Text(label),
        visualDensity: VisualDensity.compact,
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Chip(
      avatar: Icon(icon, size: 18),
      label: Text(label),
      visualDensity: VisualDensity.compact,
      backgroundColor: theme.colorScheme.surfaceContainerLowest,
      side: BorderSide(color: theme.colorScheme.outlineVariant),
    );
  }
}

String _statusLabel(AppLocalizations l10n, BoardTaskStatus status) {
  return switch (status) {
    BoardTaskStatus.notStarted => l10n.boardsWorkspaceStatusNotStarted,
    BoardTaskStatus.inProgress => l10n.boardsWorkspaceStatusInProgress,
    BoardTaskStatus.blocked => l10n.boardsWorkspaceStatusBlocked,
    BoardTaskStatus.done => l10n.boardsWorkspaceStatusDone,
  };
}

BoardColumnWorkspace? _nextColumn(
  BoardWorkspace board,
  BoardColumnWorkspace column,
) {
  final currentIndex = board.columns.indexWhere((item) => item.id == column.id);
  if (currentIndex < 0 || currentIndex >= board.columns.length - 1) {
    return null;
  }
  return board.columns[currentIndex + 1];
}

BoardColumnWorkspace? _columnForStatus(
  BoardWorkspace board,
  BoardTaskStatus status,
) {
  for (final column in board.columns) {
    if (column.semanticStatus == status) {
      return column;
    }
  }
  return null;
}

String _providerLabel(AppLocalizations l10n, String provider) {
  return switch (provider) {
    'in-memory' => l10n.boardsWorkspaceProviderInMemory,
    'vikunja' => l10n.boardsWorkspaceProviderVikunja,
    'openproject' => l10n.boardsWorkspaceProviderOpenProject,
    'nextcloud-deck' => l10n.boardsWorkspaceProviderNextcloudDeck,
    'none' => l10n.boardsWorkspaceProviderNone,
    'unavailable' => l10n.boardsWorkspaceProviderUnavailable,
    _ => l10n.boardsWorkspaceProviderUnknown,
  };
}
