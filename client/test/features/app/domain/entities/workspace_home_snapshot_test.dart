import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';

void main() {
  test('keeps degraded Home navigable without accepting blocked states', () {
    WorkspaceHomeSnapshot snapshot(WorkspaceCapabilityReadiness readiness) {
      return WorkspaceHomeSnapshot(
        version: 2,
        readiness: readiness,
        summary: 'Support-safe Home summary.',
        sections: const [],
        actions: const [],
        recentActivity: const [],
        supportSafe: true,
      );
    }

    expect(
      snapshot(WorkspaceCapabilityReadiness.ready).isMemberSurfaceAvailable,
      isTrue,
    );
    expect(
      snapshot(WorkspaceCapabilityReadiness.degraded).isMemberSurfaceAvailable,
      isTrue,
      reason:
          'Out-of-scope product-line sections may degrade the aggregate without hiding Home.',
    );
    expect(
      snapshot(WorkspaceCapabilityReadiness.blocked).isMemberSurfaceAvailable,
      isFalse,
    );
    expect(
      snapshot(
        WorkspaceCapabilityReadiness.unavailable,
      ).isMemberSurfaceAvailable,
      isFalse,
    );
  });
}
