import 'package:flutter/material.dart';
import 'package:weave/features/workflows/domain/entities/workflow_preview.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class WorkflowPreviewPanel extends StatelessWidget {
  const WorkflowPreviewPanel({required this.snapshot, super.key});

  final WorkflowPreviewSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final blockers = snapshot.blockers.length;
    final activeSteps = snapshot.activeSteps.length;

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: l10n.workflowPreviewSemanticSummary(
        snapshot.runs.length,
        activeSteps,
        blockers,
      ),
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerHighest,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.route_outlined, color: theme.colorScheme.primary),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          header: true,
                          child: Text(
                            l10n.workflowPreviewTitle,
                            style: theme.textTheme.titleLarge,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          l10n.workflowPreviewDescription,
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
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _WorkflowInfoChip(
                    icon: Icons.format_list_numbered_outlined,
                    label: l10n.workflowPreviewLinearViewChip,
                  ),
                  _WorkflowInfoChip(
                    icon: Icons.visibility_outlined,
                    label: l10n.workflowPreviewExplicitContextChip,
                  ),
                  _WorkflowInfoChip(
                    icon: Icons.admin_panel_settings_outlined,
                    label: l10n.workflowPreviewGovernedActionsChip,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              ...snapshot.runs.map(
                (run) => Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _WorkflowRunCard(run: run),
                ),
              ),
              Text(
                l10n.workflowPreviewNoBackgroundReading,
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

class _WorkflowInfoChip extends StatelessWidget {
  const _WorkflowInfoChip({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Chip(avatar: Icon(icon, size: 18), label: Text(label));
  }
}

class _WorkflowRunCard extends StatelessWidget {
  const _WorkflowRunCard({required this.run});

  final WorkflowRunPreview run;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final nextStep = run.nextActionStep;

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: l10n.workflowPreviewRunSemantic(
        run.title,
        run.contextLabel,
        run.steps.length,
        run.blockers.length,
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(run.title, style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                l10n.workflowPreviewContextLabel(run.contextLabel),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (nextStep != null) ...[
                const SizedBox(height: 8),
                MergeSemantics(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.flag_outlined,
                        size: 20,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          l10n.workflowPreviewNextAction(
                            nextStep.title,
                            nextStep.nextAction,
                          ),
                          style: theme.textTheme.bodyMedium,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 12),
              ...run.steps.map(
                (step) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _WorkflowStepTile(step: step),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _WorkflowStepTile extends StatelessWidget {
  const _WorkflowStepTile({required this.step});

  final WorkflowStepPreview step;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = _statusLabel(l10n, step.state);
    final kind = _kindLabel(l10n, step.kind);
    final blockerText = step.blockers.isEmpty
        ? l10n.workflowPreviewNoBlockers
        : step.blockers.map((blocker) => blocker.description).join('; ');
    final evidenceText = step.evidence.map((item) => item.label).join(', ');

    return DecoratedBox(
      decoration: BoxDecoration(
        color: step.isBlocked
            ? theme.colorScheme.errorContainer.withValues(alpha: 0.45)
            : theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: step.isBlocked
              ? theme.colorScheme.error.withValues(alpha: 0.5)
              : theme.colorScheme.outlineVariant,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              container: true,
              label: l10n.workflowPreviewStepSemantic(
                step.title,
                kind,
                status,
                step.ownerLabel,
                step.dueLabel,
                step.nextAction,
                blockerText,
                evidenceText,
              ),
              child: ExcludeSemantics(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          _statusIcon(step.state),
                          color: _statusColor(context, step.state),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                step.title,
                                style: theme.textTheme.titleSmall,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '$kind · $status',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    _WorkflowStepFact(
                      icon: Icons.person_outline,
                      text: l10n.workflowPreviewOwner(step.ownerLabel),
                    ),
                    _WorkflowStepFact(
                      icon: Icons.event_outlined,
                      text: l10n.workflowPreviewDue(step.dueLabel),
                    ),
                    _WorkflowStepFact(
                      icon: Icons.playlist_add_check_outlined,
                      text: l10n.workflowPreviewStepNextAction(step.nextAction),
                    ),
                    _WorkflowStepFact(
                      icon: step.blockers.isEmpty
                          ? Icons.check_circle_outline
                          : Icons.report_problem_outlined,
                      text: l10n.workflowPreviewBlockers(blockerText),
                    ),
                    _WorkflowStepFact(
                      icon: Icons.fact_check_outlined,
                      text: l10n.workflowPreviewEvidence(evidenceText),
                    ),
                    if (step.requiresApproval ||
                        step.assignee.kind == WorkflowAssigneeKind.agent)
                      _WorkflowStepFact(
                        icon: Icons.verified_user_outlined,
                        text: step.requiresApproval
                            ? l10n.workflowPreviewApprovalRequired
                            : l10n.workflowPreviewAgentDryRunOnly,
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: () => _showStepSnackBar(
                    context,
                    '${step.title}: ${step.nextAction}',
                  ),
                  icon: const Icon(Icons.open_in_new_outlined),
                  label: Text(l10n.workflowPreviewOpenStepButton),
                ),
                OutlinedButton.icon(
                  onPressed: () => _showStepSnackBar(context, evidenceText),
                  icon: const Icon(Icons.fact_check_outlined),
                  label: Text(l10n.workflowPreviewReviewEvidenceButton),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _WorkflowStepFact extends StatelessWidget {
  const _WorkflowStepFact({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }
}

void _showStepSnackBar(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

String _kindLabel(AppLocalizations l10n, WorkflowStepKind kind) {
  return switch (kind) {
    WorkflowStepKind.step => l10n.workflowPreviewKindStep,
    WorkflowStepKind.gate => l10n.workflowPreviewKindGate,
    WorkflowStepKind.approval => l10n.workflowPreviewKindApproval,
  };
}

String _statusLabel(AppLocalizations l10n, WorkflowStepState state) {
  return switch (state) {
    WorkflowStepState.ready => l10n.workflowPreviewStatusReady,
    WorkflowStepState.inProgress => l10n.workflowPreviewStatusInProgress,
    WorkflowStepState.blocked => l10n.workflowPreviewStatusBlocked,
    WorkflowStepState.waitingForApproval => l10n.workflowPreviewStatusWaiting,
    WorkflowStepState.done => l10n.workflowPreviewStatusDone,
  };
}

IconData _statusIcon(WorkflowStepState state) {
  return switch (state) {
    WorkflowStepState.ready => Icons.radio_button_unchecked,
    WorkflowStepState.inProgress => Icons.timelapse_outlined,
    WorkflowStepState.blocked => Icons.report_problem_outlined,
    WorkflowStepState.waitingForApproval => Icons.hourglass_top_outlined,
    WorkflowStepState.done => Icons.check_circle_outline,
  };
}

Color _statusColor(BuildContext context, WorkflowStepState state) {
  final scheme = Theme.of(context).colorScheme;
  return switch (state) {
    WorkflowStepState.blocked => scheme.error,
    WorkflowStepState.done => scheme.primary,
    WorkflowStepState.waitingForApproval => scheme.tertiary,
    WorkflowStepState.ready || WorkflowStepState.inProgress => scheme.primary,
  };
}
