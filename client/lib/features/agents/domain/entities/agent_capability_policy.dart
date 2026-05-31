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
    final weaver = workspaceCapabilities.weaver;
    final enabled =
        weaver.policyState == WorkspaceCapabilityPolicyState.allowed &&
        weaver.readiness == WorkspaceCapabilityReadiness.ready &&
        weaver.grants('weaver.enabled');
    final availability = switch (weaver.policyState) {
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
      weaverMemberUx: WeaverMemberUxState.fromCapability(weaver),
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

  factory WeaverMemberUxState.fromCapability(WorkspaceCapabilityState weaver) {
    final available =
        weaver.policyState == WorkspaceCapabilityPolicyState.allowed &&
        weaver.readiness == WorkspaceCapabilityReadiness.ready &&
        weaver.grants('weaver.enabled');
    if (!available) {
      return WeaverMemberUxState(
        available: false,
        isBlocked: weaver.readiness == WorkspaceCapabilityReadiness.blocked,
        modelAliases: const <String>[],
        allowedSkills: const <String>[],
        allowedPersonalConnections: const <String>[],
        canConfigureStyle: false,
        canConfigureMemory: false,
        canConfigureWorkspace: false,
        memberImpact: weaver.memberImpact,
      );
    }

    return WeaverMemberUxState(
      available: true,
      isBlocked: false,
      modelAliases: _labelsForPrefix(weaver, 'weaver.model_alias.'),
      allowedSkills: _labelsForPrefix(weaver, 'weaver.skill.'),
      allowedPersonalConnections: _labelsForPrefix(
        weaver,
        'weaver.personal_connection.',
      ),
      canConfigureStyle: weaver.grants('weaver.configure_style'),
      canConfigureMemory: weaver.grants('weaver.configure_memory'),
      canConfigureWorkspace: weaver.grants('weaver.configure_workspace'),
      memberImpact: weaver.memberImpact,
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

  static List<String> _labelsForPrefix(
    WorkspaceCapabilityState weaver,
    String prefix,
  ) {
    final labels =
        weaver.grantedCapabilities
            .where((grant) => grant.startsWith(prefix))
            .map((grant) => _humanizeGrant(grant.substring(prefix.length)))
            .where((label) => label.isNotEmpty)
            .toSet()
            .toList()
          ..sort();
    return labels;
  }

  static String _humanizeGrant(String rawValue) {
    final words = rawValue
        .replaceAll(RegExp(r'[_\-]+'), ' ')
        .trim()
        .split(RegExp(r'\s+'))
        .where((word) => word.isNotEmpty)
        .toList();
    return words
        .map(
          (word) => word.length == 1
              ? word.toUpperCase()
              : '${word[0].toUpperCase()}${word.substring(1)}',
        )
        .join(' ');
  }
}
