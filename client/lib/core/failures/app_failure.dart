enum AppFailureType { bootstrap, storage, validation, unknown }

/// App-level failure model used across repositories and presentation.
class AppFailure implements Exception {
  const AppFailure({required this.type, required this.message, this.cause});

  const AppFailure.bootstrap(String message, {Object? cause})
    : this(type: AppFailureType.bootstrap, message: message, cause: cause);

  const AppFailure.storage(String message, {Object? cause})
    : this(type: AppFailureType.storage, message: message, cause: cause);

  const AppFailure.validation(String message, {Object? cause})
    : this(type: AppFailureType.validation, message: message, cause: cause);

  const AppFailure.unknown(String message, {Object? cause})
    : this(type: AppFailureType.unknown, message: message, cause: cause);

  final AppFailureType type;
  final String message;
  final Object? cause;

  @override
  String toString() => 'AppFailure(type: $type, message: $message)';
}
