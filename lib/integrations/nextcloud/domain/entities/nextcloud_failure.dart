enum NextcloudFailureType {
  cancelled,
  configuration,
  sessionRequired,
  invalidCredentials,
  protocol,
  storage,
  unsupportedPlatform,
  unknown,
}

class NextcloudFailure implements Exception {
  const NextcloudFailure({
    required this.type,
    required this.message,
    this.cause,
  });

  const NextcloudFailure.cancelled(String message, {Object? cause})
    : this(
        type: NextcloudFailureType.cancelled,
        message: message,
        cause: cause,
      );

  const NextcloudFailure.configuration(String message, {Object? cause})
    : this(
        type: NextcloudFailureType.configuration,
        message: message,
        cause: cause,
      );

  const NextcloudFailure.sessionRequired(String message, {Object? cause})
    : this(
        type: NextcloudFailureType.sessionRequired,
        message: message,
        cause: cause,
      );

  const NextcloudFailure.invalidCredentials(String message, {Object? cause})
    : this(
        type: NextcloudFailureType.invalidCredentials,
        message: message,
        cause: cause,
      );

  const NextcloudFailure.protocol(String message, {Object? cause})
    : this(type: NextcloudFailureType.protocol, message: message, cause: cause);

  const NextcloudFailure.storage(String message, {Object? cause})
    : this(type: NextcloudFailureType.storage, message: message, cause: cause);

  const NextcloudFailure.unsupportedPlatform(String message, {Object? cause})
    : this(
        type: NextcloudFailureType.unsupportedPlatform,
        message: message,
        cause: cause,
      );

  const NextcloudFailure.unknown(String message, {Object? cause})
    : this(type: NextcloudFailureType.unknown, message: message, cause: cause);

  final NextcloudFailureType type;
  final String message;
  final Object? cause;

  @override
  String toString() => 'NextcloudFailure(type: $type, message: $message)';
}
