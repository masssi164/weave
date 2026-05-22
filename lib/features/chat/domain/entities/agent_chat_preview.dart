enum AgentChatAvailability { previewOnly, adminSetupRequired, blocked }

enum AgentChatPreviewKind { personalAssistant, channelAgent }

class AgentChatPreviewCapability {
  const AgentChatPreviewCapability({
    required this.id,
    required this.kind,
    required this.availability,
    this.canStart = false,
  });

  final String id;
  final AgentChatPreviewKind kind;
  final AgentChatAvailability availability;

  /// Agent chats remain non-startable until owner/admin policy, consent,
  /// audit, and connector gates are connected through the backend facade.
  final bool canStart;
}
