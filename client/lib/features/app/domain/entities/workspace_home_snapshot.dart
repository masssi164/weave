import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

class WorkspaceHomeSnapshot {
  const WorkspaceHomeSnapshot({
    required this.version,
    required this.readiness,
    required this.summary,
    required this.sections,
    required this.actions,
    required this.supportSafe,
  });

  final int version;
  final WorkspaceCapabilityReadiness readiness;
  final String summary;
  final List<WorkspaceHomeSection> sections;
  final List<WorkspaceHomeAction> actions;
  final bool supportSafe;

  bool get hasActionableWork =>
      actions.isNotEmpty || sections.any((section) => section.itemCount > 0);
}

class WorkspaceHomeSection {
  const WorkspaceHomeSection({
    required this.key,
    required this.title,
    required this.readiness,
    required this.summary,
    required this.itemCount,
    required this.accessible,
    required this.productRoute,
  });

  final String key;
  final String title;
  final WorkspaceCapabilityReadiness readiness;
  final String summary;
  final int itemCount;
  final bool accessible;
  final String productRoute;
}

class WorkspaceHomeAction {
  const WorkspaceHomeAction({
    required this.key,
    required this.label,
    required this.productRoute,
    required this.reason,
  });

  final String key;
  final String label;
  final String productRoute;
  final String reason;
}
