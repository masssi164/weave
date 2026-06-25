import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/l10n/generated/app_localizations_en.dart';

void main() {
  final l10n = AppLocalizationsEn();

  test('maps the six member capability recovery states to localized copy', () {
    final cases = <WorkspaceCapabilityState, WorkspaceMemberRecoveryState>{
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.ready,
        policyState: WorkspaceCapabilityPolicyState.allowed,
        memberImpact: 'RAW BACKEND READY MESSAGE',
      ): WorkspaceMemberRecoveryState.available,
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.ready,
        policyState: WorkspaceCapabilityPolicyState.policyBlocked,
        memberImpact: 'RAW BACKEND POLICY MESSAGE',
      ): WorkspaceMemberRecoveryState.disabledByPolicy,
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.blocked,
        policyState: WorkspaceCapabilityPolicyState.allowed,
        memberImpact: 'RAW BACKEND SETUP MESSAGE',
      ): WorkspaceMemberRecoveryState.notConfigured,
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.calendar,
        readiness: WorkspaceCapabilityReadiness.degraded,
        policyState: WorkspaceCapabilityPolicyState.allowed,
        memberImpact: 'RAW BACKEND DEGRADED MESSAGE',
      ): WorkspaceMemberRecoveryState.degraded,
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.unavailable,
        policyState: WorkspaceCapabilityPolicyState.unavailable,
        memberImpact: 'RAW BACKEND UNAVAILABLE MESSAGE',
      ): WorkspaceMemberRecoveryState.unavailable,
      const WorkspaceCapabilityState(
        capability: WorkspaceCapability.boards,
        readiness: WorkspaceCapabilityReadiness.unavailable,
        policyState: WorkspaceCapabilityPolicyState.unavailable,
        memberImpact: 'RAW BACKEND FUTURE MESSAGE',
      ): WorkspaceMemberRecoveryState.comingLater,
    };

    for (final entry in cases.entries) {
      final presentation = workspaceCapabilityRecoveryPresentation(
        l10n,
        entry.key,
      );

      expect(presentation.state, entry.value);
      expect(presentation.stateLabel, isNot(contains('RAW BACKEND')));
      expect(presentation.recovery, isNot(contains('RAW BACKEND')));
      expect(
        presentation.semanticLabel(l10n, 'Capability'),
        contains('Support reference: Not provided.'),
      );
    }
  });
}
