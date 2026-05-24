import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

enum AgentCapability { personalAssistant, channelAgent }

enum AgentCapabilityEnablement { disabled, enabled }

enum AgentCapabilityAvailability {
  adminSetupRequired,
  disabledByPolicy,
  blocked,
}

class AgentCapabilityPolicy {
  const AgentCapabilityPolicy({
    required this.canManageCapabilities,
    required this.capabilities,
    this.isFailClosed = false,
  });

  factory AgentCapabilityPolicy.disabled({
    required bool canManageCapabilities,
  }) {
    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      capabilities: const <AgentCapabilityState>[
        AgentCapabilityState(
          capability: AgentCapability.personalAssistant,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.disabledByPolicy,
        ),
        AgentCapabilityState(
          capability: AgentCapability.channelAgent,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.adminSetupRequired,
        ),
      ],
    );
  }

  factory AgentCapabilityPolicy.failClosed({
    required bool canManageCapabilities,
  }) {
    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      isFailClosed: true,
      capabilities: const <AgentCapabilityState>[
        AgentCapabilityState(
          capability: AgentCapability.personalAssistant,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.blocked,
        ),
        AgentCapabilityState(
          capability: AgentCapability.channelAgent,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.blocked,
        ),
      ],
    );
  }

  factory AgentCapabilityPolicy.fromWorkspaceCapabilities({
    required bool canManageCapabilities,
    required WorkspaceCapabilitySnapshot workspaceCapabilities,
  }) {
    final weaver = workspaceCapabilities.weaver;
    final availability = switch (weaver.policyState) {
      WorkspaceCapabilityPolicyState.disabled =>
        AgentCapabilityAvailability.disabledByPolicy,
      WorkspaceCapabilityPolicyState.policyBlocked =>
        AgentCapabilityAvailability.disabledByPolicy,
      WorkspaceCapabilityPolicyState.unavailable =>
        AgentCapabilityAvailability.adminSetupRequired,
      WorkspaceCapabilityPolicyState.allowed =>
        weaver.readiness == WorkspaceCapabilityReadiness.ready &&
                weaver.grants('weaver.enabled')
            ? AgentCapabilityAvailability.adminSetupRequired
            : AgentCapabilityAvailability.disabledByPolicy,
    };

    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      capabilities: <AgentCapabilityState>[
        AgentCapabilityState(
          capability: AgentCapability.personalAssistant,
          enablement: AgentCapabilityEnablement.disabled,
          availability: availability,
        ),
        const AgentCapabilityState(
          capability: AgentCapability.channelAgent,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.adminSetupRequired,
        ),
      ],
    );
  }

  final bool canManageCapabilities;
  final List<AgentCapabilityState> capabilities;
  final bool isFailClosed;

  bool get canStartAnyCapability {
    return capabilities.any((capability) => capability.canStart);
  }

  AgentCapabilityState stateFor(AgentCapability capability) {
    return capabilities.firstWhere(
      (state) => state.capability == capability,
      orElse: () => AgentCapabilityState(
        capability: capability,
        enablement: AgentCapabilityEnablement.disabled,
        availability: AgentCapabilityAvailability.blocked,
      ),
    );
  }
}

class AgentCapabilityState {
  const AgentCapabilityState({
    required this.capability,
    required this.enablement,
    required this.availability,
  });

  final AgentCapability capability;
  final AgentCapabilityEnablement enablement;
  final AgentCapabilityAvailability availability;

  bool get canStart {
    return enablement == AgentCapabilityEnablement.enabled &&
        availability != AgentCapabilityAvailability.blocked;
  }
}
