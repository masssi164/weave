import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/agent_chat_preview.dart';

final agentChatPreviewProvider = Provider<List<AgentChatPreviewCapability>>((
  ref,
) {
  return const <AgentChatPreviewCapability>[
    AgentChatPreviewCapability(
      id: 'personal-assistant',
      kind: AgentChatPreviewKind.personalAssistant,
      availability: AgentChatAvailability.previewOnly,
    ),
    AgentChatPreviewCapability(
      id: 'channel-agent',
      kind: AgentChatPreviewKind.channelAgent,
      availability: AgentChatAvailability.adminSetupRequired,
    ),
  ];
});
