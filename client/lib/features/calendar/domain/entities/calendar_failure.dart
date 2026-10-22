enum CalendarOperationStage {
  context,
  discovery,
  profileTimeZone,
  agenda,
  read,
  decode,
  mutation,
}

enum CalendarFailureKind {
  session,
  conflict,
  permission,
  unsupportedEdit,
  unavailable,
}

/// Support-safe Calendar outcome; transport payloads and bearer values stay private.
class CalendarFailure implements Exception {
  const CalendarFailure(
    this.kind, {
    this.writeMayHaveCommitted = false,
    this.stage = CalendarOperationStage.decode,
    this.httpStatus,
    this.apiCode = 'unavailable',
  });
  final bool writeMayHaveCommitted;
  final CalendarFailureKind kind;
  final CalendarOperationStage stage;
  final int? httpStatus;
  final String apiCode;
  @override
  String toString() => 'CalendarFailure($kind)';
}
