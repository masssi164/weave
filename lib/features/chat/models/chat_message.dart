/// A stub chat message model.
///
/// This will be replaced with the Matrix SDK message type once
/// the network layer is integrated.
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
