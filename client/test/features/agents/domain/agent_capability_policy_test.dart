import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';

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
  });
}
