enum AgentChatAvailability { previewOnly, adminSetupRequired, blocked }

enum AgentChatPreviewKind { personalAssistant, channelAgent }

class AgentChatPreviewCapability {
  const AgentChatPreviewCapability({
    required this.id,
    required this.kind,
    required this.availability,
  });

  final String id;
  final AgentChatPreviewKind kind;
  final AgentChatAvailability availability;

  /// Agent chats are intentionally non-startable until backend capability,
  /// consent, audit, and policy gates are connected.
  bool get canStart => false;
}
