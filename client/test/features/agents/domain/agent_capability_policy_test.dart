import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

void main() {
  group('AgentCapabilityPolicy', () {
    test('disabled defaults are non-startable and policy-owned', () {
      final policy = AgentCapabilityPolicy.disabled(
        canManageCapabilities: true,
      );

      expect(policy.canManageCapabilities, isTrue);
      expect(policy.isFailClosed, isFalse);
      expect(policy.canStartAnyCapability, isFalse);
      expect(
        policy.stateFor(AgentCapability.personalAssistant).availability,
        AgentCapabilityAvailability.disabledByPolicy,
      );
      expect(
        policy.stateFor(AgentCapability.channelAgent).availability,
        AgentCapabilityAvailability.adminSetupRequired,
      );
      expect(
        policy.capabilities.every((capability) => capability.canStart == false),
        isTrue,
      );
    });

    test('unknown or unresolved policy fails closed for every capability', () {
      final policy = AgentCapabilityPolicy.failClosed(
        canManageCapabilities: false,
      );

      expect(policy.canManageCapabilities, isFalse);
      expect(policy.isFailClosed, isTrue);
      expect(policy.canStartAnyCapability, isFalse);
      expect(
        policy.capabilities.map((capability) => capability.availability),
        everyElement(AgentCapabilityAvailability.blocked),
      );
      expect(
        policy.stateFor(AgentCapability.personalAssistant).canStart,
        isFalse,
      );
      expect(policy.stateFor(AgentCapability.channelAgent).canStart, isFalse);
    });

    test(
      'maps ARC entitlement without exposing obsolete v1 runtime grants',
      () {
        const snapshot = WorkspaceCapabilitySnapshot(
          shellAccess: WorkspaceCapabilityState(
            capability: WorkspaceCapability.shellAccess,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          chat: WorkspaceCapabilityState(
            capability: WorkspaceCapability.chat,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          files: WorkspaceCapabilityState(
            capability: WorkspaceCapability.files,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          calendar: WorkspaceCapabilityState(
            capability: WorkspaceCapability.calendar,
            readiness: WorkspaceCapabilityReadiness.unavailable,
          ),
          boards: WorkspaceCapabilityState(
            capability: WorkspaceCapability.boards,
            readiness: WorkspaceCapabilityReadiness.unavailable,
          ),
          agentRuntimeControl: WorkspaceCapabilityState(
            capability: WorkspaceCapability.agentRuntimeControl,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
            grantedCapabilities: ['agent-runtime.entitled'],
          ),
        );

        final policy = AgentCapabilityPolicy.fromWorkspaceCapabilities(
          canManageCapabilities: false,
          workspaceCapabilities: snapshot,
        );

        expect(policy.canStartAnyCapability, isTrue);
        expect(policy.weaverMemberUx.available, isTrue);
        expect(policy.weaverMemberUx.modelAliases, isEmpty);
        expect(policy.weaverMemberUx.allowedSkills, isEmpty);
        expect(policy.weaverMemberUx.allowedPersonalConnections, isEmpty);
        expect(policy.weaverMemberUx.canConfigureStyle, isFalse);
        expect(policy.weaverMemberUx.canConfigureMemory, isFalse);
        expect(policy.weaverMemberUx.canConfigureWorkspace, isFalse);
        expect(policy.weaverMemberUx.hasAnyPersonalSetting, isFalse);
      },
    );
  });
}
