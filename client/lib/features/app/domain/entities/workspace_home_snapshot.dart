import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

class WorkspaceHomeSnapshot {
  const WorkspaceHomeSnapshot({
    required this.version,
    required this.readiness,
    required this.summary,
    required this.sections,
    required this.actions,
    required this.recentActivity,
    required this.supportSafe,
  });

  final int version;
  final WorkspaceCapabilityReadiness readiness;
  final String summary;
  final List<WorkspaceHomeSection> sections;
  final List<WorkspaceHomeAction> actions;
  final List<WorkspaceHomeActivity> recentActivity;
  final bool supportSafe;

  bool get hasActionableWork =>
      actions.isNotEmpty || sections.any((section) => section.itemCount > 0);

  /// Whether the current Home surface can be rendered for a member.
  ///
  /// The aggregate may be degraded by product-line sections that are not
  /// current AppShell destinations. Human-testing readiness still requires
  /// independent navigation and item-level activity evidence.
  bool get isMemberSurfaceAvailable =>
      readiness == WorkspaceCapabilityReadiness.ready ||
      readiness == WorkspaceCapabilityReadiness.degraded;
}

enum WorkspaceHomeActivityDomain { files }

enum WorkspaceHomeActivityAction { filesWebDavWriteCompleted }

enum WorkspaceHomeActivityVisibility { workspace }

/// One support-safe, authorization-filtered activity projected by Weave Home.
///
/// Opaque references remain available for deterministic evidence comparison,
/// but presentation code must never display them or infer member identity from
/// [actorRefHash].
class WorkspaceHomeActivity {
  const WorkspaceHomeActivity({
    required this.activityRef,
    required this.domain,
    required this.action,
    required this.occurredAt,
    required this.visibility,
    required this.actorRefHash,
    required this.actorIsCurrentUser,
    required this.supportSafe,
  });

  final String activityRef;
  final WorkspaceHomeActivityDomain domain;
  final WorkspaceHomeActivityAction action;
  final DateTime occurredAt;
  final WorkspaceHomeActivityVisibility visibility;
  final String actorRefHash;
  final bool actorIsCurrentUser;
  final bool supportSafe;
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
