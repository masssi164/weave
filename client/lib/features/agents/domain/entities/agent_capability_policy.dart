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
    this.weaverMemberUx = WeaverMemberUxState.disabled,
    this.isFailClosed = false,
  });

  factory AgentCapabilityPolicy.disabled({
    required bool canManageCapabilities,
  }) {
    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      weaverMemberUx: WeaverMemberUxState.disabled,
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
      weaverMemberUx: WeaverMemberUxState.blockedState,
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
    final runtimeControl = workspaceCapabilities.agentRuntimeControl;
    final enabled =
        runtimeControl.policyState == WorkspaceCapabilityPolicyState.allowed &&
        runtimeControl.readiness == WorkspaceCapabilityReadiness.ready &&
        runtimeControl.grants('agent-runtime.entitled');
    final availability = switch (runtimeControl.policyState) {
      WorkspaceCapabilityPolicyState.disabled =>
        AgentCapabilityAvailability.disabledByPolicy,
      WorkspaceCapabilityPolicyState.policyBlocked =>
        AgentCapabilityAvailability.disabledByPolicy,
      WorkspaceCapabilityPolicyState.unavailable =>
        AgentCapabilityAvailability.adminSetupRequired,
      WorkspaceCapabilityPolicyState.allowed =>
        enabled
            ? AgentCapabilityAvailability.adminSetupRequired
            : AgentCapabilityAvailability.disabledByPolicy,
    };

    return AgentCapabilityPolicy(
      canManageCapabilities: canManageCapabilities,
      weaverMemberUx: WeaverMemberUxState.fromCapability(runtimeControl),
      capabilities: <AgentCapabilityState>[
        AgentCapabilityState(
          capability: AgentCapability.personalAssistant,
          enablement: enabled
              ? AgentCapabilityEnablement.enabled
              : AgentCapabilityEnablement.disabled,
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
  final WeaverMemberUxState weaverMemberUx;
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

class WeaverMemberUxState {
  const WeaverMemberUxState({
    required this.available,
    required this.isBlocked,
    required this.modelAliases,
    required this.allowedSkills,
    required this.allowedPersonalConnections,
    required this.canConfigureStyle,
    required this.canConfigureMemory,
    required this.canConfigureWorkspace,
    this.memberImpact,
  });

  static const disabled = WeaverMemberUxState(
    available: false,
    isBlocked: false,
    modelAliases: <String>[],
    allowedSkills: <String>[],
    allowedPersonalConnections: <String>[],
    canConfigureStyle: false,
    canConfigureMemory: false,
    canConfigureWorkspace: false,
  );

  static const blockedState = WeaverMemberUxState(
    available: false,
    isBlocked: true,
    modelAliases: <String>[],
    allowedSkills: <String>[],
    allowedPersonalConnections: <String>[],
    canConfigureStyle: false,
    canConfigureMemory: false,
    canConfigureWorkspace: false,
  );

  factory WeaverMemberUxState.fromCapability(
    WorkspaceCapabilityState runtimeControl,
  ) {
    final available =
        runtimeControl.policyState == WorkspaceCapabilityPolicyState.allowed &&
        runtimeControl.readiness == WorkspaceCapabilityReadiness.ready &&
        runtimeControl.grants('agent-runtime.entitled');
    if (!available) {
      return WeaverMemberUxState(
        available: false,
        isBlocked:
            runtimeControl.readiness == WorkspaceCapabilityReadiness.blocked,
        modelAliases: const <String>[],
        allowedSkills: const <String>[],
        allowedPersonalConnections: const <String>[],
        canConfigureStyle: false,
        canConfigureMemory: false,
        canConfigureWorkspace: false,
        memberImpact: runtimeControl.memberImpact,
      );
    }

    return WeaverMemberUxState(
      available: true,
      isBlocked: false,
      modelAliases: const <String>[],
      allowedSkills: const <String>[],
      allowedPersonalConnections: const <String>[],
      canConfigureStyle: false,
      canConfigureMemory: false,
      canConfigureWorkspace: false,
      memberImpact: runtimeControl.memberImpact,
    );
  }

  final bool available;
  final bool isBlocked;
  final List<String> modelAliases;
  final List<String> allowedSkills;
  final List<String> allowedPersonalConnections;
  final bool canConfigureStyle;
  final bool canConfigureMemory;
  final bool canConfigureWorkspace;
  final String? memberImpact;

  bool get hasAnyPersonalSetting =>
      canConfigureStyle || canConfigureMemory || canConfigureWorkspace;
}
