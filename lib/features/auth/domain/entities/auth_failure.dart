enum AuthFailureType {
  cancelled,
  configuration,
  protocol,
  storage,
  unsupportedPlatform,
  unknown,
}

class AuthFailure implements Exception {
  const AuthFailure({required this.type, required this.message, this.cause});

  const AuthFailure.cancelled(String message, {Object? cause})
    : this(type: AuthFailureType.cancelled, message: message, cause: cause);

  const AuthFailure.configuration(String message, {Object? cause})
    : this(type: AuthFailureType.configuration, message: message, cause: cause);

  const AuthFailure.protocol(String message, {Object? cause})
    : this(type: AuthFailureType.protocol, message: message, cause: cause);

  const AuthFailure.storage(String message, {Object? cause})
    : this(type: AuthFailureType.storage, message: message, cause: cause);

  const AuthFailure.unsupportedPlatform(String message, {Object? cause})
    : this(
        type: AuthFailureType.unsupportedPlatform,
        message: message,
        cause: cause,
      );

  const AuthFailure.unknown(String message, {Object? cause})
    : this(type: AuthFailureType.unknown, message: message, cause: cause);

  final AuthFailureType type;
  final String message;
  final Object? cause;

  @override
  String toString() => 'AuthFailure(type: $type, message: $message)';
}
