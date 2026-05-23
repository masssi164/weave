enum FilesFailureType {
  cancelled,
  configuration,
  sessionRequired,
  invalidCredentials,
  protocol,
  storage,
  unsupportedPlatform,
  unknown,
}

class FilesFailure implements Exception {
  const FilesFailure({required this.type, required this.message, this.cause});

  const FilesFailure.cancelled(String message, {Object? cause})
    : this(type: FilesFailureType.cancelled, message: message, cause: cause);

  const FilesFailure.configuration(String message, {Object? cause})
    : this(
        type: FilesFailureType.configuration,
        message: message,
        cause: cause,
      );

  const FilesFailure.sessionRequired(String message, {Object? cause})
    : this(
        type: FilesFailureType.sessionRequired,
        message: message,
        cause: cause,
      );

  const FilesFailure.invalidCredentials(String message, {Object? cause})
    : this(
        type: FilesFailureType.invalidCredentials,
        message: message,
        cause: cause,
      );

  const FilesFailure.protocol(String message, {Object? cause})
    : this(type: FilesFailureType.protocol, message: message, cause: cause);

  const FilesFailure.storage(String message, {Object? cause})
    : this(type: FilesFailureType.storage, message: message, cause: cause);

  const FilesFailure.unsupportedPlatform(String message, {Object? cause})
    : this(
        type: FilesFailureType.unsupportedPlatform,
        message: message,
        cause: cause,
      );

  const FilesFailure.unknown(String message, {Object? cause})
    : this(type: FilesFailureType.unknown, message: message, cause: cause);

  final FilesFailureType type;
  final String message;
  final Object? cause;

  @override
  String toString() => 'FilesFailure(type: $type, message: $message)';
}
