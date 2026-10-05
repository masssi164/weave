enum CalendarFailureKind {
  session,
  conflict,
  permission,
  unsupportedEdit,
  unavailable,
}

/// Support-safe Calendar outcome; transport payloads and bearer values stay private.
class CalendarFailure implements Exception {
  const CalendarFailure(this.kind);
  final CalendarFailureKind kind;
  @override
  String toString() => 'CalendarFailure($kind)';
}
