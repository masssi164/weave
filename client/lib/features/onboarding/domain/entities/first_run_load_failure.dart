class FirstRunUnauthorizedFailure implements Exception {
  const FirstRunUnauthorizedFailure();

  @override
  String toString() => 'The Weave backend rejected the current session.';
}

class FirstRunBackendUnavailableFailure implements Exception {
  const FirstRunBackendUnavailableFailure(this.cause);

  final Object cause;

  @override
  String toString() => 'The Weave onboarding backend is unavailable: $cause';
}

class FirstRunInvalidPayloadFailure implements Exception {
  const FirstRunInvalidPayloadFailure(this.cause);

  final Object cause;

  @override
  String toString() =>
      'The Weave onboarding backend returned invalid data: $cause';
}
