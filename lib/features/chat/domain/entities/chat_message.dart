/// Stub chat entity to be replaced by Matrix-backed domain models later.
class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.sender,
    required this.body,
    required this.timestamp,
  });

  final String id;
  final String sender;
  final String body;
  final DateTime timestamp;
}
