enum AgentCapability { personalAssistant, channelAgent }

enum AgentCapabilityEnablement { disabled, enabled }

enum AgentCapabilityAvailability { previewOnly, adminSetupRequired, blocked }

class AgentCapabilityPolicy {
  const AgentCapabilityPolicy({
    required this.canManageCapabilities,
    required this.capabilities,
    this.isFailClosed = false,
  });

  factory AgentCapabilityPolicy.preview({required bool canManageCapabilities}) {
    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      capabilities: const <AgentCapabilityState>[
        AgentCapabilityState(
          capability: AgentCapability.personalAssistant,
          enablement: AgentCapabilityEnablement.disabled,
          availability: AgentCapabilityAvailability.previewOnly,
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
