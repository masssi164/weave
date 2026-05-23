import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/chat/domain/entities/agent_chat_preview.dart';

final agentChatPreviewProvider = Provider<List<AgentChatPreviewCapability>>((
  ref,
) {
  final policy = ref
      .watch(agentCapabilityPolicyProvider)
      .when(
        data: (value) => value,
        error: (_, _) =>
            AgentCapabilityPolicy.failClosed(canManageCapabilities: false),
        loading: () =>
            AgentCapabilityPolicy.failClosed(canManageCapabilities: false),
      );

  return <AgentChatPreviewCapability>[
    _previewFor(policy.stateFor(AgentCapability.personalAssistant)),
    _previewFor(policy.stateFor(AgentCapability.channelAgent)),
  ];
});

AgentChatPreviewCapability _previewFor(AgentCapabilityState state) {
  return AgentChatPreviewCapability(
    id: switch (state.capability) {
      AgentCapability.personalAssistant => 'personal-assistant',
      AgentCapability.channelAgent => 'channel-agent',
    },
    kind: switch (state.capability) {
      AgentCapability.personalAssistant =>
        AgentChatPreviewKind.personalAssistant,
      AgentCapability.channelAgent => AgentChatPreviewKind.channelAgent,
    },
    availability: switch (state.availability) {
      AgentCapabilityAvailability.adminSetupRequired =>
        AgentChatAvailability.adminSetupRequired,
      AgentCapabilityAvailability.disabledByPolicy =>
        AgentChatAvailability.disabledByPolicy,
      AgentCapabilityAvailability.blocked => AgentChatAvailability.blocked,
    },
    canStart: state.canStart,
  );
}
