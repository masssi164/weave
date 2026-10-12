/// A member returned by the authorized Weave Matrix user directory.
class ChatParticipant {
  const ChatParticipant({required this.userId, required this.displayName});
  final String userId;
  final String displayName;
}
