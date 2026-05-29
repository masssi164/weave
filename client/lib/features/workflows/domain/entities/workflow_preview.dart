enum WorkflowContextReferenceKind {
  channel,
  project,
  event,
  task,
  decision,
  file,
  meeting,
  agentRun,
}

enum WorkflowStepKind { step, gate, approval }

enum WorkflowStepState { ready, inProgress, blocked, waitingForApproval, done }

enum WorkflowAssigneeKind { person, agent }

class WorkflowTemplatePreview {
  const WorkflowTemplatePreview({
    required this.id,
    required this.title,
    required this.description,
    required this.attachableContextKinds,
  });

  final String id;
  final String title;
  final String description;
  final List<WorkflowContextReferenceKind> attachableContextKinds;

  bool canAttachTo(WorkflowContextReferenceKind kind) =>
      attachableContextKinds.contains(kind);
}

class WorkflowContextReference {
  const WorkflowContextReference({
    required this.id,
    required this.kind,
    required this.label,
    required this.sourceLabel,
    this.isExplicit = true,
  });

  final String id;
  final WorkflowContextReferenceKind kind;
  final String label;
  final String sourceLabel;
  final bool isExplicit;
}

class WorkflowEvidenceReference {
  const WorkflowEvidenceReference({
    required this.id,
    required this.label,
    required this.sourceLabel,
    required this.contextReferenceId,
  });

  final String id;
  final String label;
  final String sourceLabel;
  final String contextReferenceId;
}

class WorkflowAssignee {
  const WorkflowAssignee({required this.label, required this.kind});

  final String label;
  final WorkflowAssigneeKind kind;
}

class WorkflowBlocker {
  const WorkflowBlocker({
    required this.id,
    required this.description,
    required this.ownerLabel,
    required this.evidenceIds,
  });

  final String id;
  final String description;
  final String ownerLabel;
  final List<String> evidenceIds;
}

class WorkflowStepPreview {
  const WorkflowStepPreview({
    required this.id,
    required this.kind,
    required this.title,
    required this.state,
    required this.ownerLabel,
    required this.assignee,
    required this.nextAction,
    required this.dueLabel,
    required this.contextReferences,
    required this.evidence,
    required this.blockers,
    required this.requiresApproval,
    required this.agentDryRunOnly,
  });

  final String id;
  final WorkflowStepKind kind;
  final String title;
  final WorkflowStepState state;
  final String ownerLabel;
  final WorkflowAssignee assignee;
  final String nextAction;
  final String dueLabel;
  final List<WorkflowContextReference> contextReferences;
  final List<WorkflowEvidenceReference> evidence;
  final List<WorkflowBlocker> blockers;
  final bool requiresApproval;
  final bool agentDryRunOnly;

  bool get isBlocked => state == WorkflowStepState.blocked;

  bool get isActionable =>
      state == WorkflowStepState.ready ||
      state == WorkflowStepState.inProgress ||
      state == WorkflowStepState.blocked ||
      state == WorkflowStepState.waitingForApproval;

  bool get hasTraceableEvidence {
    final contextReferenceIds = contextReferences
        .where((reference) => reference.isExplicit)
        .map((reference) => reference.id)
        .toSet();

    return evidence.isNotEmpty &&
        evidence.every(
          (reference) =>
              contextReferenceIds.contains(reference.contextReferenceId),
        );
  }

  bool get hasTraceableBlockers {
    final evidenceIds = evidence.map((reference) => reference.id).toSet();

    return blockers.every(
      (blocker) =>
          blocker.evidenceIds.isNotEmpty &&
          blocker.evidenceIds.every(evidenceIds.contains),
    );
  }

  bool get isExplainable =>
      contextReferences.isNotEmpty &&
      contextReferences.every((reference) => reference.isExplicit) &&
      hasTraceableEvidence &&
      hasTraceableBlockers;
}

class WorkflowRunPreview {
  const WorkflowRunPreview({
    required this.template,
    required this.runId,
    required this.title,
    required this.contextLabel,
    required this.steps,
    required this.backgroundRoomReadingEnabled,
    required this.agentActionsEnabled,
    required this.auditTrailRequired,
  });

  final WorkflowTemplatePreview template;
  final String runId;
  final String title;
  final String contextLabel;
  final List<WorkflowStepPreview> steps;
  final bool backgroundRoomReadingEnabled;
  final bool agentActionsEnabled;
  final bool auditTrailRequired;

  Iterable<WorkflowStepPreview> get activeSteps =>
      steps.where((step) => step.isActionable);

  Iterable<WorkflowBlocker> get blockers =>
      steps.expand((step) => step.blockers);

  WorkflowStepPreview? get nextActionStep {
    for (final step in activeSteps) {
      return step;
    }
    return null;
  }

  bool get usesOnlyExplicitContext =>
      !backgroundRoomReadingEnabled &&
      steps.every(
        (step) =>
            step.contextReferences.every((reference) => reference.isExplicit),
      );

  bool get keepsAgentActionsGoverned =>
      !agentActionsEnabled && steps.every((step) => step.agentDryRunOnly);

  bool get isExplainable =>
      usesOnlyExplicitContext &&
      auditTrailRequired &&
      steps.every((step) => step.isExplainable);
}

class WorkflowPreviewSnapshot {
  const WorkflowPreviewSnapshot({required this.runs});

  final List<WorkflowRunPreview> runs;

  Iterable<WorkflowStepPreview> get activeSteps =>
      runs.expand((run) => run.activeSteps);

  Iterable<WorkflowBlocker> get blockers => runs.expand((run) => run.blockers);

  bool get hasActiveWorkflows => runs.isNotEmpty;

  bool get isLinearAccessiblePreview => runs.every((run) => run.isExplainable);

  bool get usesOnlyExplicitContext =>
      runs.every((run) => run.usesOnlyExplicitContext);

  bool get keepsAgentsGoverned =>
      runs.every((run) => run.keepsAgentActionsGoverned);
}
