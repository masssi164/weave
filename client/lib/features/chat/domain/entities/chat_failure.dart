enum ChatFailureType {
  cancelled,
  configuration,
  sessionRequired,
  unsupportedConfiguration,
  protocol,
  storage,
  unsupportedPlatform,
  unknown,
}

class ChatFailure implements Exception {
  const ChatFailure({required this.type, required this.message, this.cause});

  const ChatFailure.cancelled(String message, {Object? cause})
    : this(type: ChatFailureType.cancelled, message: message, cause: cause);

  const ChatFailure.configuration(String message, {Object? cause})
    : this(type: ChatFailureType.configuration, message: message, cause: cause);

  const ChatFailure.sessionRequired(String message, {Object? cause})
    : this(
        type: ChatFailureType.sessionRequired,
        message: message,
        cause: cause,
      );

  const ChatFailure.unsupportedConfiguration(String message, {Object? cause})
    : this(
        type: ChatFailureType.unsupportedConfiguration,
        message: message,
        cause: cause,
      );

  const ChatFailure.protocol(String message, {Object? cause})
    : this(type: ChatFailureType.protocol, message: message, cause: cause);

  const ChatFailure.storage(String message, {Object? cause})
    : this(type: ChatFailureType.storage, message: message, cause: cause);

  const ChatFailure.unsupportedPlatform(String message, {Object? cause})
    : this(
        type: ChatFailureType.unsupportedPlatform,
        message: message,
        cause: cause,
      );

  const ChatFailure.unknown(String message, {Object? cause})
    : this(type: ChatFailureType.unknown, message: message, cause: cause);

  final ChatFailureType type;
  final String message;
  final Object? cause;

  @override
  String toString() => 'ChatFailure(type: $type, message: $message)';
}
